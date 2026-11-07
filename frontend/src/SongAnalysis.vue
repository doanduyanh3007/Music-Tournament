<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  isAdmin: { type: Boolean, default: false },
  selectedSongId: { type: [Number, String], default: null }
})

const emit = defineEmits(['request-login', 'close'])

// --- Navigation & Core State ---
const currentTab = ref('structure') // 'structure' | 'lyrics' | 'arrangement' | 'background' | 'interpretation' | 'sources' | 'notes' | 'chat'
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')

// Song selection & list
const songList = ref([])
const songSearchQuery = ref('')
const activeSongId = ref(props.selectedSongId || null)
const analysis = ref(null)
const activeVersionId = ref(null)

// --- Audio & Waveform State ---
const audioElement = ref(null)
const audioFileInput = ref(null)
const isUploadingAudio = ref(false)
const currentAudioTimeMs = ref(0)
const isAudioPlaying = ref(false)
const audioDurationMs = ref(180000) // Default 3 mins if not detected
const showEmbedPlayer = ref(false)
const waveformHoverTime = ref(null)

const playableAudioUrl = computed(() => {
  if (analysis.value?.currentVersion?.audioFilePath) {
    return analysis.value.currentVersion.audioFilePath
  }
  return analysis.value?.previewUrl || ''
})

const embedSpotifyUrl = computed(() => {
  const url = analysis.value?.spotifyUrl
  if (!url) return null
  const m = url.match(/track\/([a-zA-Z0-9]+)/)
  if (m && m[1]) return `https://open.spotify.com/embed/track/${m[1]}?utm_source=generator&theme=0`
  return null
})

const embedYoutubeUrl = computed(() => {
  const url = analysis.value?.youtubeUrl
  if (!url) return null
  const m = url.match(/(?:watch\?v=|youtu\.be\/|embed\/)([a-zA-Z0-9_-]{11})/)
  if (m && m[1]) return `https://www.youtube-nocookie.com/embed/${m[1]}?enablejsapi=1`
  return null
})

// Generate 64 energy waveform bars based on audio duration and section intensity
const waveformBars = computed(() => {
  const total = 64
  const bars = []
  const dur = (analysis.value?.currentVersion?.durationMs && analysis.value.currentVersion.durationMs > 10000)
    ? analysis.value.currentVersion.durationMs
    : (audioDurationMs.value || 180000)

  const sections = analysis.value?.sections || []

  for (let i = 0; i < total; i++) {
    const timeMs = Math.round((i / total) * dur)
    // Find if inside a section to scale height by emotionalIntensity
    const sec = sections.find(s => timeMs >= s.startTimeMs && timeMs <= s.endTimeMs)
    const baseEnergy = sec ? (sec.emotionalIntensity || 5) * 8 : 45
    // Add sinusoidal wave pattern for natural sound rhythm
    const waveFluctuation = Math.sin(i * 0.45) * 18 + Math.cos(i * 0.9) * 12
    const heightPercent = Math.min(100, Math.max(18, Math.round(baseEnergy + waveFluctuation)))

    bars.push({
      index: i,
      timeMs,
      heightPercent,
      active: timeMs <= currentAudioTimeMs.value
    })
  }
  return bars
})

// --- Interactive Lyrics State ---
const lyricSearchQuery = ref('')
const selectedLyricIndex = ref(null)
const lyricsViewMode = ref('interactive') // 'interactive' | 'edit'

const filteredLyrics = computed(() => {
  const list = analysis.value?.lyrics || []
  const q = lyricSearchQuery.value.trim().toLowerCase()
  if (!q) return list
  return list.filter(l =>
    (l.japaneseText && l.japaneseText.toLowerCase().includes(q)) ||
    (l.romajiReading && l.romajiReading.toLowerCase().includes(q)) ||
    (l.poeticTranslation && l.poeticTranslation.toLowerCase().includes(q)) ||
    (l.literalTranslation && l.literalTranslation.toLowerCase().includes(q))
  )
})

// --- Musical Arrangement Structure ---
const arrangementViewMode = ref('cards') // 'cards' | 'raw'
const rawArrangementText = ref('')

const instrumentCards = computed(() => {
  const raw = analysis.value?.musicalArrangement || rawArrangementText.value || ''
  const instruments = [
    {
      key: 'guitar',
      icon: '🎸',
      name: 'Guitar (Lead & Rhythm)',
      role: 'Định hình giai điệu và tạo lớp nền hòa âm năng lượng cao',
      badge: 'Distortion / Arpeggio / Melancholic',
      detail: 'Rhythm guitar sử dụng power chords dồn dập, lead guitar đan cài những câu fill du dương mang âm hưởng melancholic đặc trưng.',
      timestampHint: 'Bắt đầu từ Intro, nâng tông ở Pre-Chorus và bùng nổ ở Chorus',
      targetMs: 0
    },
    {
      key: 'bass',
      icon: '🎸',
      name: 'Bassline',
      role: 'Cột trụ giữ nhịp và liên kết nhịp trống với hòa thanh guitar',
      badge: 'Driving Bass / Chromatic Runs',
      detail: 'Đường bass di chuyển linh hoạt, vừa giữ chắc nhịp đập vừa hỗ trợ nâng tầm cao trào ở Tiền điệp khúc (Pre-Chorus).',
      timestampHint: 'Dồn nhịp mạnh ở Pre-Chorus đẩy cao trào vào Chorus',
      targetMs: 45000
    },
    {
      key: 'drums',
      icon: '🥁',
      name: 'Drums & Tiết tấu',
      role: 'Dẫn dắt tiết tấu, điều phối nhịp độ và nhịp thở của bài hát',
      badge: '4/4 Tempo / Snare Fills / Kick Punch',
      detail: 'Nhịp hi-hat dứt khoát chuyển đổi mượt mà giữa các đoạn tĩnh lặng và các cú đập snare uy lực ở điệp khúc.',
      timestampHint: 'Kiểm soát nhịp độ qua từng phân đoạn, dồn trống trước Chorus',
      targetMs: 15000
    },
    {
      key: 'keyboard',
      icon: '🎹',
      name: 'Keyboard / Synthesizer',
      role: 'Tạo không gian ambient và điểm xuyết giai điệu tinh tế',
      badge: 'Ambient Pad / Piano Accents',
      detail: 'Lớp synthesizer pad nhẹ nhàng trải nền hoặc tiếng piano trong trẻo điểm xuyết tạo chiều sâu cảm xúc.',
      timestampHint: 'Dạo đầu Intro và lắng đọng ở Bridge',
      targetMs: 90000
    },
    {
      key: 'vocal',
      icon: '🎤',
      name: 'Vocal Dynamics',
      role: 'Tâm điểm truyền tải thông điệp và biểu cảm trực tiếp',
      badge: 'Chest Voice / Belting / Emotional Delivery',
      detail: 'Giọng hát tự sự mộc mạc ở nốt thấp, chuyển sang giọng vang ngân nội lực và gằn giọng đầy xúc động ở cao trào.',
      timestampHint: 'Xuyên suốt bài hát, chạm đỉnh cảm xúc ở Final Chorus',
      targetMs: 120000
    }
  ]

  if (raw) {
    const lines = raw.split('\n')
    instruments.forEach(inst => {
      const matchStart = lines.findIndex(l => l.toLowerCase().includes(inst.key) || l.toLowerCase().includes(inst.name.toLowerCase().split(' ')[0]))
      if (matchStart !== -1) {
        const extracted = lines.slice(matchStart, matchStart + 6).join('\n')
        if (extracted.trim().length > 20) {
          inst.detail = extracted
        }
      }
    })
  }

  return instruments
})

// --- Song Chat State ---
const chatMessages = ref([
  {
    role: 'assistant',
    content: 'Xin chào! Tôi là Trợ lý Phân tích Âm nhạc (Music Intelligence Assistant). Tôi đã nạp toàn bộ dữ liệu phân tích, cấu trúc mốc thời gian, lời bài hát tiếng Nhật và hòa âm của ca khúc này. Bạn muốn đào sâu vào chi tiết nào?',
    category: 'FACT',
    suggestedQuestions: [
      'Phân tích ý nghĩa tên bài hát và cách đọc Ateji',
      'Tiếng Bass và Guitar phối hợp như thế nào ở Điệp khúc?',
      'Giải thích các tầng ẩn dụ trong lời bài hát'
    ]
  }
])
const chatInput = ref('')
const isChatLoading = ref(false)
const chatContextModule = ref('GENERAL')
const chatContextDetail = ref('')

async function sendChatMessage(customText = null) {
  const query = (customText || chatInput.value).trim()
  if (!query || isChatLoading.value || !analysis.value?.id) return

  chatMessages.value.push({ role: 'user', content: query })
  chatInput.value = ''
  isChatLoading.value = true

  try {
    const historyPayload = chatMessages.value.slice(-6).map(m => ({
      role: m.role,
      content: m.content
    }))

    const res = await requestApi(`/${analysis.value.id}/chat`, {
      method: 'POST',
      body: JSON.stringify({
        message: query,
        history: historyPayload,
        contextModule: chatContextModule.value,
        contextDetail: chatContextDetail.value
      })
    })

    chatMessages.value.push({
      role: 'assistant',
      content: res.reply || 'Không có phản hồi từ hệ thống phân tích.',
      category: res.category || 'ANALYSIS',
      suggestedQuestions: res.suggestedQuestions || []
    })
  } catch (err) {
    chatMessages.value.push({
      role: 'assistant',
      content: 'Lỗi khi trao đổi với AI: ' + err.message,
      category: 'FACT',
      suggestedQuestions: []
    })
  } finally {
    isChatLoading.value = false
  }
}

function promptChatWithContext(moduleName, detailText, initialQuestion = null) {
  chatContextModule.value = moduleName
  chatContextDetail.value = detailText
  currentTab.value = 'chat'
  if (initialQuestion) {
    sendChatMessage(initialQuestion)
  }
}

function clearChatContext() {
  chatContextModule.value = 'GENERAL'
  chatContextDetail.value = ''
}

// --- Audio Player Controls & Seeking ---
function seekAudio(ms) {
  if (audioElement.value && !isNaN(ms)) {
    audioElement.value.currentTime = ms / 1000
    audioElement.value.play().catch(() => {})
  }
  currentAudioTimeMs.value = ms
}

function onWaveformClick(bar) {
  seekAudio(bar.timeMs)
}

function onAudioTimeUpdate() {
  if (audioElement.value) {
    currentAudioTimeMs.value = Math.round(audioElement.value.currentTime * 1000)
    if (audioElement.value.duration && !isNaN(audioElement.value.duration)) {
      audioDurationMs.value = Math.round(audioElement.value.duration * 1000)
    }
  }
}

function toggleAudioPlay() {
  if (!audioElement.value) return
  if (isAudioPlaying.value) {
    audioElement.value.pause()
  } else {
    audioElement.value.play().catch(() => {})
  }
}

// --- Formatters & Helpers ---
function msToTimeString(ms) {
  if (ms == null || isNaN(ms)) return '00:00.00'
  const totalSeconds = Math.max(0, ms) / 1000
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = Math.floor(totalSeconds % 60)
  const milliseconds = Math.floor((totalSeconds % 1) * 100)
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(milliseconds).padStart(2, '0')}`
}

function timeStringToMs(str) {
  if (!str) return 0
  const parts = str.trim().split(':')
  if (parts.length === 2) {
    const min = parseInt(parts[0], 10) || 0
    const secParts = parts[1].split('.')
    const sec = parseInt(secParts[0], 10) || 0
    const ms = secParts.length > 1 ? parseInt(secParts[1].padEnd(3, '0').slice(0, 3), 10) || 0 : 0
    return (min * 60 + sec) * 1000 + ms
  }
  const numeric = parseFloat(str)
  return isNaN(numeric) ? 0 : Math.round(numeric * 1000)
}

function getSectionColorClass(type) {
  switch (type) {
    case 'INTRO': return 'seg-intro'
    case 'VERSE': return 'seg-verse'
    case 'PRE_CHORUS': return 'seg-pre-chorus'
    case 'CHORUS': return 'seg-chorus'
    case 'BRIDGE': return 'seg-bridge'
    case 'FINAL_CHORUS': return 'seg-final-chorus'
    case 'SOLO': return 'seg-solo'
    case 'OUTRO': return 'seg-outro'
    default: return 'seg-default'
  }
}

function showFlash(msg, isErr = false) {
  if (isErr) error.value = msg
  else notice.value = msg
  setTimeout(() => {
    if (notice.value === msg) notice.value = ''
    if (error.value === msg) error.value = ''
  }, 4000)
}

function getAdminHeaders() {
  const token = localStorage.getItem('musicTournamentAdminToken') || 'admin-demo-2026'
  const email = localStorage.getItem('musicTournamentAdminEmail') || ''
  return {
    'Content-Type': 'application/json',
    'X-Admin-Token': token,
    'X-Admin-Email': email
  }
}

async function requestApi(path, options = {}) {
  const headers = { ...getAdminHeaders(), ...(options.headers || {}) }
  const res = await fetch(`/api/admin/analysis${path}`, { ...options, headers })
  if (res.status === 401 || res.status === 403) {
    throw new Error('Yêu cầu quyền Quản trị viên (ADMIN) để thực hiện thao tác này.')
  }
  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    throw new Error(data.message || `Lỗi HTTP ${res.status}`)
  }
  return data
}

// --- Data Fetching ---
async function loadSongsLibrary() {
  try {
    const res = await fetch('/api/quiz/songs?size=100&sortBy=title&sortDir=asc')
    if (res.ok) {
      const data = await res.json()
      songList.value = data.content || []
      if (!activeSongId.value && songList.value.length > 0) {
        activeSongId.value = songList.value[0].id
        loadAnalysis(activeSongId.value)
      }
    }
  } catch (err) {
    console.error('Không thể tải danh sách bài hát:', err)
  }
}

const filteredSongs = computed(() => {
  const q = songSearchQuery.value.trim().toLowerCase()
  if (!q) return songList.value
  return songList.value.filter(s =>
    (s.title && s.title.toLowerCase().includes(q)) ||
    (s.artist && s.artist.toLowerCase().includes(q)) ||
    (s.romajiTitle && s.romajiTitle.toLowerCase().includes(q))
  )
})

async function loadAnalysis(songId) {
  if (!songId || !props.isAdmin) return
  loading.value = true
  error.value = ''
  try {
    const data = await requestApi(`/song/${songId}`)
    analysis.value = data
    activeVersionId.value = data.currentVersionId || (data.versions?.[0]?.id || null)
    rawArrangementText.value = data.musicalArrangement || ''
    if (data.currentVersion?.durationMs) {
      audioDurationMs.value = data.currentVersion.durationMs
    }
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function switchVersion(versionId) {
  if (!versionId || versionId === activeVersionId.value) return
  loading.value = true
  try {
    const data = await requestApi(`/${analysis.value.id}?versionId=${versionId}`)
    analysis.value = data
    activeVersionId.value = versionId
    rawArrangementText.value = data.musicalArrangement || ''
  } catch (err) {
    showFlash('Không thể chuyển đổi phiên bản: ' + err.message, true)
  } finally {
    loading.value = false
  }
}

// --- Save & Update Methods ---
async function saveCoreAnalysis() {
  if (!analysis.value?.id) return
  saving.value = true
  try {
    if (arrangementViewMode.value === 'raw') {
      analysis.value.musicalArrangement = rawArrangementText.value
    }
    const req = {
      status: analysis.value.status,
      songBackground: analysis.value.songBackground,
      musicalArrangement: analysis.value.musicalArrangement,
      overallInterpretation: analysis.value.overallInterpretation
    }
    const updated = await requestApi(`/${analysis.value.id}`, {
      method: 'PUT',
      body: JSON.stringify(req)
    })
    analysis.value.songBackground = updated.songBackground
    analysis.value.musicalArrangement = updated.musicalArrangement
    analysis.value.overallInterpretation = updated.overallInterpretation
    analysis.value.status = updated.status
    showFlash('Đã lưu thông tin phân tích thành công!')
  } catch (err) {
    showFlash('Lỗi lưu phân tích: ' + err.message, true)
  } finally {
    saving.value = false
  }
}

async function saveSections() {
  if (!activeVersionId.value || !analysis.value?.sections) return
  saving.value = true
  try {
    const payload = analysis.value.sections.map((sec, idx) => ({
      ...sec,
      sortOrder: idx + 1
    }))
    const saved = await requestApi(`/versions/${activeVersionId.value}/sections`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    })
    analysis.value.sections = saved
    showFlash('Đã lưu cấu trúc và mốc thời gian bài hát!')
  } catch (err) {
    showFlash('Lỗi khi lưu cấu trúc: ' + err.message, true)
  } finally {
    saving.value = false
  }
}

async function saveLyrics() {
  if (!activeVersionId.value || !analysis.value?.lyrics) return
  saving.value = true
  try {
    const payload = analysis.value.lyrics.map((l, idx) => ({
      ...l,
      sortOrder: idx + 1
    }))
    const saved = await requestApi(`/versions/${activeVersionId.value}/lyrics`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    })
    analysis.value.lyrics = saved
    showFlash('Đã lưu danh sách lời bài hát!')
  } catch (err) {
    showFlash('Lỗi khi lưu lời: ' + err.message, true)
  } finally {
    saving.value = false
  }
}

// Section & Lyric mutations
function addSection() {
  if (!analysis.value) return
  if (!analysis.value.sections) analysis.value.sections = []
  const lastSec = analysis.value.sections[analysis.value.sections.length - 1]
  const nextStart = lastSec ? lastSec.endTimeMs : 0
  analysis.value.sections.push({
    sectionType: 'VERSE',
    sectionLabel: `Phân đoạn ${analysis.value.sections.length + 1}`,
    startTimeMs: nextStart,
    endTimeMs: nextStart + 30000,
    emotionalIntensity: 5,
    explanation: '',
    sortOrder: analysis.value.sections.length + 1
  })
}

function removeSection(index) {
  if (!analysis.value?.sections) return
  analysis.value.sections.splice(index, 1)
}

function addLyricLine() {
  if (!analysis.value) return
  if (!analysis.value.lyrics) analysis.value.lyrics = []
  const lastLine = analysis.value.lyrics[analysis.value.lyrics.length - 1]
  const nextStart = lastLine ? lastLine.endTimeMs : 0
  analysis.value.lyrics.push({
    japaneseText: '',
    furiganaReading: '',
    romajiReading: '',
    literalTranslation: '',
    poeticTranslation: '',
    metaphorExplanation: '',
    startTimeMs: nextStart,
    endTimeMs: nextStart + 4000,
    sortOrder: analysis.value.lyrics.length + 1
  })
}

function removeLyricLine(index) {
  if (!analysis.value?.lyrics) return
  analysis.value.lyrics.splice(index, 1)
}

function captureTimeToSection(section, field) {
  if (field === 'start') {
    section.startTimeMs = currentAudioTimeMs.value
    if (section.endTimeMs <= section.startTimeMs) {
      section.endTimeMs = section.startTimeMs + 15000
    }
  } else if (field === 'end') {
    section.endTimeMs = currentAudioTimeMs.value
  }
  showFlash(`Đã gán mốc thời gian ${msToTimeString(currentAudioTimeMs.value)} vào ${field.toUpperCase()}`)
}

function captureTimeToLyric(lyric, field) {
  if (field === 'start') lyric.startTimeMs = currentAudioTimeMs.value
  else if (field === 'end') lyric.endTimeMs = currentAudioTimeMs.value
  showFlash(`Đã gán mốc ${msToTimeString(currentAudioTimeMs.value)} vào câu hát`)
}

// Librosa Audio Upload
function triggerAudioUpload() {
  audioFileInput.value?.click()
}

async function onAudioFileSelected(event) {
  const file = event.target.files?.[0]
  if (!file || !activeVersionId.value) return
  isUploadingAudio.value = true
  showFlash('Đang tải lên và phân tích đặc trưng âm thanh bằng Librosa...')
  try {
    const formData = new FormData()
    formData.append('file', file)
    const token = localStorage.getItem('musicTournamentAdminToken') || 'admin-demo-2026'
    const email = localStorage.getItem('musicTournamentAdminEmail') || ''

    const res = await fetch(`/api/admin/analysis/versions/${activeVersionId.value}/upload-audio`, {
      method: 'POST',
      headers: {
        'X-Admin-Token': token,
        'X-Admin-Email': email
      },
      body: formData
    })
    if (!res.ok) {
      const errData = await res.json().catch(() => ({}))
      throw new Error(errData.message || 'Lỗi khi phân tích file âm thanh')
    }
    const feature = await res.json()
    analysis.value.audioFeature = feature
    const updated = await requestApi(`/${analysis.value.id}?versionId=${activeVersionId.value}`)
    analysis.value = updated
    showFlash(`Đã phân tích âm thanh thành công! BPM: ${feature.bpm}, Giọng: ${feature.detectedKey}`)
  } catch (err) {
    showFlash(err.message, true)
  } finally {
    isUploadingAudio.value = false
    if (event.target) event.target.value = ''
  }
}

function applyBoundaryPointsToSections() {
  if (!analysis.value?.audioFeature?.boundaryPointsJson) return
  try {
    const points = JSON.parse(analysis.value.audioFeature.boundaryPointsJson)
    if (!Array.isArray(points) || points.length < 2) {
      showFlash('Không có đủ mốc boundary để tạo phân đoạn.', true)
      return
    }
    const newSections = []
    for (let i = 0; i < points.length; i++) {
      const startMs = points[i]
      const endMs = (i + 1 < points.length) ? points[i + 1] : (analysis.value.currentVersion?.durationMs || startMs + 30000)
      newSections.push({
        sectionType: (i === 0) ? 'INTRO' : (i === points.length - 1 ? 'OUTRO' : 'VERSE'),
        sectionLabel: (i === 0) ? 'Mở đầu (Intro)' : (i === points.length - 1 ? 'Kết thúc (Outro)' : `Phân đoạn ${i}`),
        startTimeMs: startMs,
        endTimeMs: endMs,
        explanation: 'Mốc thay đổi âm sắc / tiết tấu tiềm năng phát hiện bởi Librosa',
        emotionalIntensity: 5,
        sortOrder: i + 1
      })
    }
    analysis.value.sections = newSections
    currentTab.value = 'structure'
    showFlash('Đã áp dụng các mốc boundary points từ Librosa vào Cấu trúc bài hát!')
  } catch (e) {
    showFlash('Lỗi khi đọc danh sách boundary points: ' + e.message, true)
  }
}

// AI trigger
const isAiRunning = ref(false)
async function triggerAiResearch(moduleName = 'ALL') {
  if (!analysis.value?.id) return
  isAiRunning.value = true
  showFlash(`Đang gửi yêu cầu phân tích AI cho module: ${moduleName}...`)
  try {
    await requestApi(`/${analysis.value.id}/generate?module=${moduleName}`, { method: 'POST' })
    showFlash('AI đang tiến hành phân tích ngầm. Đang thăm dò cập nhật...')
    let attempts = 0
    const pollInterval = setInterval(async () => {
      attempts++
      try {
        const fresh = await requestApi(`/${analysis.value.id}?versionId=${activeVersionId.value}`)
        if (fresh.status === 'COMPLETED' || fresh.status === 'FAILED' || attempts > 12) {
          clearInterval(pollInterval)
          analysis.value = fresh
          rawArrangementText.value = fresh.musicalArrangement || ''
          isAiRunning.value = false
          if (fresh.status === 'COMPLETED') {
            showFlash('Bản phân tích AI đã hoàn tất thành công!')
          } else if (fresh.status === 'FAILED') {
            showFlash('Quá trình phân tích AI gặp sự cố.', true)
          }
        }
      } catch (err) {
        clearInterval(pollInterval)
        isAiRunning.value = false
      }
    }, 2500)
  } catch (err) {
    showFlash('Lỗi kích hoạt AI: ' + err.message, true)
    isAiRunning.value = false
  }
}

// Sources & Notes
const newSource = ref({
  sourceType: 'VERIFIED_OFFICIAL',
  title: '',
  url: '',
  author: '',
  citedClaim: ''
})
async function addSource() {
  if (!newSource.value.title) {
    showFlash('Vui lòng nhập tiêu đề nguồn tham khảo', true)
    return
  }
  try {
    const saved = await requestApi(`/${analysis.value.id}/sources`, {
      method: 'POST',
      body: JSON.stringify(newSource.value)
    })
    if (!analysis.value.sources) analysis.value.sources = []
    analysis.value.sources.unshift(saved)
    newSource.value = { sourceType: 'VERIFIED_OFFICIAL', title: '', url: '', author: '', citedClaim: '' }
    showFlash('Đã thêm nguồn tham khảo đối chứng!')
  } catch (err) {
    showFlash('Lỗi thêm nguồn: ' + err.message, true)
  }
}

async function deleteSource(sourceId) {
  try {
    await requestApi(`/sources/${sourceId}`, { method: 'DELETE' })
    analysis.value.sources = analysis.value.sources.filter(s => s.id !== sourceId)
    showFlash('Đã xóa nguồn tham khảo.')
  } catch (err) {
    showFlash('Lỗi xóa nguồn: ' + err.message, true)
  }
}

const newNote = ref({ content: '' })
async function addNote() {
  if (!newNote.value.content.trim()) return
  try {
    const saved = await requestApi(`/${analysis.value.id}/notes`, {
      method: 'POST',
      body: JSON.stringify(newNote.value)
    })
    if (!analysis.value.notes) analysis.value.notes = []
    analysis.value.notes.unshift(saved)
    newNote.value.content = ''
    showFlash('Đã lưu cảm nhận cá nhân!')
  } catch (err) {
    showFlash('Lỗi lưu ghi chú: ' + err.message, true)
  }
}

async function deleteNote(noteId) {
  try {
    await requestApi(`/notes/${noteId}`, { method: 'DELETE' })
    analysis.value.notes = analysis.value.notes.filter(n => n.id !== noteId)
    showFlash('Đã xóa ghi chú.')
  } catch (err) {
    showFlash('Lỗi xóa ghi chú: ' + err.message, true)
  }
}

// Watchers & Lifecycle
watch(() => props.selectedSongId, (newId) => {
  if (newId) {
    activeSongId.value = newId
    loadAnalysis(newId)
  }
})

watch(activeSongId, (newId) => {
  if (newId) {
    loadAnalysis(newId)
  }
})

onMounted(() => {
  if (props.isAdmin) {
    loadSongsLibrary()
  }
})
</script>

<template>
  <div class="song-analysis-container">
    <!-- 403 Forbidden Guard for Non-Admin -->
    <div v-if="!isAdmin" class="forbidden-card">
      <div class="forbidden-icon">🔒</div>
      <h2>403 Forbidden - Quyền truy cập bị từ chối</h2>
      <p>Chức năng <b>Song Analysis Studio</b> chỉ dành riêng cho tài khoản Quản trị viên (ADMIN).</p>
      <button class="btn primary" @click="emit('request-login')">Đăng nhập tài khoản Admin</button>
    </div>

    <!-- Admin Workspace Content -->
    <div v-else class="admin-workspace">
      <!-- Flash Alert Banners -->
      <div v-if="notice" class="flash-banner success">
        <span>✓ {{ notice }}</span>
        <button class="close-flash" @click="notice = ''">✕</button>
      </div>
      <div v-if="error" class="flash-banner danger">
        <span>⚠ {{ error }}</span>
        <button class="close-flash" @click="error = ''">✕</button>
      </div>

      <!-- Top Dashboard Header -->
      <header class="workspace-header">
        <div class="header-left">
          <span class="studio-badge">✦ SONG INTELLIGENCE DASHBOARD</span>
          <div class="song-selector-wrapper">
            <input
              type="text"
              v-model="songSearchQuery"
              placeholder="🔍 Tìm bài hát..."
              class="song-filter-input"
            />
            <select v-model="activeSongId" class="song-select">
              <option :value="null" disabled>-- Chọn bài hát trong thư viện --</option>
              <option v-for="s in filteredSongs" :key="s.id" :value="s.id">
                {{ s.title }} ({{ s.artist }})
              </option>
            </select>
          </div>
        </div>

        <div class="header-right">
          <div class="status-selector" v-if="analysis">
            <span>Trạng thái:</span>
            <span class="status-tag" :class="`status-${analysis.status?.toLowerCase()}`">
              {{ analysis.status }}
            </span>
          </div>

          <button
            class="btn ai-btn"
            :disabled="isAiRunning || !analysis"
            @click="triggerAiResearch('ALL')"
            title="Nghiên cứu bối cảnh, phân tích hòa âm và dịch lời Nhật bằng AI"
          >
            ✦ {{ isAiRunning ? 'Đang phân tích...' : 'AI Re-Analyze' }}
          </button>

          <button class="btn primary save-btn" :disabled="saving || !analysis" @click="saveCoreAnalysis">
            {{ saving ? 'Đang lưu...' : '💾 Lưu tất cả' }}
          </button>

          <button class="btn close-workspace-btn" @click="emit('close')">✕ Đóng</button>
        </div>
      </header>

      <!-- Loading State Indicator -->
      <div v-if="loading" class="loading-state">
        <div class="spinner"></div>
        <p>Đang tải dữ liệu phân tích âm nhạc...</p>
      </div>

      <!-- Main Intelligence Hub -->
      <div v-else-if="analysis" class="analysis-body">
        <!-- Unified Audio Intelligence Dock -->
        <section class="audio-dock-panel">
          <div class="dock-top-row">
            <div class="song-hero">
              <img
                :src="analysis.coverUrl || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=200&h=200&fit=crop'"
                alt="Song Cover"
                class="song-cover-thumb"
              />
              <div class="song-hero-text">
                <div class="title-row">
                  <h3 class="hero-title">{{ analysis.title }}</h3>
                  <span class="genre-tag">{{ analysis.genre || 'J-Rock' }}</span>
                </div>
                <p class="hero-artist">Nghệ sĩ: <b>{{ analysis.artist }}</b></p>
                <p class="hero-romaji" v-if="analysis.romajiTitle">Romaji: <span>{{ analysis.romajiTitle }}</span></p>

                <div class="external-link-row">
                  <a v-if="analysis.spotifyUrl" :href="analysis.spotifyUrl" target="_blank" class="ext-btn spotify">
                    🟢 Mở Spotify
                  </a>
                  <a v-if="analysis.youtubeUrl" :href="analysis.youtubeUrl" target="_blank" class="ext-btn youtube">
                    🔴 Mở YouTube
                  </a>
                  <button
                    v-if="embedSpotifyUrl || embedYoutubeUrl"
                    class="ext-btn embed-toggle"
                    :class="{ active: showEmbedPlayer }"
                    @click="showEmbedPlayer = !showEmbedPlayer"
                  >
                    📺 {{ showEmbedPlayer ? 'Ẩn Player nhúng' : 'Hiện Player nhúng' }}
                  </button>
                </div>
              </div>
            </div>

            <!-- Audio Feature Badges & Librosa Upload -->
            <div class="dock-meta-actions">
              <div class="feature-pills" v-if="analysis.audioFeature">
                <span class="feat-pill bpm">⚡ {{ analysis.audioFeature.bpm }} BPM</span>
                <span class="feat-pill key">🎼 Giọng: {{ analysis.audioFeature.detectedKey }}</span>
                <span class="feat-pill engine">🔬 {{ analysis.audioFeature.analysisEngine || 'Librosa' }}</span>
              </div>

              <div class="upload-actions">
                <input type="file" ref="audioFileInput" accept="audio/*" style="display:none" @change="onAudioFileSelected" />
                <button
                  class="btn upload-audio-btn"
                  :disabled="isUploadingAudio"
                  @click="triggerAudioUpload"
                  title="Tải lên file audio MP3/WAV để Librosa tính toán đặc trưng và mốc section"
                >
                  🎧 {{ isUploadingAudio ? 'Đang phân tích...' : 'Upload Audio (Librosa)' }}
                </button>

                <button
                  v-if="analysis.audioFeature?.boundaryPointsJson"
                  class="btn boundary-btn"
                  @click="applyBoundaryPointsToSections"
                  title="Tạo mốc phân đoạn tự động từ Librosa"
                >
                  ⚡ Áp dụng mốc Librosa
                </button>
              </div>
            </div>
          </div>

          <!-- Embedded Player Panel (Spotify / YouTube) -->
          <div v-if="showEmbedPlayer" class="embed-player-container">
            <div class="embed-player-header">
              <span>Trình phát trực tiếp (Official Stream)</span>
              <button class="embed-close" @click="showEmbedPlayer = false">✕ Đóng</button>
            </div>
            <div class="embed-frame-wrapper">
              <iframe
                v-if="embedYoutubeUrl"
                :src="embedYoutubeUrl"
                title="YouTube Video Player"
                frameborder="0"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowfullscreen
                class="embedded-iframe youtube"
              ></iframe>
              <iframe
                v-else-if="embedSpotifyUrl"
                :src="embedSpotifyUrl"
                title="Spotify Embed Player"
                frameborder="0"
                allow="encrypted-media"
                class="embedded-iframe spotify"
              ></iframe>
            </div>
          </div>

          <!-- Native Audio Stream Control (if available) -->
          <audio
            v-if="playableAudioUrl"
            ref="audioElement"
            :src="playableAudioUrl"
            @timeupdate="onAudioTimeUpdate"
            @play="isAudioPlaying = true"
            @pause="isAudioPlaying = false"
            style="display:none"
          ></audio>

          <!-- Interactive Waveform Scrubber -->
          <div class="waveform-dock">
            <div class="waveform-header">
              <div class="play-state-group">
                <button
                  v-if="playableAudioUrl"
                  class="play-toggle-btn"
                  @click="toggleAudioPlay"
                  :title="isAudioPlaying ? 'Tạm dừng' : 'Phát âm thanh'"
                >
                  {{ isAudioPlaying ? '⏸' : '▶' }}
                </button>
                <span class="time-readout">
                  {{ msToTimeString(currentAudioTimeMs) }} / {{ msToTimeString(audioDurationMs) }}
                </span>
              </div>
              <span class="waveform-hint muted">
                Nhấn vào thanh sóng để tua mốc phát trực tiếp
              </span>
            </div>

            <!-- Dynamic Waveform Bars -->
            <div class="waveform-visualizer" @mouseleave="waveformHoverTime = null">
              <div
                v-for="bar in waveformBars"
                :key="bar.index"
                class="waveform-bar"
                :class="{ active: bar.active }"
                :style="{ height: `${bar.heightPercent}%` }"
                :title="msToTimeString(bar.timeMs)"
                @mouseenter="waveformHoverTime = msToTimeString(bar.timeMs)"
                @click="onWaveformClick(bar)"
              ></div>
              <!-- Playhead Scrubber Line -->
              <div
                class="playhead-line"
                :style="{ left: `${Math.min(100, Math.max(0, (currentAudioTimeMs / (audioDurationMs || 180000)) * 100))}%` }"
              ></div>
            </div>

            <!-- Colored Segment Timeline Bar aligned with Waveform -->
            <div class="timeline-bar" v-if="analysis.sections?.length">
              <div
                v-for="(sec, idx) in analysis.sections"
                :key="idx"
                class="timeline-segment-chip"
                :class="[getSectionColorClass(sec.sectionType), { playing: currentAudioTimeMs >= sec.startTimeMs && currentAudioTimeMs <= sec.endTimeMs }]"
                :style="{ flex: Math.max(1, (sec.endTimeMs - sec.startTimeMs) / 1000) }"
                :title="`${sec.sectionLabel}: ${msToTimeString(sec.startTimeMs)} - ${msToTimeString(sec.endTimeMs)}`"
                @click="seekAudio(sec.startTimeMs)"
              >
                <span class="seg-title">{{ sec.sectionLabel }}</span>
                <span class="seg-sub">{{ msToTimeString(sec.startTimeMs) }}</span>
              </div>
            </div>
          </div>
        </section>

        <!-- Navigation Tabs Bar -->
        <nav class="analysis-tabs">
          <button :class="{ active: currentTab === 'structure' }" @click="currentTab = 'structure'">
            🎼 Cấu trúc & Timestamps
          </button>
          <button :class="{ active: currentTab === 'lyrics' }" @click="currentTab = 'lyrics'">
            ✍ Phân tích Lời & Dịch nghĩa ({{ analysis.lyrics?.length || 0 }})
          </button>
          <button :class="{ active: currentTab === 'arrangement' }" @click="currentTab = 'arrangement'">
            🎸 Hòa âm & Nhạc cụ
          </button>
          <button :class="{ active: currentTab === 'background' }" @click="currentTab = 'background'">
            📖 Bối cảnh sáng tác
          </button>
          <button :class="{ active: currentTab === 'interpretation' }" @click="currentTab = 'interpretation'">
            💡 Ý nghĩa tổng thể
          </button>
          <button :class="{ active: currentTab === 'sources' }" @click="currentTab = 'sources'">
            📚 Nguồn đối chứng ({{ analysis.sources?.length || 0 }})
          </button>
          <button :class="{ active: currentTab === 'notes' }" @click="currentTab = 'notes'">
            📝 Cảm nhận cá nhân ({{ analysis.notes?.length || 0 }})
          </button>
          <button :class="{ active: currentTab === 'chat' }" class="chat-tab-btn" @click="currentTab = 'chat'">
            💬 Song Intelligence Chat
            <span v-if="chatContextModule !== 'GENERAL'" class="chat-dot" title="Đang có ngữ cảnh"></span>
          </button>
        </nav>

        <!-- ================= TAB 1: STRUCTURE & TIMESTAMPS ================= -->
        <div v-if="currentTab === 'structure'" class="tab-pane">
          <div class="pane-header">
            <div>
              <h4>Phân tích Cấu trúc & Mốc thời gian (Timestamps)</h4>
              <p class="muted small">Phân chia chi tiết Intro, Verse, Chorus với độ cao trào cảm xúc và mốc milliseconds.</p>
            </div>
            <div class="pane-actions">
              <button class="btn soft" @click="addSection">+ Thêm phân đoạn</button>
              <button class="btn primary" :disabled="saving" @click="saveSections">Lưu cấu trúc</button>
            </div>
          </div>

          <div class="section-cards-list">
            <div
              v-for="(sec, idx) in analysis.sections"
              :key="idx"
              class="section-item-card"
              :class="{ highlighted: currentAudioTimeMs >= sec.startTimeMs && currentAudioTimeMs <= sec.endTimeMs }"
            >
              <div class="section-card-top">
                <div class="sec-type-badge">
                  <select v-model="sec.sectionType">
                    <option value="INTRO">INTRO</option>
                    <option value="VERSE">VERSE</option>
                    <option value="PRE_CHORUS">PRE-CHORUS</option>
                    <option value="CHORUS">CHORUS</option>
                    <option value="BRIDGE">BRIDGE</option>
                    <option value="FINAL_CHORUS">FINAL CHORUS</option>
                    <option value="SOLO">SOLO</option>
                    <option value="OUTRO">OUTRO</option>
                  </select>
                </div>

                <input type="text" v-model="sec.sectionLabel" class="sec-label-input" placeholder="Tên đoạn (ví dụ: Điệp khúc 1)" />

                <div class="timestamp-group">
                  <label>BẮT ĐẦU:</label>
                  <input
                    type="text"
                    :value="msToTimeString(sec.startTimeMs)"
                    @change="sec.startTimeMs = timeStringToMs($event.target.value)"
                    class="time-input"
                  />
                  <button type="button" class="stamp-btn" @click="captureTimeToSection(sec, 'start')" title="Lấy mốc audio hiện tại">
                    📍 Set Start
                  </button>

                  <label>KẾT THÚC:</label>
                  <input
                    type="text"
                    :value="msToTimeString(sec.endTimeMs)"
                    @change="sec.endTimeMs = timeStringToMs($event.target.value)"
                    class="time-input"
                  />
                  <button type="button" class="stamp-btn" @click="captureTimeToSection(sec, 'end')" title="Lấy mốc audio hiện tại">
                    📍 Set End
                  </button>

                  <button type="button" class="play-seg-btn" @click="seekAudio(sec.startTimeMs)" title="Tua tới phân đoạn này">
                    ▶ Play
                  </button>
                </div>

                <button class="delete-btn" @click="removeSection(idx)" title="Xóa phân đoạn">✕</button>
              </div>

              <div class="intensity-slider-row">
                <label>Mức độ cao trào: <b>{{ sec.emotionalIntensity || 5 }}/10</b></label>
                <input type="range" min="1" max="10" v-model.number="sec.emotionalIntensity" class="intensity-range" />

                <button
                  class="btn soft mini-chat-action"
                  @click="promptChatWithContext('STRUCTURE', `${sec.sectionLabel} (${sec.sectionType}): ${msToTimeString(sec.startTimeMs)} - ${msToTimeString(sec.endTimeMs)}`, `Phân tích vai trò và sự biến chuyển hòa âm trong phân đoạn ${sec.sectionLabel}`)"
                >
                  💬 Hỏi AI về đoạn này
                </button>
              </div>

              <textarea
                v-model="sec.explanation"
                placeholder="Giải thích vai trò của phần này trong tổng thể bài hát, biến chuyển giai điệu, tâm trạng..."
                rows="2"
                class="sec-notes-input"
              ></textarea>
            </div>
          </div>
        </div>

        <!-- ================= TAB 2: INTERACTIVE LYRICS ================= -->
        <div v-if="currentTab === 'lyrics'" class="tab-pane">
          <div class="pane-header">
            <div>
              <h4>Phân tích Lời Nhật, Furigana, Romaji & Bản dịch tiếng Việt</h4>
              <p class="muted small">Khám phá từng câu hát với ngữ cảnh nghệ thuật, ẩn dụ và cách phát âm Ateji chuẩn xác.</p>
            </div>
            <div class="pane-actions">
              <div class="view-switch-group">
                <button
                  class="switch-pill"
                  :class="{ active: lyricsViewMode === 'interactive' }"
                  @click="lyricsViewMode = 'interactive'"
                >
                  ✨ Khám phá tương tác
                </button>
                <button
                  class="switch-pill"
                  :class="{ active: lyricsViewMode === 'edit' }"
                  @click="lyricsViewMode = 'edit'"
                >
                  ✏ Soạn thảo & Mốc ms
                </button>
              </div>

              <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('LYRICS')">
                ✦ AI dịch & phân tích
              </button>
              <button class="btn soft" @click="addLyricLine">+ Thêm câu</button>
              <button class="btn primary" :disabled="saving" @click="saveLyrics">Lưu lời</button>
            </div>
          </div>

          <!-- Search filter for lyrics -->
          <div class="filter-bar">
            <input
              type="text"
              v-model="lyricSearchQuery"
              placeholder="🔍 Tìm câu hát theo tiếng Nhật, Romaji hoặc nghĩa tiếng Việt..."
              class="lyric-search-box"
            />
          </div>

          <!-- Mode 1: Interactive Exploration Cards -->
          <div v-if="lyricsViewMode === 'interactive'" class="lyrics-interactive-list">
            <div
              v-for="(line, idx) in filteredLyrics"
              :key="idx"
              class="lyric-interactive-card"
              :class="{
                selected: selectedLyricIndex === idx,
                active_playing: currentAudioTimeMs >= line.startTimeMs && currentAudioTimeMs <= line.endTimeMs
              }"
              @click="selectedLyricIndex = idx"
            >
              <div class="card-head">
                <div class="head-badges">
                  <span class="order-badge">#{{ idx + 1 }}</span>
                  <span
                    v-if="line.startTimeMs"
                    class="time-pill"
                    @click.stop="seekAudio(line.startTimeMs)"
                    title="Nhấn để tua đến đoạn này"
                  >
                    ▶ {{ msToTimeString(line.startTimeMs) }} - {{ msToTimeString(line.endTimeMs) }}
                  </span>
                </div>

                <div class="head-actions">
                  <button
                    class="btn soft mini-chat-action"
                    @click.stop="promptChatWithContext('LYRICS', `Câu hát: ${line.japaneseText} (${line.romajiReading})`, `Hãy phân tích sâu cấu trúc ngữ pháp, ẩn dụ và diễn biến cảm xúc của câu hát: &quot;${line.japaneseText}&quot; (${line.romajiReading})`)"
                  >
                    💬 Hỏi AI về câu này
                  </button>
                </div>
              </div>

              <!-- Japanese & Furigana Annotated Text -->
              <div class="jp-ruby-block">
                <p class="furigana-sub" v-if="line.furiganaReading">{{ line.furiganaReading }}</p>
                <h3 class="jp-kanji-text">{{ line.japaneseText || 'Chưa nhập lời tiếng Nhật' }}</h3>
                <span class="romaji-chip" v-if="line.romajiReading">{{ line.romajiReading }}</span>
              </div>

              <!-- Poetic & Literal Translations -->
              <div class="translation-block">
                <div class="poetic-quote" v-if="line.poeticTranslation">
                  <span class="quote-mark">“</span>
                  <span class="quote-text">{{ line.poeticTranslation }}</span>
                </div>
                <p class="literal-text" v-if="line.literalTranslation">
                  <span class="tag">Dịch nghĩa:</span> {{ line.literalTranslation }}
                </p>
              </div>

              <!-- Metaphor / Context Breakdown (Collapsible or visible) -->
              <div class="metaphor-box" v-if="line.metaphorExplanation">
                <div class="metaphor-title">💡 Giải thích ngữ cảnh & Ẩn dụ nghệ thuật:</div>
                <p class="metaphor-content">{{ line.metaphorExplanation }}</p>
              </div>
            </div>
          </div>

          <!-- Mode 2: Quick Edit Grid -->
          <div v-else class="lyrics-edit-grid">
            <div v-for="(line, idx) in analysis.lyrics" :key="idx" class="lyric-edit-card">
              <div class="edit-card-top">
                <span class="order-tag">#{{ idx + 1 }}</span>
                <div class="timestamp-edit-row">
                  <label>Bắt đầu:</label>
                  <input
                    type="text"
                    :value="msToTimeString(line.startTimeMs)"
                    @change="line.startTimeMs = timeStringToMs($event.target.value)"
                    class="time-input small"
                  />
                  <button type="button" class="stamp-btn mini" @click="captureTimeToLyric(line, 'start')">📍 Set</button>

                  <label>Kết thúc:</label>
                  <input
                    type="text"
                    :value="msToTimeString(line.endTimeMs)"
                    @change="line.endTimeMs = timeStringToMs($event.target.value)"
                    class="time-input small"
                  />
                  <button type="button" class="stamp-btn mini" @click="captureTimeToLyric(line, 'end')">📍 Set</button>
                  <button v-if="line.startTimeMs" type="button" class="play-seg-btn mini" @click="seekAudio(line.startTimeMs)">▶</button>
                </div>
                <button class="delete-btn mini" @click="removeLyricLine(idx)">✕</button>
              </div>

              <div class="edit-fields-matrix">
                <div class="field-item">
                  <label>Lời tiếng Nhật (Kanji / Kana):</label>
                  <input type="text" v-model="line.japaneseText" placeholder="Ví dụ: 迷星叫" class="jp-input" />
                </div>
                <div class="field-item">
                  <label>Cách đọc Furigana / Hiragana:</label>
                  <input type="text" v-model="line.furiganaReading" placeholder="Ví dụ: まよいうた" />
                </div>
                <div class="field-item">
                  <label>Phiên âm Romaji chuẩn:</label>
                  <input type="text" v-model="line.romajiReading" placeholder="Ví dụ: Mayoiuta" />
                </div>
                <div class="field-item">
                  <label>Dịch sát nghĩa (Literal):</label>
                  <input type="text" v-model="line.literalTranslation" placeholder="Dịch nghĩa từng từ..." />
                </div>
                <div class="field-item full-span">
                  <label>Dịch tự nhiên & Phóng tác biểu cảm (Poetic):</label>
                  <textarea v-model="line.poeticTranslation" rows="2" placeholder="Bản dịch thơ hoặc lời hát tiếng Việt..."></textarea>
                </div>
                <div class="field-item full-span">
                  <label>Giải thích ẩn dụ & Bối cảnh tâm lý:</label>
                  <textarea v-model="line.metaphorExplanation" rows="2" placeholder="Ý nghĩa biểu tượng, liên hệ với anime..."></textarea>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================= TAB 3: MUSICAL ARRANGEMENT ================= -->
        <div v-if="currentTab === 'arrangement'" class="tab-pane">
          <div class="pane-header">
            <div>
              <h4>Phân tích Hòa âm & Nhạc cụ (Musical Arrangement)</h4>
              <p class="muted small">Mổ xẻ chi tiết Guitar, Bass, Drums, Keyboard và Vocal với mốc cao trào cảm xúc.</p>
            </div>
            <div class="pane-actions">
              <div class="view-switch-group">
                <button
                  class="switch-pill"
                  :class="{ active: arrangementViewMode === 'cards' }"
                  @click="arrangementViewMode = 'cards'"
                >
                  🎸 Thẻ nhạc cụ chuyên sâu
                </button>
                <button
                  class="switch-pill"
                  :class="{ active: arrangementViewMode === 'raw' }"
                  @click="arrangementViewMode = 'raw'"
                >
                  📝 Soạn thảo văn bản
                </button>
              </div>
              <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('ARRANGEMENT')">
                ✦ AI phân tích hòa âm
              </button>
              <button class="btn primary" :disabled="saving" @click="saveCoreAnalysis">Lưu hòa âm</button>
            </div>
          </div>

          <!-- Structured Instrument Cards View -->
          <div v-if="arrangementViewMode === 'cards'" class="instrument-deck">
            <div v-for="inst in instrumentCards" :key="inst.key" class="instrument-card">
              <div class="card-top">
                <div class="inst-hero">
                  <span class="inst-icon">{{ inst.icon }}</span>
                  <div>
                    <h4 class="inst-title">{{ inst.name }}</h4>
                    <span class="inst-badge">{{ inst.badge }}</span>
                  </div>
                </div>

                <button
                  class="btn soft mini-chat-action"
                  @click="promptChatWithContext('ARRANGEMENT', `Nhạc cụ: ${inst.name}`, `Hãy phân tích chi tiết kỹ thuật và tác động cảm xúc của ${inst.name} trong ca khúc này`)"
                >
                  💬 Hỏi AI về nhạc cụ này
                </button>
              </div>

              <div class="inst-body">
                <div class="inst-row">
                  <span class="row-label">Vai trò:</span>
                  <span class="row-val">{{ inst.role }}</span>
                </div>
                <div class="inst-row">
                  <span class="row-label">Đặc tính âm thanh:</span>
                  <p class="inst-desc">{{ inst.detail }}</p>
                </div>
                <div class="inst-row footer-row">
                  <span class="row-label">Điểm nhấn thời gian:</span>
                  <span class="time-hint">{{ inst.timestampHint }}</span>
                  <button class="btn soft mini-play-btn" @click="seekAudio(inst.targetMs)">
                    ▶ Nghe mốc gợi ý
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Raw Text View -->
          <div v-else class="arrangement-raw-view">
            <textarea
              v-model="rawArrangementText"
              rows="15"
              placeholder="Nhập hoặc chỉnh sửa toàn văn bản phân tích hòa âm..."
              class="full-text-area"
            ></textarea>
          </div>
        </div>

        <!-- ================= TAB 4: SONG BACKGROUND ================= -->
        <div v-if="currentTab === 'background'" class="tab-pane">
          <div class="pane-header">
            <div>
              <h4>Bối cảnh sáng tác & Câu chuyện tác phẩm</h4>
              <p class="muted small">Tác giả, nguồn cảm hứng, vị trí trong dự án anime và hồ sơ nghệ sĩ biểu diễn.</p>
            </div>
            <div class="pane-actions">
              <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('BACKGROUND')">
                ✦ AI nghiên cứu bối cảnh
              </button>
              <button
                class="btn soft mini-chat-action"
                @click="promptChatWithContext('BACKGROUND', 'Bối cảnh sáng tác', 'Phân tích bối cảnh phát hành và câu chuyện hậu trường của ca khúc này')"
              >
                💬 Hỏi AI về bối cảnh
              </button>
              <button class="btn primary" :disabled="saving" @click="saveCoreAnalysis">Lưu bối cảnh</button>
            </div>
          </div>

          <div class="background-card-panel">
            <textarea
              v-model="analysis.songBackground"
              rows="12"
              placeholder="Nhập bối cảnh sáng tác, người viết lời/nhạc, sự kiện anime liên quan..."
              class="full-text-area"
            ></textarea>
          </div>
        </div>

        <!-- ================= TAB 5: OVERALL INTERPRETATION ================= -->
        <div v-if="currentTab === 'interpretation'" class="tab-pane">
          <div class="pane-header">
            <div>
              <h4>Ý nghĩa tổng thể & Diễn giải nghệ thuật</h4>
              <p class="muted small">Sự hòa quyện giữa ca từ, cấu trúc giai điệu và thông điệp triết lý bài hát.</p>
            </div>
            <div class="pane-actions">
              <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('INTERPRETATION')">
                ✦ AI tổng hợp ý nghĩa
              </button>
              <button
                class="btn soft mini-chat-action"
                @click="promptChatWithContext('INTERPRETATION', 'Ý nghĩa tổng thể', 'Hãy tóm lược thông điệp nghệ thuật và triết lý sống được gửi gắm trong ca khúc')"
              >
                💬 Hỏi AI về thông điệp
              </button>
              <button class="btn primary" :disabled="saving" @click="saveCoreAnalysis">Lưu ý nghĩa</button>
            </div>
          </div>

          <div class="interpretation-card-panel">
            <textarea
              v-model="analysis.overallInterpretation"
              rows="12"
              placeholder="Đánh giá tổng hợp thông điệp nghệ thuật, sự biến chuyển tâm trạng của nhân vật..."
              class="full-text-area"
            ></textarea>
          </div>
        </div>

        <!-- ================= TAB 6: SOURCES & EVIDENCE ================= -->
        <div v-if="currentTab === 'sources'" class="tab-pane">
          <div class="pane-header">
            <div>
              <h4>Nguồn tham khảo & Dữ liệu bảo chứng (Evidence)</h4>
              <p class="muted small">Phân biệt minh bạch giữa Dữ kiện đã xác minh, Phân tích chuyên môn và Diễn giải nghệ thuật.</p>
            </div>
            <div class="pane-actions">
              <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('SOURCES')">
                ✦ AI tìm kiếm nguồn
              </button>
            </div>
          </div>

          <!-- Add New Source Card -->
          <div class="new-source-form-card">
            <div class="form-title">Thêm nguồn đối chứng mới:</div>
            <div class="form-row">
              <select v-model="newSource.sourceType" class="source-type-select">
                <option value="VERIFIED_OFFICIAL">🟢 ĐÃ XÁC MINH CHÍNH THỨC</option>
                <option value="MUSIC_ANALYSIS">🔵 PHÂN TÍCH CHUYÊN MÔN</option>
                <option value="INTERPRETATION">🟡 DIỄN GIẢI NGHỆ THUẬT</option>
                <option value="UNVERIFIED">⚪ THÔNG TIN THAM KHẢO</option>
              </select>
              <input type="text" v-model="newSource.title" placeholder="Tiêu đề tài liệu, bài phỏng vấn..." class="source-title-input" />
              <input type="text" v-model="newSource.url" placeholder="https://..." class="source-url-input" />
              <input type="text" v-model="newSource.author" placeholder="Tác giả / Nhà xuất bản" class="source-author-input" />
            </div>
            <div class="form-row">
              <input type="text" v-model="newSource.citedClaim" placeholder="Nhận định / Trích dẫn mà nguồn này bảo chứng..." class="source-claim-input" />
              <button class="btn primary" @click="addSource">+ Thêm nguồn</button>
            </div>
          </div>

          <div class="sources-list">
            <div v-for="src in analysis.sources" :key="src.id" class="source-item-card">
              <div class="source-card-header">
                <div class="source-meta">
                  <span class="source-badge" :class="`source-${src.sourceType?.toLowerCase()}`">
                    {{ src.sourceType }}
                  </span>
                  <a v-if="src.url" :href="src.url" target="_blank" class="source-title-link">
                    <b>{{ src.title }}</b> ↗
                  </a>
                  <b v-else class="source-title-plain">{{ src.title }}</b>
                  <span class="source-author" v-if="src.author">• {{ src.author }}</span>
                </div>

                <div class="source-actions">
                  <button
                    class="btn soft mini-chat-action"
                    @click="promptChatWithContext('SOURCES', `Nguồn: ${src.title}`, `Hãy đối chiếu và làm rõ độ tin cậy của nguồn '${src.title}' đối với nhận định: &quot;${src.citedClaim}&quot;`)"
                  >
                    💬 Kiểm chứng với AI
                  </button>
                  <button class="delete-btn mini" @click="deleteSource(src.id)">✕</button>
                </div>
              </div>

              <p class="source-claim" v-if="src.citedClaim">
                <b>Nhận định bảo chứng:</b> “{{ src.citedClaim }}”
              </p>
            </div>
          </div>
        </div>

        <!-- ================= TAB 7: PERSONAL NOTES ================= -->
        <div v-if="currentTab === 'notes'" class="tab-pane">
          <div class="pane-header">
            <div>
              <h4>Ghi chú & Cảm nhận cá nhân (Personal Notes)</h4>
              <p class="muted small">Lưu lại những suy nghĩ, góc nhìn riêng hoặc điểm cần lưu ý khi thưởng thức ca khúc.</p>
            </div>
          </div>

          <div class="new-note-card">
            <textarea
              v-model="newNote.content"
              rows="3"
              placeholder="Nhập cảm nhận cá nhân, cảm xúc khi nghe, nhận xét riêng..."
              class="note-input"
            ></textarea>
            <button class="btn primary add-note-btn" @click="addNote">+ Lưu ghi chú</button>
          </div>

          <div class="notes-list">
            <div v-for="note in analysis.notes" :key="note.id" class="note-card">
              <div class="note-card-header">
                <span class="note-date">{{ new Date(note.createdAt).toLocaleString('vi-VN') }}</span>
                <button class="delete-btn mini" @click="deleteNote(note.id)">✕</button>
              </div>
              <p class="note-body">{{ note.content }}</p>
            </div>
          </div>
        </div>

        <!-- ================= TAB 8: SONG INTELLIGENCE CHAT ================= -->
        <div v-if="currentTab === 'chat'" class="tab-pane chat-pane">
          <div class="pane-header">
            <div>
              <h4>💬 Song Intelligence Assistant</h4>
              <p class="muted small">Trò chuyện chuyên sâu với AI dựa trên toàn bộ bối cảnh, lời bài hát và hòa âm của ca khúc hiện tại.</p>
            </div>
            <div class="pane-actions">
              <div class="active-context-pill" v-if="chatContextModule !== 'GENERAL'">
                <span>📍 Ngữ cảnh: <b>{{ chatContextDetail || chatContextModule }}</b></span>
                <button class="clear-ctx-btn" @click="clearChatContext" title="Xóa ngữ cảnh cụ thể">✕</button>
              </div>
            </div>
          </div>

          <!-- Chat Messages Container -->
          <div class="chat-viewport">
            <div
              v-for="(msg, idx) in chatMessages"
              :key="idx"
              class="chat-bubble-row"
              :class="msg.role"
            >
              <div class="avatar-col">
                <span v-if="msg.role === 'assistant'" class="bot-avatar">🤖</span>
                <span v-else class="user-avatar">👤</span>
              </div>

              <div class="bubble-content-col">
                <div class="bubble-meta" v-if="msg.role === 'assistant'">
                  <span class="sender-name">Music Intelligence AI</span>
                  <span class="cat-pill" :class="`cat-${msg.category?.toLowerCase()}`">
                    {{ msg.category === 'FACT' ? '🟢 DỮ KIỆN XÁC MINH' : (msg.category === 'ANALYSIS' ? '🔵 PHÂN TÍCH CHUYÊN MÔN' : '🟡 DIỄN GIẢI & ẨN DỤ') }}
                  </span>
                </div>

                <div class="bubble-text">
                  <p v-for="(p, pIdx) in msg.content.split('\n\n')" :key="pIdx">{{ p }}</p>
                </div>

                <!-- Suggested Follow-up Questions Chips -->
                <div class="suggested-chips" v-if="msg.suggestedQuestions?.length">
                  <span class="chips-title">Gợi ý khám phá tiếp:</span>
                  <button
                    v-for="(q, qIdx) in msg.suggestedQuestions"
                    :key="qIdx"
                    class="question-chip"
                    @click="sendChatMessage(q)"
                  >
                    💡 {{ q }}
                  </button>
                </div>
              </div>
            </div>

            <!-- Loading Spinner in Chat -->
            <div v-if="isChatLoading" class="chat-bubble-row assistant">
              <div class="avatar-col">
                <span class="bot-avatar">🤖</span>
              </div>
              <div class="bubble-content-col">
                <div class="thinking-spinner">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <small>AI đang suy ngẫm dữ liệu bài hát...</small>
                </div>
              </div>
            </div>
          </div>

          <!-- Chat Input Bar -->
          <div class="chat-input-bar">
            <input
              type="text"
              v-model="chatInput"
              @keydown.enter="sendChatMessage()"
              placeholder="Hỏi AI về lời ca, cách đọc Ateji, hòa âm bass/guitar, hoặc bối cảnh anime..."
              class="chat-text-input"
              :disabled="isChatLoading"
            />
            <button
              class="btn primary send-chat-btn"
              :disabled="isChatLoading || !chatInput.trim()"
              @click="sendChatMessage()"
            >
              Gửi câu hỏi ➔
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.song-analysis-container {
  padding: 16px;
  background: transparent;
  color: #f1f5f9;
}

/* 403 Forbidden */
.forbidden-card {
  text-align: center;
  padding: 60px 24px;
  max-width: 540px;
  margin: 40px auto;
  border: 1px dashed rgba(239, 68, 68, 0.4);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.85);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.4);
}
.forbidden-icon {
  font-size: 54px;
  margin-bottom: 16px;
}
.forbidden-card h2 {
  color: #ef4444;
  margin-bottom: 12px;
}
.forbidden-card button {
  margin-top: 24px;
}

/* Form inputs & controls reset for Dark Theme */
input[type="text"],
input[type="number"],
select,
textarea {
  background: #0f172a !important;
  border: 1px solid #334155 !important;
  color: #f8fafc !important;
  border-radius: 8px;
  outline: none;
  transition: all 0.2s ease;
}
input::placeholder,
textarea::placeholder {
  color: #64748b !important;
}
input:focus,
select:focus,
textarea:focus {
  border-color: #818cf8 !important;
  box-shadow: 0 0 0 3px rgba(129, 140, 248, 0.25) !important;
}

/* Flash Banners */
.flash-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-radius: 8px;
  margin-bottom: 14px;
  font-weight: 600;
  font-size: 13.5px;
}
.flash-banner.success {
  background: rgba(16, 185, 129, 0.15);
  border: 1px solid rgba(16, 185, 129, 0.4);
  color: #6ee7b7;
}
.flash-banner.danger {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #fca5a5;
}
.close-flash {
  background: none;
  border: none;
  color: inherit;
  font-size: 16px;
  cursor: pointer;
}

/* Workspace Header */
.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.studio-badge {
  font-weight: 800;
  font-size: 12.5px;
  letter-spacing: 0.06em;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.25), rgba(168, 85, 247, 0.25));
  color: #c7d2fe;
  border: 1px solid rgba(168, 85, 247, 0.4);
  padding: 6px 14px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.2);
}
.song-selector-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}
.song-select, .song-filter-input {
  padding: 8px 14px;
  font-size: 13.5px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.status-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #94a3b8;
}
.status-tag {
  padding: 6px 12px;
  border-radius: 6px;
  font-weight: 700;
  font-size: 12px;
}
.status-draft { background: rgba(245, 158, 11, 0.15); color: #fde68a; border: 1px solid rgba(245, 158, 11, 0.3); }
.status-analyzing { background: rgba(59, 130, 246, 0.15); color: #93c5fd; border: 1px solid rgba(59, 130, 246, 0.3); }
.status-completed { background: rgba(16, 185, 129, 0.15); color: #6ee7b7; border: 1px solid rgba(16, 185, 129, 0.3); }
.status-failed { background: rgba(239, 68, 68, 0.15); color: #fca5a5; border: 1px solid rgba(239, 68, 68, 0.3); }

/* Buttons */
.btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13.5px;
  font-weight: 700;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  transition: all 0.2s ease;
}
.btn.primary {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  box-shadow: 0 2px 10px rgba(99, 102, 241, 0.3);
}
.btn.primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #4338ca, #4f46e5);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.45);
}
.btn.soft {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #e2e8f0;
}
.btn.soft:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}
.btn.ai-btn {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.25), rgba(168, 85, 247, 0.25));
  border: 1px solid rgba(168, 85, 247, 0.5);
  color: #d8b4fe;
}
.btn.ai-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.4), rgba(168, 85, 247, 0.4));
  box-shadow: 0 0 14px rgba(168, 85, 247, 0.4);
  color: #fff;
}
.close-workspace-btn {
  background: rgba(255, 255, 255, 0.05);
  color: #94a3b8;
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.close-workspace-btn:hover {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border-color: rgba(239, 68, 68, 0.3);
}

/* Audio Dock Panel */
.audio-dock-panel {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 16px;
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}
.dock-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}
.song-hero {
  display: flex;
  gap: 14px;
  align-items: center;
}
.song-cover-thumb {
  width: 84px;
  height: 84px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}
.song-hero-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hero-title {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: #fff;
}
.genre-tag {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  background: rgba(99, 102, 241, 0.2);
  color: #a5b4fc;
  border-radius: 4px;
  border: 1px solid rgba(99, 102, 241, 0.3);
}
.hero-artist {
  margin: 0;
  font-size: 13.5px;
  color: #94a3b8;
}
.hero-artist b { color: #f8fafc; }
.hero-romaji {
  margin: 0;
  font-size: 12.5px;
  color: #64748b;
}
.hero-romaji span { color: #cbd5e1; font-weight: 600; }
.external-link-row {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.ext-btn {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}
.ext-btn.spotify {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}
.ext-btn.spotify:hover { background: #10b981; color: #fff; }
.ext-btn.youtube {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
}
.ext-btn.youtube:hover { background: #ef4444; color: #fff; }
.ext-btn.embed-toggle {
  background: rgba(99, 102, 241, 0.15);
  color: #c7d2fe;
  border: 1px solid rgba(99, 102, 241, 0.3);
}
.ext-btn.embed-toggle.active {
  background: #6366f1;
  color: #fff;
}

.dock-meta-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
}
.feature-pills {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.feat-pill {
  font-size: 11.5px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 6px;
}
.feat-pill.bpm { background: rgba(59, 130, 246, 0.18); color: #93c5fd; border: 1px solid rgba(59, 130, 246, 0.3); }
.feat-pill.key { background: rgba(245, 158, 11, 0.18); color: #fde68a; border: 1px solid rgba(245, 158, 11, 0.3); }
.feat-pill.engine { background: rgba(168, 85, 247, 0.18); color: #d8b4fe; border: 1px solid rgba(168, 85, 247, 0.3); }
.upload-actions {
  display: flex;
  gap: 8px;
}
.upload-audio-btn {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #cbd5e1;
  font-size: 12px;
}
.upload-audio-btn:hover { background: rgba(255, 255, 255, 0.1); color: #fff; }
.boundary-btn {
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.3);
  color: #a5b4fc;
  font-size: 12px;
}
.boundary-btn:hover { background: #4f46e5; color: #fff; }

/* Embed Player Container */
.embed-player-container {
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  padding: 12px;
  margin-top: 4px;
}
.embed-player-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  margin-bottom: 8px;
}
.embed-close {
  background: none;
  border: none;
  color: #cbd5e1;
  cursor: pointer;
}
.embed-frame-wrapper {
  display: flex;
  justify-content: center;
}
.embedded-iframe.youtube {
  width: 100%;
  max-width: 560px;
  height: 240px;
  border-radius: 8px;
}
.embedded-iframe.spotify {
  width: 100%;
  height: 152px;
  border-radius: 8px;
}

/* Waveform Dock */
.waveform-dock {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.waveform-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.play-state-group {
  display: flex;
  align-items: center;
  gap: 10px;
}
.play-toggle-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: #6366f1;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 10px rgba(99, 102, 241, 0.4);
}
.play-toggle-btn:hover { background: #4f46e5; }
.time-readout {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 14px;
  font-weight: 700;
  color: #38bdf8;
}
.waveform-hint {
  font-size: 12px;
  color: #64748b;
}

/* Dynamic Waveform Visualizer */
.waveform-visualizer {
  display: flex;
  align-items: flex-end;
  height: 54px;
  background: rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 4px 6px;
  gap: 2px;
  position: relative;
  cursor: pointer;
}
.waveform-bar {
  flex: 1;
  background: #334155;
  border-radius: 2px;
  transition: height 0.15s ease, background 0.15s ease;
  min-height: 4px;
}
.waveform-bar.active {
  background: linear-gradient(to top, #4f46e5, #38bdf8);
  box-shadow: 0 0 6px rgba(56, 189, 248, 0.4);
}
.waveform-bar:hover {
  background: #f43f5e;
}
.playhead-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #ff0055;
  box-shadow: 0 0 8px #ff0055;
  pointer-events: none;
  transition: left 0.1s linear;
}

/* Timeline Segments Bar */
.timeline-bar {
  display: flex;
  height: 38px;
  border-radius: 8px;
  overflow: hidden;
  background: #090d16;
  border: 1px solid rgba(255, 255, 255, 0.08);
  gap: 2px;
  padding: 2px;
}
.timeline-segment-chip {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border-radius: 5px;
  font-size: 11px;
  color: #fff;
  cursor: pointer;
  overflow: hidden;
  padding: 2px 4px;
  transition: filter 0.2s;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.timeline-segment-chip:hover { filter: brightness(1.25); }
.timeline-segment-chip.playing {
  border: 2px solid #fff;
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.6);
}
.seg-title {
  font-weight: 800;
  white-space: nowrap;
}
.seg-sub {
  font-size: 9.5px;
  opacity: 0.85;
  font-family: 'JetBrains Mono', monospace;
}

/* Section Segment Colors */
.seg-intro { background: linear-gradient(135deg, #0284c7, #0369a1); }
.seg-verse { background: linear-gradient(135deg, #4f46e5, #3730a3); }
.seg-pre-chorus { background: linear-gradient(135deg, #7c3aed, #5b21b6); }
.seg-chorus { background: linear-gradient(135deg, #e11d48, #9f1239); }
.seg-bridge { background: linear-gradient(135deg, #d97706, #92400e); }
.seg-final-chorus { background: linear-gradient(135deg, #c026d3, #86198f); }
.seg-solo { background: linear-gradient(135deg, #ec4899, #be185d); }
.seg-outro { background: linear-gradient(135deg, #059669, #065f46); }
.seg-default { background: linear-gradient(135deg, #475569, #1e293b); }

/* Navigation Tabs */
.analysis-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 12px;
  margin: 18px 0 20px 0;
}
.analysis-tabs button {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 10px 16px;
  font-size: 13.5px;
  font-weight: 700;
  color: #94a3b8;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.analysis-tabs button:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #f8fafc;
  border-color: rgba(99, 102, 241, 0.3);
}
.analysis-tabs button.active {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.25), rgba(168, 85, 247, 0.25));
  color: #ffffff;
  border-color: #818cf8;
  box-shadow: 0 0 12px rgba(99, 102, 241, 0.35);
}
.chat-tab-btn {
  position: relative;
}
.chat-dot {
  width: 8px;
  height: 8px;
  background: #f43f5e;
  border-radius: 50%;
  display: inline-block;
}

/* Tab Panes */
.tab-pane {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.pane-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.pane-header h4 {
  margin: 0;
  font-size: 17px;
  color: #fff;
}
.pane-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.view-switch-group {
  display: flex;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 8px;
  padding: 3px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.switch-pill {
  background: none;
  border: none;
  color: #94a3b8;
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}
.switch-pill.active {
  background: #6366f1;
  color: #fff;
}

/* Mini Action Buttons */
.mini-chat-action {
  background: rgba(168, 85, 247, 0.15);
  border: 1px solid rgba(168, 85, 247, 0.35);
  color: #d8b4fe;
  font-size: 12px;
  padding: 4px 10px;
}
.mini-chat-action:hover {
  background: #a855f7;
  color: #fff;
}
.mini-ai-btn {
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.35);
  color: #c7d2fe;
}
.mini-ai-btn:hover { background: #6366f1; color: #fff; }

/* Section Cards */
.section-cards-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.section-item-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.2s;
}
.section-item-card.highlighted {
  border-color: #818cf8;
  box-shadow: 0 0 16px rgba(129, 140, 248, 0.2);
}
.section-card-top {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.sec-type-badge select {
  padding: 8px 12px;
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
}
.sec-label-input {
  padding: 8px 14px;
  font-weight: 700;
  font-size: 14px;
  flex: 1;
  min-width: 140px;
}
.timestamp-group {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(0, 0, 0, 0.25);
  padding: 4px 10px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}
.timestamp-group label {
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
}
.time-input {
  width: 88px;
  padding: 6px 8px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12.5px;
  font-weight: 700;
  color: #38bdf8 !important;
  text-align: center;
}
.time-input.small { width: 78px; }
.stamp-btn {
  background: rgba(99, 102, 241, 0.18);
  color: #c7d2fe;
  border: 1px solid rgba(99, 102, 241, 0.4);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 11.5px;
  font-weight: 700;
  cursor: pointer;
}
.stamp-btn:hover { background: #4f46e5; color: #fff; }
.stamp-btn.mini { padding: 3px 8px; font-size: 11px; }
.play-seg-btn {
  background: rgba(16, 185, 129, 0.18);
  color: #6ee7b7;
  border: 1px solid rgba(16, 185, 129, 0.4);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 11.5px;
  font-weight: 700;
  cursor: pointer;
}
.play-seg-btn:hover { background: #10b981; color: #fff; }
.play-seg-btn.mini { padding: 3px 8px; font-size: 11px; }
.intensity-slider-row {
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(0, 0, 0, 0.2);
  padding: 8px 14px;
  border-radius: 8px;
}
.intensity-slider-row label {
  font-size: 13px;
  color: #cbd5e1;
  font-weight: 600;
}
.intensity-slider-row label b {
  color: #f43f5e;
  font-weight: 800;
}
.intensity-range {
  flex: 1;
  max-width: 240px;
  accent-color: #818cf8;
}
.sec-notes-input {
  width: 100%;
  padding: 10px 14px;
  font-size: 13.5px;
  line-height: 1.6;
  resize: vertical;
}
.delete-btn {
  background: rgba(239, 68, 68, 0.12);
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #f87171;
  border-radius: 6px;
  cursor: pointer;
  padding: 4px 8px;
  font-size: 13px;
}
.delete-btn:hover { background: #ef4444; color: #fff; }
.delete-btn.mini { padding: 2px 6px; font-size: 11px; }

/* Interactive Lyrics Styling */
.filter-bar {
  display: flex;
}
.lyric-search-box {
  width: 100%;
  padding: 10px 14px;
  font-size: 13.5px;
}
.lyrics-interactive-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.lyric-interactive-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.lyric-interactive-card:hover {
  border-color: rgba(99, 102, 241, 0.4);
}
.lyric-interactive-card.selected {
  border-color: #818cf8;
  box-shadow: 0 0 16px rgba(99, 102, 241, 0.2);
}
.lyric-interactive-card.active_playing {
  border-color: #38bdf8;
  background: rgba(30, 41, 59, 0.9);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-badges {
  display: flex;
  align-items: center;
  gap: 8px;
}
.order-badge {
  font-weight: 800;
  font-size: 12px;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.06);
  padding: 2px 8px;
  border-radius: 4px;
}
.time-pill {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 700;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.3);
  padding: 3px 8px;
  border-radius: 4px;
}
.time-pill:hover { background: #38bdf8; color: #000; }
.jp-ruby-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.furigana-sub {
  margin: 0;
  font-size: 12px;
  color: #a5b4fc;
  font-family: 'Noto Sans JP', sans-serif;
}
.jp-kanji-text {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
  font-family: 'Noto Sans JP', sans-serif;
  letter-spacing: 0.03em;
}
.romaji-chip {
  align-self: flex-start;
  font-size: 12px;
  font-weight: 600;
  color: #cbd5e1;
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 8px;
  border-radius: 4px;
  margin-top: 4px;
}
.translation-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-left: 3px solid #818cf8;
  padding-left: 14px;
}
.poetic-quote {
  font-size: 15px;
  font-weight: 600;
  color: #f8fafc;
  line-height: 1.5;
}
.quote-mark { color: #818cf8; font-weight: 800; margin-right: 4px; }
.literal-text {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
  font-style: italic;
}
.literal-text .tag { font-style: normal; font-weight: 700; color: #64748b; }
.metaphor-box {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 10px 14px;
}
.metaphor-title {
  font-size: 12px;
  font-weight: 700;
  color: #fbbf24;
  margin-bottom: 4px;
}
.metaphor-content {
  margin: 0;
  font-size: 13px;
  color: #cbd5e1;
  line-height: 1.6;
}

/* Lyrics Edit Grid */
.lyrics-edit-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.lyric-edit-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.edit-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.order-tag { font-weight: 800; color: #818cf8; font-size: 13px; }
.timestamp-edit-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.timestamp-edit-row label { font-size: 11px; color: #94a3b8; font-weight: 700; }
.edit-fields-matrix {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}
.field-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-item.full-span {
  grid-column: 1 / -1;
}
.field-item label {
  font-size: 11.5px;
  font-weight: 700;
  color: #94a3b8;
}
.field-item input, .field-item textarea {
  padding: 8px 10px;
  font-size: 13px;
}

/* Instrument Cards Deck */
.instrument-deck {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}
.instrument-card {
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}
.inst-hero {
  display: flex;
  align-items: center;
  gap: 12px;
}
.inst-icon { font-size: 28px; }
.inst-title {
  margin: 0 0 4px 0;
  font-size: 16px;
  color: #fff;
}
.inst-badge {
  font-size: 11px;
  font-weight: 700;
  color: #a5b4fc;
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.3);
  padding: 2px 6px;
  border-radius: 4px;
}
.inst-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13.5px;
}
.inst-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.row-label {
  font-size: 11px;
  font-weight: 800;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.row-val { color: #f1f5f9; font-weight: 600; }
.inst-desc {
  margin: 0;
  color: #cbd5e1;
  line-height: 1.6;
}
.footer-row {
  margin-top: 6px;
  padding-top: 8px;
  border-top: 1px dashed rgba(255, 255, 255, 0.08);
}
.time-hint {
  font-size: 12px;
  color: #38bdf8;
  font-weight: 600;
}
.mini-play-btn {
  margin-top: 6px;
  align-self: flex-start;
  font-size: 11.5px;
  padding: 4px 10px;
}

/* Full Text Areas */
.full-text-area {
  width: 100%;
  padding: 16px;
  font-size: 14px;
  line-height: 1.7;
  resize: vertical;
}

/* Sources & Evidence */
.new-source-form-card {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.form-title { font-weight: 700; font-size: 13px; color: #cbd5e1; }
.form-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.source-type-select { flex: 1; min-width: 180px; }
.source-title-input { flex: 2; min-width: 200px; }
.source-url-input { flex: 2; min-width: 160px; }
.source-author-input { flex: 1; min-width: 140px; }
.source-claim-input { flex: 4; min-width: 260px; }
.sources-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.source-item-card {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 14px;
  background: rgba(15, 23, 42, 0.7);
}
.source-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.source-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.source-badge {
  font-size: 10.5px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 4px;
}
.source-verified_official { background: rgba(16, 185, 129, 0.18); color: #6ee7b7; border: 1px solid rgba(16, 185, 129, 0.35); }
.source-music_analysis { background: rgba(99, 102, 241, 0.18); color: #c7d2fe; border: 1px solid rgba(99, 102, 241, 0.35); }
.source-interpretation { background: rgba(245, 158, 11, 0.18); color: #fde68a; border: 1px solid rgba(245, 158, 11, 0.35); }
.source-unverified { background: rgba(148, 163, 184, 0.18); color: #cbd5e1; border: 1px solid rgba(148, 163, 184, 0.35); }
.source-title-link { color: #818cf8; text-decoration: none; font-size: 14px; }
.source-title-link:hover { text-decoration: underline; }
.source-title-plain { color: #f8fafc; font-size: 14px; }
.source-author { color: #94a3b8; font-size: 12.5px; }
.source-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.source-claim {
  margin: 10px 0 0 0;
  font-style: italic;
  color: #cbd5e1;
  font-size: 13.5px;
}

/* Personal Notes */
.new-note-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}
.note-input { width: 100%; padding: 12px; font-size: 13.5px; resize: vertical; }
.notes-list { display: flex; flex-direction: column; gap: 12px; }
.note-card {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 14px;
  background: rgba(15, 23, 42, 0.7);
}
.note-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.note-date { font-size: 11px; color: #94a3b8; }
.note-body { margin: 0; color: #f1f5f9; font-size: 13.5px; line-height: 1.6; }

/* ================= SONG INTELLIGENCE CHAT ================= */
.chat-pane {
  background: rgba(15, 23, 42, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 18px;
}
.active-context-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(168, 85, 247, 0.2);
  border: 1px solid rgba(168, 85, 247, 0.4);
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 12.5px;
  color: #d8b4fe;
}
.clear-ctx-btn {
  background: none;
  border: none;
  color: #f87171;
  cursor: pointer;
  font-size: 13px;
}
.chat-viewport {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 520px;
  overflow-y: auto;
  padding-right: 8px;
  margin: 14px 0;
}
.chat-bubble-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.chat-bubble-row.user {
  flex-direction: row-reverse;
}
.avatar-col {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.chat-bubble-row.assistant .avatar-col {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
}
.bubble-content-col {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 82%;
}
.chat-bubble-row.user .bubble-content-col {
  align-items: flex-end;
}
.bubble-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sender-name { font-size: 12px; font-weight: 700; color: #94a3b8; }
.cat-pill {
  font-size: 10px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 4px;
}
.cat-pill.cat-fact { background: rgba(16, 185, 129, 0.2); color: #6ee7b7; border: 1px solid rgba(16, 185, 129, 0.4); }
.cat-pill.cat-analysis { background: rgba(59, 130, 246, 0.2); color: #93c5fd; border: 1px solid rgba(59, 130, 246, 0.4); }
.cat-pill.cat-inference { background: rgba(245, 158, 11, 0.2); color: #fde68a; border: 1px solid rgba(245, 158, 11, 0.4); }

.bubble-text {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}
.chat-bubble-row.assistant .bubble-text {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #f1f5f9;
}
.chat-bubble-row.user .bubble-text {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #ffffff;
}
.bubble-text p { margin: 0 0 8px 0; }
.bubble-text p:last-child { margin-bottom: 0; }

.suggested-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 4px;
}
.chips-title {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 700;
}
.question-chip {
  background: rgba(99, 102, 241, 0.12);
  border: 1px solid rgba(99, 102, 241, 0.3);
  color: #c7d2fe;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}
.question-chip:hover {
  background: #6366f1;
  color: #fff;
}

/* Thinking Spinner */
.thinking-spinner {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(30, 41, 59, 0.6);
  padding: 8px 14px;
  border-radius: 8px;
  color: #94a3b8;
  font-size: 12px;
}
.thinking-spinner .dot {
  width: 6px;
  height: 6px;
  background: #818cf8;
  border-radius: 50%;
  animation: pulse 1s infinite alternate;
}
.thinking-spinner .dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-spinner .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes pulse { from { opacity: 0.3; transform: scale(0.8); } to { opacity: 1; transform: scale(1.2); } }

/* Chat Input Bar */
.chat-input-bar {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
.chat-text-input {
  flex: 1;
  padding: 12px 16px;
  font-size: 14px;
}
.send-chat-btn {
  padding: 12px 20px;
  font-size: 14px;
}
</style>
