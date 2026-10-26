<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { parseSongs, uniqueSongs, formatSong } from './songInput.js'
import ChampionStage from './ChampionStage.vue'

const API = '/api'
const voterKey = getVoterKey()
const tournaments = ref([])
const tournament = ref(null)
const selectedRound = ref(0)
const page = ref('home')
const viewMode = ref('tabs') // 'tabs' | 'tree'
const loading = ref(false)
const error = ref('')
const notice = ref('')
const tournamentName = ref('Anime & J-Music Cup')
const songText = ref('')
const adminToken = ref('admin-demo-2026')
const endTime = ref('')
const showAdmin = ref(false)
const isSidebarCollapsed = ref(false)
const displayName = ref(localStorage.getItem('musicTournamentDisplayName') || '')
const nameInput = ref('')
const showNameModal = ref(false)
const nameError = ref('')
const pendingVote = ref(null)
const voteReason = ref('')
const voteSubmitting = ref(false)
const voteError = ref('')
const detailMatch = ref(null)
const detailTournamentId = ref(null)
const voteDetails = ref({ items: [], page: 0, totalPages: 0, totalElements: 0 })
const detailsLoading = ref(false)
const detailsError = ref('')
const players = ref([])
const playersLoading = ref(false)
const playersError = ref('')
const playerPage = ref(1)
const playerSearch = ref('')
const filteredPlayers = computed(() => players.value.filter(p => p.displayName.toLocaleLowerCase().includes(playerSearch.value.trim().toLocaleLowerCase())))
const playerPages = computed(() => Math.max(1, Math.ceil(filteredPlayers.value.length / 10)))
const visiblePlayers = computed(() => filteredPlayers.value.slice((playerPage.value - 1) * 10, playerPage.value * 10))
const resultRound = ref('all')
const resultStatus = ref('all')
const resultPage = ref(1)
const resultPageSize = ref(10)
const resultMatches = computed(() => (tournament.value?.rounds || [])
  .filter(r => resultRound.value === 'all' || r.index === Number(resultRound.value))
  .flatMap(r => r.matches.map(m => ({ ...m, roundName: r.name })))
  .filter(m => resultStatus.value === 'all' || (resultStatus.value === 'completed' ? m.winner : !m.winner)))
const resultPages = computed(() => Math.max(1, Math.ceil(resultMatches.value.length / resultPageSize.value)))
const visibleResults = computed(() => resultMatches.value.slice((resultPage.value - 1) * resultPageSize.value, resultPage.value * resultPageSize.value))

function editDisplayName() {
  nameInput.value = displayName.value
  nameError.value = ''
  showNameModal.value = true
}

function saveDisplayName() {
  const value = nameInput.value.trim()
  if (!value || value.length > 50) { nameError.value = 'Tên hiển thị cần từ 1 đến 50 ký tự.'; return }
  displayName.value = value
  localStorage.setItem('musicTournamentDisplayName', value)
  showNameModal.value = false
  flash(`Tên hiển thị: ${value}`)
}

async function loadPlayers() {
  const id = tournament.value?.id
  if (!id) return
  playersLoading.value = true
  playersError.value = ''
  try {
    const data = await api(`/tournaments/${id}/players?voterKey=${encodeURIComponent(voterKey)}`)
    if (tournament.value?.id === id) players.value = data
  } catch (e) { if (tournament.value?.id === id) playersError.value = e.message }
  finally { playersLoading.value = false }
}

async function openVoteDetails(match, targetPage = 0) {
  if (detailsLoading.value) return
  if (detailMatch.value?.id !== match.id) voteDetails.value = { items: [], page: 0, totalPages: 0, totalElements: 0 }
  detailMatch.value = match
  detailTournamentId.value = tournament.value.id
  detailsLoading.value = true
  detailsError.value = ''
  try {
    voteDetails.value = await api(`/tournaments/${detailTournamentId.value}/matches/${match.id}/votes?page=${targetPage}&size=10`)
  } catch (e) { detailsError.value = e.message }
  finally { detailsLoading.value = false }
}

function formatVoteTime(value) {
  return new Date(value).toLocaleString('vi-VN')
}

watch([resultRound, resultStatus, resultPageSize], () => { resultPage.value = 1 })
watch(resultPages, count => { resultPage.value = Math.min(resultPage.value, count) })
watch(playerSearch, () => { playerPage.value = 1 })
watch(playerPages, count => { playerPage.value = Math.min(playerPage.value, count) })
watch(page, value => { if (value === 'players') loadPlayers() })
let modalReturnFocus = null
watch(() => Boolean(pendingVote.value || showNameModal.value || detailMatch.value), async open => {
  if (open) {
    modalReturnFocus = document.activeElement
    await nextTick()
    document.querySelector('.vote-dialog input, .vote-dialog button')?.focus()
  } else {
    modalReturnFocus?.focus?.()
  }
})
function trapVoteDialogFocus(event) {
  if (event.key !== 'Tab') return
  const dialog = document.querySelector('.vote-dialog')
  if (!dialog) return
  const elements = [...dialog.querySelectorAll('button:not(:disabled), input:not(:disabled), textarea:not(:disabled)')]
  const first = elements[0], last = elements[elements.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}

// Seeding & Bracket setup
const seedingMode = ref('custom') // 'custom' (theo thứ tự sắp xếp) | 'shuffle' (ngẫu nhiên)
const createTab = ref('input') // 'input' | 'preview'
const initialOpenOption = ref('all') // 'all' | 'round1' | 'round1_2'

// Vote pagination & filters
const votePage = ref(1)
const votePageSize = ref(8)
const voteFilter = ref('all') // 'all' | 'unvoted' | 'voted' | 'completed'

// Tree zoom & screen fit
const treeScale = ref(0.7)
const isTreeFitScreen = ref(true)

// File upload ref
const fileInput = ref(null)

// 1. Inline Music Player state
const activePlayerSong = ref(null)
const championStage = ref(null)

// 2. Share & QR state
const showQrModal = ref(false)

// 3. Theme Toggle (Dark / Light)
const currentTheme = ref(localStorage.getItem('musicTournamentTheme') || 'light')

function applyTheme(theme) {
  currentTheme.value = theme
  localStorage.setItem('musicTournamentTheme', theme)
  document.documentElement.setAttribute('data-theme', theme)
  if (theme === 'dark') {
    document.body.classList.add('dark-mode')
  } else {
    document.body.classList.remove('dark-mode')
  }
}

function toggleTheme() {
  applyTheme(currentTheme.value === 'dark' ? 'light' : 'dark')
}

// 4. Admin & Guest Role Permissions
const userRole = ref(localStorage.getItem('musicTournamentRole') || 'GUEST')
const isAdmin = computed(() => userRole.value === 'ADMIN')
const currentAdminName = ref(localStorage.getItem('musicTournamentAdminName') || '')
const currentAdminEmail = ref(localStorage.getItem('musicTournamentAdminEmail') || '')
const showLoginModal = ref(false)
const adminEmailInput = ref('')
const adminPasswordInput = ref('')
const loginError = ref('')

async function loginAdmin(customEmail, customPass) {
  const email = customEmail !== undefined ? customEmail : adminEmailInput.value
  const password = customPass !== undefined ? customPass : adminPasswordInput.value
  loginError.value = ''
  try {
    const res = await api('/admin/login', {
      method: 'POST',
      body: JSON.stringify({
        email: (email || '').trim(),
        password: (password || '').trim()
      })
    })
    if (res.valid) {
      userRole.value = 'ADMIN'
      currentAdminName.value = res.name || 'Quản trị viên'
      currentAdminEmail.value = res.email || email
      localStorage.setItem('musicTournamentRole', 'ADMIN')
      localStorage.setItem('musicTournamentAdminName', currentAdminName.value)
      localStorage.setItem('musicTournamentAdminEmail', currentAdminEmail.value)
      showLoginModal.value = false
      adminPasswordInput.value = ''
      adminEmailInput.value = ''
      flash(`👑 Xin chào ${currentAdminName.value}! Đăng nhập Quản trị viên thành công.`)
    }
  } catch (err) {
    loginError.value = err.message || 'Tài khoản hoặc mật khẩu không chính xác.'
  }
}

function quickLoginAdminAccount(email, password) {
  adminEmailInput.value = email
  adminPasswordInput.value = password
  loginAdmin(email, password)
}

function quickLoginAdmin() {
  quickLoginAdminAccount('duyanh@gmail.com', '123456')
}

function logoutAdmin() {
  userRole.value = 'GUEST'
  currentAdminName.value = ''
  currentAdminEmail.value = ''
  localStorage.setItem('musicTournamentRole', 'GUEST')
  localStorage.removeItem('musicTournamentAdminName')
  localStorage.removeItem('musicTournamentAdminEmail')
  flash('👤 Đã đăng xuất. Bạn đang ở chế độ Khách (Người bình chọn).')
}

function requireAdmin(actionName, callback) {
  if (isAdmin.value) {
    callback()
  } else {
    loginError.value = `Chức năng "${actionName}" chỉ dành cho Quản trị viên. Vui lòng đăng nhập để tiếp tục.`
    showLoginModal.value = true
  }
}

// 5. Guest Features: Favorites & Champion Prediction
const favoriteSongIds = ref(JSON.parse(localStorage.getItem('musicTournamentFavorites') || '[]'))

function toggleFavorite(songId) {
  if (!songId) return
  const idx = favoriteSongIds.value.indexOf(songId)
  if (idx >= 0) {
    favoriteSongIds.value.splice(idx, 1)
    flash('Đã bỏ yêu thích bài hát')
  } else {
    favoriteSongIds.value.push(songId)
    flash('❤️ Đã thêm vào danh sách bài hát yêu thích!')
  }
  localStorage.setItem('musicTournamentFavorites', JSON.stringify(favoriteSongIds.value))
}

function isFavorite(songId) {
  return favoriteSongIds.value.includes(songId)
}

const predictionSongId = ref(0)

function loadPrediction() {
  if (!tournament.value) {
    predictionSongId.value = 0
    return
  }
  const saved = localStorage.getItem(`musicTournamentPred_${tournament.value.id}`)
  predictionSongId.value = saved ? Number(saved) : 0
}

function setPrediction(songId) {
  if (!tournament.value) return
  predictionSongId.value = songId
  localStorage.setItem(`musicTournamentPred_${tournament.value.id}`, String(songId))
  const song = tournament.value.songs.find(s => s.id === songId)
  flash(`🔮 Đã lưu dự đoán Quán quân: “${song?.title || 'Bài hát'}”!`)
}

// 6. Leaderboard & Standings (Hiệu số vote & Thứ hạng)
const leaderboardSearch = ref('')
const leaderboardSort = ref('rank') // 'rank' | 'diff' | 'votes' | 'winrate'
const leaderboardFavOnly = ref(false)

const computedLeaderboard = computed(() => {
  if (!tournament.value || !tournament.value.songs) return []
  const t = tournament.value
  const songs = t.songs || []
  const rounds = t.rounds || []
  const totalRounds = rounds.length
  const champ = tournament.value.champion

  const statsMap = new Map()
  songs.forEach(s => {
    statsMap.set(s.id, {
      song: s,
      matchesPlayed: 0,
      wins: 0,
      losses: 0,
      votesFor: 0,
      votesAgainst: 0,
      furthestRound: 0,
      furthestStage: 'Vòng 1',
      tier: 99
    })
  })

  rounds.forEach((r, rIdx) => {
    (r.matches || []).forEach(m => {
      const a = m.songA
      const b = m.songB
      if (!a || !b) return
      const vA = m.votesA || 0
      const vB = m.votesB || 0

      const stA = statsMap.get(a.id)
      if (stA) {
        stA.matchesPlayed++
        stA.votesFor += vA
        stA.votesAgainst += vB
        if (m.winner) {
          if (m.winner.id === a.id) stA.wins++
          else stA.losses++
        }
        if (rIdx >= stA.furthestRound) {
          stA.furthestRound = rIdx
          stA.furthestStage = r.name || `Vòng ${rIdx + 1}`
        }
      }

      const stB = statsMap.get(b.id)
      if (stB) {
        stB.matchesPlayed++
        stB.votesFor += vB
        stB.votesAgainst += vA
        if (m.winner) {
          if (m.winner.id === b.id) stB.wins++
          else stB.losses++
        }
        if (rIdx >= stB.furthestRound) {
          stB.furthestRound = rIdx
          stB.furthestStage = r.name || `Vòng ${rIdx + 1}`
        }
      }
    })
  })

  statsMap.forEach(st => {
    if (champ && st.song.id === champ.id) {
      st.tier = 1
      st.furthestStage = 'Quán quân 🏆'
    } else if (totalRounds > 0 && st.furthestRound === totalRounds - 1) {
      st.tier = 2
      st.furthestStage = 'Á quân 🥈'
    } else if (totalRounds > 1 && st.furthestRound === totalRounds - 2) {
      st.tier = 3
      st.furthestStage = 'Bán kết 🥉'
    } else if (totalRounds > 2 && st.furthestRound === totalRounds - 3) {
      st.tier = 4
      st.furthestStage = 'Tứ kết'
    } else {
      st.tier = 100 - st.furthestRound
    }
    st.voteDiff = st.votesFor - st.votesAgainst
    const totalV = st.votesFor + st.votesAgainst
    st.voteRatioPercent = totalV > 0 ? Math.round((st.votesFor / totalV) * 1000) / 10 : 0
    st.winRatePercent = st.matchesPlayed > 0 ? Math.round((st.wins / st.matchesPlayed) * 1000) / 10 : 0
  })

  let list = Array.from(statsMap.values())

  list.sort((x, y) => {
    if (x.tier !== y.tier) return x.tier - y.tier
    if (y.voteDiff !== x.voteDiff) return y.voteDiff - x.voteDiff
    if (y.votesFor !== x.votesFor) return y.votesFor - x.votesFor
    return y.wins - x.wins
  })

  list.forEach((item, idx) => {
    item.rank = idx + 1
  })

  if (leaderboardSort.value === 'diff') {
    list.sort((a, b) => b.voteDiff - a.voteDiff || b.votesFor - a.votesFor)
  } else if (leaderboardSort.value === 'votes') {
    list.sort((a, b) => b.votesFor - a.votesFor || b.voteDiff - a.voteDiff)
  } else if (leaderboardSort.value === 'winrate') {
    list.sort((a, b) => b.winRatePercent - a.winRatePercent || b.wins - a.wins)
  }

  const q = leaderboardSearch.value.trim().toLowerCase()
  if (q) {
    list = list.filter(item => 
      (item.song.title && item.song.title.toLowerCase().includes(q)) ||
      (item.song.artist && item.song.artist.toLowerCase().includes(q))
    )
  }

  if (leaderboardFavOnly.value) {
    list = list.filter(item => favoriteSongIds.value.includes(item.song.id))
  }

  return list
})

const podiumSongs = computed(() => {
  if (!tournament.value) return []
  const fullRanked = [...computedLeaderboard.value].sort((a, b) => a.rank - b.rank)
  return fullRanked.slice(0, 3)
})

function getVoterKey() {
  let key = localStorage.getItem('musicTournamentVoterKey')
  if (!key) {
    key = (crypto?.randomUUID?.() || `voter-${Date.now()}-${Math.random().toString(36).slice(2)}`)
    localStorage.setItem('musicTournamentVoterKey', key)
  }
  return key
}

async function api(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  })
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(data.message || `Lỗi HTTP ${response.status}`)
  return data
}

function flash(message) {
  notice.value = message
  setTimeout(() => { if (notice.value === message) notice.value = '' }, 3500)
}

async function loadTournaments() {
  try { tournaments.value = await api('/tournaments') } catch (e) { error.value = e.message }
}

async function openTournament(id, targetPage = 'rounds') {
  loading.value = true; error.value = ''
  try {
    tournament.value = await api(`/tournaments/${id}?voterKey=${encodeURIComponent(voterKey)}`)
    selectedRound.value = firstPendingRound()
    votePage.value = 1
    voteFilter.value = 'all'
    loadPrediction()
    autoFitTree()
    resultRound.value = 'all'
    resultStatus.value = 'all'
    resultPage.value = 1
    players.value = []
    playerPage.value = 1
    detailMatch.value = null
    pendingVote.value = null
    page.value = targetPage
    if (targetPage === 'players') await loadPlayers()
    window.location.hash = `/tournament/${id}`
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function checkUrlHash() {
  const match = window.location.hash.match(/tournament\/(\d+)/)
  if (match) {
    const id = Number(match[1])
    if (!tournament.value || tournament.value.id !== id) {
      openTournament(id)
    }
  }
}

function firstPendingRound() {
  const rounds = tournament.value?.rounds || []
  const pending = rounds.findIndex(r => !r.closed && r.matches.some(m => m.songA && m.songB && !m.winner && !m.closed))
  return pending >= 0 ? pending : 0
}

// ─── Fast Song Input & Parsing ───
const inputSongs = computed(() => parseSongs(songText.value))
const songValidation = computed(() => uniqueSongs(inputSongs.value))
const parsedSongs = computed(() => songValidation.value.unique)
const duplicateSongs = computed(() => songValidation.value.duplicates)
function removeDuplicateSongs() {
  songText.value = parsedSongs.value.map(formatSong).join('\n')
  flash('Đã loại bỏ bài trùng khỏi danh sách.')
}

// Bracket Calculation Helper
const bracketStats = computed(() => {
  const count = parsedSongs.value.length
  if (count < 2) return null
  if (count === 48) {
    return { count, is48: true, groupSize: 24, matchesPerGroup: 12, playoffMatches: 12, finalEntrants: 8 }
  }
  let size = 1
  while (size < count) size *= 2
  const byes = size - count
  const rounds = Math.log2(size)
  return { count, is48: false, size, byes, rounds }
})

function sameArtist(a, b) {
  if (!a || !b) return false
  const artA = (a.artist || '').trim().toLowerCase()
  const artB = (b.artist || '').trim().toLowerCase()
  if (!artA || !artB || artA === '—' || artA === '-' || artA === 'unknown') return false
  return artA === artB
}

// Luật chia nhánh: Tách các bài hát cùng một nghệ sĩ (Vòng 64 & 32 không gặp nhau)
function separateSongs(songList) {
  if (!songList || songList.length < 4) return [...songList]
  const n = songList.length
  const byArtist = new Map()
  const noArtist = []

  songList.forEach(s => {
    const art = s.artist ? s.artist.trim().toLowerCase() : ''
    if (!art || art === '—' || art === '-' || art === 'unknown') {
      noArtist.push(s)
    } else {
      if (!byArtist.has(art)) byArtist.set(art, [])
      byArtist.get(art).push(s)
    }
  })

  let hasMultiple = false
  for (const list of byArtist.values()) {
    if (list.length > 1) { hasMultiple = true; break }
  }
  if (!hasMultiple) return [...songList]

  const groups = Array.from(byArtist.values()).sort((a, b) => b.length - a.length)
  noArtist.forEach(s => groups.push([s]))

  const distributed = []
  let remaining = true
  let round = 0
  while (remaining) {
    remaining = false
    for (const g of groups) {
      if (round < g.length) {
        distributed.push(g[round])
        remaining = true
      }
    }
    round++
  }

  const numQuads = Math.max(1, Math.floor(n / 4))
  const result = new Array(n)
  const slotOrder = []
  for (let q = 0; q < numQuads; q++) if (4 * q < n) slotOrder.push(4 * q)
  for (let q = 0; q < numQuads; q++) if (4 * q + 2 < n) slotOrder.push(4 * q + 2)
  for (let q = 0; q < numQuads; q++) if (4 * q + 1 < n) slotOrder.push(4 * q + 1)
  for (let q = 0; q < numQuads; q++) if (4 * q + 3 < n) slotOrder.push(4 * q + 3)

  for (let i = 0; i < distributed.length && i < slotOrder.length; i++) {
    result[slotOrder[i]] = distributed[i]
  }
  let distIdx = slotOrder.length
  for (let i = 0; i < n; i++) {
    if (!result[i] && distIdx < distributed.length) {
      result[i] = distributed[distIdx++]
    }
  }

  // Conflict resolution
  const matches = Math.floor(n / 2)
  for (let i = 0; i < matches; i++) {
    const a = result[2 * i]
    const b = result[2 * i + 1]
    if (sameArtist(a, b)) {
      for (let j = 0; j < matches; j++) {
        if (j === i) continue
        const oA = result[2 * j]
        const oB = result[2 * j + 1]
        if (!sameArtist(a, oB) && !sameArtist(oA, b)) {
          result[2 * i + 1] = oB
          result[2 * j + 1] = b
          break
        }
      }
    }
  }

  return result.filter(Boolean)
}

// Preview of Initial Pairings (Bảng A & B khi 48 bài hoặc Cây chuẩn)
const previewPairings = computed(() => {
  let songs = [...parsedSongs.value]
  if (songs.length < 2) return []
  const count = songs.length
  const pairs = []
  let pos = 1

  // Tự động bảo vệ tác giả ở Vòng 64 & 32
  if (count === 64 || count === 32 || seedingMode.value === 'shuffle') {
    songs = separateSongs(songs)
  }

  if (count === 48) {
    // Chia 2 Bảng A và B: Mỗi bảng 24 bài (12 trận)
    const groupA = songs.slice(0, 24)
    const groupB = songs.slice(24, 48)

    // Bảng A (12 trận)
    for (let i = 0; i < groupA.length; i += 2) {
      pairs.push({
        num: pos++,
        roundLabel: 'BẢNG A',
        groupName: 'A',
        songA: groupA[i],
        songB: groupA[i + 1] || null,
        isBye: false,
        indexA: i,
        indexB: i + 1
      })
    }
    // Bảng B (12 trận)
    for (let i = 0; i < groupB.length; i += 2) {
      pairs.push({
        num: pos++,
        roundLabel: 'BẢNG B',
        groupName: 'B',
        songA: groupB[i],
        songB: groupB[i + 1] || null,
        isBye: false,
        indexA: 24 + i,
        indexB: 24 + i + 1
      })
    }
    return pairs
  }

  // Chuẩn các số lượng bài khác (32, 16, 8, hoặc lẻ có byes)
  let size = 1
  while (size < count) size *= 2
  const byes = size - count

  // Bye matches
  for (let i = 0; i < byes; i++) {
    pairs.push({
      num: pos++,
      roundLabel: 'Vòng 1',
      songA: songs[i],
      songB: null,
      isBye: true,
      indexA: i,
      indexB: -1
    })
  }

  // Regular matches
  for (let i = byes; i < count; i += 2) {
    pairs.push({
      num: pos++,
      roundLabel: 'Vòng 1',
      songA: songs[i],
      songB: i + 1 < count ? songs[i + 1] : null,
      isBye: false,
      indexA: i,
      indexB: i + 1 < count ? i + 1 : -1
    })
  }
  return pairs
})

// Reordering helper
function moveSongInList(index, dir) {
  const lines = songText.value.split(/\r?\n/).filter(s => s.trim())
  const target = index + dir
  if (target < 0 || target >= lines.length) return
  const temp = lines[index]
  lines[index] = lines[target]
  lines[target] = temp
  songText.value = lines.join('\n')
}

function removeSongFromList(index) {
  const lines = songText.value.split(/\r?\n/).filter(s => s.trim())
  lines.splice(index, 1)
  songText.value = lines.join('\n')
}

function shuffleSongList() {
  const lines = songText.value.split(/\r?\n/).filter(s => s.trim())
  for (let i = lines.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [lines[i], lines[j]] = [lines[j], lines[i]]
  }
  songText.value = lines.join('\n')
  flash('Đã xáo trộn ngẫu nhiên! Luật phân tách cùng tác giả sẽ tự động áp dụng 🔀')
}

function handleFileUpload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (e) => {
    const text = e.target.result
    if (text) {
      songText.value = text.trim()
      flash(`Đã nhập ${parsedSongs.value.length} bài hát từ file ${file.name}! 📁`)
    }
  }
  reader.readAsText(file)
}

function triggerFileUpload() {
  fileInput.value?.click()
}

// Preset Loaders
function loadPreset(count) {
  if (count === 8) {
    songText.value = `Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg
Gurenge — LiSA | https://youtu.be/CwkzK-F0Y00
Blue Bird — Ikimono-gakari | https://youtu.be/bM3OAPfrjzk
Kaikai Kitan — Eve | https://youtu.be/K_7To_y9IAM
Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ
Unravel — TK from 凛として時雨 | https://youtu.be/7aMOurgDB-o
Silhouette — KANA-BOON | https://youtu.be/dlFA0Zq1k2A
Again — YUI | https://youtu.be/2DYYVp4QXew`
  } else if (count === 16) {
    songText.value = `Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg
Gurenge — LiSA | https://youtu.be/CwkzK-F0Y00
Blue Bird — Ikimono-gakari | https://youtu.be/bM3OAPfrjzk
Kaikai Kitan — Eve | https://youtu.be/K_7To_y9IAM
Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ
Unravel — TK from 凛として時雨 | https://youtu.be/7aMOurgDB-o
Silhouette — KANA-BOON | https://youtu.be/dlFA0Zq1k2A
Again — YUI | https://youtu.be/2DYYVp4QXew
Cry Baby — Official HIGE DANdism | https://youtu.be/O1bhZgkC4Gw
KICK BACK — Kenshi Yonezu | https://youtu.be/M2cckDmNLMI
Shinunoga E-Wa — Fujii Kaze | https://youtu.be/dusrG66olzo
Bling-Bang-Bang-Born — Creepy Nuts | https://youtu.be/mLW35YMz1eo
Suzume — RADWIMPS feat. Toaka | https://youtu.be/grasqUvQ2X0
SPECIALZ — King Gnu | https://youtu.be/fhzKLBZJC3w
Nandemonaiya — RADWIMPS | https://youtu.be/n3e6M521rso
Lemon — Kenshi Yonezu | https://youtu.be/SX_ViT4Ra7k`
  } else if (count === 32) {
    const list = []
    const base = [
      'Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg',
      'Gurenge — LiSA | https://youtu.be/CwkzK-F0Y00',
      'Blue Bird — Ikimono-gakari | https://youtu.be/bM3OAPfrjzk',
      'Kaikai Kitan — Eve | https://youtu.be/K_7To_y9IAM',
      'Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ',
      'Unravel — TK from 凛として時雨 | https://youtu.be/7aMOurgDB-o',
      'Silhouette — KANA-BOON | https://youtu.be/dlFA0Zq1k2A',
      'Again — YUI | https://youtu.be/2DYYVp4QXew',
      'Cry Baby — Official HIGE DANdism',
      'KICK BACK — Kenshi Yonezu',
      'Shinunoga E-Wa — Fujii Kaze',
      'Bling-Bang-Bang-Born — Creepy Nuts',
      'Suzume — RADWIMPS feat. Toaka',
      'SPECIALZ — King Gnu',
      'Nandemonaiya — RADWIMPS',
      'Lemon — Kenshi Yonezu',
      'Racing Into The Night (Yoru ni Kakeru) — YOASOBI',
      'Monster — YOASOBI',
      'Chouchou Musubi — Aimer',
      'Zankyosanka — Aimer',
      'Crossing Field — LiSA',
      'Catch the Moment — LiSA',
      'Inferno — Mrs. GREEN APPLE',
      'Ao to Natsu — Mrs. GREEN APPLE',
      'Peace Sign — Kenshi Yonezu',
      'Grand Escape — RADWIMPS feat. Toko Miura',
      'Otonablue — ATARASHII GAKKO!',
      'Kaibutsu — YOASOBI',
      'First Love — Hikaru Utada',
      'One Last Kiss — Hikaru Utada',
      'Homura — LiSA',
      'DADDY ! DADDY ! DO ! — Masayuki Suzuki feat. Airi Suzuki'
    ]
    songText.value = base.join('\n')
  } else if (count === 48) {
    const list48 = [
      'Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg',
      'Gurenge — LiSA | https://youtu.be/CwkzK-F0Y00',
      'Blue Bird — Ikimono-gakari | https://youtu.be/bM3OAPfrjzk',
      'Kaikai Kitan — Eve | https://youtu.be/K_7To_y9IAM',
      'Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ',
      'Unravel — TK from 凛として時雨 | https://youtu.be/7aMOurgDB-o',
      'Silhouette — KANA-BOON | https://youtu.be/dlFA0Zq1k2A',
      'Again — YUI | https://youtu.be/2DYYVp4QXew',
      'Cry Baby — Official HIGE DANdism | https://youtu.be/O1bhZgkC4Gw',
      'KICK BACK — Kenshi Yonezu | https://youtu.be/M2cckDmNLMI',
      'Shinunoga E-Wa — Fujii Kaze | https://youtu.be/dusrG66olzo',
      'Bling-Bang-Bang-Born — Creepy Nuts | https://youtu.be/mLW35YMz1eo',
      'Suzume — RADWIMPS feat. Toaka | https://youtu.be/grasqUvQ2X0',
      'SPECIALZ — King Gnu | https://youtu.be/fhzKLBZJC3w',
      'Nandemonaiya — RADWIMPS | https://youtu.be/n3e6M521rso',
      'Lemon — Kenshi Yonezu | https://youtu.be/SX_ViT4Ra7k',
      'Racing Into The Night (Yoru ni Kakeru) — YOASOBI',
      'Monster — YOASOBI',
      'Chouchou Musubi — Aimer',
      'Zankyosanka — Aimer',
      'Crossing Field — LiSA',
      'Catch the Moment — LiSA',
      'Inferno — Mrs. GREEN APPLE',
      'Ao to Natsu — Mrs. GREEN APPLE',
      'Peace Sign — Kenshi Yonezu',
      'Grand Escape — RADWIMPS feat. Toko Miura',
      'Otonablue — ATARASHII GAKKO!',
      'Kaibutsu — YOASOBI',
      'First Love — Hikaru Utada',
      'One Last Kiss — Hikaru Utada',
      'Homura — LiSA',
      'DADDY ! DADDY ! DO ! — Masayuki Suzuki feat. Airi Suzuki',
      'Shunkan Sentimental — SCANDAL',
      'Haruka Kanata — ASIAN KUNG-FU GENERATION',
      'GO!!! — FLOW',
      'Sign — FLOW',
      'Colors — FLOW',
      'The Rumbling — SiM',
      'Red Swan — YOSHIKI feat. HYDE',
      'Boku no Sensou — Shinsei Kamattechan',
      'Akuma no Ko — Ai Higuchi',
      'Fiction — sumika',
      'Kibou no Uta — Ultra Tower',
      'Fly High!! — BURNOUT SYNDROMES',
      'Hikariare — BURNOUT SYNDROMES',
      'Phantom Minds — Nana Mizuki',
      'Renai Circulation — Kana Hanazawa',
      'Sugar Song to Bitter Step — UNISON SQUARE GARDEN'
    ]
    songText.value = list48.join('\n')
  } else if (count === 64) {
    const list64 = [
      'Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg',
      'Gurenge — LiSA | https://youtu.be/CwkzK-F0Y00',
      'Blue Bird — Ikimono-gakari | https://youtu.be/bM3OAPfrjzk',
      'Kaikai Kitan — Eve | https://youtu.be/K_7To_y9IAM',
      'Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ',
      'Unravel — TK from 凛として時雨 | https://youtu.be/7aMOurgDB-o',
      'Silhouette — KANA-BOON | https://youtu.be/dlFA0Zq1k2A',
      'Again — YUI | https://youtu.be/2DYYVp4QXew',
      'Cry Baby — Official HIGE DANdism | https://youtu.be/O1bhZgkC4Gw',
      'KICK BACK — Kenshi Yonezu | https://youtu.be/M2cckDmNLMI',
      'Shinunoga E-Wa — Fujii Kaze | https://youtu.be/dusrG66olzo',
      'Bling-Bang-Bang-Born — Creepy Nuts | https://youtu.be/mLW35YMz1eo',
      'Suzume — RADWIMPS feat. Toaka | https://youtu.be/grasqUvQ2X0',
      'SPECIALZ — King Gnu | https://youtu.be/fhzKLBZJC3w',
      'Nandemonaiya — RADWIMPS | https://youtu.be/n3e6M521rso',
      'Lemon — Kenshi Yonezu | https://youtu.be/SX_ViT4Ra7k',
      'Racing Into The Night (Yoru ni Kakeru) — YOASOBI',
      'Monster — YOASOBI',
      'Chouchou Musubi — Aimer',
      'Zankyosanka — Aimer',
      'Crossing Field — LiSA',
      'Catch the Moment — LiSA',
      'Inferno — Mrs. GREEN APPLE',
      'Ao to Natsu — Mrs. GREEN APPLE',
      'Peace Sign — Kenshi Yonezu',
      'Grand Escape — RADWIMPS feat. Toko Miura',
      'Otonablue — ATARASHII GAKKO!',
      'Kaibutsu — YOASOBI',
      'First Love — Hikaru Utada',
      'One Last Kiss — Hikaru Utada',
      'Homura — LiSA',
      'DADDY ! DADDY ! DO ! — Masayuki Suzuki feat. Airi Suzuki',
      'Shunkan Sentimental — SCANDAL',
      'Haruka Kanata — ASIAN KUNG-FU GENERATION',
      'GO!!! — FLOW',
      'Sign — FLOW',
      'Colors — FLOW',
      'The Rumbling — SiM',
      'Red Swan — YOSHIKI feat. HYDE',
      'Boku no Sensou — Shinsei Kamattechan',
      'Akuma no Ko — Ai Higuchi',
      'Fiction — sumika',
      'Kibou no Uta — Ultra Tower',
      'Fly High!! — BURNOUT SYNDROMES',
      'Hikariare — BURNOUT SYNDROMES',
      'Phantom Minds — Nana Mizuki',
      'Renai Circulation — Kana Hanazawa',
      'Sugar Song to Bitter Step — UNISON SQUARE GARDEN',
      'A Cruel Angels Thesis — Yoko Takahashi',
      'Tank! — The Seatbelts',
      'Connect — ClariS',
      'Only My Railgun — fripSide',
      'Brave Shine — Aimer',
      'Flyers — BRADIO',
      'Hacking to the Gate — Kanako Ito',
      'My Dearest — supercell',
      'Kimi no Shiranai Monogatari — supercell',
      'History Maker — DEAN FUJIOKA',
      'Tabi no Tochuu — Natsumi Kiyoura',
      'Seishun Kyousoukyoku — Sambomaster',
      'Drivers High — L\'Arc~en~Ciel',
      'Kizuna no Kiseki — MAN WITH A MISSION x milet',
      'Chainsaw Blood — Vaundy',
      'Kaijuu no Hanauta — Vaundy'
    ]
    songText.value = list64.join('\n')
  }
  flash(`Đã nạp bộ mẫu chuẩn ${count} bài!`)
}

// ─── Song Search (YouTube & Spotify / iTunes API) ───
const searchQuery = ref('')
const searchFilter = ref('all') // 'all' | 'spotify' | 'youtube'
const searchResults = ref([])
const searchLoading = ref(false)
const activePreviewUrl = ref('')
let previewAudio = null
const quickTags = ['🔥 Anime Hits', '🎵 YOASOBI', '⭐ V-Pop Top', '🎧 K-Pop', '🎸 J-Rock']


async function executeSearch() {
  const q = searchQuery.value.trim()
  if (!q) return
  searchLoading.value = true
  try {
    const res = await api(`/search?q=${encodeURIComponent(q)}&limit=16`)
    searchResults.value = uniqueSongs((res.results || []).map(item => ({ ...item, link: item.spotifyUrl || item.youtubeUrl }))).unique
    if (!searchResults.value.length) {
      flash('Không tìm thấy kết quả. Thử từ khóa khác nhé!')
    }
  } catch (err) {
    flash('Không thể tìm kiếm bài hát lúc này, hãy thử lại!')
  } finally {
    searchLoading.value = false
  }
}

function quickSearch(tag) {
  const clean = tag.replace(/^[^\w\s\u00C0-\u1EF9]+/, '').trim()
  searchQuery.value = clean
  executeSearch()
}

const filteredSearchResults = computed(() => {
  return searchResults.value
})

function searchSong(item) {
  return parseSongs(item.formattedText || formatSong({ ...item, link: item.youtubeUrl }))[0]
}
function isSongAdded(item) {
  const song = searchSong(item)
  return !song || uniqueSongs([...parsedSongs.value, song]).duplicates.length > 0
}
function addSongFromSearch(item) {
  if (isSongAdded(item)) { flash('Bài hát này đã có trong danh sách.'); return }
  const song = searchSong(item)
  songText.value = [...inputSongs.value.map(formatSong), formatSong(song)].join('\n')
  flash('Đã thêm “' + song.title + '” vào danh sách!')
}
function addAllSearchResults() {
  const combined = [...inputSongs.value, ...searchResults.value.map(searchSong).filter(Boolean)]
  const { unique } = uniqueSongs(combined)
  const added = unique.length - parsedSongs.value.length
  songText.value = unique.map(formatSong).join('\n')
  flash('Đã thêm ' + added + ' bài mới; bỏ qua các bài trùng.')
}

function toggleAudioPreview(url) {
  if (!url) return
  if (activePreviewUrl.value === url) {
    if (previewAudio) {
      previewAudio.pause()
      previewAudio = null
    }
    activePreviewUrl.value = ''
    return
  }
  if (previewAudio) {
    previewAudio.pause()
  }
  previewAudio = new Audio(url)
  activePreviewUrl.value = url
  previewAudio.play().catch(() => {
    activePreviewUrl.value = ''
  })
  previewAudio.onended = () => {
    activePreviewUrl.value = ''
  }
}

function copyChampionTitle() {
  if (!champion.value) return
  const text = `🏆 QUÁN QUÂN: ${champion.value.title} — ${champion.value.artist} (${tournament.value?.name || 'Music Tournament'})`
  navigator.clipboard?.writeText?.(text).then(() => {
    flash('Đã sao chép vinh danh Quán quân vào clipboard! 📋')
  })
}

// ─── Create Tournament with Seeding & Round Config ───
async function createTournament() {
  if (!isAdmin.value) {
    requireAdmin('Tạo giải đấu', createTournament)
    return
  }
  error.value = ''
  if (duplicateSongs.value.length) { error.value = 'Danh sách có bài trùng. Hãy loại bỏ bài trùng trước khi tạo giải.'; return }
  const songs = parsedSongs.value.map(formatSong)
  if (songs.length < 2) { error.value = 'Nhập ít nhất 2 bài hát hợp lệ nhé.'; return }
  loading.value = true

  // Determine initial open rounds
  let initialOpenRounds = null
  if (initialOpenOption.value === 'round1') {
    initialOpenRounds = [0] // Only Round 1 open
  } else if (initialOpenOption.value === 'round1_2') {
    initialOpenRounds = [0, 1] // Round 1 & Round 2 open
  }

  const payload = {
    name: tournamentName.value.trim() || 'Music Tournament',
    songs,
    shuffle: seedingMode.value === 'shuffle',
    initialOpenRounds
  }

  try {
    const created = await api('/tournaments', { method: 'POST', body: JSON.stringify(payload) })
    await loadTournaments()
    await openTournament(created.id)
    flash('Tạo giải thành công! Các nhánh đấu đã được phân bổ 🎵')
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function vote(match, song) {
  if (!tournament.value || match.hasVoted || match.winner || match.closed || !match.songA || !match.songB) return
  pendingVote.value = { tournamentId: tournament.value.id, match, song }
  voteReason.value = ''
  voteError.value = ''
  nameInput.value = displayName.value
}

async function submitVote() {
  if (!pendingVote.value || voteSubmitting.value) return
  const name = nameInput.value.trim()
  if (!name || name.length > 50) { voteError.value = 'Vui lòng nhập tên hiển thị từ 1 đến 50 ký tự.'; return }
  const { tournamentId, match, song } = pendingVote.value
  voteSubmitting.value = true
  voteError.value = ''
  try {
    const updated = await api('/tournaments/' + tournamentId + '/matches/' + match.id + '/vote', {
      method: 'POST', body: JSON.stringify({ voterKey, songId: song.id, displayName: name, reason: voteReason.value.trim() })
    })
    displayName.value = name
    localStorage.setItem('musicTournamentDisplayName', name)
    if (tournament.value?.id === tournamentId) tournament.value = updated
    pendingVote.value = null
    await loadTournaments()
    flash('Đã ghi nhận vote cho “' + song.title + '”')
  } catch (e) {
    voteError.value = e.message
    await refreshCurrent()
  } finally { voteSubmitting.value = false }
}

async function refreshCurrent() {
  if (!tournament.value) return
  try { tournament.value = await api(`/tournaments/${tournament.value.id}?voterKey=${encodeURIComponent(voterKey)}`) } catch (e) { error.value = e.message }
}

// 3. Silent live refresh (polling)
async function silentRefresh() {
  if (!tournament.value || loading.value) return
  try {
    const updated = await api(`/tournaments/${tournament.value.id}?voterKey=${encodeURIComponent(voterKey)}`)
    if (tournament.value?.id === updated.id) tournament.value = updated
    if (page.value === 'players') await loadPlayers()
  } catch (_) {}
}

// ─── Round Management (Open / Close Voting / Finalize) ───
async function toggleRoundVoting(roundIndex, currentlyClosed) {
  if (!tournament.value) return
  if (!isAdmin.value) {
    requireAdmin('Mở/Đóng bình chọn vòng', () => toggleRoundVoting(roundIndex, currentlyClosed))
    return
  }
  const open = currentlyClosed // If closed -> open; if open -> close
  try {
    tournament.value = await api(`/tournaments/${tournament.value.id}/admin/rounds/${roundIndex}/voting?open=${open}`, {
      method: 'PUT'
    })
    await loadTournaments()
    flash(open ? `Đã MỞ bình chọn cho ${tournament.value.rounds[roundIndex]?.name || 'vòng này'}!` : `Đã ĐÓNG bình chọn cho ${tournament.value.rounds[roundIndex]?.name || 'vòng này'}!`)
  } catch (e) { error.value = e.message }
}

async function finalizeRound(roundIndex) {
  if (!tournament.value) return
  if (!isAdmin.value) {
    requireAdmin('Chốt kết quả vòng đấu', () => finalizeRound(roundIndex))
    return
  }
  if (!confirm(`Xác nhận chốt kết quả ${tournament.value.rounds[roundIndex]?.name}? Người nhiều phiếu hơn sẽ vào vòng tiếp theo.`)) return
  try {
    tournament.value = await api(`/tournaments/${tournament.value.id}/admin/rounds/${roundIndex}/close`, {
      method: 'POST'
    })
    await loadTournaments()
    flash('Đã chốt kết quả và đưa các bài thắng vào vòng tiếp theo! 🏆')
  } catch (e) { error.value = e.message }
}

async function toggleTournament() {
  if (!tournament.value) return
  if (!isAdmin.value) {
    requireAdmin('Đóng/Mở toàn bộ giải', toggleTournament)
    return
  }
  try {
    tournament.value = await api(`/tournaments/${tournament.value.id}/admin/status?active=${!tournament.value.active}`, {
      method: 'PUT'
    })
    await loadTournaments(); flash(tournament.value.active ? 'Đã mở giải.' : 'Đã đóng giải.')
  } catch (e) { error.value = e.message }
}

async function exportResults() {
  if (!tournament.value) return
  const lines = [tournament.value.name, `Tổng bài: ${tournament.value.songs.length}`, '']
  tournament.value.rounds.forEach(round => {
    lines.push(round.name)
    round.matches.forEach((m, i) => {
      if (m.songA || m.songB) lines.push(`${i + 1}. ${m.songA?.title || 'Chưa xác định'} vs ${m.songB?.title || 'Vào thẳng'} => ${m.winner?.title || 'Chưa có kết quả'} | Vote ${m.votesA}:${m.votesB}`)
    })
    lines.push('')
  })
  const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = 'ket-qua-music-tournament.txt'; a.click()
  URL.revokeObjectURL(url)
}

// 1. Music Player Helpers
function getYoutubeEmbedUrl(url) {
  if (!url) return null
  const regExp = /(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?)\/|.*[?&]v=|shorts\/)|youtu\.be\/)([^"&?\/\s]{11})/i
  const match = url.match(regExp)
  return match && match[1] ? `https://www.youtube-nocookie.com/embed/${match[1]}?autoplay=1` : null
}

function getSpotifyEmbedUrl(url) {
  if (!url) return null
  // Match: open.spotify.com/track/ID or /album/ID or /playlist/ID or /episode/ID
  // Also supports spotify:track:ID uri format
  const trackMatch = url.match(/(?:open\.spotify\.com\/(track|album|playlist|episode)\/|spotify:(track|album|playlist|episode):)([a-zA-Z0-9]+)/i)
  if (trackMatch) {
    const type = trackMatch[1] || trackMatch[2]
    const id = trackMatch[3]
    return `https://open.spotify.com/embed/${type}/${id}?utm_source=generator&theme=0`
  }
  return null
}

function isSpotifyLink(url) {
  return !!url && (url.includes('spotify.com') || url.startsWith('spotify:'))
}

function playSong(song) {
  if (!song || !song.link) return
  championStage.value?.stop()
  activePlayerSong.value = song
}

function stopOtherPlayers() {
  activePlayerSong.value = null
  if (previewAudio) { previewAudio.pause(); previewAudio = null }
  activePreviewUrl.value = ''
}

function closePlayer() {
  activePlayerSong.value = null
}

// 2. Share helpers
const shareUrl = computed(() => {
  if (!tournament.value) return window.location.href
  return `${window.location.origin}${window.location.pathname}#/tournament/${tournament.value.id}`
})

async function copyShareLink() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    flash('Đã sao chép link giải đấu vào clipboard! 📋')
  } catch (_) {
    prompt('Link giải đấu của bạn:', shareUrl.value)
  }
}

const currentRound = computed(() => tournament.value?.rounds?.[selectedRound.value] || null)
const champion = computed(() => tournament.value?.champion || null)
const isFinalRound = computed(() => {
  const rounds = tournament.value?.rounds || []
  const round = currentRound.value
  return Boolean(round && round.index === rounds[rounds.length-1]?.index && round.matches.length === 1 && round.matches[0].songA && round.matches[0].songB)
})
const completedMatches = computed(() => (tournament.value?.rounds || []).flatMap(r => r.matches).filter(m => m.winner && m.songA && m.songB).length)
const totalMatches = computed(() => (tournament.value?.rounds || []).flatMap(r => r.matches).filter(m => m.songA && m.songB).length)
const progress = computed(() => totalMatches.value ? Math.round(completedMatches.value / totalMatches.value * 100) : 0)

// Check if a song won a wildcard entry
function isWildcardSong(songId) {
  if (!songId || !tournament.value?.wildcards) return false
  return tournament.value.wildcards.some(w => w.song?.id === songId)
}

// World Cup style two-sided Bracket Layout (Nhánh Trái ➔ Chung kết ở Giữa ➔ Nhánh Phải)
const worldCupBracket = computed(() => {
  const rounds = tournament.value?.rounds || []
  if (!rounds.length) return null
  const numRounds = rounds.length
  if (numRounds < 2) {
    return { isSplit: false, rounds }
  }

  const is48 = tournament.value?.songs?.length === 48
  const finalRound = rounds[numRounds - 1]
  const leftColumns = []
  const rightColumns = []

  // Các vòng từ 0 đến numRounds - 2 chia 2 nửa: Trái & Phải
  for (let r = 0; r < numRounds - 1; r++) {
    const round = rounds[r]
    const allMatches = round.matches
    const mid = Math.ceil(allMatches.length / 2)
    const leftMatches = allMatches.slice(0, mid)
    const rightMatches = allMatches.slice(mid)

    let leftName = round.name
    let rightName = round.name

    if (is48) {
      if (r === 0) {
        leftName = 'Bảng A Vòng 1 (12 cặp)'
        rightName = 'Bảng B Vòng 1 (12 cặp)'
      } else if (r === 1) {
        leftName = 'Bảng A Vòng 2 (6 cặp)'
        rightName = 'Bảng B Vòng 2 (6 cặp)'
      } else if (r === 2) {
        leftName = 'Play-off (3 cặp Trái)'
        rightName = 'Play-off (3 cặp Phải)'
      } else if (r === 3) {
        leftName = 'Tứ kết (Trái)'
        rightName = 'Tứ kết (Phải)'
      } else if (r === 4) {
        leftName = 'Bán kết 1'
        rightName = 'Bán kết 2'
      }
    } else {
      if (r === numRounds - 2) {
        leftName = 'Bán kết 1'
        rightName = 'Bán kết 2'
      } else if (r === numRounds - 3) {
        leftName = 'Tứ kết (Trái)'
        rightName = 'Tứ kết (Phải)'
      } else if (r === numRounds - 4) {
        leftName = 'Vòng 16 (Trái)'
        rightName = 'Vòng 16 (Phải)'
      } else if (r === numRounds - 5) {
        leftName = 'Vòng 32 (Trái)'
        rightName = 'Vòng 32 (Phải)'
      } else if (r === numRounds - 6) {
        leftName = 'Vòng 64 (Trái)'
        rightName = 'Vòng 64 (Phải)'
      } else {
        leftName = `${round.name} (Trái)`
        rightName = `${round.name} (Phải)`
      }
    }

    leftColumns.push({
      roundIndex: round.index,
      name: leftName,
      closed: round.closed,
      completed: round.completed,
      matches: leftMatches,
      side: 'left'
    })

    rightColumns.push({
      roundIndex: round.index,
      name: rightName,
      closed: round.closed,
      completed: round.completed,
      matches: rightMatches,
      side: 'right'
    })
  }

  return {
    isSplit: true,
    leftColumns, // Đi từ Vòng 1 ➔ Tứ kết ➔ Bán kết (bên trái)
    centerRound: finalRound, // Chung kết nằm chính giữa
    rightColumns: [...rightColumns].reverse() // Đi từ Bán kết ➔ Tứ kết ➔ Vòng 1 (bên phải tiến vào giữa)
  }
})

// Artwork & Thumbnail helpers (Hỗ trợ ảnh YouTube và Logo Web)
function extractYoutubeId(url) {
  if (!url) return null
  const regExp = /(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?)\/|.*[?&]v=|shorts\/)|youtu\.be\/)([^"&?\/\s]{11})/i
  const match = url.match(regExp)
  return match && match[1] ? match[1] : null
}

function getSongArtwork(song) {
  if (!song) return '/logo.png'
  if (song.artwork) return song.artwork
  const ytId = extractYoutubeId(song.link)
  if (ytId) {
    return `https://img.youtube.com/vi/${ytId}/hqdefault.jpg`
  }
  return '/logo.png'
}

function handleImgError(e) {
  if (e && e.target) {
    e.target.src = '/logo.png'
  }
}

// Tree view zoom & fit
function setTreeZoom(delta) {
  isTreeFitScreen.value = false
  treeScale.value = Math.max(0.35, Math.min(1.5, Number((treeScale.value + delta).toFixed(2))))
}

function resetTreeZoom() {
  isTreeFitScreen.value = false
  treeScale.value = 1.0
}

function toggleFitScreen() {
  isTreeFitScreen.value = !isTreeFitScreen.value
  if (isTreeFitScreen.value) {
    autoFitTree()
  } else {
    treeScale.value = 1.0
  }
}

function autoFitTree() {
  const total = tournament.value?.songs?.length || 32
  if (total >= 64) treeScale.value = 0.58
  else if (total >= 48) treeScale.value = 0.65
  else if (total >= 32) treeScale.value = 0.75
  else treeScale.value = 0.90
}

// Vote pagination & filters
const activeRoundMatches = computed(() => {
  if (!currentRound.value) return []
  return currentRound.value.matches.filter(m => m.songA || m.songB)
})

const filteredRoundMatches = computed(() => {
  const matches = activeRoundMatches.value
  if (voteFilter.value === 'unvoted') {
    return matches.filter(m => !m.hasVoted && !m.winner && !m.closed)
  }
  if (voteFilter.value === 'voted') {
    return matches.filter(m => m.hasVoted)
  }
  if (voteFilter.value === 'completed') {
    return matches.filter(m => !!m.winner)
  }
  return matches
})

const totalVotePages = computed(() => {
  return Math.max(1, Math.ceil(filteredRoundMatches.value.length / votePageSize.value))
})

const paginatedMatches = computed(() => {
  const start = (votePage.value - 1) * votePageSize.value
  return filteredRoundMatches.value.slice(start, start + votePageSize.value)
})

function setVotePage(p) {
  if (p >= 1 && p <= totalVotePages.value) {
    votePage.value = p
  }
}

watch(selectedRound, () => {
  votePage.value = 1
  voteFilter.value = 'all'
})

// Polling interval
let liveTimer = null

onMounted(() => {
  applyTheme(currentTheme.value)
  loadTournaments()
  checkUrlHash()
  loadPrediction()
  window.addEventListener('hashchange', checkUrlHash)
  document.addEventListener('keydown', trapVoteDialogFocus)
  liveTimer = setInterval(() => {
    if (tournament.value && tournament.value.active && (page.value === 'rounds' || page.value === 'results' || page.value === 'leaderboard' || page.value === 'players')) {
      silentRefresh()
    }
  }, 4000)
})

onUnmounted(() => {
  if (liveTimer) clearInterval(liveTimer)
  window.removeEventListener('hashchange', checkUrlHash)
  document.removeEventListener('keydown', trapVoteDialogFocus)
})
</script>

<template>
  <header class="topbar">
    <button class="btn sidebar-toggle-btn" aria-controls="main-sidebar" :aria-expanded="!isSidebarCollapsed" :aria-label="isSidebarCollapsed ? 'Mở sidebar' : 'Thu gọn sidebar'" @click="isSidebarCollapsed = !isSidebarCollapsed" :title="isSidebarCollapsed ? 'Mở rộng thanh menu' : 'Thu gọn thanh menu để tiết kiệm diện tích'">
      {{ isSidebarCollapsed ? '☰' : '◀' }}
    </button>
    <div class="brand-avatar-box">
      <img src="/logo.png" alt="Music Tournament Logo" class="brand-logo-img">
    </div>
    <div class="brand-copy"><h1>Music Tournament</h1><p>Giải đấu bài hát · tìm ra quán quân</p></div>
    <div class="top-spacer"></div><span class="top-slogan">Cùng chọn ra bài hát hay nhất! ♪</span>

    <!-- Theme Toggle (Dark / Light) -->
    <button class="theme-toggle-btn" @click="toggleTheme" :title="currentTheme==='dark' ? 'Chuyển sang giao diện Sáng (Light)' : 'Chuyển sang giao diện Tối (Dark)'">
      <span class="theme-icon">{{ currentTheme==='dark' ? '🌙' : '☀️' }}</span>
      <span class="theme-text">{{ currentTheme==='dark' ? 'Tối' : 'Sáng' }}</span>
    </button>

    <button class="btn mini-btn player-name-btn" @click="editDisplayName">👤 {{ displayName ? 'Đổi tên' : 'Đặt tên để vote' }}</button>
    <!-- Role Badge & Admin Login / Logout -->
    <div class="topbar-role-group">
      <span v-if="isAdmin" class="role-pill admin-pill" :title="'Quản trị viên: ' + (currentAdminEmail || '')">
        👑 {{ currentAdminName ? currentAdminName.split(' ')[0] : 'Admin' }} ({{ currentAdminEmail || 'Admin' }})
      </span>
      <span v-else class="role-pill guest-pill" title="Chế độ Khách bình chọn (Guest)">👤 {{ displayName || 'Khách' }}</span>
      <button v-if="isAdmin" class="btn mini-btn role-btn logout-btn" @click="logoutAdmin" title="Đăng xuất chế độ Admin">Đăng xuất</button>
      <button v-else class="btn mini-btn role-btn login-btn" @click="showLoginModal=true" title="Đăng nhập tài khoản Quản trị">👑 Đăng nhập Admin</button>
    </div>

    <button class="btn top-btn" @click="exportResults" :disabled="!tournament">⇩ Xuất kết quả</button>
  </header>

  <div class="shell" :class="{sidebarCollapsed: isSidebarCollapsed}">
    <aside id="main-sidebar" class="sidebar" :class="{collapsed: isSidebarCollapsed}">
      <div class="sidebar-header-row">
        <div v-show="!isSidebarCollapsed" class="sidebar-brand-preview">
          <img src="/logo.png" alt="Logo" class="sidebar-logo-img">
          <div class="sidebar-brand-text">
            <b>Music Arena</b>
            <small>Championship 2026</small>
          </div>
        </div>

      </div>
      <button v-for="item in [{id:'home',icon:'⌂',label:'Tổng quan & Tạo giải'},{id:'songs',icon:'♫',label:'Danh sách bài hát'},{id:'rounds',icon:'♧',label:'Vòng đấu'},{id:'leaderboard',icon:'🏆',label:'Bảng xếp hạng'},{id:'players',icon:'♟',label:'BXH người chơi'},{id:'results',icon:'▥',label:'Kết quả'},{id:'settings',icon:'⚙',label:'Quản trị'}]"
        :key="item.id" class="nav-item" :class="{active:page===item.id}" @click="page=item.id" :title="item.label">
        <span>{{ item.icon }}</span><span class="nav-label">{{ item.label }}</span>
      </button>
      <div v-show="!isSidebarCollapsed" class="side-quote">“Âm nhạc không chỉ là giai điệu, mà còn là cảm xúc kết nối chúng ta.”<div class="sound-wave">∿∿∿∿∿ ♡</div></div>
    </aside>

    <main class="main">
      <div class="page-heading">
        <div>
          <p class="eyebrow">MUSIC TOURNAMENT ENGINE / PRO BRACKETS</p>
          <h2>{{ page==='home'?'Tạo & Quản lý giải đấu':page==='songs'?'Danh sách bài hát':page==='rounds'?'Sơ đồ vòng đấu':page==='leaderboard'?'Bảng xếp hạng & Hiệu số vote':page==='results'?'Kết quả giải đấu':page==='players'?'Bảng xếp hạng người chơi':'Quản trị giải đấu' }}</h2>
          <p class="muted">Tùy biến nhánh đấu, tải danh sách tốc độ cao và mở/đóng từng vòng bình chọn linh hoạt.</p>
        </div>
        <div class="heading-badges">
          <span v-if="tournament && tournament.active && (page==='rounds'||page==='results')" class="live-badge" title="Tự động cập nhật số phiếu theo thời gian thực">
            <span class="pulse-dot"></span> Trực tiếp
          </span>
          <span class="status-pill" :class="{off:tournament&&!tournament.active}">{{ tournament ? (tournament.active?'● Đang mở vote':'● Giải đã đóng') : '● Sẵn sàng' }}</span>
        </div>
      </div>

      <div v-if="error" class="alert error"><span>⚠</span>{{ error }}<button @click="error=''">×</button></div>
      <div v-if="notice" class="alert success"><span>✓</span>{{ notice }}<button @click="notice=''">×</button></div>

      <div v-if="page==='results' || page==='players'" class="tournament-selector" aria-label="Chọn giải đấu">
        <button v-for="t in tournaments" :key="t.id" class="tournament-choice" :class="{active:tournament?.id===t.id}" :disabled="loading" @click="openTournament(t.id, page)">
          <b>{{ t.name }}</b><small>{{ t.songCount }} bài · {{ t.champion ? '🏆 Đã có quán quân' : t.active ? '● Đang mở' : 'Đã đóng' }}</small>
        </button>
        <p v-if="!tournaments.length" class="muted">Chưa có giải đấu.</p>
      </div>

      <!-- PAGE: HOME (CREATION & TOURNAMENTS) -->
      <section v-if="page==='home'" class="dashboard-grid">
        <div class="panel create-panel advanced-create">
          <div class="section-head">
            <div><span class="mini-icon">✦</span><h3>Tạo giải đấu chuyên nghiệp</h3></div>
            <div class="create-sub-tabs">
              <button class="sub-tab-btn" :class="{active:createTab==='input'}" @click="createTab='input'">📝 1. Nhập bài hát</button>
              <button class="sub-tab-btn" :class="{active:createTab==='preview'}" @click="createTab='preview'" :disabled="parsedSongs.length < 2 || duplicateSongs.length > 0">
                ⑂ 2. Xếp nhánh & cặp đấu ({{ parsedSongs.length }})
              </button>
            </div>
          </div>

          <!-- GUEST NOTICE IN CREATION PANEL -->
          <div v-if="!isAdmin" class="guest-mode-notice">
            <div class="guest-notice-content">
              <span class="guest-notice-icon">👤</span>
              <div>
                <b>Chế độ Khách (Người bình chọn)</b>
                <p>Bạn đang khám phá với tư cách khán giả. Bạn có thể vote cho bài hát, xem sơ đồ cây & bảng xếp hạng. Để tạo giải đấu hoặc chỉnh sửa cấu hình, hãy đăng nhập Quản trị viên.</p>
              </div>
            </div>
            <button class="btn mini-btn primary" @click="showLoginModal=true">👑 Đăng nhập Admin</button>
          </div>

          <!-- TAB 1: FAST INPUT & TOOLBAR -->
          <div v-if="createTab==='input'">
            <label>Tên giải đấu</label>
            <input v-model="tournamentName" placeholder="Ví dụ: Anime Song Championship 2026">

            <!-- Fast Input Toolbar -->
            <div class="fast-input-bar">
              <span class="input-bar-label">⚡ Nhập nhanh:</span>
              <button class="btn mini-btn" @click="triggerFileUpload" title="Tải file text hoặc csv">📁 Tải file (.txt)</button>
              <input type="file" ref="fileInput" accept=".txt,.csv" @change="handleFileUpload" style="display:none">
              <div class="preset-group">
                <span class="preset-label">Mẫu chuẩn:</span>
                <button class="btn mini-btn" @click="loadPreset(8)">8 bài</button>
                <button class="btn mini-btn" @click="loadPreset(16)">16 bài</button>
                <button class="btn mini-btn" @click="loadPreset(32)">32 bài</button>
                <button class="btn mini-btn" @click="loadPreset(48)">48 bài (A/B + Wildcard)</button>
                <button class="btn mini-btn" @click="loadPreset(64)">64 bài</button>
              </div>
              <button v-if="parsedSongs.length > 1" class="btn mini-btn" @click="shuffleSongList" title="Xáo trộn danh sách bài">🔀 Xáo trộn</button>
            </div>

            <!-- SEARCH PANEL: TÌM BÀI HÁT QUA YOUTUBE & SPOTIFY -->
            <div class="song-search-container">
              <div class="search-head-row">
                <div class="search-title">
                  <span class="search-icon">🔍</span>
                  <b>Tìm & Thêm bài hát nhanh qua YouTube & Spotify</b>
                </div>
                <div class="search-filter-pills">
                  <button class="pill-btn" :class="{active: searchFilter==='all'}" @click="searchFilter='all'">Tất cả</button>
                  <button class="pill-btn" :class="{active: searchFilter==='spotify'}" @click="searchFilter='spotify'">🟢 Spotify</button>
                  <button class="pill-btn" :class="{active: searchFilter==='youtube'}" @click="searchFilter='youtube'">🔴 YouTube</button>
                </div>
              </div>

              <div class="search-input-box">
                <input
                  v-model="searchQuery"
                  @keydown.enter.prevent="executeSearch"
                  placeholder="Nhập tên bài hát hoặc ca sĩ (VD: YOASOBI, Lemon, Sơn Tùng, Jujutsu Kaisen...)"
                  class="search-input-field"
                >
                <button class="btn primary search-submit-btn" :disabled="searchLoading" @click="executeSearch">
                  {{ searchLoading ? '⏳ Đang tìm...' : '🔍 Tìm kiếm' }}
                </button>
              </div>

              <!-- Quick Genre Suggestions -->
              <div class="search-tags-bar">
                <span class="tag-label">Gợi ý hot:</span>
                <button v-for="tag in quickTags" :key="tag" class="genre-tag-chip" @click="quickSearch(tag)">
                  {{ tag }}
                </button>
              </div>

              <!-- Search Results -->
              <div v-if="searchResults.length > 0" class="search-results-panel">
                <div class="results-toolbar">
                  <span>Tìm thấy <b>{{ filteredSearchResults.length }}</b> bài hát:</span>
                  <button class="btn add-all-songs-btn" @click="addAllSearchResults" title="Thêm tất cả các bài trong kết quả tìm kiếm vào danh sách thi đấu">
                    ➕ Thêm tất cả ({{ filteredSearchResults.length }} bài)
                  </button>
                </div>

                <div class="search-cards-grid">
                  <div v-for="(item, idx) in filteredSearchResults" :key="idx" class="search-song-card">
                    <div class="search-card-art">
                      <img v-if="item.artwork" :src="item.artwork" :alt="item.title" class="search-thumb-img">
                      <span v-else class="search-thumb-fallback">♫</span>
                      <button v-if="item.previewUrl" class="card-play-preview-btn" :class="{isPlaying: activePreviewUrl===item.previewUrl}" @click="toggleAudioPreview(item.previewUrl)" title="Nghe thử đoạn 30s">
                        {{ activePreviewUrl===item.previewUrl ? '⏸' : '▶' }}
                      </button>
                    </div>
                    <div class="search-card-meta">
                      <b :title="item.title">{{ item.title }}</b>
                      <small :title="item.artist">{{ item.artist || '—' }}</small>
                      <span class="genre-badge">{{ item.genre }}</span>
                    </div>
                    <div class="search-card-actions">
                      <a :href="item.spotifyUrl" target="_blank" rel="noopener noreferrer" class="btn mini-btn spotify-action-btn" title="Mở trên Spotify">
                        🟢 Spotify
                      </a>
                      <a :href="item.youtubeUrl" target="_blank" rel="noopener noreferrer" class="btn mini-btn yt-action-btn" title="Tìm trên YouTube">
                        🔴 YouTube
                      </a>
                      <button class="btn add-song-btn" :disabled="isSongAdded(item)" @click="addSongFromSearch(item)" title="Thêm bài này vào danh sách giải đấu">
                        {{ isSongAdded(item) ? '✓ Đã thêm' : '➕ Thêm bài' }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <label class="textarea-label">
              <span>Danh sách bài hát <span class="muted small">(mỗi dòng 1 bài: Tên — Nghệ sĩ | link YouTube/Spotify hoặc copy từ Excel)</span></span>
              <span v-if="bracketStats" class="bracket-badge" :class="{perfect: bracketStats.is48 || bracketStats.byes===0}">
                <template v-if="bracketStats.is48">
                  ⚡ 48 bài ➔ Chia Bảng A & B (mỗi bảng 24 bài / 12 trận) ➔ Play-off ➔ Tứ kết (6 thắng + 2 vé đặc cách Wildcard)
                </template>
                <template v-else>
                  {{ bracketStats.count }} bài ➔ Nhánh {{ bracketStats.size }} ({{ bracketStats.byes === 0 ? 'Chuẩn nhánh, 0 vé vào thẳng' : bracketStats.byes + ' vé vào thẳng' }})
                </template>
              </span>
            </label>
            <textarea v-model="songText" rows="9" placeholder="Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg&#10;Gurenge — LiSA | https://youtu.be/CwkzK-F0Y00&#10;Blue Bird — Ikimono-gakari&#10;Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ"></textarea>

            <div v-if="duplicateSongs.length" class="duplicate-warning" role="alert">
              <b>Phát hiện {{ duplicateSongs.length }} bài trùng:</b>
              <span>{{ duplicateSongs.map(s => s.title + ' — ' + s.artist).join('; ') }}</span>
              <button class="btn soft" @click="removeDuplicateSongs">Loại bỏ bài trùng</button>
            </div>
            <div class="setup-row">
              <div class="setup-col">
                <label>Phân nhánh vòng 1</label>
                <select v-model="seedingMode" class="form-select">
                  <option value="custom">🔢 Theo thứ tự sắp xếp (Bài 1 gặp 2, 3 gặp 4...)</option>
                  <option value="shuffle">🔀 Xáo trộn ngẫu nhiên tất cả bài hát</option>
                </select>
              </div>
              <div class="setup-col">
                <label>Mở vòng ban đầu</label>
                <select v-model="initialOpenOption" class="form-select">
                  <option value="all">🔓 Mở bình chọn tất cả các vòng</option>
                  <option value="round1">🔒 Chỉ mở Vòng 1 (Các vòng sau đóng)</option>
                  <option value="round1_2">🔒 Chỉ mở Vòng 1 & 2 (Các vòng sau đóng)</option>
                </select>
              </div>
            </div>

            <div class="form-foot">
              <button class="btn soft" @click="createTab='preview'" :disabled="parsedSongs.length < 2 || duplicateSongs.length > 0">
                👉 Xem trước & Tùy chỉnh cặp đấu ({{ parsedSongs.length }} bài)
              </button>
              <button class="btn primary" @click="createTournament" :disabled="loading || parsedSongs.length < 2 || duplicateSongs.length > 0">
                {{ loading ? 'Đang tạo…' : '＋ Tạo giải đấu ngay' }}
              </button>
            </div>
          </div>

          <!-- TAB 2: SEEDING & PAIRING ADJUSTMENT -->
          <div v-else class="preview-pairing-view">
            <div class="preview-head">
              <div>
                <h4>Điều chỉnh thứ tự & Cặp đấu</h4>
                <p class="muted small">Bạn có thể bấm ⬆ ⬇ để di chuyển bài hát và sắp xếp cặp đấu như ý muốn trước khi tạo giải.</p>
              </div>
              <button class="btn soft" @click="shuffleSongList">🔀 Xáo trộn ngẫu nhiên</button>
            </div>

            <!-- Artist Protection Rule Notification -->
            <div class="artist-rule-alert">
              <span>🛡</span>
              <div>
                <b>Luật bảo vệ tác giả tự động:</b>
                <span> Ở Vòng 64 & Vòng 32, các bài hát cùng một tác giả sẽ không gặp nhau để tạo sự công bằng cao nhất.</span>
              </div>
            </div>

            <div class="preview-pairings-list">
              <div v-for="pair in previewPairings" :key="pair.num" class="preview-pair-card" :class="{byeCard:pair.isBye, sameArtistCard: pair.songA && pair.songB && sameArtist(pair.songA, pair.songB)}">
                <div class="pair-tag">
                  <span>{{ pair.roundLabel ? pair.roundLabel + ' · ' : '' }}TRẬN {{ pair.num }}</span>
                  <span v-if="pair.isBye" class="bye-label">Vé vào thẳng vòng sau</span>
                  <span v-if="pair.songA && pair.songB && sameArtist(pair.songA, pair.songB)" class="pair-same-artist-warn">
                    ⚠️ Trùng tác giả: {{ pair.songA.artist }}
                  </span>
                </div>
                
                <!-- Song A -->
                <div class="pair-song-row">
                  <span class="seed-num">#{{ pair.indexA + 1 }}</span>
                  <div class="pair-song-thumb">
                    <img :src="getSongArtwork(pair.songA)" :alt="pair.songA?.title" class="pair-thumb-img" @error="handleImgError($event)">
                  </div>
                  <div class="pair-song-info">
                    <b>{{ pair.songA?.title }}</b>
                    <small>{{ pair.songA?.artist || '—' }}</small>
                  </div>
                  <div class="pair-actions">
                    <button class="icon-btn" :disabled="pair.indexA === 0" @click="moveSongInList(pair.indexA, -1)" title="Di chuyển lên">▲</button>
                    <button class="icon-btn" :disabled="pair.indexA === parsedSongs.length - 1" @click="moveSongInList(pair.indexA, 1)" title="Di chuyển xuống">▼</button>
                  </div>
                </div>

                <div v-if="!pair.isBye" class="pair-vs">VS</div>

                <!-- Song B -->
                <div v-if="!pair.isBye && pair.songB" class="pair-song-row">
                  <span class="seed-num">#{{ pair.indexB + 1 }}</span>
                  <div class="pair-song-thumb">
                    <img :src="getSongArtwork(pair.songB)" :alt="pair.songB?.title" class="pair-thumb-img" @error="handleImgError($event)">
                  </div>
                  <div class="pair-song-info">
                    <b>{{ pair.songB?.title }}</b>
                    <small>{{ pair.songB?.artist || '—' }}</small>
                  </div>
                  <div class="pair-actions">
                    <button class="icon-btn" :disabled="pair.indexB === 0" @click="moveSongInList(pair.indexB, -1)" title="Di chuyển lên">▲</button>
                    <button class="icon-btn" :disabled="pair.indexB === parsedSongs.length - 1" @click="moveSongInList(pair.indexB, 1)" title="Di chuyển xuống">▼</button>
                  </div>
                </div>
              </div>
            </div>

            <div class="form-foot">
              <button class="btn soft" @click="createTab='input'">← Quay lại nhập bài</button>
              <button class="btn primary" @click="createTournament" :disabled="loading">
                {{ loading ? 'Đang tạo…' : '✓ Hoàn tất & Khởi tạo giải đấu' }}
              </button>
            </div>
          </div>
        </div>

        <div class="panel info-panel">
          <div class="section-head"><div><span class="mini-icon violet">▦</span><h3>Tổng quan</h3></div></div>
          <div class="stat"><span>Giải đấu đã tạo</span><b>{{ tournaments.length }}</b></div>
          <div class="stat"><span>Giải đang mở</span><b>{{ tournaments.filter(t=>t.active).length }}</b></div>
          <div class="stat"><span>Thể thức</span><b>Loại trực tiếp</b></div>
          <div class="stat"><span>Tốc độ nạp bài</span><b>Tối ưu hàng loạt</b></div>
          <div class="tip-box">
            <span>⚡</span>
            <p><b>Tính năng mở/đóng vòng</b><br>Bạn có thể mở riêng Vòng 1 và Vòng 2 cho bình chọn trước, các vòng tiếp theo sẽ tạm đóng cho đến khi bạn quyết định mở!</p>
          </div>
        </div>

        <div class="panel tournament-list-panel">
          <div class="section-head"><div><span class="mini-icon">♫</span><h3>Giải đấu gần đây</h3></div><button class="text-btn" @click="loadTournaments">↻ Làm mới</button></div>
          <div v-if="!tournaments.length" class="empty-state"><div class="empty-note">♫</div><b>Chưa có giải đấu nào</b><p>Tạo giải đầu tiên của bạn ở khung bên trái nhé.</p></div>
          <button v-for="t in tournaments" :key="t.id" class="tournament-row" @click="openTournament(t.id)">
            <span class="album">♫</span><span class="tournament-info"><b>{{ t.name }}</b><small>{{ t.songCount }} bài · {{ t.completedMatches }} trận đã có kết quả</small></span><span class="row-status" :class="{closed:!t.active}">{{ t.active?'Đang mở':'Đã đóng' }}</span><span>›</span>
          </button>
        </div>
      </section>

      <!-- PAGE: SONGS -->
      <section v-else-if="page==='songs'" class="panel">
        <div class="section-head"><div><span class="mini-icon">♫</span><h3>{{ tournament?.name || 'Chọn một giải đấu' }}</h3></div><button class="btn soft" @click="page='home'">＋ Tạo giải mới</button></div>
        <p v-if="!tournament" class="muted">Chọn giải đấu từ trang Tổng quan để xem danh sách bài hát.</p>
        <div v-else class="song-grid">
          <div v-for="(song,i) in tournament.songs" :key="song.id" class="library-song">
            <span class="song-index">{{ String(i+1).padStart(2,'0') }}</span>
            <div class="song-cover-box" style="width:34px;height:34px;border-radius:4px;flex-shrink:0;">
              <img :src="getSongArtwork(song)" :alt="song.title" class="song-cover-img" @error="handleImgError($event)">
            </div>
            <div><b>{{ song.title }}</b><small>{{ song.artist || 'Chưa nhập nghệ sĩ' }}</small></div>
            <button v-if="song.link" @click="playSong(song)" class="btn song-link-btn" :class="{spotifyBtn: isSpotifyLink(song.link)}" :title="isSpotifyLink(song.link) ? 'Nghe thử trên Spotify' : 'Nghe thử bài này'">
              {{ isSpotifyLink(song.link) ? '🟢 Nghe thử' : '▶ Nghe thử' }}
            </button>
          </div>
        </div>
      </section>

      <!-- PAGE: ROUNDS (BRACKET & CONTROLS) -->
      <section v-else-if="page==='rounds'" class="panel bracket-panel">
        <div class="section-head">
          <div><span class="mini-icon violet">♧</span><h3>{{ tournament?.name || 'Sơ đồ vòng đấu' }}</h3></div>
          <div class="head-actions">
            <button v-if="tournament" class="btn share-action-btn" @click="copyShareLink" title="Sao chép link gửi cho bạn bè">📋 Copy Link</button>
            <button v-if="tournament" class="btn share-action-btn qr-btn" @click="showQrModal=true" title="Mã QR quét trên điện thoại">📱 QR Code</button>
            <button class="btn soft" @click="refreshCurrent" :disabled="!tournament">↻ Làm mới</button>
            <button class="btn primary" @click="page='home'">＋ Tạo giải</button>
          </div>
        </div>
        <p v-if="!tournament" class="muted">Hãy chọn một giải từ trang Tổng quan.</p>
        <template v-else>
          <div class="tournament-summary">
            <div><span class="muted small">TỔNG SỐ BÀI</span><b>{{ tournament.songs.length }}</b></div>
            <div><span class="muted small">TIẾN ĐỘ</span><b>{{ progress }}%</b></div>
            <div class="summary-progress"><div class="progress-track"><i :style="{width:progress+'%'}"></i></div><span class="muted small">{{ completedMatches }}/{{ totalMatches }} trận có kết quả</span></div>
            <!-- View Mode Switcher -->
            <div class="view-mode-toggle">
              <button class="mode-btn" :class="{active:viewMode==='tabs'}" @click="viewMode='tabs'">⊞ Theo vòng</button>
              <button class="mode-btn" :class="{active:viewMode==='tree'}" @click="viewMode='tree'">⑂ Sơ đồ cây</button>
            </div>
          </div>

          <!-- ROUND CONTROLLER BAR (ĐÓNG/MỞ TỪNG VÒNG) -->
          <div class="rounds-status-panel">
            <div class="rounds-status-title">
              <b>🎛 Điều khiển đóng/mở bình chọn từng vòng:</b>
              <small class="muted">Bật hoặc tắt vote độc lập cho từng vòng (ví dụ mở vòng 1 & 2, đóng các vòng còn lại).</small>
            </div>
            <div class="rounds-chips-row">
              <div v-for="round in tournament.rounds" :key="round.index" class="round-status-chip" :class="{chipClosed:round.closed, chipCompleted:round.completed}">
                <div class="chip-info">
                  <span class="chip-name">{{ round.name }}</span>
                  <span class="chip-state" :class="{openState:!round.closed, closedState:round.closed}">
                    {{ round.completed ? '🏆 Đã chốt xong' : (round.matches.some(m => m.songA || m.songB) ? (round.closed ? '🔒 Đã đóng vote' : '🔓 Đang mở vote') : '⏳ Chờ vòng trước') }}
                  </span>
                </div>
                <div class="chip-actions">
                  <button v-if="round.matches.some(m => m.songA || m.songB)" class="chip-toggle-btn" :class="{openAction:round.closed, closeAction:!round.closed}" @click="toggleRoundVoting(round.index, round.closed)" :title="round.closed ? 'Mở bình chọn cho vòng này' : 'Tạm đóng bình chọn cho vòng này'">
                    {{ round.closed ? '🔓 Mở vote' : '🔒 Đóng vote' }}
                  </button>
                  <button v-if="round.matches.some(m => m.songA || m.songB) && !round.completed" class="chip-finish-btn" @click="finalizeRound(round.index)" title="Chốt kết quả vòng này để đưa bài thắng vào vòng tiếp theo">
                    ✓ Chốt vòng
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- WILDCARD BANNER (KHI CÓ BÀI ĐẶC CÁCH VÀO TỨ KẾT) -->
          <div v-if="tournament.wildcards && tournament.wildcards.length > 0" class="wildcard-history-banner">
            <div class="wildcard-history-header">
              <span class="wildcard-trophy-icon">⭐</span>
              <div>
                <h4>VÉ ĐẶC CÁCH VÀO TỨ KẾT (WILDCARD)</h4>
                <p>2 bài hát thua ở Play-off có thành tích cao nhất được đặc cách vào vòng Tứ kết</p>
              </div>
            </div>
            <div class="wildcard-cards-grid">
              <div v-for="(wc, idx) in tournament.wildcards" :key="idx" class="wildcard-item-card">
                <div class="wc-badge-num">Hạng {{ idx + 1 }}</div>
                <div class="wc-song-info">
                  <b>{{ wc.song?.title }}</b>
                  <small>{{ wc.song?.artist || '—' }}</small>
                </div>
                <div class="wc-stats-col">
                  <span class="wc-ratio-val">{{ wc.voteRatioPercent ? wc.voteRatioPercent.toFixed(1) : 0 }}%</span>
                  <span class="wc-votes-text">{{ wc.votesReceived }} / {{ wc.totalMatchVotes }} phiếu</span>
                </div>
                <button v-if="wc.song?.link" class="btn mini-btn" :class="{spotifyBtn: isSpotifyLink(wc.song.link)}" @click="playSong(wc.song)" :title="isSpotifyLink(wc.song.link) ? 'Nghe thử Spotify' : 'Nghe thử YouTube'">
                  {{ isSpotifyLink(wc.song.link) ? '🟢 Nghe' : '▶ Nghe' }}
                </button>
              </div>
            </div>
          </div>

          <!-- MODE 1: TABS VIEW -->
          <div v-if="viewMode==='tabs'">
            <div class="round-tabs">
              <button v-for="(round,i) in tournament.rounds" :key="round.index" :class="{active:selectedRound===i, isClosedTab:round.closed}" @click="selectedRound=i">
                <b>{{ round.name }}</b>
                <small>
                  {{ round.matches.filter(m=>m.songA||m.songB).length }} cặp · {{ round.completed ? '🏆 Xong' : (round.closed ? '🔒 Đóng' : '🔓 Mở') }}
                </small>
              </button>
            </div>

            <div v-if="currentRound" class="current-round-banner" :class="{roundClosedBanner:currentRound.closed}">
              <div class="banner-text">
                <span v-if="currentRound.closed" class="banner-tag closedTag">🔒 VÒNG NÀY ĐANG ĐÓNG BÌNH CHỌN</span>
                <span v-else class="banner-tag openTag">🔓 VÒNG NÀY ĐANG MỞ BÌNH CHỌN</span>
                <span>{{ currentRound.name }} · {{ currentRound.matches.filter(m=>m.songA||m.songB).length }} cặp đấu</span>
              </div>
              <div class="banner-actions">
                <button class="btn mini-btn" :class="{primary:currentRound.closed, soft:!currentRound.closed}" @click="toggleRoundVoting(currentRound.index, currentRound.closed)">
                  {{ currentRound.closed ? '🔓 Mở bình chọn vòng này' : '🔒 Đóng bình chọn vòng này' }}
                </button>
              </div>
            </div>

            <!-- Vote Filter & Pagination Toolbar -->
            <div class="vote-pagination-toolbar">
              <div class="vote-filter-tabs">
                <button class="vote-filter-btn" :class="{active: voteFilter==='all'}" @click="voteFilter='all'; votePage=1">
                  Tất cả ({{ activeRoundMatches.length }})
                </button>
                <button class="vote-filter-btn" :class="{active: voteFilter==='unvoted'}" @click="voteFilter='unvoted'; votePage=1">
                  ⏳ Chưa bình chọn ({{ activeRoundMatches.filter(m => !m.hasVoted && !m.winner && !m.closed).length }})
                </button>
                <button class="vote-filter-btn" :class="{active: voteFilter==='voted'}" @click="voteFilter='voted'; votePage=1">
                  ✓ Đã vote ({{ activeRoundMatches.filter(m => m.hasVoted).length }})
                </button>
                <button class="vote-filter-btn" :class="{active: voteFilter==='completed'}" @click="voteFilter='completed'; votePage=1">
                  🏆 Có kết quả ({{ activeRoundMatches.filter(m => !!m.winner).length }})
                </button>
              </div>

              <div class="vote-pages-control" v-if="totalVotePages > 1">
                <span class="pagination-info">
                  Trang <b>{{ votePage }}</b> / {{ totalVotePages }}
                </span>
                <div class="pagination-btn-group">
                  <button class="page-nav-btn" :disabled="votePage <= 1" @click="setVotePage(votePage - 1)">‹ Trước</button>
                  <button 
                    v-for="p in totalVotePages" 
                    :key="p" 
                    class="page-num-btn" 
                    :class="{active: votePage === p}"
                    @click="setVotePage(p)"
                  >
                    {{ p }}
                  </button>
                  <button class="page-nav-btn" :disabled="votePage >= totalVotePages" @click="setVotePage(votePage + 1)">Sau ›</button>
                </div>
              </div>
            </div>

            <div v-if="isFinalRound" class="finale-intro">
              <span class="finale-eyebrow">✦ THE GRAND FINAL ✦</span>
              <h3>Chung kết</h3>
              <p>Hai bài hát. Một ngôi vô địch. {{ champion ? 'Quán quân đã được gọi tên.' : 'Lá phiếu của bạn làm nên khoảnh khắc này.' }}</p>
            </div>
            <div v-if="currentRound" class="match-grid" :class="{'finale-match-grid':isFinalRound}">
              <article v-for="match in paginatedMatches" :key="match.id" class="match-card" :class="{matchLocked:match.closed, 'finale-match-card':isFinalRound}">
                <div class="match-top">
                  <span>{{ isFinalRound ? '🏆 TRẬN TRANH NGÔI VÔ ĐỊCH' : 'TRẬN ' + (match.position+1) }}</span><button class="match-detail-btn" @click="openVoteDetails(match)">Xem chi tiết</button>
                  <span v-if="match.isWildcard" class="wildcard-tag" title="Trận đấu có bài đặc cách Wildcard">⭐ Vé đặc cách</span>
                  <span v-if="match.winner" class="match-state won">✓ Đã có kết quả</span>
                  <span v-else-if="match.closed" class="match-state closedState">🔒 Đã đóng vote</span>
                  <span v-else class="match-state live">● Đang vote</span>
                </div>
                <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="match-song" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}">
                  <template v-if="song">
                    <div class="song-cover-box" :title="song.title + ' — ' + (song.artist || '')">
                      <img :src="getSongArtwork(song)" :alt="song.title" class="song-cover-img" @error="handleImgError($event)">
                    </div>
                    <div class="song-meta">
                      <b>{{ song.title }} <span v-if="isWildcardSong(song.id)" class="song-wc-badge" title="Bài hát đạt vé đặc cách Wildcard">⭐</span></b>
                      <small>{{ song.artist || 'Chưa nhập nghệ sĩ' }}</small>
                      <div class="vote-bar"><i :style="{width:((match['votes'+(side===0?'A':'B')]+1)/(match.votesA+match.votesB+2)*100)+'%'}"></i></div>
                    </div>
                    <div class="vote-side">
                      <small>{{ match['votes'+(side===0?'A':'B')] }} vote</small>
                      <button v-if="song.link" class="btn song-link-btn" :class="{spotifyBtn: isSpotifyLink(song.link)}" :title="isSpotifyLink(song.link) ? 'Nghe thử Spotify' : 'Nghe thử YouTube'" @click="playSong(song)">
                        {{ isSpotifyLink(song.link) ? '🟢' : '▶' }}
                      </button>
                      <button class="btn vote-btn" :class="{selected:match.winner?.id===song.id}" :disabled="!tournament.active||match.closed||match.winner||match.hasVoted||!match.songB" @click="vote(match,song)">
                        {{ match.winner?.id===song.id ? '✓ Thắng' : (match.closed ? 'Đã đóng' : 'Vote') }}
                      </button>
                    </div>
                  </template>
                  <div v-else class="tbd">Đang chờ kết quả vòng trước…</div>
                </div>
                <div v-if="match.hasVoted&&!match.winner" class="match-foot">✓ Bạn đã bình chọn cặp này</div>
                <div v-else-if="match.bye" class="match-foot">Tự động vào vòng tiếp theo</div>
              </article>
            </div>

            <!-- Bottom Pagination -->
            <div class="vote-pagination-bottom" v-if="totalVotePages > 1">
              <span class="pagination-info">
                Hiển thị trận {{ (votePage - 1) * votePageSize + 1 }} - {{ Math.min(votePage * votePageSize, filteredRoundMatches.length) }} trên tổng số {{ filteredRoundMatches.length }} trận
              </span>
              <div class="pagination-btn-group">
                <button class="page-nav-btn" :disabled="votePage <= 1" @click="setVotePage(votePage - 1)">‹ Trước</button>
                <button 
                  v-for="p in totalVotePages" 
                  :key="p" 
                  class="page-num-btn" 
                  :class="{active: votePage === p}"
                  @click="setVotePage(p)"
                >
                  {{ p }}
                </button>
                <button class="page-nav-btn" :disabled="votePage >= totalVotePages" @click="setVotePage(votePage + 1)">Sau ›</button>
              </div>
            </div>
          </div>

          <!-- MODE 2: BRACKET TREE VIEW (WORLD CUP 2-WING BRACKET) -->
          <div v-else class="bracket-tree-wrapper world-cup-stadium">
            <div class="stadium-header">
              <div class="stadium-titles">
                <span class="stadium-badge">NHÁNH THI ĐẤU QUỐC TẾ</span>
                <h3 class="stadium-title">{{ tournament.name.toUpperCase() }}</h3>
              </div>
              <div class="tree-controls-bar">
                <button class="tree-zoom-btn fit-btn" :class="{active: isTreeFitScreen}" @click="toggleFitScreen" title="Tự động thu phóng để khớp toàn bộ sơ đồ với màn hình">
                  ⛶ Khớp màn hình
                </button>
                <div class="zoom-stepper">
                  <button class="tree-zoom-btn mini" @click="setTreeZoom(-0.1)" title="Thu nhỏ sơ đồ">🔍−</button>
                  <span class="zoom-percent-val">{{ Math.round(treeScale * 100) }}%</span>
                  <button class="tree-zoom-btn mini" @click="setTreeZoom(0.1)" title="Phóng to sơ đồ">🔍＋</button>
                </div>
                <button class="tree-zoom-btn reset-btn" @click="resetTreeZoom" title="Đặt lại kích thước chuẩn 100%">
                  100%
                </button>
              </div>
              <div class="stadium-glow"></div>
            </div>

            <div class="bracket-tree-scroll world-cup-tree" :style="{ transform: 'scale(' + treeScale + ')', transformOrigin: 'top center' }">
              <!-- CÁNH TRÁI (LEFT WING) -->
              <div class="bracket-wing left-wing">
                <div v-for="col in worldCupBracket.leftColumns" :key="'left-'+col.roundIndex" class="tree-round-column">
                  <div class="tree-round-header" :class="{treeHeaderClosed:col.closed}">
                    <h4>{{ col.name }}</h4>
                    <small>{{ col.completed ? '🏆 Xong' : (col.closed ? '🔒 Đóng' : '🔓 Đang mở') }}</small>
                    <button class="tree-toggle-round-btn" @click="toggleRoundVoting(col.roundIndex, col.closed)">
                      {{ col.closed ? '🔓 Mở' : '🔒 Đóng' }}
                    </button>
                  </div>
                  <div class="tree-round-matches">
                    <div v-for="match in col.matches" :key="match.id" class="tree-match-card" :class="{treeMatchLocked:match.closed}">
                      <div class="tree-match-header">
                        <span>#{{ match.position + 1 }}</span><button class="match-detail-btn" @click="openVoteDetails(match)">Chi tiết</button>
                        <span v-if="match.isWildcard" class="wildcard-tag" title="Trận đấu có bài đặc cách Wildcard">⭐ Đặc cách</span>
                        <span v-if="match.winner" class="mini-status won">✓ Xong</span>
                        <span v-else-if="match.closed" class="mini-status closed">🔒 Đóng</span>
                        <span v-else class="mini-status live">Vote</span>
                      </div>
                      <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="tree-match-song" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}">
                        <template v-if="song">
                          <div class="tree-song-thumb" :title="song.title">
                            <img :src="getSongArtwork(song)" :alt="song.title" class="tree-thumb-img" @error="handleImgError($event)">
                          </div>
                          <div class="tree-song-text">
                            <b :title="song.title">{{ song.title }} <span v-if="isWildcardSong(song.id)" class="song-wc-badge" title="Bài hát đạt vé đặc cách">⭐</span></b>
                            <small :title="song.artist">{{ song.artist || '—' }}</small>
                          </div>
                          <div class="tree-song-actions">
                            <button v-if="song.link" class="tree-play-btn" :class="{spotifyTreeBtn: isSpotifyLink(song.link)}" @click="playSong(song)" :title="isSpotifyLink(song.link) ? 'Nghe thử Spotify' : 'Nghe thử YouTube'">
                              {{ isSpotifyLink(song.link) ? '🟢' : '▶' }}
                            </button>
                            <span class="tree-vote-count">{{ match['votes'+(side===0?'A':'B')] }}</span>
                            <button class="tree-vote-btn" :class="{selected:match.winner?.id===song.id}" :disabled="!tournament.active||match.closed||match.winner||match.hasVoted||!match.songB" @click="vote(match,song)">
                              {{ match.winner?.id===song.id ? '✓' : 'Vote' }}
                            </button>
                          </div>
                        </template>
                        <div v-else class="tree-tbd">Chờ vòng trước…</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- CHÍNH GIỮA: TRẬN CHUNG KẾT & CÚP VÀNG (CENTER TROPHY & FINAL MATCH) -->
              <div class="bracket-center-podium">
                <div class="cup-trophy-display">
                  <div class="gold-trophy-icon">🏆</div>
                  <div class="trophy-ribbon">CHUNG KẾT VÔ ĐỊCH</div>
                </div>

                <div v-if="worldCupBracket.centerRound" class="center-final-card">
                  <div class="tree-round-header gold-header">
                    <h4>{{ worldCupBracket.centerRound.name }}</h4>
                    <small>{{ worldCupBracket.centerRound.completed ? '🏆 Đã tìm ra quán quân' : (worldCupBracket.centerRound.closed ? '🔒 Đã đóng' : '🔓 Đang tranh cúp') }}</small>
                    <button class="tree-toggle-round-btn" @click="toggleRoundVoting(worldCupBracket.centerRound.index, worldCupBracket.centerRound.closed)">
                      {{ worldCupBracket.centerRound.closed ? '🔓 Mở vote' : '🔒 Đóng vote' }}
                    </button>
                  </div>

                  <div class="center-final-matches">
                    <div v-for="match in worldCupBracket.centerRound.matches" :key="match.id" class="tree-match-card final-glow-card" :class="{treeMatchLocked:match.closed}">
                      <div class="tree-match-header final-header-bar">
                        <span>TRẬN TRANH CÚP VÀNG</span><button class="match-detail-btn" @click="openVoteDetails(match)">Chi tiết</button>
                        <span v-if="match.winner" class="mini-status won">🏆 QUÁN QUÂN: {{ match.winner.title }}</span>
                        <span v-else-if="match.closed" class="mini-status closed">🔒 Đóng vote</span>
                        <span v-else class="mini-status live">● ĐANG BÌNH CHỌN</span>
                      </div>
                      <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="tree-match-song final-song-row" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}">
                        <template v-if="song">
                          <div class="tree-song-thumb" :title="song.title">
                            <img :src="getSongArtwork(song)" :alt="song.title" class="tree-thumb-img" @error="handleImgError($event)">
                          </div>
                          <div class="tree-song-text">
                            <b :title="song.title">{{ song.title }} <span v-if="isWildcardSong(song.id)" class="song-wc-badge" title="Bài hát đạt vé đặc cách">⭐</span></b>
                            <small :title="song.artist">{{ song.artist || '—' }}</small>
                          </div>
                          <div class="tree-song-actions">
                            <button v-if="song.link" class="tree-play-btn" :class="{spotifyTreeBtn: isSpotifyLink(song.link)}" @click="playSong(song)" :title="isSpotifyLink(song.link) ? 'Nghe thử Spotify' : 'Nghe thử YouTube'">
                              {{ isSpotifyLink(song.link) ? '🟢' : '▶' }}
                            </button>
                            <span class="tree-vote-count">{{ match['votes'+(side===0?'A':'B')] }}</span>
                            <button class="tree-vote-btn" :class="{selected:match.winner?.id===song.id}" :disabled="!tournament.active||match.closed||match.winner||match.hasVoted||!match.songB" @click="vote(match,song)">
                              {{ match.winner?.id===song.id ? '✓ VÔ ĐỊCH' : 'Vote' }}
                            </button>
                          </div>
                        </template>
                        <div v-else class="tree-tbd">Đang đợi đối thủ vào Chung kết…</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- CÁNH PHẢI (RIGHT WING) -->
              <div class="bracket-wing right-wing">
                <div v-for="col in worldCupBracket.rightColumns" :key="'right-'+col.roundIndex" class="tree-round-column">
                  <div class="tree-round-header" :class="{treeHeaderClosed:col.closed}">
                    <h4>{{ col.name }}</h4>
                    <small>{{ col.completed ? '🏆 Xong' : (col.closed ? '🔒 Đóng' : '🔓 Đang mở') }}</small>
                    <button class="tree-toggle-round-btn" @click="toggleRoundVoting(col.roundIndex, col.closed)">
                      {{ col.closed ? '🔓 Mở' : '🔒 Đóng' }}
                    </button>
                  </div>
                  <div class="tree-round-matches">
                    <div v-for="match in col.matches" :key="match.id" class="tree-match-card" :class="{treeMatchLocked:match.closed}">
                      <div class="tree-match-header">
                        <span>#{{ match.position + 1 }}</span><button class="match-detail-btn" @click="openVoteDetails(match)">Chi tiết</button>
                        <span v-if="match.isWildcard" class="wildcard-tag" title="Trận đấu có bài đặc cách Wildcard">⭐ Đặc cách</span>
                        <span v-if="match.winner" class="mini-status won">✓ Xong</span>
                        <span v-else-if="match.closed" class="mini-status closed">🔒 Đóng</span>
                        <span v-else class="mini-status live">Vote</span>
                      </div>
                      <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="tree-match-song" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}">
                        <template v-if="song">
                          <div class="tree-song-thumb" :title="song.title">
                            <img :src="getSongArtwork(song)" :alt="song.title" class="tree-thumb-img" @error="handleImgError($event)">
                          </div>
                          <div class="tree-song-text">
                            <b :title="song.title">{{ song.title }} <span v-if="isWildcardSong(song.id)" class="song-wc-badge" title="Bài hát đạt vé đặc cách">⭐</span></b>
                            <small :title="song.artist">{{ song.artist || '—' }}</small>
                          </div>
                          <div class="tree-song-actions">
                            <button v-if="song.link" class="tree-play-btn" :class="{spotifyTreeBtn: isSpotifyLink(song.link)}" @click="playSong(song)" :title="isSpotifyLink(song.link) ? 'Nghe thử Spotify' : 'Nghe thử YouTube'">
                              {{ isSpotifyLink(song.link) ? '🟢' : '▶' }}
                            </button>
                            <span class="tree-vote-count">{{ match['votes'+(side===0?'A':'B')] }}</span>
                            <button class="tree-vote-btn" :class="{selected:match.winner?.id===song.id}" :disabled="!tournament.active||match.closed||match.winner||match.hasVoted||!match.songB" @click="vote(match,song)">
                              {{ match.winner?.id===song.id ? '✓' : 'Vote' }}
                            </button>
                          </div>
                        </template>
                        <div v-else class="tree-tbd">Chờ vòng trước…</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

            </div>
          </div>

          <!-- GRAND CHAMPION CELEBRATION STAGE -->
          <ChampionStage v-if="champion" :key="tournament.id + ':' + champion.id" ref="championStage" :song="champion" :tournament-name="tournament.name" :artwork="getSongArtwork(champion)" @before-play="stopOtherPlayers" @share="copyChampionTitle" />
          <div v-if="showAdmin" class="admin-tools">
            <h3>🔐 Quản trị toàn giải</h3>
            <div class="admin-actions">
              <button class="btn soft" @click="toggleTournament">{{ tournament.active ? 'Đóng toàn bộ giải' : 'Mở lại giải' }}</button>
            </div>
          </div>
          <button class="admin-toggle" @click="showAdmin=!showAdmin">{{ showAdmin ? 'Ẩn công cụ quản trị' : '⚙ Cài đặt đóng/mở toàn giải' }}</button>
        </template>
      </section>

      <!-- PAGE: LEADERBOARD -->
      <section v-else-if="page==='leaderboard'" class="panel leaderboard-panel">
        <div class="section-head">
          <div><span class="mini-icon gold">🏆</span><h3>Bảng Xếp Hạng & Hiệu Số Vote</h3></div>
          <div class="head-actions">
            <button class="btn soft" @click="refreshCurrent" :disabled="!tournament">↻ Làm mới</button>
            <button class="btn primary" @click="exportResults" :disabled="!tournament">⇩ Xuất kết quả</button>
          </div>
        </div>

        <p v-if="!tournament" class="muted">Hãy chọn một giải đấu từ trang Tổng quan để xem bảng xếp hạng chi tiết.</p>
        <template v-else>
          <!-- GUEST CHAMPION PREDICTION WIDGET -->
          <div class="prediction-card-banner">
            <div class="prediction-card-head">
              <span class="prediction-symbol">🔮</span>
              <div class="prediction-text">
                <b>Dự đoán Quán quân Giải đấu (Dành cho Khách & Khán giả)</b>
                <p v-if="predictionSongId">
                  Lựa chọn của bạn: 
                  <strong class="prediction-selected-title">
                    {{ tournament.songs.find(s => s.id === predictionSongId)?.title || 'Bài hát đã chọn' }}
                  </strong>
                  <span v-if="champion && champion.id === predictionSongId" class="pred-tag pred-win">
                    🎉 BẠN ĐÃ DỰ ĐOÁN CHÍNH XÁC!
                  </span>
                  <span v-else-if="champion && champion.id !== predictionSongId" class="pred-tag pred-lose">
                    Đã dừng bước
                  </span>
                </p>
                <p v-else class="muted small">Bạn dự đoán bài hát nào sẽ đăng quang ngôi vị quán quân? Hãy chọn bài hát bên dưới:</p>
              </div>
            </div>
            <div class="prediction-card-control">
              <select :value="predictionSongId" @change="setPrediction(Number($event.target.value))" class="prediction-select">
                <option :value="0">-- Chọn bài hát dự đoán Quán quân --</option>
                <option v-for="s in tournament.songs" :key="s.id" :value="s.id">
                  {{ s.title }} ({{ s.artist || '—' }})
                </option>
              </select>
            </div>
          </div>

          <!-- TOP 3 PODIUM SECTION -->
          <div v-if="podiumSongs.length >= 2" class="podium-wrapper">
            <div class="podium-header-badge">
              <span>✨ BỤC VINH QUANG TOP 3 ✨</span>
            </div>

            <div class="podium-stage">
              <!-- RANK 2: SILVER (BÊN TRÁI) -->
              <div v-if="podiumSongs[1]" class="podium-pedestal-group silver-group">
                <div class="podium-card silver-card">
                  <div class="podium-medal-badge silver-badge">🥈 2</div>
                  <div class="podium-cover-box">
                    <img :src="getSongArtwork(podiumSongs[1].song)" :alt="podiumSongs[1].song.title" class="podium-thumb" @error="handleImgError($event)">
                    <button v-if="podiumSongs[1].song.link" class="podium-play-trigger" :class="{spotifyBtn: isSpotifyLink(podiumSongs[1].song.link)}" @click="playSong(podiumSongs[1].song)">
                      {{ isSpotifyLink(podiumSongs[1].song.link) ? '🟢' : '▶' }}
                    </button>
                  </div>
                  <b class="podium-title-text" :title="podiumSongs[1].song.title">{{ podiumSongs[1].song.title }}</b>
                  <small class="podium-artist-text">{{ podiumSongs[1].song.artist || '—' }}</small>
                  <div class="podium-round-tag">{{ podiumSongs[1].furthestStage }}</div>
                  <div class="podium-diff-pill" :class="{diffPos: podiumSongs[1].voteDiff > 0, diffNeg: podiumSongs[1].voteDiff < 0}">
                    Hiệu số: {{ podiumSongs[1].voteDiff > 0 ? '+' + podiumSongs[1].voteDiff : podiumSongs[1].voteDiff }}
                  </div>
                </div>
                <div class="podium-step step-2">
                  <span>Á QUÂN</span>
                </div>
              </div>

              <!-- RANK 1: GOLD (CHÍNH GIỮA, CAO NHẤT) -->
              <div v-if="podiumSongs[0]" class="podium-pedestal-group gold-group">
                <div class="podium-crown-icon">👑</div>
                <div class="podium-card gold-card">
                  <div class="podium-medal-badge gold-badge">🥇 1</div>
                  <div class="podium-cover-box gold-cover">
                    <img :src="getSongArtwork(podiumSongs[0].song)" :alt="podiumSongs[0].song.title" class="podium-thumb" @error="handleImgError($event)">
                    <button v-if="podiumSongs[0].song.link" class="podium-play-trigger" :class="{spotifyBtn: isSpotifyLink(podiumSongs[0].song.link)}" @click="playSong(podiumSongs[0].song)">
                      {{ isSpotifyLink(podiumSongs[0].song.link) ? '🟢' : '▶' }}
                    </button>
                  </div>
                  <b class="podium-title-text gold-text" :title="podiumSongs[0].song.title">{{ podiumSongs[0].song.title }}</b>
                  <small class="podium-artist-text">{{ podiumSongs[0].song.artist || '—' }}</small>
                  <div class="podium-round-tag gold-round">{{ podiumSongs[0].furthestStage }}</div>
                  <div class="podium-diff-pill diffPos">
                    Hiệu số: {{ podiumSongs[0].voteDiff > 0 ? '+' + podiumSongs[0].voteDiff : podiumSongs[0].voteDiff }}
                  </div>
                </div>
                <div class="podium-step step-1">
                  <span>QUÁN QUÂN 🏆</span>
                </div>
              </div>

              <!-- RANK 3: BRONZE (BÊN PHẢI) -->
              <div v-if="podiumSongs[2]" class="podium-pedestal-group bronze-group">
                <div class="podium-card bronze-card">
                  <div class="podium-medal-badge bronze-badge">🥉 3</div>
                  <div class="podium-cover-box">
                    <img :src="getSongArtwork(podiumSongs[2].song)" :alt="podiumSongs[2].song.title" class="podium-thumb" @error="handleImgError($event)">
                    <button v-if="podiumSongs[2].song.link" class="podium-play-trigger" :class="{spotifyBtn: isSpotifyLink(podiumSongs[2].song.link)}" @click="playSong(podiumSongs[2].song)">
                      {{ isSpotifyLink(podiumSongs[2].song.link) ? '🟢' : '▶' }}
                    </button>
                  </div>
                  <b class="podium-title-text" :title="podiumSongs[2].song.title">{{ podiumSongs[2].song.title }}</b>
                  <small class="podium-artist-text">{{ podiumSongs[2].song.artist || '—' }}</small>
                  <div class="podium-round-tag">{{ podiumSongs[2].furthestStage }}</div>
                  <div class="podium-diff-pill" :class="{diffPos: podiumSongs[2].voteDiff > 0, diffNeg: podiumSongs[2].voteDiff < 0}">
                    Hiệu số: {{ podiumSongs[2].voteDiff > 0 ? '+' + podiumSongs[2].voteDiff : podiumSongs[2].voteDiff }}
                  </div>
                </div>
                <div class="podium-step step-3">
                  <span>BÁN KẾT</span>
                </div>
              </div>
            </div>
          </div>

          <!-- CONTROLS & SEARCH BAR -->
          <div class="leaderboard-toolbar">
            <div class="lb-search-container">
              <span class="lb-search-ico">🔍</span>
              <input v-model="leaderboardSearch" placeholder="Tìm kiếm bài hát, ca sĩ..." class="lb-search-input">
            </div>

            <div class="lb-filter-sort-group">
              <span class="lb-sort-caption">Sắp xếp:</span>
              <div class="lb-sort-pills">
                <button class="lb-pill" :class="{active: leaderboardSort==='rank'}" @click="leaderboardSort='rank'" title="Thứ hạng giải đấu từ Quán quân, Á quân đến các vòng trước">
                  🏆 Thứ hạng giải
                </button>
                <button class="lb-pill" :class="{active: leaderboardSort==='diff'}" @click="leaderboardSort='diff'" title="Sắp theo hiệu số vote cao nhất">
                  ± Hiệu số vote
                </button>
                <button class="lb-pill" :class="{active: leaderboardSort==='votes'}" @click="leaderboardSort='votes'" title="Sắp theo tổng số vote nhận được">
                  🗳 Tổng số vote
                </button>
                <button class="lb-pill" :class="{active: leaderboardSort==='winrate'}" @click="leaderboardSort='winrate'" title="Sắp theo tỷ lệ chiến thắng">
                  ⚡ Tỷ lệ thắng
                </button>
              </div>

              <button class="lb-fav-toggle-btn" :class="{active: leaderboardFavOnly}" @click="leaderboardFavOnly = !leaderboardFavOnly" title="Xem danh sách bài hát bạn đã bấm tim">
                {{ leaderboardFavOnly ? '❤️ Bài đã thích' : '♡ Bài yêu thích' }} ({{ favoriteSongIds.length }})
              </button>
            </div>
          </div>

          <!-- LEADERBOARD TABLE -->
          <div class="leaderboard-table-wrapper">
            <table class="leaderboard-table">
              <thead>
                <tr>
                  <th class="col-rank">Hạng</th>
                  <th class="col-song">Bài hát & Nghệ sĩ</th>
                  <th class="col-stage">Vòng cao nhất</th>
                  <th class="col-record">Thành tích</th>
                  <th class="col-for">Phiếu nhận</th>
                  <th class="col-against">Đối thủ</th>
                  <th class="col-diff">Hiệu số (±)</th>
                  <th class="col-ratio">Tỷ lệ phiếu</th>
                  <th class="col-actions">Tương tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!computedLeaderboard.length">
                  <td colspan="9" class="lb-empty-text">Không tìm thấy bài hát nào phù hợp.</td>
                </tr>
                <tr v-for="item in computedLeaderboard" :key="item.song.id" class="lb-data-row" :class="{isChampRow: item.tier===1, isFavRow: isFavorite(item.song.id)}">
                  <td class="col-rank">
                    <span class="rank-number-pill" :class="'rank-style-' + item.rank">
                      {{ item.rank === 1 ? '🥇 1' : item.rank === 2 ? '🥈 2' : item.rank === 3 ? '🥉 3' : item.rank }}
                    </span>
                  </td>
                  <td class="col-song">
                    <div class="lb-song-media">
                      <div class="lb-artwork-box">
                        <img :src="getSongArtwork(item.song)" :alt="item.song.title" class="lb-cover-img" @error="handleImgError($event)">
                      </div>
                      <div class="lb-song-details">
                        <b class="lb-song-title">
                          {{ item.song.title }}
                          <span v-if="isWildcardSong(item.song.id)" class="song-wc-badge" title="Đặc cách Wildcard">⭐</span>
                        </b>
                        <small class="lb-song-artist">{{ item.song.artist || 'Chưa nhập nghệ sĩ' }}</small>
                      </div>
                    </div>
                  </td>
                  <td class="col-stage">
                    <span class="stage-name-pill" :class="{goldStage: item.tier===1, silverStage: item.tier===2, bronzeStage: item.tier===3}">
                      {{ item.furthestStage }}
                    </span>
                  </td>
                  <td class="col-record">
                    <div class="record-box">
                      <b>{{ item.wins }}W - {{ item.losses }}L</b>
                      <small>{{ item.winRatePercent }}% thắng</small>
                    </div>
                  </td>
                  <td class="col-for">
                    <span class="votes-for-count">{{ item.votesFor }}</span>
                  </td>
                  <td class="col-against">
                    <span class="votes-against-count">{{ item.votesAgainst }}</span>
                  </td>
                  <td class="col-diff">
                    <span class="vote-diff-chip" :class="{diffPosChip: item.voteDiff > 0, diffNegChip: item.voteDiff < 0, diffZeroChip: item.voteDiff === 0}">
                      {{ item.voteDiff > 0 ? '+' + item.voteDiff : item.voteDiff }}
                    </span>
                  </td>
                  <td class="col-ratio">
                    <div class="ratio-progress-box">
                      <span class="ratio-percent-text">{{ item.voteRatioPercent }}%</span>
                      <div class="ratio-track">
                        <div class="ratio-fill" :style="{width: item.voteRatioPercent + '%'}"></div>
                      </div>
                    </div>
                  </td>
                  <td class="col-actions">
                    <div class="lb-actions-cell">
                      <button class="favorite-heart-btn" :class="{favorited: isFavorite(item.song.id)}" @click="toggleFavorite(item.song.id)" :title="isFavorite(item.song.id) ? 'Bỏ yêu thích' : 'Thêm vào yêu thích'">
                        {{ isFavorite(item.song.id) ? '❤️' : '♡' }}
                      </button>
                      <button v-if="item.song.link" class="btn mini-btn lb-listen-btn" :class="{spotifyBtn: isSpotifyLink(item.song.link)}" @click="playSong(item.song)" :title="isSpotifyLink(item.song.link) ? 'Nghe thử Spotify' : 'Nghe thử YouTube'">
                        {{ isSpotifyLink(item.song.link) ? '🟢' : '▶' }}
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </section>

      <!-- PAGE: RESULTS -->
      <section v-else-if="page==='players'" class="panel">
        <div class="section-head"><div><span class="mini-icon">♟</span><h3>Bảng xếp hạng người chơi</h3></div><button class="btn soft" :disabled="!tournament || playersLoading" @click="loadPlayers">Làm mới</button></div>
        <p v-if="!tournament" class="muted">Chọn một giải đấu ở trên để xem bảng xếp hạng.</p>
        <template v-else>
          <h4>{{ tournament.name }}</h4>
          <p class="muted">Xếp theo tổng lượt vote, sau đó số lượt chọn bài thắng ở các trận đã chốt. Người bằng điểm có cùng hạng. Tên giống nhau vẫn là những người chơi riêng biệt.</p>
          <div class="results-filters"><label>Tìm người chơi<input v-model="playerSearch" placeholder="Nhập tên hiển thị" maxlength="50"></label><span>{{ filteredPlayers.length }} người chơi</span></div>
          <p v-if="playersError" class="form-error" role="alert">{{ playersError }}</p>
          <p v-if="playersLoading && !players.length" class="muted">Đang tải bảng xếp hạng…</p>
          <p v-else-if="!filteredPlayers.length" class="muted">{{ players.length ? 'Không tìm thấy người chơi.' : 'Chưa có lượt vote nào. Hãy là người đầu tiên!' }}</p>
          <div v-else class="data-table-wrapper">
            <table class="player-table"><thead><tr><th>Hạng</th><th>Người chơi</th><th>Tổng lượt vote</th><th>Chọn bài thắng</th><th>Tỷ lệ chọn bài thắng</th></tr></thead>
              <tbody><tr v-for="(p, index) in visiblePlayers" :key="index" :class="{currentPlayer:p.isCurrentPlayer}"><td>#{{ p.rank }}</td><td><b>{{ p.displayName }}</b> <small v-if="p.isCurrentPlayer">(Bạn)</small></td><td>{{ p.totalVotes }}</td><td>{{ p.winningVotes }} / {{ p.decidedVotes }}</td><td>{{ p.decidedVotes ? (p.winningVotes / p.decidedVotes * 100).toFixed(1) + '%' : 'Chưa có kết quả' }}</td></tr></tbody>
            </table>
          </div>
          <div v-if="filteredPlayers.length" class="results-pagination"><span>Trang {{ playerPage }} / {{ playerPages }}</span><button class="btn soft" :disabled="playerPage<=1" @click="playerPage--">‹ Trước</button><button class="btn soft" :disabled="playerPage>=playerPages" @click="playerPage++">Sau ›</button></div>
        </template>
      </section>

      <section v-else-if="page==='results'" class="panel">
        <div class="section-head"><div><span class="mini-icon">🏆</span><h3>Bảng kết quả</h3></div><button class="btn primary" @click="exportResults" :disabled="!tournament">⇩ Xuất TXT</button></div>
        <p v-if="!tournament" class="muted">Chọn giải đấu ở trên để xem kết quả.</p>
        <template v-else>
          <h4>{{ tournament.name }}</h4>
          <div class="results-filters">
            <label>Vòng đấu<select v-model="resultRound"><option value="all">Tất cả các vòng</option><option v-for="r in tournament.rounds" :key="r.index" :value="String(r.index)">{{ r.name }}</option></select></label>
            <label>Trạng thái<select v-model="resultStatus"><option value="all">Tất cả trạng thái</option><option value="completed">Đã có kết quả</option><option value="pending">Chưa có kết quả</option></select></label>
            <label>Số trận / trang<select v-model.number="resultPageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select></label>
            <span>{{ resultMatches.length }} trận</span>
          </div>
          <!-- GRAND CHAMPION CELEBRATION STAGE -->
          <ChampionStage v-if="champion" :key="tournament.id + ':' + champion.id" ref="championStage" :song="champion" :tournament-name="tournament.name" :artwork="getSongArtwork(champion)" @before-play="stopOtherPlayers" @share="copyChampionTitle" />
          <p v-if="!visibleResults.length" class="muted">Không có trận đấu phù hợp bộ lọc.</p>
          <div v-for="m in visibleResults" :key="m.id" class="result-round">
            <div class="result-row-heading"><small>{{ m.roundName }} · Trận {{ m.position + 1 }}</small><button class="match-detail-btn" @click="openVoteDetails(m)">Xem chi tiết</button></div>
            <div class="result-match"><div><b>{{ m.songA?.title || 'Chưa xác định' }}</b><small>{{ m.songA?.artist }}</small></div><span class="score">{{ m.votesA }} : {{ m.votesB }}</span><div><b>{{ m.songB?.title || (m.bye ? 'Vào thẳng' : 'Chưa xác định') }}</b><small>{{ m.songB?.artist }}</small></div><strong class="winner-label">{{ m.winner?.title || 'Chưa quyết định' }}</strong></div>
          </div>
          <div v-if="resultMatches.length" class="results-pagination"><span>{{ (resultPage-1)*resultPageSize+1 }}–{{ Math.min(resultPage*resultPageSize,resultMatches.length) }} / {{ resultMatches.length }} trận · Trang {{ resultPage }} / {{ resultPages }}</span><button class="btn soft" :disabled="resultPage<=1" @click="resultPage--">‹ Trước</button><button class="btn soft" :disabled="resultPage>=resultPages" @click="resultPage++">Sau ›</button></div>
        </template>
      </section>

      <!-- PAGE: SETTINGS -->
      <section v-else class="panel settings-panel">
        <div class="section-head"><div><span class="mini-icon violet">⚙</span><h3>Cài đặt & Hướng dẫn</h3></div></div>
        <p class="muted">Hệ thống bình chọn giải đấu âm nhạc chuyên nghiệp.</p>
        <div class="tip-box"><span>⚡</span><p><b>Nhập số lượng lớn</b><br>Dùng nút "Tải file (.txt)" để nạp 32, 64 hoặc 128 bài hát trong 1 giây. Hỗ trợ dán trực tiếp từ file Excel/Google Sheets.</p></div>
        <div class="tip-box"><span>🎛</span><p><b>Đóng/Mở từng vòng</b><br>Trong sơ đồ vòng đấu, bạn có thể bấm "Đóng vote" hoặc "Mở vote" cho từng vòng độc lập.</p></div>
      </section>
    </main>
  </div>

  <!-- POPUP INLINE MUSIC PLAYER -->
  <div v-if="showNameModal" class="modal-overlay" @click.self="showNameModal=false" @keydown.esc="showNameModal=false">
    <form class="vote-dialog" role="dialog" aria-modal="true" aria-labelledby="name-title" @submit.prevent="saveDisplayName">
      <div class="player-modal-header"><h3 id="name-title">Tên hiển thị của bạn</h3><button type="button" class="modal-close-btn" aria-label="Đóng" @click="showNameModal=false">✕</button></div>
      <div class="vote-dialog-body"><label>Tên hiển thị<input v-model="nameInput" autofocus required maxlength="50" autocomplete="nickname" placeholder="Ví dụ: Duy Anh"></label><p class="muted">Tên này xuất hiện công khai cùng phiếu vote và trên bảng xếp hạng. Mỗi trình duyệt vẫn chỉ vote một lần cho mỗi cặp.</p><p v-if="nameError" class="form-error" role="alert">{{ nameError }}</p><button class="btn primary" type="submit">Lưu tên</button></div>
    </form>
  </div>

  <div v-if="pendingVote" class="modal-overlay" @click.self="!voteSubmitting && (pendingVote=null)" @keydown.esc="!voteSubmitting && (pendingVote=null)">
    <form class="vote-dialog" role="dialog" aria-modal="true" aria-labelledby="vote-title" @submit.prevent="submitVote">
      <div class="player-modal-header"><h3 id="vote-title">Bình chọn bài hát</h3><button type="button" class="modal-close-btn" :disabled="voteSubmitting" aria-label="Đóng" @click="pendingVote=null">✕</button></div>
      <div class="vote-dialog-body"><div class="vote-choice"><b>{{ pendingVote.song.title }}</b><small>{{ pendingVote.song.artist }}</small></div>
        <label>Tên hiển thị <span class="muted">(bắt buộc)</span><input v-model="nameInput" required maxlength="50" autocomplete="nickname" :disabled="voteSubmitting" placeholder="Nhập tên của bạn"></label>
        <label>Vì sao bạn chọn bài này? <span class="muted">(không bắt buộc)</span><textarea v-model="voteReason" rows="4" maxlength="1000" :disabled="voteSubmitting" placeholder="Chia sẻ cảm nhận của bạn, hoặc để trống…"></textarea></label><small class="muted">{{ voteReason.length }}/1000 ký tự · Tên và lý do sẽ được hiển thị công khai trong chi tiết phiếu.</small>
        <p v-if="voteError" class="form-error" role="alert">{{ voteError }}</p><div class="dialog-actions"><button type="button" class="btn soft" :disabled="voteSubmitting" @click="pendingVote=null">Hủy</button><button class="btn primary" type="submit" :disabled="voteSubmitting">{{ voteSubmitting ? 'Đang gửi…' : 'Xác nhận vote' }}</button></div>
      </div>
    </form>
  </div>

  <div v-if="detailMatch" class="modal-overlay" @click.self="detailMatch=null" @keydown.esc="detailMatch=null">
    <section class="vote-dialog vote-details-dialog" role="dialog" aria-modal="true" aria-labelledby="details-title">
      <div class="player-modal-header"><h3 id="details-title">Chi tiết bình chọn · Trận {{ detailMatch.position+1 }}</h3><button class="modal-close-btn" aria-label="Đóng" @click="detailMatch=null">✕</button></div>
      <div class="vote-dialog-body"><p><b>{{ detailMatch.songA?.title || 'Chưa xác định' }}</b> vs <b>{{ detailMatch.songB?.title || 'Chưa xác định' }}</b></p><p class="muted">{{ voteDetails.totalElements }} phiếu · Mới nhất trước</p>
        <p v-if="detailsError" class="form-error" role="alert">{{ detailsError }}</p><p v-if="detailsLoading" class="muted">Đang tải phiếu…</p>
        <template v-else-if="!detailsError"><p v-if="!voteDetails.items.length" class="muted">Chưa có ai bình chọn cho cặp này.</p><article v-for="v in voteDetails.items" :key="v.id" class="vote-detail-item"><div><b>{{ v.displayName }}</b><time :datetime="v.createdAt">{{ formatVoteTime(v.createdAt) }}</time></div><p>Đã chọn: <b>{{ v.song.title }}</b> <span class="muted">— {{ v.song.artist }}</span></p><p class="vote-reason">{{ v.reason || 'Không ghi lý do.' }}</p></article></template>
        <div class="results-pagination"><button class="btn soft" :disabled="detailsLoading" @click="openVoteDetails(detailMatch, voteDetails.page)">Làm mới</button><span>Trang {{ voteDetails.page+1 }} / {{ Math.max(1,voteDetails.totalPages) }}</span><button class="btn soft" :disabled="detailsLoading || voteDetails.page<=0" @click="openVoteDetails(detailMatch, voteDetails.page-1)">‹ Trước</button><button class="btn soft" :disabled="detailsLoading || voteDetails.page+1>=voteDetails.totalPages" @click="openVoteDetails(detailMatch, voteDetails.page+1)">Sau ›</button></div>
      </div>
    </section>
  </div>

  <div v-if="activePlayerSong" class="modal-overlay" @click.self="closePlayer">
    <div class="player-modal" :class="{spotifyModal: isSpotifyLink(activePlayerSong.link)}">
      <div class="player-modal-header">
        <div class="player-modal-info">
          <span class="player-symbol" :class="{spotifySymbol: isSpotifyLink(activePlayerSong.link)}">
            {{ isSpotifyLink(activePlayerSong.link) ? '🟢' : '♫' }}
          </span>
          <div>
            <h4>{{ activePlayerSong.title }}</h4>
            <p>{{ activePlayerSong.artist || 'Chưa có thông tin nghệ sĩ' }}</p>
          </div>
        </div>
        <div class="player-modal-right">
          <a v-if="activePlayerSong.link" :href="activePlayerSong.link" target="_blank" rel="noopener noreferrer" class="btn open-external-link-btn" title="Mở trực tiếp trên Spotify / YouTube">
            {{ isSpotifyLink(activePlayerSong.link) ? 'Mở Spotify ↗' : 'Mở tab mới ↗' }}
          </a>
          <button class="modal-close-btn" @click="closePlayer">✕</button>
        </div>
      </div>
      <div class="player-modal-body" :class="{spotifyBody: isSpotifyLink(activePlayerSong.link)}">
        <!-- SPOTIFY EMBED -->
        <iframe v-if="getSpotifyEmbedUrl(activePlayerSong.link)"
          :src="getSpotifyEmbedUrl(activePlayerSong.link)"
          class="spotify-iframe"
          frameborder="0"
          allowfullscreen=""
          allow="autoplay; clipboard-write; encrypted-media; fullscreen; picture-in-picture"
          loading="lazy">
        </iframe>
        <!-- YOUTUBE EMBED -->
        <iframe v-else-if="getYoutubeEmbedUrl(activePlayerSong.link)"
          :src="getYoutubeEmbedUrl(activePlayerSong.link)"
          class="youtube-iframe"
          frameborder="0"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
          allowfullscreen>
        </iframe>
        <!-- FALLBACK -->
        <div v-else class="fallback-player">
          <p>Bài hát này sử dụng liên kết ngoài:</p>
          <a :href="activePlayerSong.link" target="_blank" class="btn primary">Mở bài hát trong tab mới ↗</a>
        </div>
      </div>
    </div>
  </div>

  <!-- POPUP QR CODE SHARE -->
  <div v-if="showQrModal && tournament" class="modal-overlay" @click.self="showQrModal=false">
    <div class="qr-modal">
      <div class="player-modal-header">
        <h4>Quét mã QR để bình chọn</h4>
        <button class="modal-close-btn" @click="showQrModal=false">✕</button>
      </div>
      <div class="qr-modal-body">
        <p class="muted small">Dùng camera điện thoại quét mã QR bên dưới để mở giải đấu ngay:</p>
        <div class="qr-frame">
          <img :src="'https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=' + encodeURIComponent(shareUrl)" alt="QR Code" class="qr-image">
        </div>
        <p class="qr-tournament-name"><b>{{ tournament.name }}</b></p>
        <div class="share-copy-row">
          <input :value="shareUrl" readonly class="share-url-box">
          <button class="btn primary" @click="copyShareLink">Sao chép</button>
        </div>
      </div>
    </div>
  </div>

  <!-- POPUP ADMIN LOGIN MODAL -->
  <div v-if="showLoginModal" class="modal-overlay" @click.self="showLoginModal=false">
    <div class="admin-login-modal">
      <div class="player-modal-header">
        <div class="login-modal-title">
          <span class="admin-crown-icon">👑</span>
          <div>
            <h4>Đăng nhập Quản trị viên (Admin)</h4>
            <p class="muted small">Truy cập toàn quyền tạo giải đấu, mở/đóng từng vòng và chốt kết quả.</p>
          </div>
        </div>
        <button class="modal-close-btn" @click="showLoginModal=false">✕</button>
      </div>
      <div class="admin-login-body">
        <div v-if="loginError" class="alert error login-error">
          <span>⚠</span>{{ loginError }}
        </div>

        <!-- Form fields: Email & Password -->
        <div class="login-form-group">
          <label>Tài khoản / Email:</label>
          <input 
            v-model="adminEmailInput" 
            type="text" 
            placeholder="duyanh@gmail.com hoặc hoangkhanh@gmail.com" 
            class="admin-password-box"
            @keyup.enter="loginAdmin()"
          >
        </div>

        <div class="login-form-group">
          <label>Mật khẩu:</label>
          <input 
            v-model="adminPasswordInput" 
            type="password" 
            placeholder="Nhập mật khẩu (123456)" 
            class="admin-password-box"
            @keyup.enter="loginAdmin()"
          >
        </div>

        <div class="login-modal-buttons">
          <button class="btn primary login-submit-btn" @click="loginAdmin()">
            ✓ Đăng nhập Quản trị
          </button>

          <div class="quick-admin-accounts-list">
            <span class="quick-admin-title">⚡ Đăng nhập nhanh 1 chạm:</span>
            <div class="quick-admin-btns-row">
              <button class="btn soft quick-login-btn-acc" @click="quickLoginAdminAccount('duyanh@gmail.com', '123456')">
                👤 Duy Anh (duyanh@gmail.com - mk: 123456)
              </button>
              <button class="btn soft quick-login-btn-acc" @click="quickLoginAdminAccount('hoangkhanh@gmail.com', '123456')">
                👤 Hoàng Khánh (hoangkhanh@gmail.com - mk: 123456)
              </button>
            </div>
          </div>

          <button class="btn cancel-btn" @click="showLoginModal=false">
            Hủy bỏ
          </button>
        </div>

        <div class="login-role-tip">
          <b>🔑 2 Tài khoản Quản trị viên (Admin):</b>
          <p>• <b>Admin 1</b>: <code>duyanh@gmail.com</code> | Mật khẩu: <code>123456</code> (Đoàn Duy Anh)</p>
          <p>• <b>Admin 2</b>: <code>hoangkhanh@gmail.com</code> | Mật khẩu: <code>123456</code> (Hoàng Khánh)</p>
          <small class="muted" style="display:block;margin-top:6px;">Quản trị viên có toàn quyền: Tạo giải mới, nạp bài hát, xáo trộn & xếp nhánh, mở/đóng bình chọn từng vòng, chốt kết quả và đóng/mở toàn bộ giải đấu.</small>
        </div>
      </div>
    </div>
  </div>

  <footer class="footer">♫ Made for music fans <span>·</span> Music Tournament Pro 2026</footer>
</template>
