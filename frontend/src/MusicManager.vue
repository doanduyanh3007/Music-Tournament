<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  isAdmin: { type: Boolean, default: false }
})

const emit = defineEmits(['add-to-tournament'])

// Current active tab: 'quick-search' | 'bulk-import' | 'library' | 'playlists'
const activeTab = ref('quick-search')

// --- Global Alerts / Notices ---
const alertMessage = ref('')
const alertType = ref('success') // 'success' | 'error' | 'info'

function showAlert(msg, type = 'success') {
  alertMessage.value = msg
  alertType.value = type
  setTimeout(() => {
    if (alertMessage.value === msg) alertMessage.value = ''
  }, 5000)
}

// --- Quiz Pools ---
const pools = ref([])
const selectedPoolId = ref(null)

async function fetchPools() {
  try {
    const res = await fetch('/api/quiz/pools?all=true')
    if (res.ok) {
      pools.value = await res.json()
    }
  } catch (e) {}
}

// ============================================================
// 1. QUICK SEARCH & ADD TAB (YouTube & Spotify / iTunes)
// ============================================================
const quickSearchQuery = ref('')
const quickSearchFilter = ref('all') // 'all' | 'spotify' | 'youtube'
const quickSearchResults = ref([])
const quickSearchLoading = ref(false)
const selectedSongIndexes = ref(new Set())
const quickBatchAdding = ref(false)
const quickBatchProgress = ref({ current: 0, total: 0, text: '' })

// Audio Preview
const activeAudioPreviewUrl = ref('')
let audioPreviewInstance = null

const hotTags = [
  '🔥 Anime Hits',
  '🎵 YOASOBI',
  '⭐ V-Pop Top',
  '🎧 K-Pop',
  '🎸 J-Rock',
  '⚡ Touhou',
  '🌸 Studio Ghibli'
]

async function executeQuickSearch() {
  const q = quickSearchQuery.value.trim()
  if (!q) return
  quickSearchLoading.value = true
  selectedSongIndexes.value.clear()
  try {
    const res = await fetch(`/api/search?q=${encodeURIComponent(q)}&source=${encodeURIComponent(quickSearchFilter.value)}&limit=24`)
    if (!res.ok) throw new Error('Không thể tìm kiếm bài hát lúc này')
    const data = await res.json()
    quickSearchResults.value = data.results || []
    if (quickSearchResults.value.length === 0) {
      showAlert('Không tìm thấy bài hát nào. Hãy thử từ khóa khác!', 'info')
    } else {
      // Auto-select all by default for fast bulk adding
      quickSearchResults.value.forEach((_, idx) => selectedSongIndexes.value.add(idx))
    }
  } catch (err) {
    showAlert(err.message, 'error')
  } finally {
    quickSearchLoading.value = false
  }
}

function selectQuickTag(tag) {
  const clean = tag.replace(/^[^\w\s\u00C0-\u1EF9]+/, '').trim()
  quickSearchQuery.value = clean
  executeQuickSearch()
}

watch(quickSearchFilter, () => {
  if (quickSearchQuery.value.trim()) {
    executeQuickSearch()
  }
})

function toggleSelectSong(idx) {
  if (selectedSongIndexes.value.has(idx)) {
    selectedSongIndexes.value.delete(idx)
  } else {
    selectedSongIndexes.value.add(idx)
  }
}

function selectAllQuickSearch() {
  quickSearchResults.value.forEach((_, idx) => selectedSongIndexes.value.add(idx))
}

function deselectAllQuickSearch() {
  selectedSongIndexes.value.clear()
}

function toggleAudioPreview(url) {
  if (!url) return
  if (activeAudioPreviewUrl.value === url) {
    if (audioPreviewInstance) {
      audioPreviewInstance.pause()
      audioPreviewInstance = null
    }
    activeAudioPreviewUrl.value = ''
  } else {
    if (audioPreviewInstance) audioPreviewInstance.pause()
    audioPreviewInstance = new Audio(url)
    activeAudioPreviewUrl.value = url
    audioPreviewInstance.play().catch(() => {
      showAlert('Trình duyệt chặn tự động phát audio', 'error')
    })
    audioPreviewInstance.onended = () => {
      activeAudioPreviewUrl.value = ''
      audioPreviewInstance = null
    }
  }
}

// Add selected songs to destination
async function addSelectedQuickSongs(target = 'library') {
  const selected = Array.from(selectedSongIndexes.value).map(idx => quickSearchResults.value[idx]).filter(Boolean)
  if (selected.length === 0) {
    showAlert('Vui lòng chọn ít nhất 1 bài hát!', 'error')
    return
  }

  quickBatchAdding.value = true
  quickBatchProgress.value = { current: 0, total: selected.length, text: 'Đang chuẩn bị thêm...' }

  try {
    const payload = {
      songs: selected.map(s => ({
        title: s.title,
        artist: s.artist,
        youtubeUrl: s.youtubeUrl,
        spotifyUrl: s.spotifyUrl,
        artworkUrl: s.artwork,
        previewUrl: s.previewUrl,
        genre: s.genre || 'General'
      })),
      quizPoolId: selectedPoolId.value,
      addToQuizLibrary: true,
      enrichMetadata: true
    }

    const res = await fetch('/api/quiz/songs/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    if (!res.ok) throw new Error('Lỗi khi thêm bài hát hàng loạt')
    const result = await res.json()

    let msg = `Đã thêm thành công ${result.addedCount} bài mới`
    if (result.duplicateCount > 0) {
      msg += ` (${result.duplicateCount} bài đã có được cập nhật thông tin)`
    }
    if (result.poolLinkedCount > 0) {
      msg += ` và liên kết ${result.poolLinkedCount} bài vào Quiz Pool`
    }
    showAlert(msg + '!', 'success')

    // Also emit to tournament if user wants
    emit('add-to-tournament', selected)

    // Refresh library count
    fetchSongs(0)
  } catch (err) {
    showAlert(err.message, 'error')
  } finally {
    quickBatchAdding.value = false
  }
}

// ============================================================
// 2. BULK IMPORT TAB (Text lines / CSV / TXT upload)
// ============================================================
const bulkText = ref('')
const bulkParsedRows = ref([])
const bulkSelectedRows = ref(new Set())
const bulkAutoEnrich = ref(true)
const bulkImporting = ref(false)

function parseCSVLine(line) {
  const result = []
  let insideQuotes = false
  let current = ''
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (c === '"') {
      if (insideQuotes && line[i + 1] === '"') {
        current += '"'
        i++
      } else {
        insideQuotes = !insideQuotes
      }
    } else if (c === ',' && !insideQuotes) {
      result.push(current.trim())
      current = ''
    } else {
      current += c
    }
  }
  result.push(current.trim())
  return result
}

function parseBulkText() {
  const lines = bulkText.value.split(/\r?\n/).map(l => l.trim()).filter(Boolean)
  if (lines.length === 0) {
    showAlert('Vui lòng nhập hoặc dán danh sách bài hát!', 'error')
    return
  }

  // Check if first line is a CSV Header (e.g. Exportify or standard CSV)
  const firstCols = parseCSVLine(lines[0]).map(c => c.toLowerCase().replace(/[^a-z0-9]/g, ''))
  const titleColIdx = firstCols.findIndex(c => c.includes('trackname') || c.includes('title') || c.includes('songname') || c === 'song' || c === 'name')
  const artistColIdx = firstCols.findIndex(c => c.includes('artist') || c.includes('singer'))
  const idColIdx = firstCols.findIndex(c => c === 'spotifyid' || c === 'id' || c === 'trackid')
  const linkColIdx = firstCols.findIndex(c => c.includes('url') || c.includes('link') || c.includes('uri'))
  const previewColIdx = firstCols.findIndex(c => c.includes('preview'))
  const genreColIdx = firstCols.findIndex(c => c.includes('genre'))
  const artColIdx = firstCols.findIndex(c => c.includes('image') || c.includes('cover') || c.includes('artwork'))

  const isCsvWithHeader = titleColIdx !== -1 && lines.length > 1
  const startIdx = isCsvWithHeader ? 1 : 0

  const rows = []
  for (let i = startIdx; i < lines.length; i++) {
    const line = lines[i]
    let title = ''
    let artist = ''
    let link = ''
    let previewUrl = ''
    let artworkUrl = ''
    let genre = 'General'

    if (isCsvWithHeader) {
      const cols = parseCSVLine(line)
      title = cols[titleColIdx] || ''
      artist = (artistColIdx !== -1 ? cols[artistColIdx] : '') || 'Unknown Artist'
      if (linkColIdx !== -1 && cols[linkColIdx]) {
        link = cols[linkColIdx]
      } else if (idColIdx !== -1 && cols[idColIdx]) {
        link = 'https://open.spotify.com/track/' + cols[idColIdx]
      }
      if (previewColIdx !== -1 && cols[previewColIdx]) previewUrl = cols[previewColIdx]
      if (artColIdx !== -1 && cols[artColIdx]) artworkUrl = cols[artColIdx]
      if (genreColIdx !== -1 && cols[genreColIdx]) genre = cols[genreColIdx]
    } else {
      if (line.includes(',') && !line.includes(' - ') && !line.includes('|')) {
        const cols = parseCSVLine(line)
        if (cols.length >= 2) {
          title = cols[0]
          artist = cols[1]
          if (cols[2]) link = cols[2]
        }
      }

      if (!title) {
        let contentPart = line
        if (line.includes('|')) {
          const parts = line.split('|')
          contentPart = parts[0].trim()
          link = parts.slice(1).join('|').trim()
        }

        if (contentPart.includes(' - ') || contentPart.includes(' – ')) {
          const p = contentPart.split(/ - | – /)
          title = p[0].trim()
          artist = p.slice(1).join(' - ').trim()
        } else if (contentPart.includes(' / ')) {
          const p = contentPart.split(' / ')
          title = p[0].trim()
          artist = p[1].trim()
        } else {
          title = contentPart
          artist = 'Unknown Artist'
        }
      }
    }

    // Clean leading numbers if any (e.g. "1. Song Name")
    title = title.replace(/^\d+[\.\)\-\s]+/, '').trim()
    if (!title) continue

    rows.push({
      title,
      artist: artist || 'Unknown Artist',
      link,
      previewUrl,
      artworkUrl,
      youtubeUrl: (link.includes('youtube.com') || link.includes('youtu.be')) ? link : '',
      spotifyUrl: (link.includes('spotify.com') || link.startsWith('spotify:')) ? link : '',
      genre: genre || 'General',
      isDuplicate: false
    })
  }

  bulkParsedRows.value = rows
  bulkSelectedRows.value = new Set(rows.map((_, i) => i))
  showAlert(`Đã phân tích ${rows.length} bài hát! Xem trước danh sách bên dưới trước khi nhập.`, 'info')
}

function handleFileUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (event) => {
    bulkText.value = event.target?.result || ''
    parseBulkText()
  }
  reader.readAsText(file)
}

function toggleSelectBulkRow(i) {
  if (bulkSelectedRows.value.has(i)) bulkSelectedRows.value.delete(i)
  else bulkSelectedRows.value.add(i)
}

function selectAllBulk() {
  bulkParsedRows.value.forEach((_, i) => bulkSelectedRows.value.add(i))
}

function deselectAllBulk() {
  bulkSelectedRows.value.clear()
}

function removeBulkRow(i) {
  bulkParsedRows.value.splice(i, 1)
  bulkSelectedRows.value.delete(i)
}

async function executeBulkImport() {
  const selected = Array.from(bulkSelectedRows.value).map(i => bulkParsedRows.value[i]).filter(Boolean)
  if (selected.length === 0) {
    showAlert('Chưa chọn bài hát nào để nhập!', 'error')
    return
  }

  bulkImporting.value = true
  try {
    const payload = {
      songs: selected.map(r => ({
        title: r.title,
        artist: r.artist,
        youtubeUrl: r.youtubeUrl,
        spotifyUrl: r.spotifyUrl,
        link: r.link,
        genre: r.genre || 'General',
        artworkUrl: r.artworkUrl || null,
        previewUrl: r.previewUrl || null
      })),
      quizPoolId: selectedPoolId.value,
      addToQuizLibrary: true,
      enrichMetadata: bulkAutoEnrich.value
    }

    const res = await fetch('/api/quiz/songs/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    if (!res.ok) throw new Error('Lỗi khi nhập bài hát hàng loạt')
    const result = await res.json()

    showAlert(`Đã nhập xong: ${result.addedCount} bài mới, ${result.duplicateCount} bài trùng!`, 'success')
    emit('add-to-tournament', selected)

    bulkParsedRows.value = []
    bulkSelectedRows.value.clear()
    bulkText.value = ''
    fetchSongs(0)
  } catch (err) {
    showAlert(err.message, 'error')
  } finally {
    bulkImporting.value = false
  }
}

// ============================================================
// 3. SONG LIBRARY TAB
// ============================================================
const songs = ref([])
const totalElements = ref(0)
const totalPages = ref(1)
const currentPage = ref(0)
const pageSize = ref(15)
const loading = ref(false)

const searchQuery = ref('')
const selectedGenre = ref('')
const selectedDiffTier = ref('')
const activeOnly = ref(false)
const availableGenres = ref([])

// Sorting
const selectedSort = ref('id_desc') // 'id_desc' | 'artist_asc' | 'artist_desc' | 'title_asc' | 'diff_asc' | 'diff_desc'

// Bulk Enrich Preview
const enrichInProgress = ref(false)
const enrichStatus = ref({ isRunning: false, total: 0, processed: 0, enriched: 0, errors: 0, currentSong: '', lastMessage: '', missingPreviewCount: 0 })
let enrichPollTimer = null

// Page Jump
const pageJumpInput = ref(1)

function jumpToPage() {
  let p = parseInt(pageJumpInput.value)
  if (isNaN(p) || p < 1) p = 1
  if (p > totalPages.value) p = totalPages.value
  fetchSongs(p - 1)
}

// Deduplication
const dedupInProgress = ref(false)

async function runDeduplication() {
  if (dedupInProgress.value) return
  if (!confirm('Hệ thống sẽ quét kho và tự động gộp các bài hát bị trùng lặp (khác biệt dấu phân cách ca sĩ, cùng mã Spotify/YouTube, hoặc cùng tên). Bạn có muốn tiếp tục?')) return
  dedupInProgress.value = true
  showAlert('Đang kiểm tra và gộp các bài hát trùng lặp...', 'info')
  try {
    const res = await fetch('/api/quiz/songs/deduplicate', { method: 'POST' })
    if (!res.ok) throw new Error('Không thể gộp bài trùng lúc này')
    const data = await res.json()
    if (data.mergedCount > 0) {
      showAlert(`Đã gộp thành công ${data.mergedCount} bài hát bị trùng lặp!`, 'success')
    } else {
      showAlert('Không phát hiện bài hát nào bị trùng lặp trong kho!', 'info')
    }
    fetchSongs(currentPage.value)
  } catch (err) {
    showAlert(err.message, 'error')
  } finally {
    dedupInProgress.value = false
  }
}

async function checkEnrichStatus() {
  try {
    const res = await fetch('/api/quiz/songs/enrich-status')
    if (res.ok) {
      const data = await res.json()
      enrichStatus.value = data
      enrichInProgress.value = !!data.isRunning
      if (!data.isRunning && enrichPollTimer) {
        clearInterval(enrichPollTimer)
        enrichPollTimer = null
      }
    }
  } catch (e) {}
}

async function startEnrichment() {
  if (enrichInProgress.value) return
  try {
    const res = await fetch('/api/quiz/songs/enrich-all', { method: 'POST' })
    if (!res.ok) throw new Error('Không thể bắt đầu tìm preview')
    enrichInProgress.value = true
    showAlert('Đã bắt đầu tìm preview & bìa cho các bài hát!', 'info')
    if (enrichPollTimer) clearInterval(enrichPollTimer)
    enrichPollTimer = setInterval(async () => {
      await checkEnrichStatus()
      if (!enrichInProgress.value) {
        showAlert('Hoàn tất tìm preview!', 'success')
        fetchSongs(currentPage.value)
      }
    }, 2500)
    await checkEnrichStatus()
  } catch (err) {
    showAlert(err.message, 'error')
  }
}

async function enrichSingle(song) {
  try {
    showAlert(`Đang tìm preview cho "${song.title}"...`, 'info')
    const res = await fetch(`/api/quiz/songs/${song.id}/enrich`, { method: 'POST' })
    if (!res.ok) throw new Error('Không tìm thấy thông tin phù hợp')
    const updated = await res.json()
    const idx = songs.value.findIndex(s => s.id === song.id)
    if (idx !== -1) {
      songs.value[idx] = updated
    }
    showAlert(`Đã cập nhật bài hát "${song.title}"!`, 'success')
  } catch (err) {
    showAlert(err.message, 'error')
  }
}

const playingSongId = ref(null)
const libraryAudioElement = ref(null)

// Modal Add / Edit Song
const showSongModal = ref(false)
const isEditing = ref(false)
const songForm = ref({
  id: null,
  title: '',
  artist: '',
  aliases: '',
  genre: 'J-Pop',
  difficulty: 40,
  artworkUrl: '',
  previewUrl: '',
  youtubeUrl: '',
  spotifyUrl: '',
  active: true
})
const songModalError = ref('')
const savingSong = ref(false)
const autoEnrichingSong = ref(false)

async function fetchSongs(page = 0) {
  loading.value = true
  currentPage.value = page
  try {
    let minDiff = ''
    let maxDiff = ''
    if (selectedDiffTier.value === 'easy') { minDiff = 0; maxDiff = 20 }
    else if (selectedDiffTier.value === 'normal') { minDiff = 21; maxDiff = 40 }
    else if (selectedDiffTier.value === 'hard') { minDiff = 41; maxDiff = 65 }
    else if (selectedDiffTier.value === 'expert') { minDiff = 66; maxDiff = 85 }
    else if (selectedDiffTier.value === 'master') { minDiff = 86; maxDiff = 100 }

    let sortBy = 'id'
    let sortDir = 'desc'
    if (selectedSort.value === 'artist_asc') { sortBy = 'artist'; sortDir = 'asc' }
    else if (selectedSort.value === 'artist_desc') { sortBy = 'artist'; sortDir = 'desc' }
    else if (selectedSort.value === 'title_asc') { sortBy = 'title'; sortDir = 'asc' }
    else if (selectedSort.value === 'diff_asc') { sortBy = 'difficulty'; sortDir = 'asc' }
    else if (selectedSort.value === 'diff_desc') { sortBy = 'difficulty'; sortDir = 'desc' }

    const params = new URLSearchParams({
      page: page.toString(),
      size: pageSize.value.toString(),
      query: searchQuery.value.trim(),
      genre: selectedGenre.value,
      activeOnly: activeOnly.value ? 'true' : 'false',
      sortBy: sortBy,
      sortDir: sortDir
    })
    if (minDiff !== '') params.append('minDiff', minDiff)
    if (maxDiff !== '') params.append('maxDiff', maxDiff)

    const res = await fetch('/api/quiz/songs?' + params.toString())
    if (!res.ok) throw new Error('Không thể tải danh sách bài hát')
    const data = await res.json()
    songs.value = data.content || []
    totalElements.value = data.totalElements || 0
    totalPages.value = data.totalPages || 1
  } catch (err) {
    showAlert(err.message, 'error')
  } finally {
    loading.value = false
  }
}

async function fetchGenres() {
  try {
    const res = await fetch('/api/quiz/songs/genres')
    if (res.ok) availableGenres.value = await res.json()
  } catch (e) {}
}

function toggleLibraryAudio(song) {
  if (!song.previewUrl) {
    showAlert('Bài hát chưa có preview MP3. Có thể nghe qua link YouTube.', 'info')
    return
  }
  if (playingSongId.value === song.id) {
    if (libraryAudioElement.value) {
      libraryAudioElement.value.pause()
      libraryAudioElement.value = null
    }
    playingSongId.value = null
  } else {
    if (libraryAudioElement.value) libraryAudioElement.value.pause()
    const audio = new Audio(song.previewUrl)
    audio.play().catch(() => showAlert('Trình duyệt chặn tự động phát audio', 'error'))
    audio.onended = () => {
      playingSongId.value = null
      libraryAudioElement.value = null
    }
    libraryAudioElement.value = audio
    playingSongId.value = song.id
  }
}

async function toggleActive(song) {
  try {
    const res = await fetch(`/api/quiz/songs/${song.id}/toggle-active`, { method: 'PATCH' })
    if (res.ok) {
      const data = await res.json()
      song.active = data.active
      showAlert(song.active ? `Đã bật: ${song.title}` : `Đã tắt: ${song.title}`)
    }
  } catch (e) {
    showAlert('Không thể thay đổi trạng thái bài hát', 'error')
  }
}

function openAddSongModal() {
  isEditing.value = false
  songForm.value = {
    id: null,
    title: '',
    artist: '',
    aliases: '',
    genre: 'Anime / J-Pop',
    difficulty: 40,
    artworkUrl: '',
    previewUrl: '',
    youtubeUrl: '',
    spotifyUrl: '',
    active: true
  }
  songModalError.value = ''
  showSongModal.value = true
}

function openEditSongModal(song) {
  isEditing.value = true
  songForm.value = {
    id: song.id,
    title: song.title,
    artist: song.artist,
    aliases: song.aliases || '',
    genre: song.genre || 'General',
    difficulty: song.difficulty != null ? song.difficulty : 50,
    artworkUrl: song.artworkUrl || '',
    previewUrl: song.previewUrl || '',
    youtubeUrl: song.youtubeUrl || '',
    spotifyUrl: song.spotifyUrl || '',
    active: song.active !== false
  }
  songModalError.value = ''
  showSongModal.value = true
}

// Auto-enrich in modal
async function enrichModalData() {
  if (!songForm.value.title.trim() && !songForm.value.youtubeUrl.trim() && !songForm.value.spotifyUrl.trim()) {
    songModalError.value = 'Vui lòng nhập tên bài hát hoặc dán link YouTube/Spotify trước khi tìm thông tin!'
    return
  }
  autoEnrichingSong.value = true
  songModalError.value = ''
  try {
    const params = new URLSearchParams({
      title: songForm.value.title.trim(),
      artist: songForm.value.artist.trim(),
      link: songForm.value.youtubeUrl || songForm.value.spotifyUrl || ''
    })
    const res = await fetch('/api/quiz/songs/enrich?' + params.toString())
    if (res.ok) {
      const data = await res.json()
      if (data.title && !songForm.value.title) songForm.value.title = data.title
      if (data.artist && (!songForm.value.artist || songForm.value.artist === 'Unknown Artist')) songForm.value.artist = data.artist
      if (data.artworkUrl && !songForm.value.artworkUrl) songForm.value.artworkUrl = data.artworkUrl
      if (data.previewUrl && !songForm.value.previewUrl) songForm.value.previewUrl = data.previewUrl
      if (data.genre && data.genre !== 'General') songForm.value.genre = data.genre
      if (data.difficulty != null) songForm.value.difficulty = data.difficulty
      showAlert('Đã tự động bổ sung bìa, audio preview và thể loại!', 'success')
    }
  } catch (e) {
    songModalError.value = 'Không thể tự động tìm thông tin lúc này'
  } finally {
    autoEnrichingSong.value = false
  }
}

async function saveSong() {
  if (!songForm.value.title.trim()) {
    songModalError.value = 'Vui lòng nhập tên bài hát'
    return
  }
  savingSong.value = true
  songModalError.value = ''
  try {
    const url = isEditing.value ? `/api/quiz/songs/${songForm.value.id}` : '/api/quiz/songs'
    const method = isEditing.value ? 'PUT' : 'POST'
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(songForm.value)
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || 'Lỗi khi lưu bài hát')
    }
    showSongModal.value = false
    showAlert(isEditing.value ? 'Đã cập nhật bài hát!' : 'Đã thêm bài hát mới vào kho!')
    await fetchSongs(currentPage.value)
    await fetchGenres()
  } catch (e) {
    songModalError.value = e.message
  } finally {
    savingSong.value = false
  }
}

async function deleteSong(song) {
  if (!confirm(`Bạn có chắc muốn xóa bài hát "${song.title}" khỏi kho nhạc không?`)) return
  try {
    const res = await fetch(`/api/quiz/songs/${song.id}`, { method: 'DELETE' })
    if (res.ok) {
      showAlert(`Đã xóa bài hát "${song.title}"`)
      fetchSongs(currentPage.value)
    }
  } catch (e) {
    showAlert('Không thể xóa bài hát', 'error')
  }
}

async function importFromTournaments() {
  if (!confirm('Sao chép toàn bộ bài hát từ các giải đấu hiện có vào kho Quiz? Hệ thống tự động lọc trùng và lấy preview audio.')) return
  loading.value = true
  try {
    const res = await fetch('/api/quiz/songs/import-from-tournaments', { method: 'POST' })
    if (!res.ok) throw new Error('Lỗi khi nhập dữ liệu')
    const data = await res.json()
    showAlert(`Đã nhập thành công ${data.importedCount} bài mới (${data.skippedDuplicateCount} bài trùng đã bỏ qua)!`)
    fetchSongs(0)
    fetchGenres()
  } catch (e) {
    showAlert(e.message, 'error')
  } finally {
    loading.value = false
  }
}

// ============================================================
// 4. PLAYLIST SYNC TAB (YouTube & Spotify)
// ============================================================
const playlists = ref([])
const newPlaylistUrl = ref('')
const newPlaylistTitle = ref('')
const playlistError = ref('')
const addingPlaylist = ref(false)
let syncPollingTimer = null

async function fetchPlaylists() {
  try {
    const res = await fetch('/api/quiz/playlists')
    if (res.ok) {
      playlists.value = await res.json()
    }
  } catch (e) {}
}

async function addPlaylist() {
  const url = newPlaylistUrl.value.trim()
  if (!url) {
    playlistError.value = 'Vui lòng dán link playlist YouTube hoặc Spotify'
    return
  }
  addingPlaylist.value = true
  playlistError.value = ''
  try {
    const res = await fetch('/api/quiz/playlists', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        url,
        title: newPlaylistTitle.value.trim()
      })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || 'Không thể thêm playlist')
    }
    const created = await res.json()
    newPlaylistUrl.value = ''
    newPlaylistTitle.value = ''
    showAlert('Đã thêm playlist! Bắt đầu đồng bộ tự động...', 'success')
    await fetchPlaylists()
    triggerPlaylistSync(created.id)
  } catch (e) {
    playlistError.value = e.message
  } finally {
    addingPlaylist.value = false
  }
}

async function triggerPlaylistSync(playlistId) {
  try {
    const res = await fetch(`/api/quiz/playlists/${playlistId}/sync`, { method: 'POST' })
    if (res.ok) {
      showAlert('Đang bắt đầu đồng bộ bài hát trong nền...')
      fetchPlaylists()
    }
  } catch (e) {
    showAlert('Lỗi khi kích hoạt đồng bộ', 'error')
  }
}

async function deletePlaylist(playlist) {
  if (!confirm(`Xóa playlist "${playlist.title}"? Các bài hát đã đồng bộ vào kho vẫn được giữ lại.`)) return
  try {
    const res = await fetch(`/api/quiz/playlists/${playlist.id}`, { method: 'DELETE' })
    if (res.ok) {
      showAlert('Đã xóa playlist')
      fetchPlaylists()
    }
  } catch (e) {
    showAlert('Lỗi khi xóa playlist', 'error')
  }
}

function getTierBadgeClass(diff) {
  if (diff <= 20) return 'badge-easy'
  if (diff <= 40) return 'badge-normal'
  if (diff <= 65) return 'badge-hard'
  if (diff <= 85) return 'badge-expert'
  return 'badge-master'
}

function getTierLabel(diff) {
  if (diff <= 20) return 'Easy'
  if (diff <= 40) return 'Normal'
  if (diff <= 65) return 'Hard'
  if (diff <= 85) return 'Expert'
  return 'Master'
}

// Watch filters in Library
watch([searchQuery, selectedGenre, selectedDiffTier, activeOnly, selectedSort], () => {
  fetchSongs(0)
})

watch(currentPage, (val) => {
  pageJumpInput.value = val + 1
})

onMounted(() => {
  fetchSongs(0)
  fetchGenres()
  fetchPlaylists()
  fetchPools()
  checkEnrichStatus()

  // Polling for playlist sync progress
  syncPollingTimer = setInterval(() => {
    if (playlists.value.some(p => p.syncStatus === 'SYNCING')) {
      fetchPlaylists()
      fetchSongs(currentPage.value)
    }
  }, 3000)
})

onUnmounted(() => {
  if (syncPollingTimer) clearInterval(syncPollingTimer)
  if (enrichPollTimer) clearInterval(enrichPollTimer)
  if (audioPreviewInstance) audioPreviewInstance.pause()
  if (libraryAudioElement.value) libraryAudioElement.value.pause()
})
</script>

<template>
  <div class="music-manager-root">
    <!-- Header banner -->
    <div class="manager-header">
      <div class="manager-title-area">
        <div class="title-with-pill">
          <h2>🎵 Quản Lý Kho Nhạc & Đồng Bộ Playlist</h2>
          <span class="arena-tag">QUIZ & TOURNAMENT</span>
        </div>
        <p class="manager-desc">
          Kho nhạc chung hợp nhất cho Music Quiz và Music Tournament. Hỗ trợ tìm kiếm nhanh đa nguồn, nhập hàng loạt bằng text/file, và đồng bộ tự động Playlist YouTube & Spotify với thống kê chi tiết.
        </p>
      </div>

      <div class="manager-header-actions">
        <!-- Target Quiz Pool Selector -->
        <div class="pool-selector-wrapper" v-if="pools.length > 0">
          <label class="pool-select-label">Gắn vào Pool:</label>
          <select v-model="selectedPoolId" class="pool-select">
            <option :value="null">-- Mặc định (Kho chung) --</option>
            <option v-for="p in pools" :key="p.id" :value="p.id">🎯 {{ p.name }}</option>
          </select>
        </div>

        <button class="anime-btn btn-secondary" @click="importFromTournaments" :disabled="loading" title="Sao chép bài hát từ giải đấu hiện có vào kho Quiz">
          ⚡ Nạp từ Giải đấu
        </button>
        <button class="anime-btn btn-primary" @click="openAddSongModal">
          + Thêm thủ công
        </button>
      </div>
    </div>

    <!-- Alert / Toast -->
    <div v-if="alertMessage" class="manager-alert" :class="alertType">
      <span class="alert-icon">{{ alertType === 'error' ? '⚠️' : alertType === 'info' ? 'ℹ️' : '✨' }}</span>
      <span class="alert-text">{{ alertMessage }}</span>
      <button class="close-alert-btn" @click="alertMessage = ''">×</button>
    </div>

    <!-- Navigation Tabs -->
    <div class="manager-nav-tabs">
      <button 
        class="nav-tab-btn" 
        :class="{ active: activeTab === 'quick-search' }" 
        @click="activeTab = 'quick-search'"
      >
        <span>🔍 Tìm kiếm & Thêm nhanh</span>
        <span class="tab-badge hot">HOT</span>
      </button>

      <button 
        class="nav-tab-btn" 
        :class="{ active: activeTab === 'bulk-import' }" 
        @click="activeTab = 'bulk-import'"
      >
        <span>📥 Nhập hàng loạt</span>
      </button>

      <button 
        class="nav-tab-btn" 
        :class="{ active: activeTab === 'library' }" 
        @click="activeTab = 'library'"
      >
        <span>📚 Kho bài hát</span>
        <span class="tab-counter">{{ totalElements }}</span>
      </button>

      <button 
        class="nav-tab-btn" 
        :class="{ active: activeTab === 'playlists' }" 
        @click="activeTab = 'playlists'; fetchPlaylists()"
      >
        <span>🔄 Đồng bộ Playlist</span>
        <span class="tab-counter">{{ playlists.length }}</span>
      </button>
    </div>

    <!-- ======================================================== -->
    <!-- TAB 1: TÌM KIẾM & THÊM NHANH (QUICK SEARCH)               -->
    <!-- ======================================================== -->
    <div v-if="activeTab === 'quick-search'" class="tab-content quick-search-pane">
      <div class="search-panel-box">
        <div class="search-head-row">
          <div class="search-title">
            <span class="search-icon">🔍</span>
            <b>Tìm & Thêm bài hát trực tiếp qua YouTube & Spotify / iTunes</b>
          </div>
          <div class="search-filter-pills">
            <button class="pill-btn" :class="{ active: quickSearchFilter === 'all' }" @click="quickSearchFilter = 'all'">Tất cả</button>
            <button class="pill-btn" :class="{ active: quickSearchFilter === 'spotify' }" @click="quickSearchFilter = 'spotify'">🟢 Spotify</button>
            <button class="pill-btn" :class="{ active: quickSearchFilter === 'youtube' }" @click="quickSearchFilter = 'youtube'">🔴 YouTube</button>
          </div>
        </div>

        <div class="search-input-box">
          <input
            v-model="quickSearchQuery"
            @keydown.enter.prevent="executeQuickSearch"
            placeholder="Nhập tên bài hát hoặc ca sĩ (VD: YOASOBI, Lemon, Sơn Tùng, Jujutsu Kaisen, Gurenge...)"
            class="search-input-field"
          />
          <button class="btn-primary search-submit-btn" :disabled="quickSearchLoading" @click="executeQuickSearch">
            {{ quickSearchLoading ? '⏳ Đang tìm...' : '🔍 Tìm kiếm' }}
          </button>
        </div>

        <!-- Hot Suggestion Tags -->
        <div class="search-tags-bar">
          <span class="tag-label">Gợi ý hot:</span>
          <button 
            v-for="tag in hotTags" 
            :key="tag" 
            class="genre-tag-chip" 
            @click="selectQuickTag(tag)"
          >
            {{ tag }}
          </button>
        </div>
      </div>

      <!-- Search Results Area -->
      <div v-if="quickSearchResults.length > 0" class="search-results-section">
        <!-- Toolbar -->
        <div class="results-toolbar">
          <div class="toolbar-left">
            <span>Tìm thấy <b>{{ quickSearchResults.length }}</b> kết quả:</span>
            <span class="selected-badge" v-if="selectedSongIndexes.size > 0">
              Đã chọn: <b>{{ selectedSongIndexes.size }}</b> / {{ quickSearchResults.length }} bài
            </span>
            <button class="text-action-btn" @click="selectAllQuickSearch">Chọn tất cả</button>
            <button class="text-action-btn" @click="deselectAllQuickSearch">Bỏ chọn</button>
          </div>

          <div class="toolbar-actions">
            <button 
              class="anime-btn btn-primary batch-add-btn" 
              :disabled="quickBatchAdding || selectedSongIndexes.size === 0"
              @click="addSelectedQuickSongs"
            >
              {{ quickBatchAdding ? '⏳ Đang thêm...' : `➕ Thêm ${selectedSongIndexes.size} bài đã chọn vào Kho & Pool` }}
            </button>
          </div>
        </div>

        <!-- Progress bar when batch adding -->
        <div v-if="quickBatchAdding" class="batch-progress-box">
          <div class="progress-label">{{ quickBatchProgress.text }}</div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: ((quickBatchProgress.current / (quickBatchProgress.total || 1)) * 100) + '%' }"></div>
          </div>
        </div>

        <!-- Cards Grid -->
        <div class="search-cards-grid">
          <div 
            v-for="(item, idx) in quickSearchResults" 
            :key="idx" 
            class="search-song-card"
            :class="{ isSelected: selectedSongIndexes.has(idx) }"
            @click="toggleSelectSong(idx)"
          >
            <!-- Checkbox -->
            <div class="card-checkbox" @click.stop="toggleSelectSong(idx)">
              <input type="checkbox" :checked="selectedSongIndexes.has(idx)" @change="toggleSelectSong(idx)" />
            </div>

            <!-- Artwork / Preview Play -->
            <div 
              class="search-card-art" 
              @click.stop="item.previewUrl ? toggleAudioPreview(item.previewUrl) : null"
              title="Bấm vào bìa để nghe thử"
            >
              <img v-if="item.artwork" :src="item.artwork" :alt="item.title" class="search-thumb-img" />
              <span v-else class="search-thumb-fallback">♫</span>
              <button 
                v-if="item.previewUrl" 
                class="card-play-preview-btn" 
                :class="{ isPlaying: activeAudioPreviewUrl === item.previewUrl }"
                @click.stop="toggleAudioPreview(item.previewUrl)"
                title="Nghe thử 30s preview MP3"
              >
                {{ activeAudioPreviewUrl === item.previewUrl ? '⏸' : '▶' }}
              </button>
            </div>

            <!-- Meta -->
            <div class="search-card-meta">
              <b :title="item.title">{{ item.title }}</b>
              <small :title="item.artist">{{ item.artist || '—' }}</small>
              <div class="card-badges-row">
                <span class="genre-badge">{{ item.genre || 'Music' }}</span>
                <span v-if="item.source" class="source-badge" :class="item.source">
                  {{ item.source === 'spotify' ? '🟢 Spotify' : item.source === 'youtube' ? '🔴 YouTube' : '♫ iTunes' }}
                </span>
              </div>
            </div>

            <!-- Actions -->
            <div class="search-card-actions" @click.stop>
              <a v-if="item.spotifyUrl" :href="item.spotifyUrl" target="_blank" rel="noopener noreferrer" class="card-link-btn" title="Mở trên Spotify">
                🟢
              </a>
              <a v-if="item.youtubeUrl" :href="item.youtubeUrl" target="_blank" rel="noopener noreferrer" class="card-link-btn" title="Mở trên YouTube">
                🔴
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- TAB 2: NHẬP HÀNG LOẠT (BULK IMPORT)                      -->
    <!-- ======================================================== -->
    <div v-if="activeTab === 'bulk-import'" class="tab-content bulk-import-pane">
      <div class="bulk-input-container">
        <div class="bulk-guide-box">
          <h4>📥 Nhập danh sách bài hát hàng loạt</h4>
          <p>
            Dán nhiều dòng theo định dạng <code>Tên bài – Nghệ sĩ | Link YouTube/Spotify</code> (hoặc sao chép từ file Excel, Notepad).
            Hệ thống sẽ tự động phân tích và bổ sung metadata (bìa, audio preview MP3).
          </p>
        </div>

        <div class="bulk-textarea-wrapper">
          <textarea
            v-model="bulkText"
            placeholder="Ví dụ:
Gurenge – LiSA | https://www.youtube.com/watch?v=CwkzK-F0SW0
Idol – YOASOBI | https://www.youtube.com/watch?v=ZRtdQ81jPUQ
Unravel – TK from Ling Tosite Sigure
Sparkle – RADWIMPS"
            rows="6"
            class="bulk-textarea"
          ></textarea>
        </div>

        <div class="bulk-actions-bar">
          <div class="file-upload-section">
            <label class="anime-btn btn-secondary upload-file-btn">
              📁 Tải file TXT / CSV
              <input type="file" accept=".txt,.csv" @change="handleFileUpload" style="display:none;" />
            </label>
            <label class="auto-enrich-checkbox">
              <input type="checkbox" v-model="bulkAutoEnrich" />
              <span>Tự động tìm bìa & preview audio qua iTunes</span>
            </label>
          </div>

          <button class="anime-btn btn-primary" @click="parseBulkText">
            🔍 Phân tích danh sách (Preview)
          </button>
        </div>
      </div>

      <!-- Preview Table -->
      <div v-if="bulkParsedRows.length > 0" class="bulk-preview-section">
        <div class="preview-toolbar">
          <div class="preview-stats">
            <span>Đã phân tích: <b>{{ bulkParsedRows.length }}</b> bài</span>
            <span>Đã chọn: <b>{{ bulkSelectedRows.size }}</b> bài</span>
            <button class="text-action-btn" @click="selectAllBulk">Chọn tất cả</button>
            <button class="text-action-btn" @click="deselectAllBulk">Bỏ chọn</button>
          </div>

          <button 
            class="anime-btn btn-primary" 
            :disabled="bulkImporting || bulkSelectedRows.size === 0" 
            @click="executeBulkImport"
          >
            {{ bulkImporting ? '⏳ Đang nhập dữ liệu...' : `✨ Nhập ${bulkSelectedRows.size} bài vào Kho Nhạc & Pool` }}
          </button>
        </div>

        <div class="preview-table-container">
          <table class="preview-table">
            <thead>
              <tr>
                <th width="40"><input type="checkbox" :checked="bulkSelectedRows.size === bulkParsedRows.length" @change="bulkSelectedRows.size === bulkParsedRows.length ? deselectAllBulk() : selectAllBulk()" /></th>
                <th width="50">#</th>
                <th>Tên bài hát</th>
                <th>Nghệ sĩ</th>
                <th>Link đính kèm</th>
                <th width="70">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in bulkParsedRows" :key="idx" :class="{ unselected: !bulkSelectedRows.has(idx) }">
                <td>
                  <input type="checkbox" :checked="bulkSelectedRows.has(idx)" @change="toggleSelectBulkRow(idx)" />
                </td>
                <td>{{ idx + 1 }}</td>
                <td>
                  <input type="text" v-model="row.title" class="inline-edit-input" />
                </td>
                <td>
                  <input type="text" v-model="row.artist" class="inline-edit-input" />
                </td>
                <td>
                  <span v-if="row.link" class="preview-link-tag">
                    {{ row.youtubeUrl ? '🔴 YouTube' : row.spotifyUrl ? '🟢 Spotify' : '🔗 Link' }}
                  </span>
                  <span v-else class="preview-link-empty">—</span>
                </td>
                <td>
                  <button class="delete-row-btn" @click="removeBulkRow(idx)" title="Xóa dòng này">✕</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- TAB 3: KHO BÀI HÁT (LIBRARY)                             -->
    <!-- ======================================================== -->
    <div v-if="activeTab === 'library'" class="tab-content library-pane">
      <!-- Filter Bar -->
      <div class="filter-bar-card">
        <div class="filter-field search-field">
          <span class="field-icon">🔍</span>
          <input 
            type="text" 
            v-model="searchQuery" 
            placeholder="Tìm theo tên bài hát, nghệ sĩ..."
            class="anime-input"
          />
          <button v-if="searchQuery" class="clear-input-btn" @click="searchQuery = ''">×</button>
        </div>

        <div class="filter-field">
          <label>Thể loại:</label>
          <select v-model="selectedGenre" class="anime-select">
            <option value="">Tất cả thể loại</option>
            <option v-for="g in availableGenres" :key="g" :value="g">{{ g }}</option>
          </select>
        </div>

        <div class="filter-field">
          <label>Độ khó:</label>
          <select v-model="selectedDiffTier" class="anime-select">
            <option value="">Tất cả độ khó</option>
            <option value="easy">Easy (0-20)</option>
            <option value="normal">Normal (21-40)</option>
            <option value="hard">Hard (41-65)</option>
            <option value="expert">Expert (66-85)</option>
            <option value="master">Master (86-100)</option>
          </select>
        </div>

        <div class="filter-field">
          <label>Sắp xếp:</label>
          <select v-model="selectedSort" class="anime-select">
            <option value="id_desc">Mới nhất (Mặc định)</option>
            <option value="artist_asc">Ca sĩ (A → Z)</option>
            <option value="artist_desc">Ca sĩ (Z → A)</option>
            <option value="title_asc">Tên bài (A → Z)</option>
            <option value="diff_asc">Độ khó: Thấp → Cao</option>
            <option value="diff_desc">Độ khó: Cao → Thấp</option>
          </select>
        </div>

        <div class="filter-field checkbox-field">
          <label class="toggle-switch">
            <input type="checkbox" v-model="activeOnly" />
            <span class="slider"></span>
          </label>
          <span class="toggle-label">Chỉ bài đang bật</span>
        </div>
      </div>

      <!-- Auto Enrich Banner / Action -->
      <div class="enrich-action-bar">
        <div class="enrich-info">
          <span class="enrich-icon">⚡</span>
          <div>
            <div class="enrich-title">Tự động tìm link nghe thử (Preview MP3) & Bìa Album</div>
            <p v-if="enrichStatus.missingPreviewCount > 0" class="enrich-subtitle">
              Có <strong>{{ enrichStatus.missingPreviewCount }}</strong> bài trong kho chưa có link nghe thử.
            </p>
            <p v-else class="enrich-subtitle text-muted">
              Kho nhạc sẵn sàng cho minigame Music Quiz đoán bài hát.
            </p>
          </div>
        </div>

        <div class="enrich-actions-group">
          <button 
            class="anime-btn btn-dedup" 
            :disabled="dedupInProgress" 
            @click="runDeduplication"
            title="Tự động quét và gộp các bài hát bị trùng lặp trong kho"
          >
            <span v-if="dedupInProgress" class="btn-spinner"></span>
            <span>{{ dedupInProgress ? 'Đang lọc...' : '🧹 Gộp bài trùng' }}</span>
          </button>

          <button 
            class="anime-btn btn-enrich" 
            :disabled="enrichInProgress" 
            @click="startEnrichment"
          >
            <span v-if="enrichInProgress" class="btn-spinner"></span>
            <span>{{ enrichInProgress ? 'Đang quét tự động...' : '✨ Tìm link nghe thử (Auto-enrich)' }}</span>
          </button>
        </div>
      </div>

      <!-- Live progress card if running -->
      <div v-if="enrichInProgress" class="enrich-progress-card">
        <div class="progress-header">
          <span>{{ enrichStatus.lastMessage || 'Đang quét bổ sung preview qua Spotify & YouTube...' }}</span>
          <span class="progress-count">{{ enrichStatus.processed }} / {{ enrichStatus.total }}</span>
        </div>
        <div class="progress-bar-track">
          <div 
            class="progress-bar-fill" 
            :style="{ width: (enrichStatus.total > 0 ? (enrichStatus.processed / enrichStatus.total * 100) : 0) + '%' }"
          ></div>
        </div>
        <div class="progress-detail" v-if="enrichStatus.currentSong">
          Bài hiện tại: <em>{{ enrichStatus.currentSong }}</em>
        </div>
      </div>

      <!-- Songs Table / Grid -->
      <div v-if="loading" class="manager-loading-state">
        <div class="spinner"></div>
        <p>Đang tải dữ liệu kho nhạc...</p>
      </div>

      <div v-else-if="songs.length === 0" class="manager-empty-state">
        <div class="empty-icon">🎧</div>
        <h3>Chưa có bài hát nào phù hợp</h3>
        <p>Hãy thử xóa bộ lọc, tìm kiếm nhanh hoặc nhập danh sách bài hát.</p>
        <button class="anime-btn btn-primary" @click="activeTab = 'quick-search'">🔍 Tìm & Thêm nhanh</button>
      </div>

      <div v-else class="songs-grid">
        <div 
          v-for="song in songs" 
          :key="song.id" 
          class="song-card-row"
          :class="{ inactive: !song.active }"
        >
          <!-- Artwork / Preview Play -->
          <div class="song-cover-box">
            <img 
              :src="song.artworkUrl || 'https://img.youtube.com/vi/' + (song.youtubeUrl ? song.youtubeUrl.slice(-11) : '') + '/hqdefault.jpg'" 
              alt="Cover" 
              class="song-cover-img"
              @error="$event.target.src = '/logo.png'"
            />
            <button 
              v-if="song.previewUrl" 
              class="play-audio-btn" 
              :class="{ playing: playingSongId === song.id }"
              @click="toggleLibraryAudio(song)"
              :title="playingSongId === song.id ? 'Tạm dừng nghe thử' : 'Nghe thử 30s preview MP3'"
            >
              {{ playingSongId === song.id ? '⏸' : '▶' }}
            </button>
          </div>

          <!-- Info -->
          <div class="song-main-info">
            <div class="song-name-row">
              <h4 class="song-title" :title="song.title">{{ song.title }}</h4>
              <span class="diff-badge" :class="getTierBadgeClass(song.difficulty)">
                {{ getTierLabel(song.difficulty) }} ({{ song.difficulty }})
              </span>
            </div>
            <p class="song-artist">{{ song.artist }}</p>

            <div class="song-tags-row">
              <span class="genre-pill">{{ song.genre || 'General' }}</span>
              <span v-if="song.aliases" class="alias-pill" :title="song.aliases">
                Aliases: {{ song.aliases }}
              </span>
            </div>
          </div>

          <!-- Media Links -->
          <div class="song-links-cell">
            <a 
              v-if="song.youtubeUrl" 
              :href="song.youtubeUrl" 
              target="_blank" 
              rel="noopener noreferrer" 
              class="icon-link yt-link" 
              title="Mở link YouTube"
            >
              🔴 YouTube
            </a>
            <a 
              v-if="song.spotifyUrl" 
              :href="song.spotifyUrl" 
              target="_blank" 
              rel="noopener noreferrer" 
              class="icon-link spotify-link" 
              title="Mở link Spotify"
            >
              🟢 Spotify
            </a>
          </div>

          <!-- Active Toggle & Actions -->
          <div class="song-actions-cell">
            <button 
              class="action-icon-btn enrich-btn" 
              @click="enrichSingle(song)" 
              title="Tìm lại link nghe thử & bìa bài hát này"
            >
              ⚡
            </button>
            <label class="toggle-switch small" :title="song.active ? 'Bài đang bật' : 'Bài đang tắt'">
              <input type="checkbox" :checked="song.active" @change="toggleActive(song)" />
              <span class="slider"></span>
            </label>

            <button class="action-icon-btn edit-btn" @click="openEditSongModal(song)" title="Chỉnh sửa bài hát">
              ✏️
            </button>
            <button class="action-icon-btn delete-btn" @click="deleteSong(song)" title="Xóa bài hát khỏi kho">
              🗑️
            </button>
          </div>
        </div>
      </div>

      <!-- Pagination Controls -->
      <div v-if="totalPages > 1" class="manager-pagination">
        <button 
          class="page-btn" 
          :disabled="currentPage === 0" 
          @click="fetchSongs(currentPage - 1)"
        >
          ‹ Trang trước
        </button>
        <span class="page-info">
          Trang <b>{{ currentPage + 1 }}</b> / <b>{{ totalPages }}</b> (Tổng {{ totalElements }} bài)
        </span>
        <button 
          class="page-btn" 
          :disabled="currentPage >= totalPages - 1" 
          @click="fetchSongs(currentPage + 1)"
        >
          Trang sau ›
        </button>

        <div class="page-jump-container">
          <span class="jump-label">Đến trang:</span>
          <input 
            type="number" 
            v-model.number="pageJumpInput" 
            :min="1" 
            :max="totalPages" 
            @keydown.enter.prevent="jumpToPage" 
            class="page-jump-input"
            placeholder="Số"
          />
          <button class="page-jump-btn" @click="jumpToPage" title="Chuyển đến trang">Đi</button>
        </div>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- TAB 4: ĐỒNG BỘ PLAYLIST (YOUTUBE & SPOTIFY)              -->
    <!-- ======================================================== -->
    <div v-if="activeTab === 'playlists'" class="tab-content playlists-pane">
      <!-- Add Playlist Card -->
      <div class="add-playlist-card">
        <h3>🔗 Thêm Playlist Tự Động Đồng Bộ</h3>
        <p class="playlist-subtext">
          Hỗ trợ link <b>YouTube Playlist</b> (công khai / unlisted) hoặc <b>Spotify Playlist</b>. Hệ thống tự động bóc tách từng bài, lọc trùng, tải metadata và cập nhật tiến trình thực tế.
        </p>

        <div class="add-playlist-form">
          <div class="form-row">
            <input 
              type="text" 
              v-model="newPlaylistUrl" 
              placeholder="Dán link playlist YouTube (https://www.youtube.com/playlist?list=...) hoặc Spotify..."
              class="anime-input url-input"
            />
            <input 
              type="text" 
              v-model="newPlaylistTitle" 
              placeholder="Tên gợi nhớ (VD: Anime Openings 2026)" 
              class="anime-input title-input"
            />
            <button 
              class="anime-btn btn-primary submit-playlist-btn" 
              :disabled="addingPlaylist" 
              @click="addPlaylist"
            >
              {{ addingPlaylist ? '⏳ Đang thêm...' : '➕ Thêm & Đồng bộ ngay' }}
            </button>
          </div>
          <p v-if="playlistError" class="form-error-msg">{{ playlistError }}</p>
        </div>
      </div>

      <!-- Playlists List -->
      <div class="playlists-list-section">
        <div class="section-title-row">
          <h3>📋 Danh sách Playlist Đã Kết Nối ({{ playlists.length }})</h3>
          <button class="refresh-btn" @click="fetchPlaylists" title="Tải lại trạng thái">🔄 Làm mới</button>
        </div>

        <div v-if="playlists.length === 0" class="playlists-empty">
          <p>Chưa có playlist nào được kết nối. Hãy dán link playlist ở trên để bắt đầu!</p>
        </div>

        <div v-else class="playlist-cards-grid">
          <div 
            v-for="pl in playlists" 
            :key="pl.id" 
            class="playlist-card"
            :class="{ isSyncing: pl.syncStatus === 'SYNCING', isError: pl.syncStatus === 'ERROR' }"
          >
            <div class="pl-header">
              <div class="pl-source-tag" :class="pl.source">
                {{ pl.source === 'youtube' ? '🔴 YouTube' : pl.source === 'spotify' ? '🟢 Spotify' : '🔗 Custom' }}
              </div>
              <span class="pl-status-pill" :class="pl.syncStatus.toLowerCase()">
                <span v-if="pl.syncStatus === 'SYNCING'" class="sync-spinner"></span>
                {{ pl.syncStatus === 'SYNCING' ? 'Đang đồng bộ...' : pl.syncStatus === 'COMPLETED' ? '✓ Đã đồng bộ' : pl.syncStatus === 'ERROR' ? '⚠️ Lỗi' : 'Chờ đồng bộ' }}
              </span>
            </div>

            <h4 class="pl-title" :title="pl.title">{{ pl.title }}</h4>
            <a :href="pl.sourceUrl" target="_blank" rel="noopener noreferrer" class="pl-url" :title="pl.sourceUrl">
              {{ pl.sourceUrl }}
            </a>

            <!-- Progress bar if syncing -->
            <div v-if="pl.syncStatus === 'SYNCING'" class="pl-progress-box">
              <div class="progress-info-row">
                <span>Đang xử lý:</span>
                <b>{{ pl.processedCount || 0 }} / {{ pl.totalFound || '?' }} bài</b>
              </div>
              <div class="progress-track">
                <div 
                  class="progress-fill" 
                  :style="{ width: pl.totalFound ? ((pl.processedCount / pl.totalFound) * 100) + '%' : '40%' }"
                ></div>
              </div>
            </div>

            <!-- Stats Breakdown -->
            <div class="pl-stats-breakdown">
              <div class="stat-item">
                <small>Tổng tìm thấy</small>
                <b>{{ pl.totalFound || pl.itemCount || 0 }}</b>
              </div>
              <div class="stat-item success">
                <small>Bài mới</small>
                <b>+{{ pl.addedCount || pl.itemCount || 0 }}</b>
              </div>
              <div class="stat-item warning">
                <small>Trùng lặp</small>
                <b>{{ pl.duplicateCount || 0 }}</b>
              </div>
              <div class="stat-item danger" v-if="pl.errorCount > 0">
                <small>Lỗi</small>
                <b>{{ pl.errorCount }}</b>
              </div>
            </div>

            <!-- Error message if any -->
            <div v-if="pl.syncStatus === 'ERROR' && pl.lastErrorMessage" class="pl-error-box">
              <span>⚠️ {{ pl.lastErrorMessage }}</span>
            </div>

            <!-- Footer & Actions -->
            <div class="pl-footer">
              <span class="pl-date" v-if="pl.lastSyncAt">
                Lần cuối: {{ new Date(pl.lastSyncAt).toLocaleString('vi-VN') }}
              </span>
              <span class="pl-date" v-else>Chưa đồng bộ</span>

              <div class="pl-actions">
                <button 
                  class="anime-btn btn-secondary pl-sync-btn" 
                  :disabled="pl.syncStatus === 'SYNCING'"
                  @click="triggerPlaylistSync(pl.id)"
                  title="Đồng bộ ngay lại danh sách"
                >
                  {{ pl.syncStatus === 'ERROR' ? '⚡ Thử lại' : '🔄 Đồng bộ' }}
                </button>
                <button class="pl-delete-btn" @click="deletePlaylist(pl)" title="Xóa playlist này">
                  🗑️
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ======================================================== -->
    <!-- MODAL: ADD / EDIT SONG                                   -->
    <!-- ======================================================== -->
    <div v-if="showSongModal" class="modal-overlay" @click.self="showSongModal = false">
      <div class="anime-modal">
        <div class="modal-header">
          <h3>{{ isEditing ? '✏️ Chỉnh Sửa Bài Hát' : '➕ Thêm Bài Hát Mới' }}</h3>
          <button class="modal-close-btn" @click="showSongModal = false">×</button>
        </div>

        <div class="modal-body">
          <p v-if="songModalError" class="modal-error">{{ songModalError }}</p>

          <div class="form-group">
            <label>Tên bài hát *</label>
            <input 
              type="text" 
              v-model="songForm.title" 
              placeholder="VD: Gurenge, Unravel, Idol..." 
              class="anime-input"
            />
          </div>

          <div class="form-group">
            <label>Ca sĩ / Nghệ sĩ</label>
            <input 
              type="text" 
              v-model="songForm.artist" 
              placeholder="VD: LiSA, YOASOBI, Sơn Tùng..." 
              class="anime-input"
            />
          </div>

          <!-- One-click auto enrich button -->
          <div class="auto-enrich-row">
            <button 
              type="button" 
              class="anime-btn btn-secondary enrich-trigger-btn" 
              :disabled="autoEnrichingSong"
              @click="enrichModalData"
            >
              {{ autoEnrichingSong ? '⏳ Đang tìm thông tin...' : '🔍 Tự động tìm bìa & MP3 preview (iTunes)' }}
            </button>
          </div>

          <div class="form-row-2">
            <div class="form-group">
              <label>Link YouTube (Watch / Embed)</label>
              <input 
                type="text" 
                v-model="songForm.youtubeUrl" 
                placeholder="https://www.youtube.com/watch?v=..." 
                class="anime-input"
              />
            </div>
            <div class="form-group">
              <label>Link Spotify (Track / URL)</label>
              <input 
                type="text" 
                v-model="songForm.spotifyUrl" 
                placeholder="https://open.spotify.com/track/..." 
                class="anime-input"
              />
            </div>
          </div>

          <details class="advanced-settings-details">
            <summary>⚙️ Cài đặt nâng cao (Độ khó, Aliases, Thể loại)</summary>
            
            <div class="advanced-fields-box">
              <div class="form-group">
                <label>Tên gọi khác / Aliases (phân tách bởi dấu phẩy)</label>
                <input 
                  type="text" 
                  v-model="songForm.aliases" 
                  placeholder="VD: 红莲华, Red Lotus, Gurenge Opening" 
                  class="anime-input"
                />
              </div>

              <div class="form-row-2">
                <div class="form-group">
                  <label>Thể loại</label>
                  <input type="text" v-model="songForm.genre" placeholder="Anime, J-Pop, V-Pop..." class="anime-input" />
                </div>
                <div class="form-group">
                  <label>Độ khó: <b>{{ songForm.difficulty }}</b> ({{ getTierLabel(songForm.difficulty) }})</label>
                  <input type="range" v-model.number="songForm.difficulty" min="0" max="100" class="diff-slider" />
                </div>
              </div>

              <div class="form-group">
                <label>Ảnh bìa (Artwork URL)</label>
                <input type="text" v-model="songForm.artworkUrl" placeholder="https://..." class="anime-input" />
              </div>

              <div class="form-group">
                <label>Audio Preview MP3 URL</label>
                <input type="text" v-model="songForm.previewUrl" placeholder="https://... .mp3" class="anime-input" />
              </div>
            </div>
          </details>
        </div>

        <div class="modal-footer">
          <button class="anime-btn btn-secondary" @click="showSongModal = false">Hủy</button>
          <button class="anime-btn btn-primary" :disabled="savingSong" @click="saveSong">
            {{ savingSong ? '⏳ Đang lưu...' : 'Lưu bài hát' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.music-manager-root {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
  color: #e2e8f0;
}

/* Header */
.manager-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 20px 24px;
  background: linear-gradient(135deg, rgba(26, 20, 48, 0.75) 0%, rgba(15, 23, 42, 0.75) 100%);
  border: 1px solid rgba(168, 85, 247, 0.25);
  border-radius: 14px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(8px);
}

.title-with-pill {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.title-with-pill h2 {
  margin: 0;
  font-size: 1.55rem;
  font-weight: 800;
  background: linear-gradient(90deg, #f8fafc 0%, #c084fc 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.arena-tag {
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  padding: 3px 8px;
  background: rgba(168, 85, 247, 0.2);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.4);
  border-radius: 6px;
}

.manager-desc {
  margin: 8px 0 0;
  font-size: 0.88rem;
  color: #94a3b8;
  max-width: 680px;
  line-height: 1.45;
}

.manager-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pool-selector-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(15, 23, 42, 0.6);
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.pool-select-label {
  font-size: 0.8rem;
  color: #cbd5e1;
}

.pool-select {
  background: #0f172a;
  color: #fbbf24;
  border: 1px solid rgba(251, 191, 36, 0.3);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.82rem;
  outline: none;
}

/* Nav Tabs */
.manager-nav-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 2px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 2px;
  overflow-x: auto;
}

.nav-tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 10px 10px 0 0;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid transparent;
  color: #94a3b8;
  font-size: 0.92rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.nav-tab-btn:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #f1f5f9;
}

.nav-tab-btn.active {
  background: rgba(168, 85, 247, 0.15);
  color: #e9d5ff;
  border-color: rgba(168, 85, 247, 0.35) rgba(168, 85, 247, 0.35) transparent rgba(168, 85, 247, 0.35);
  border-bottom: 2px solid #c084fc;
}

.tab-badge.hot {
  font-size: 0.65rem;
  background: linear-gradient(135deg, #f43f5e, #fb923c);
  color: #fff;
  padding: 2px 6px;
  border-radius: 999px;
  font-weight: 800;
}

.tab-counter {
  font-size: 0.75rem;
  background: rgba(255, 255, 255, 0.1);
  padding: 2px 7px;
  border-radius: 999px;
  color: #cbd5e1;
}

/* Alert Toast */
.manager-alert {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px;
  border-radius: 10px;
  font-size: 0.9rem;
  font-weight: 600;
  animation: slideInDown 0.25s ease;
}

.manager-alert.success {
  background: rgba(16, 185, 129, 0.15);
  border: 1px solid rgba(16, 185, 129, 0.4);
  color: #6ee7b7;
}

.manager-alert.error {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #fca5a5;
}

.manager-alert.info {
  background: rgba(6, 182, 212, 0.15);
  border: 1px solid rgba(6, 182, 212, 0.4);
  color: #67e8f9;
}

.close-alert-btn {
  margin-left: auto;
  background: none;
  border: none;
  color: inherit;
  font-size: 1.2rem;
  cursor: pointer;
  opacity: 0.7;
}

/* ======================================================== */
/* TAB 1: QUICK SEARCH STYLES                                */
/* ======================================================== */
.search-panel-box {
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(168, 85, 247, 0.25);
  border-radius: 12px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.search-head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.search-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.95rem;
  color: #f1f5f9;
}

.search-filter-pills {
  display: flex;
  gap: 6px;
}

.pill-btn {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #cbd5e1;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.pill-btn:hover {
  background: rgba(255, 255, 255, 0.12);
}

.pill-btn.active {
  background: rgba(168, 85, 247, 0.3);
  border-color: #a855f7;
  color: #fff;
}

.search-input-box {
  display: flex;
  gap: 10px;
}

.search-input-field {
  flex: 1;
  background: rgba(10, 15, 29, 0.8);
  border: 1px solid rgba(168, 85, 247, 0.35);
  border-radius: 10px;
  padding: 10px 14px;
  color: #fff;
  font-size: 0.92rem;
  outline: none;
}

.search-input-field:focus {
  border-color: #c084fc;
  box-shadow: 0 0 0 2px rgba(168, 85, 247, 0.2);
}

.search-submit-btn {
  padding: 10px 20px;
  border-radius: 10px;
  background: linear-gradient(135deg, #a855f7, #6366f1);
  color: #fff;
  font-weight: 700;
  font-size: 0.9rem;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.search-tags-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag-label {
  font-size: 0.8rem;
  color: #94a3b8;
}

.genre-tag-chip {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #e2e8f0;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.genre-tag-chip:hover {
  background: rgba(168, 85, 247, 0.25);
  border-color: #a855f7;
  color: #fff;
}

/* Results section */
.search-results-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.results-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 10px 16px;
  background: rgba(15, 23, 42, 0.5);
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.88rem;
}

.selected-badge {
  background: rgba(168, 85, 247, 0.2);
  color: #e9d5ff;
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid rgba(168, 85, 247, 0.35);
  font-size: 0.82rem;
}

.text-action-btn {
  background: none;
  border: none;
  color: #38bdf8;
  font-size: 0.82rem;
  cursor: pointer;
  text-decoration: underline;
  padding: 0 4px;
}

.batch-add-btn {
  padding: 8px 16px;
  font-size: 0.88rem;
}

/* Cards Grid */
.search-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.search-song-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(18, 24, 43, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.search-song-card:hover {
  border-color: rgba(168, 85, 247, 0.4);
  background: rgba(24, 32, 58, 0.8);
  transform: translateY(-2px);
}

.search-song-card.isSelected {
  border-color: #a855f7;
  background: rgba(168, 85, 247, 0.12);
}

.card-checkbox input {
  width: 17px;
  height: 17px;
  accent-color: #a855f7;
  cursor: pointer;
}

.search-card-art {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
  background: #0f172a;
}

.search-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.search-thumb-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 1.3rem;
}

.card-play-preview-btn {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  border: none;
  color: #fff;
  font-size: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.card-play-preview-btn:hover,
.card-play-preview-btn.isPlaying {
  background: rgba(168, 85, 247, 0.8);
}

.search-card-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.search-card-meta b {
  font-size: 0.88rem;
  color: #f8fafc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.search-card-meta small {
  font-size: 0.78rem;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-badges-row {
  display: flex;
  gap: 6px;
  margin-top: 3px;
}

.genre-badge {
  font-size: 0.68rem;
  background: rgba(255, 255, 255, 0.06);
  color: #cbd5e1;
  padding: 1px 6px;
  border-radius: 4px;
}

.source-badge {
  font-size: 0.68rem;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.source-badge.spotify {
  background: rgba(16, 185, 129, 0.18);
  color: #6ee7b7;
}

.source-badge.youtube {
  background: rgba(239, 68, 68, 0.18);
  color: #fca5a5;
}

.search-card-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-link-btn {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  padding: 4px 6px;
  font-size: 0.8rem;
  text-decoration: none;
  transition: all 0.15s;
}

.card-link-btn:hover {
  background: rgba(255, 255, 255, 0.15);
}

/* ======================================================== */
/* TAB 2: BULK IMPORT STYLES                                 */
/* ======================================================== */
.bulk-input-container {
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(168, 85, 247, 0.25);
  border-radius: 12px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.bulk-guide-box h4 {
  margin: 0 0 6px;
  font-size: 1.05rem;
  color: #f1f5f9;
}

.bulk-guide-box p {
  margin: 0;
  font-size: 0.85rem;
  color: #94a3b8;
  line-height: 1.4;
}

.bulk-textarea {
  width: 100%;
  background: rgba(10, 15, 29, 0.85);
  border: 1px solid rgba(168, 85, 247, 0.3);
  border-radius: 10px;
  padding: 12px;
  color: #f8fafc;
  font-family: inherit;
  font-size: 0.88rem;
  line-height: 1.5;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
}

.bulk-actions-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.file-upload-section {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.auto-enrich-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  color: #cbd5e1;
  cursor: pointer;
}

.auto-enrich-checkbox input {
  accent-color: #a855f7;
}

.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.preview-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.88rem;
}

.preview-table-container {
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.6);
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.86rem;
  text-align: left;
}

.preview-table th {
  background: rgba(255, 255, 255, 0.04);
  padding: 10px 12px;
  color: #94a3b8;
  font-weight: 700;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.preview-table td {
  padding: 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.preview-table tr.unselected {
  opacity: 0.45;
}

.inline-edit-input {
  width: 95%;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 4px 8px;
  color: #f1f5f9;
  font-size: 0.85rem;
}

.preview-link-tag {
  font-size: 0.72rem;
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  padding: 2px 6px;
  border-radius: 4px;
}

.delete-row-btn {
  background: none;
  border: none;
  color: #f43f5e;
  cursor: pointer;
  font-size: 0.95rem;
}

/* ======================================================== */
/* TAB 3: LIBRARY STYLES                                     */
/* ======================================================== */
/* Enrich UI */
.enrich-action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  background: linear-gradient(135deg, rgba(255, 180, 0, 0.08), rgba(255, 107, 107, 0.05));
  border: 1px solid rgba(255, 180, 0, 0.25);
  border-radius: 12px;
  padding: 0.9rem 1.25rem;
  margin-top: 1rem;
  margin-bottom: 1.25rem;
  flex-wrap: wrap;
}

.enrich-info {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.enrich-icon {
  font-size: 1.6rem;
  filter: drop-shadow(0 0 8px rgba(255, 200, 0, 0.6));
}

.enrich-title {
  font-weight: 700;
  font-size: 0.95rem;
  color: #fff;
}

.enrich-subtitle {
  margin: 0.2rem 0 0 0;
  font-size: 0.85rem;
  color: #ffb86c;
}

.enrich-subtitle.text-muted {
  color: #8be9fd;
}

.btn-enrich {
  background: linear-gradient(135deg, #ff9900, #ff5555);
  color: #fff;
  border: none;
  font-weight: 700;
  padding: 0.55rem 1.1rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 15px rgba(255, 120, 0, 0.3);
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-enrich:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 120, 0, 0.45);
}

.btn-enrich:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.enrich-progress-card {
  background: rgba(18, 18, 28, 0.9);
  border: 1px solid rgba(255, 180, 0, 0.3);
  border-radius: 10px;
  padding: 0.85rem 1.2rem;
  margin-bottom: 1.25rem;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  font-size: 0.88rem;
  font-weight: 600;
  color: #f8f8f2;
  margin-bottom: 0.45rem;
}

.progress-count {
  color: #50fa7b;
}

.progress-bar-track {
  width: 100%;
  height: 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #ff79c6, #bd93f9, #50fa7b);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-detail {
  margin-top: 0.35rem;
  font-size: 0.8rem;
  color: #8be9fd;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-icon-btn.enrich-btn {
  background: rgba(255, 184, 108, 0.15);
  border: 1px solid rgba(255, 184, 108, 0.3);
  color: #ffb86c;
}

.action-icon-btn.enrich-btn:hover {
  background: rgba(255, 184, 108, 0.3);
  transform: scale(1.1);
}

.filter-bar-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 18px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  flex-wrap: wrap;
}

.filter-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-field {
  flex: 1;
  min-width: 220px;
  position: relative;
}

.field-icon {
  position: absolute;
  left: 10px;
  color: #64748b;
  pointer-events: none;
}

.search-field .anime-input {
  width: 100%;
  padding-left: 32px;
  padding-right: 28px;
}

.clear-input-btn {
  position: absolute;
  right: 8px;
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 1rem;
  cursor: pointer;
}

.anime-input,
.anime-select {
  background: rgba(10, 15, 29, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: #f8fafc;
  padding: 7px 12px;
  border-radius: 8px;
  font-size: 0.86rem;
  outline: none;
}

.anime-select option {
  background: #0f172a;
}

/* Switch */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 38px;
  height: 20px;
}

.toggle-switch.small {
  width: 32px;
  height: 17px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: #334155;
  transition: 0.2s;
  border-radius: 20px;
}

.slider:before {
  position: absolute;
  content: "";
  height: 14px;
  width: 14px;
  left: 3px;
  bottom: 3px;
  background: white;
  transition: 0.2s;
  border-radius: 50%;
}

.toggle-switch.small .slider:before {
  height: 11px;
  width: 11px;
}

input:checked + .slider {
  background: #a855f7;
}

input:checked + .slider:before {
  transform: translateX(18px);
}

.toggle-switch.small input:checked + .slider:before {
  transform: translateX(15px);
}

/* Songs Grid */
.songs-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.song-card-row {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(18, 24, 43, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  padding: 8px 14px;
  transition: all 0.15s ease;
}

.song-card-row:hover {
  background: rgba(26, 34, 60, 0.75);
  border-color: rgba(168, 85, 247, 0.3);
}

.song-card-row.inactive {
  opacity: 0.55;
}

.song-cover-box {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
  background: #0b0f19;
}

.song-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.play-audio-btn {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  border: none;
  color: #fff;
  font-size: 0.95rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.play-audio-btn:hover,
.play-audio-btn.playing {
  background: rgba(168, 85, 247, 0.85);
}

.song-main-info {
  flex: 1;
  min-width: 0;
}

.song-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.song-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: #f8fafc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.diff-badge {
  font-size: 0.68rem;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 4px;
}

.badge-easy { background: rgba(34, 197, 94, 0.2); color: #4ade80; border: 1px solid rgba(34, 197, 94, 0.4); }
.badge-normal { background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.4); }
.badge-hard { background: rgba(251, 191, 36, 0.2); color: #fbbf24; border: 1px solid rgba(251, 191, 36, 0.4); }
.badge-expert { background: rgba(249, 115, 22, 0.2); color: #fb923c; border: 1px solid rgba(249, 115, 22, 0.4); }
.badge-master { background: rgba(236, 72, 153, 0.2); color: #f472b6; border: 1px solid rgba(236, 72, 153, 0.4); }

.song-artist {
  margin: 2px 0 0;
  font-size: 0.82rem;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.song-tags-row {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}

.genre-pill, .alias-pill {
  font-size: 0.68rem;
  background: rgba(255, 255, 255, 0.06);
  color: #cbd5e1;
  padding: 1px 6px;
  border-radius: 4px;
}

.song-links-cell {
  display: flex;
  gap: 6px;
}

.icon-link {
  font-size: 0.72rem;
  font-weight: 600;
  text-decoration: none;
  padding: 3px 8px;
  border-radius: 6px;
  transition: all 0.15s;
}

.icon-link.yt-link { background: rgba(239, 68, 68, 0.15); color: #fca5a5; }
.icon-link.spotify-link { background: rgba(16, 185, 129, 0.15); color: #6ee7b7; }

.song-actions-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-icon-btn {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 0.8rem;
  transition: all 0.15s;
}

.action-icon-btn:hover {
  background: rgba(255, 255, 255, 0.15);
}

.action-icon-btn.delete-btn:hover {
  background: rgba(239, 68, 68, 0.25);
  border-color: #ef4444;
}

/* Enrich Actions Group & Dedup */
.enrich-actions-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-dedup {
  background: linear-gradient(135deg, #a855f7, #6366f1);
  color: #fff;
  border: none;
  font-weight: 700;
  padding: 0.55rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 15px rgba(168, 85, 247, 0.3);
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-dedup:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(168, 85, 247, 0.45);
}

.btn-dedup:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Pagination */
.manager-pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.page-jump-container {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
  padding-left: 12px;
  border-left: 1px solid rgba(255, 255, 255, 0.12);
}

.jump-label {
  font-size: 0.85rem;
  color: #94a3b8;
}

.page-jump-input {
  width: 65px;
  padding: 5px 8px;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 6px;
  color: #f8fafc;
  font-size: 0.85rem;
  text-align: center;
  outline: none;
  transition: border-color 0.2s ease;
}

.page-jump-input:focus {
  border-color: #a855f7;
  box-shadow: 0 0 8px rgba(168, 85, 247, 0.4);
}

.page-jump-btn {
  background: rgba(168, 85, 247, 0.2);
  border: 1px solid rgba(168, 85, 247, 0.4);
  color: #c084fc;
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.page-jump-btn:hover {
  background: rgba(168, 85, 247, 0.35);
  color: #fff;
}

.page-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #f1f5f9;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 0.85rem;
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 0.85rem;
  color: #94a3b8;
}

/* ======================================================== */
/* TAB 4: PLAYLIST SYNC STYLES                               */
/* ======================================================== */
.add-playlist-card {
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(168, 85, 247, 0.25);
  border-radius: 12px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.add-playlist-card h3 {
  margin: 0;
  font-size: 1.05rem;
  color: #f8fafc;
}

.playlist-subtext {
  margin: 0;
  font-size: 0.85rem;
  color: #94a3b8;
  line-height: 1.4;
}

.add-playlist-form .form-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.url-input {
  flex: 2;
  min-width: 260px;
}

.title-input {
  flex: 1;
  min-width: 180px;
}

.submit-playlist-btn {
  white-space: nowrap;
}

.form-error-msg {
  color: #fca5a5;
  font-size: 0.82rem;
  margin: 4px 0 0;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 20px 0 12px;
}

.section-title-row h3 {
  margin: 0;
  font-size: 1.05rem;
  color: #f8fafc;
}

.refresh-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #cbd5e1;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 0.8rem;
  cursor: pointer;
}

.playlist-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

.playlist-card {
  background: rgba(18, 24, 43, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.2s ease;
}

.playlist-card.isSyncing {
  border-color: #a855f7;
  box-shadow: 0 0 15px rgba(168, 85, 247, 0.2);
}

.playlist-card.isError {
  border-color: rgba(239, 68, 68, 0.5);
}

.pl-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pl-source-tag {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
}

.pl-source-tag.youtube { background: rgba(239, 68, 68, 0.2); color: #fca5a5; }
.pl-source-tag.spotify { background: rgba(16, 185, 129, 0.2); color: #6ee7b7; }

.pl-status-pill {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.pl-status-pill.syncing { background: rgba(168, 85, 247, 0.25); color: #e9d5ff; }
.pl-status-pill.completed { background: rgba(16, 185, 129, 0.2); color: #6ee7b7; }
.pl-status-pill.error { background: rgba(239, 68, 68, 0.2); color: #fca5a5; }
.pl-status-pill.idle { background: rgba(148, 163, 184, 0.2); color: #cbd5e1; }

.sync-spinner {
  width: 10px;
  height: 10px;
  border: 2px solid #c084fc;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.pl-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: #f8fafc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pl-url {
  font-size: 0.78rem;
  color: #64748b;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pl-url:hover {
  color: #94a3b8;
  text-decoration: underline;
}

.pl-progress-box {
  background: rgba(0, 0, 0, 0.3);
  border-radius: 6px;
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-info-row {
  display: flex;
  justify-content: space-between;
  font-size: 0.75rem;
  color: #c084fc;
}

.progress-track {
  width: 100%;
  height: 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #a855f7, #06b6d4);
  transition: width 0.3s ease;
}

.pl-stats-breakdown {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: rgba(0, 0, 0, 0.25);
  border-radius: 8px;
  padding: 6px 4px;
  text-align: center;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-item small {
  font-size: 0.68rem;
  color: #94a3b8;
}

.stat-item b {
  font-size: 0.85rem;
  color: #f1f5f9;
}

.stat-item.success b { color: #34d399; }
.stat-item.warning b { color: #fbbf24; }
.stat-item.danger b { color: #f87171; }

.pl-error-box {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 0.78rem;
  color: #fca5a5;
  line-height: 1.35;
}

.pl-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding-top: 8px;
  margin-top: auto;
}

.pl-date {
  font-size: 0.72rem;
  color: #64748b;
}

.pl-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.pl-sync-btn {
  padding: 4px 10px;
  font-size: 0.78rem;
}

.pl-delete-btn {
  background: none;
  border: none;
  font-size: 0.9rem;
  cursor: pointer;
  opacity: 0.6;
}

.pl-delete-btn:hover {
  opacity: 1;
}

/* ======================================================== */
/* MODAL STYLES                                              */
/* ======================================================== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 16px;
}

.anime-modal {
  width: 100%;
  max-width: 540px;
  background: #111827;
  border: 1px solid rgba(168, 85, 247, 0.4);
  border-radius: 14px;
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: zoomIn 0.2s ease;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.03);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.modal-header h3 {
  margin: 0;
  font-size: 1.1rem;
  color: #f8fafc;
}

.modal-close-btn {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 1.4rem;
  cursor: pointer;
}

.modal-body {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 75vh;
  overflow-y: auto;
}

.modal-error {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #fca5a5;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 0.85rem;
  margin: 0;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 0.8rem;
  color: #cbd5e1;
  font-weight: 600;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.auto-enrich-row {
  display: flex;
  justify-content: flex-end;
}

.enrich-trigger-btn {
  font-size: 0.8rem;
  padding: 6px 12px;
}

.advanced-settings-details {
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 8px 12px;
}

.advanced-settings-details summary {
  font-size: 0.82rem;
  color: #c084fc;
  cursor: pointer;
  font-weight: 600;
}

.advanced-fields-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
}

.diff-slider {
  width: 100%;
  accent-color: #a855f7;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 18px;
  background: rgba(255, 255, 255, 0.02);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

/* Buttons */
.anime-btn {
  border: none;
  border-radius: 8px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.btn-primary {
  background: linear-gradient(135deg, #a855f7, #6366f1);
  color: #fff;
  padding: 8px 16px;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.92;
  box-shadow: 0 4px 15px rgba(168, 85, 247, 0.4);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
  border: 1px solid rgba(255, 255, 255, 0.15);
  padding: 8px 14px;
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.14);
}

.anime-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Animations */
@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes slideInDown {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes zoomIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}

.spinner {
  width: 28px;
  height: 28px;
  border: 3px solid rgba(168, 85, 247, 0.2);
  border-top-color: #a855f7;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 10px;
}

.manager-loading-state,
.manager-empty-state,
.playlists-empty {
  text-align: center;
  padding: 40px 20px;
  background: rgba(15, 23, 42, 0.4);
  border-radius: 12px;
  border: 1px dashed rgba(255, 255, 255, 0.1);
}

.empty-icon {
  font-size: 2.5rem;
  margin-bottom: 8px;
}
</style>
