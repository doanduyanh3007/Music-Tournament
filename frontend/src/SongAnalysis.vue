<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  isAdmin: { type: Boolean, default: false },
  selectedSongId: { type: [Number, String], default: null }
})

const emit = defineEmits(['request-login', 'close'])

// --- State ---
const currentTab = ref('background') // 'background' | 'structure' | 'lyrics' | 'arrangement' | 'interpretation' | 'sources' | 'notes'
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

// Audio playback helper
const audioElement = ref(null)
const currentAudioTimeMs = ref(0)
const isAudioPlaying = ref(false)
const audioDurationMs = ref(0)

// Helper: Formatter ms <-> mm:ss.ms
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

function showFlash(msg, isErr = false) {
  if (isErr) error.value = msg
  else notice.value = msg
  setTimeout(() => {
    if (notice.value === msg) notice.value = ''
    if (error.value === msg) error.value = ''
  }, 4000)
}

// API Helper with Admin headers
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

// Load songs library for selector
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

// Load or create analysis for selected song
async function loadAnalysis(songId) {
  if (!songId || !props.isAdmin) return
  loading.value = true
  error.value = ''
  try {
    const data = await requestApi(`/song/${songId}`)
    analysis.value = data
    activeVersionId.value = data.currentVersionId || (data.versions?.[0]?.id)
  } catch (err) {
    showFlash(err.message, true)
  } finally {
    loading.value = false
  }
}

// Switch version
async function switchVersion(versionId) {
  if (!analysis.value?.id) return
  loading.value = true
  try {
    const data = await requestApi(`/${analysis.value.id}?versionId=${versionId}`)
    analysis.value = data
    activeVersionId.value = versionId
  } catch (err) {
    showFlash(err.message, true)
  } finally {
    loading.value = false
  }
}

// Save core metadata
async function saveCoreAnalysis() {
  if (!analysis.value?.id) return
  saving.value = true
  try {
    const payload = {
      status: analysis.value.status,
      currentVersionId: activeVersionId.value,
      songBackground: analysis.value.songBackground,
      musicalArrangement: analysis.value.musicalArrangement,
      overallInterpretation: analysis.value.overallInterpretation
    }
    const updated = await requestApi(`/${analysis.value.id}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    })
    analysis.value = updated
    showFlash('✓ Đã lưu thông tin phân tích thành công!')
  } catch (err) {
    showFlash(err.message, true)
  } finally {
    saving.value = false
  }
}

// --- Section Management ---
function addSection() {
  if (!analysis.value) return
  const currentSections = analysis.value.sections || []
  const lastSection = currentSections[currentSections.length - 1]
  const startTime = lastSection ? lastSection.endTimeMs : 0
  const newSec = {
    sectionType: 'VERSE',
    sectionLabel: `Phần ${currentSections.length + 1}`,
    startTimeMs: startTime,
    endTimeMs: startTime + 30000,
    explanation: '',
    emotionalIntensity: 5,
    sortOrder: currentSections.length + 1
  }
  analysis.value.sections.push(newSec)
}

function removeSection(index) {
  if (!analysis.value?.sections) return
  analysis.value.sections.splice(index, 1)
}

async function saveSections() {
  if (!activeVersionId.value) return
  saving.value = true
  try {
    const res = await requestApi(`/versions/${activeVersionId.value}/sections`, {
      method: 'PUT',
      body: JSON.stringify(analysis.value.sections || [])
    })
    analysis.value.sections = res
    showFlash('✓ Đã cập nhật cấu trúc phân đoạn thành công!')
  } catch (err) {
    showFlash(err.message, true)
  } finally {
    saving.value = false
  }
}

// --- Lyrics Management ---
function addLyricLine() {
  if (!analysis.value) return
  const lyrics = analysis.value.lyrics || []
  const lastLine = lyrics[lyrics.length - 1]
  const startTime = lastLine ? (lastLine.endTimeMs || lastLine.startTimeMs || 0) : 0
  const newLine = {
    startTimeMs: startTime,
    endTimeMs: startTime + 4000,
    japaneseText: '',
    furiganaReading: '',
    romajiReading: '',
    literalTranslation: '',
    poeticTranslation: '',
    metaphorExplanation: '',
    sortOrder: lyrics.length + 1
  }
  analysis.value.lyrics.push(newLine)
}

function removeLyricLine(index) {
  if (!analysis.value?.lyrics) return
  analysis.value.lyrics.splice(index, 1)
}

async function saveLyrics() {
  if (!activeVersionId.value) return
  saving.value = true
  try {
    const res = await requestApi(`/versions/${activeVersionId.value}/lyrics`, {
      method: 'PUT',
      body: JSON.stringify(analysis.value.lyrics || [])
    })
    analysis.value.lyrics = res
    showFlash('✓ Đã cập nhật toàn bộ lời bài hát & bản dịch!')
  } catch (err) {
    showFlash(err.message, true)
  } finally {
    saving.value = false
  }
}

// --- Sources Management ---
const newSource = ref({
  sourceType: 'VERIFIED_OFFICIAL',
  title: '',
  url: '',
  citedClaim: '',
  author: '',
  verified: true
})

async function addSource() {
  if (!analysis.value?.id || !newSource.value.title.trim()) return
  saving.value = true
  try {
    const saved = await requestApi(`/${analysis.value.id}/sources`, {
      method: 'POST',
      body: JSON.stringify(newSource.value)
    })
    if (!analysis.value.sources) analysis.value.sources = []
    analysis.value.sources.unshift(saved)
    newSource.value = {
      sourceType: 'VERIFIED_OFFICIAL',
      title: '',
      url: '',
      citedClaim: '',
      author: '',
      verified: true
    }
    showFlash('✓ Đã thêm nguồn tham khảo mới!')
  } catch (err) {
    showFlash(err.message, true)
  } finally {
    saving.value = false
  }
}

async function deleteSource(sourceId) {
  if (!confirm('Bạn có chắc chắn muốn xóa nguồn này?')) return
  try {
    await requestApi(`/sources/${sourceId}`, { method: 'DELETE' })
    analysis.value.sources = analysis.value.sources.filter(s => s.id !== sourceId)
    showFlash('✓ Đã xóa nguồn tham khảo.')
  } catch (err) {
    showFlash(err.message, true)
  }
}

// --- Personal Notes Management ---
const newNoteContent = ref('')

async function addNote() {
  if (!analysis.value?.id || !newNoteContent.value.trim()) return
  saving.value = true
  try {
    const author = localStorage.getItem('musicTournamentAdminName') || 'Admin'
    const saved = await requestApi(`/${analysis.value.id}/notes`, {
      method: 'POST',
      body: JSON.stringify({ authorName: author, noteContent: newNoteContent.value.trim() })
    })
    if (!analysis.value.notes) analysis.value.notes = []
    analysis.value.notes.unshift(saved)
    newNoteContent.value = ''
    showFlash('✓ Đã lưu cảm nhận cá nhân!')
  } catch (err) {
    showFlash(err.message, true)
  } finally {
    saving.value = false
  }
}

async function deleteNote(noteId) {
  try {
    await requestApi(`/notes/${noteId}`, { method: 'DELETE' })
    analysis.value.notes = analysis.value.notes.filter(n => n.id !== noteId)
    showFlash('✓ Đã xóa ghi chú.')
  } catch (err) {
    showFlash(err.message, true)
  }
}

// --- Audio Player Controls & Timestamp Capture ---
function onAudioTimeUpdate() {
  if (audioElement.value) {
    currentAudioTimeMs.value = Math.floor(audioElement.value.currentTime * 1000)
    audioDurationMs.value = Math.floor(audioElement.value.duration * 1000) || 0
  }
}

function captureTimeToSection(section, field) {
  if (field === 'start') {
    section.startTimeMs = currentAudioTimeMs.value
    if (section.endTimeMs <= section.startTimeMs) {
      section.endTimeMs = section.startTimeMs + 15000
    }
  } else {
    section.endTimeMs = currentAudioTimeMs.value
  }
}

function captureTimeToLyric(lyric, field) {
  if (field === 'start') {
    lyric.startTimeMs = currentAudioTimeMs.value
    if (lyric.endTimeMs <= lyric.startTimeMs) {
      lyric.endTimeMs = lyric.startTimeMs + 3500
    }
  } else {
    lyric.endTimeMs = currentAudioTimeMs.value
  }
}

function seekAudio(ms) {
  if (audioElement.value && !isNaN(ms)) {
    audioElement.value.currentTime = ms / 1000
    audioElement.value.play()
  }
}

// Watchers & Lifecycle
watch(() => props.selectedSongId, (newId) => {
  if (newId) {
    activeSongId.value = newId
    loadAnalysis(newId)
  }
})

// --- AI Research & Translation Trigger ---
const isAiRunning = ref(false)
const aiRunningModule = ref('')
let aiPollTimer = null

async function triggerAiResearch(moduleName = 'ALL') {
  if (!analysis.value?.id) return
  isAiRunning.value = true
  aiRunningModule.value = moduleName
  try {
    const res = await requestApi(`/${analysis.value.id}/generate?module=${encodeURIComponent(moduleName)}`, {
      method: 'POST'
    })
    showFlash(`🤖 Đang thực hiện AI Research (${moduleName}). Vui lòng đợi trong giây lát...`)
    analysis.value.status = 'ANALYZING'

    // Poll for async completion
    if (aiPollTimer) clearInterval(aiPollTimer)
    aiPollTimer = setInterval(async () => {
      try {
        const updated = await requestApi(`/${analysis.value.id}${activeVersionId.value ? `?versionId=${activeVersionId.value}` : ''}`)
        if (updated.status === 'COMPLETED' || updated.status === 'FAILED') {
          clearInterval(aiPollTimer)
          aiPollTimer = null
          analysis.value = updated
          isAiRunning.value = false
          aiRunningModule.value = ''
          if (updated.status === 'COMPLETED') {
            showFlash(`✓ Hoàn tất phân tích AI cho module: ${moduleName}!`)
          } else {
            showFlash('⚠ Quá trình AI Research gặp sự cố.', true)
          }
        }
      } catch (e) {
        clearInterval(aiPollTimer)
        aiPollTimer = null
        isAiRunning.value = false
      }
    }, 2000)
  } catch (err) {
    isAiRunning.value = false
    aiRunningModule.value = ''
    showFlash(err.message, true)
  }
}

onUnmounted(() => {
  if (aiPollTimer) clearInterval(aiPollTimer)
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
      <h2>403 - Quyền truy cập bị từ chối</h2>
      <p class="muted">
        Chức năng <b>Song Analysis Studio</b> là khu vực quản trị chuyên sâu, chỉ dành riêng cho tài khoản có quyền <b>ADMIN</b>.
      </p>
      <div class="forbidden-actions">
        <button class="btn primary" @click="emit('request-login')">👑 Đăng nhập Quản trị viên</button>
        <button class="btn soft" @click="emit('close')">← Quay lại trang chủ</button>
      </div>
    </div>

    <!-- Admin Workspace -->
    <div v-else class="analysis-workspace">
      <!-- Top Bar: Song Switcher & Controls -->
      <header class="workspace-header">
        <div class="header-left">
          <span class="studio-badge">🔬 SONG ANALYSIS V1</span>
          <div class="song-selector-wrapper">
            <select v-model="activeSongId" @change="loadAnalysis(activeSongId)" class="song-select">
              <option v-for="s in filteredSongs" :key="s.id" :value="s.id">
                {{ s.title }} ({{ s.romajiTitle || s.artist }})
              </option>
            </select>
            <input
              type="text"
              v-model="songSearchQuery"
              placeholder="🔍 Lọc bài hát..."
              class="song-filter-input"
            />
          </div>
        </div>

        <div class="header-right">
          <div v-if="analysis" class="status-selector">
            <label>Trạng thái:</label>
            <select v-model="analysis.status" :class="`status-tag status-${analysis.status?.toLowerCase()}`">
              <option value="DRAFT">📝 Nháp (Draft)</option>
              <option value="ANALYZING">⚡ Đang phân tích</option>
              <option value="COMPLETED">✓ Hoàn thành</option>
              <option value="FAILED">⚠ Cần xem xét lại</option>
            </select>
          </div>

          <button 
            class="btn soft ai-btn" 
            :disabled="isAiRunning || !analysis" 
            @click="triggerAiResearch('ALL')"
            title="Tự động tra cứu, dịch lời và phân tích toàn bộ bằng AI"
          >
            {{ isAiRunning && aiRunningModule === 'ALL' ? '⚡ AI đang phân tích...' : '🤖 AI Auto-Research' }}
          </button>

          <button class="btn primary save-all-btn" :disabled="saving" @click="saveCoreAnalysis">
            {{ saving ? '⏳ Đang lưu...' : '💾 Lưu phân tích' }}
          </button>
        </div>
      </header>

      <!-- Alert notifications -->
      <div v-if="error" class="alert error"><span>⚠</span> {{ error }} <button @click="error=''">×</button></div>
      <div v-if="notice" class="alert success"><span>✓</span> {{ notice }} <button @click="notice=''">×</button></div>

      <!-- Main Song Card & Media Player -->
      <div v-if="analysis" class="song-overview-card">
        <div class="song-meta-box">
          <img
            :src="analysis.artworkUrl || 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=120&auto=format&fit=crop'"
            alt="Artwork"
            class="song-cover-thumb"
          />
          <div class="song-info-text">
            <h3>{{ analysis.title }}</h3>
            <p class="romaji-title" v-if="analysis.romajiTitle">Phiên âm: <b>{{ analysis.romajiTitle }}</b></p>
            <p class="artist-genre">Nghệ sĩ: <b>{{ analysis.artist }}</b> · Thể loại: <span class="genre-pill">{{ analysis.genre || 'J-Rock' }}</span></p>
            <div class="external-links">
              <a v-if="analysis.spotifyUrl" :href="analysis.spotifyUrl" target="_blank" class="link-pill spotify">🎧 Mở Spotify</a>
              <a v-if="analysis.youtubeUrl" :href="analysis.youtubeUrl" target="_blank" class="link-pill youtube">▶ Mở YouTube</a>
            </div>
          </div>
        </div>

        <!-- Integrated Audio & Timestamp Controller -->
        <div class="audio-marker-box">
          <div class="audio-marker-header">
            <span>⏱ Audio Timestamp Marker</span>
            <span class="current-time-badge">{{ msToTimeString(currentAudioTimeMs) }} ({{ currentAudioTimeMs }} ms)</span>
          </div>

          <div v-if="analysis.previewUrl" class="audio-player-row">
            <audio
              ref="audioElement"
              :src="analysis.previewUrl"
              controls
              @timeupdate="onAudioTimeUpdate"
              @play="isAudioPlaying = true"
              @pause="isAudioPlaying = false"
              class="native-audio"
            ></audio>
          </div>
          <div v-else class="no-preview-notice">
            <small class="muted">Bài hát chưa có direct audio preview. Bạn có thể sử dụng link YouTube hoặc nhập timestamp thủ công (ms).</small>
          </div>

          <!-- Version Tab Selector -->
          <div class="version-tabs-row" v-if="analysis.versions?.length">
            <span class="version-label">Phiên bản:</span>
            <button
              v-for="v in analysis.versions"
              :key="v.id"
              class="version-chip"
              :class="{ active: v.id === activeVersionId }"
              @click="switchVersion(v.id)"
            >
              {{ v.versionName }} <small>({{ v.versionType }})</small>
            </button>
          </div>
        </div>
      </div>

      <!-- Navigation Tabs -->
      <nav class="analysis-tabs">
        <button :class="{ active: currentTab === 'background' }" @click="currentTab = 'background'">
          📖 Bối cảnh sáng tác
        </button>
        <button :class="{ active: currentTab === 'structure' }" @click="currentTab = 'structure'">
          🎼 Cấu trúc & Timestamps
        </button>
        <button :class="{ active: currentTab === 'lyrics' }" @click="currentTab = 'lyrics'">
          ✍ Phân tích Lời & Dịch nghĩa
        </button>
        <button :class="{ active: currentTab === 'arrangement' }" @click="currentTab = 'arrangement'">
          🎸 Hòa âm & Nhạc cụ
        </button>
        <button :class="{ active: currentTab === 'interpretation' }" @click="currentTab = 'interpretation'">
          💡 Ý nghĩa tổng thể
        </button>
        <button :class="{ active: currentTab === 'sources' }" @click="currentTab = 'sources'">
          📚 Nguồn đối chứng ({{ analysis?.sources?.length || 0 }})
        </button>
        <button :class="{ active: currentTab === 'notes' }" @click="currentTab = 'notes'">
          📝 Cảm nhận cá nhân ({{ analysis?.notes?.length || 0 }})
        </button>
      </nav>

      <!-- TAB 1: SONG BACKGROUND -->
      <div v-if="currentTab === 'background' && analysis" class="tab-pane">
        <div class="pane-header">
          <div>
            <h4>Bối cảnh sáng tác & Câu chuyện phía sau ca khúc</h4>
            <p class="muted small">Lịch sử ra đời, tác giả lời/nhạc, sự kiện anime hoặc hành trình của nghệ sĩ.</p>
          </div>
          <div class="pane-actions">
            <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('BACKGROUND')">🤖 AI tra cứu bối cảnh</button>
            <button class="btn soft" @click="saveCoreAnalysis">Lưu nội dung</button>
          </div>
        </div>
        <textarea
          v-model="analysis.songBackground"
          rows="10"
          placeholder="Nhập bối cảnh sáng tác, mối liên kết với ban nhạc, bối cảnh phân đoạn anime..."
          class="analysis-textarea"
        ></textarea>
      </div>

      <!-- TAB 2: SONG STRUCTURE & TIMESTAMPS -->
      <div v-if="currentTab === 'structure' && analysis" class="tab-pane">
        <div class="pane-header">
          <div>
            <h4>Cấu trúc bài hát & Mốc thời gian (Timestamps)</h4>
            <p class="muted small">Phân chia chi tiết Intro, Verse, Chorus với độ cao trào cảm xúc và mốc ms.</p>
          </div>
          <div class="pane-actions">
            <button class="btn soft" @click="addSection">+ Thêm phân đoạn</button>
            <button class="btn primary" @click="saveSections">Lưu cấu trúc</button>
          </div>
        </div>

        <!-- Visual Timeline -->
        <div v-if="analysis.sections?.length" class="visual-timeline">
          <div
            v-for="(sec, idx) in analysis.sections"
            :key="idx"
            class="timeline-segment"
            :style="{ flex: Math.max(1, (sec.endTimeMs - sec.startTimeMs) / 1000) }"
            :title="`${sec.sectionLabel}: ${msToTimeString(sec.startTimeMs)} - ${msToTimeString(sec.endTimeMs)}`"
            @click="seekAudio(sec.startTimeMs)"
          >
            <span class="seg-name">{{ sec.sectionLabel }}</span>
            <span class="seg-time">{{ msToTimeString(sec.startTimeMs) }}</span>
          </div>
        </div>

        <!-- Section list cards -->
        <div class="section-cards-list">
          <div v-for="(sec, idx) in analysis.sections" :key="idx" class="section-item-card">
            <div class="section-card-top">
              <div class="sec-type-badge">
                <select v-model="sec.sectionType">
                  <option value="INTRO">Intro (Mở đầu)</option>
                  <option value="VERSE">Verse (Phiên khúc)</option>
                  <option value="PRE_CHORUS">Pre-Chorus (Tiền điệp khúc)</option>
                  <option value="CHORUS">Chorus (Điệp khúc)</option>
                  <option value="BRIDGE">Bridge (Đoạn chuyển tiếp)</option>
                  <option value="FINAL_CHORUS">Final Chorus (Điệp khúc cao trào)</option>
                  <option value="OUTRO">Outro (Kết thúc)</option>
                  <option value="SOLO">Solo Nhạc cụ</option>
                  <option value="OTHER">Khác</option>
                </select>
              </div>

              <input
                type="text"
                v-model="sec.sectionLabel"
                placeholder="Nhãn (ví dụ: Verse 1, Chorus A)"
                class="sec-label-input"
              />

              <div class="timestamp-group">
                <label>Bắt đầu:</label>
                <input
                  type="text"
                  :value="msToTimeString(sec.startTimeMs)"
                  @change="sec.startTimeMs = timeStringToMs($event.target.value)"
                  class="time-input"
                />
                <button
                  type="button"
                  class="stamp-btn"
                  @click="captureTimeToSection(sec, 'start')"
                  title="Gán timestamp hiện tại của bài hát"
                >📍 Set Start</button>

                <label>Kết thúc:</label>
                <input
                  type="text"
                  :value="msToTimeString(sec.endTimeMs)"
                  @change="sec.endTimeMs = timeStringToMs($event.target.value)"
                  class="time-input"
                />
                <button
                  type="button"
                  class="stamp-btn"
                  @click="captureTimeToSection(sec, 'end')"
                  title="Gán timestamp hiện tại của bài hát"
                >📍 Set End</button>
              </div>

              <button class="delete-btn" @click="removeSection(idx)" title="Xóa phần này">✕</button>
            </div>

            <div class="section-card-body">
              <div class="intensity-slider-row">
                <label>Mức độ cao trào: <b>{{ sec.emotionalIntensity || 5 }}/10</b></label>
                <input type="range" min="1" max="10" v-model.number="sec.emotionalIntensity" class="intensity-range" />
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
      </div>

      <!-- TAB 3: LYRICS ANALYSIS -->
      <div v-if="currentTab === 'lyrics' && analysis" class="tab-pane">
        <div class="pane-header">
          <div>
            <h4>Phân tích Lời Nhật, Furigana, Romaji và Bản dịch tiếng Việt</h4>
            <p class="muted small">Tách biệt rõ ràng phiên âm Romaji, dịch sát nghĩa từ vựng và dịch biểu cảm ngữ cảnh.</p>
          </div>
          <div class="pane-actions">
            <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('LYRICS')">🤖 AI dịch & phân tích lời Nhật</button>
            <button class="btn soft" @click="addLyricLine">+ Thêm câu hát</button>
            <button class="btn primary" @click="saveLyrics">Lưu toàn bộ lời</button>
          </div>
        </div>

        <div class="lyrics-grid">
          <div v-for="(line, idx) in analysis.lyrics" :key="idx" class="lyric-card">
            <div class="lyric-card-header">
              <span class="line-index">#{{ idx + 1 }}</span>
              <div class="timestamp-inline">
                <input
                  type="text"
                  :value="msToTimeString(line.startTimeMs)"
                  @change="line.startTimeMs = timeStringToMs($event.target.value)"
                  placeholder="00:00.00"
                  class="time-input small"
                />
                <span>-</span>
                <input
                  type="text"
                  :value="msToTimeString(line.endTimeMs)"
                  @change="line.endTimeMs = timeStringToMs($event.target.value)"
                  placeholder="00:00.00"
                  class="time-input small"
                />
                <button type="button" class="stamp-btn mini" @click="captureTimeToLyric(line, 'start')" title="Lấy mốc thời gian hiện tại">📍 Start</button>
                <button type="button" class="stamp-btn mini" @click="captureTimeToLyric(line, 'end')" title="Lấy mốc thời gian hiện tại">📍 End</button>
                <button v-if="line.startTimeMs" type="button" class="play-seg-btn mini" @click="seekAudio(line.startTimeMs)">▶ Nghe</button>
              </div>
              <button class="delete-btn mini" @click="removeLyricLine(idx)">✕</button>
            </div>

            <div class="lyric-card-grid">
              <div class="field-col">
                <label>Lời tiếng Nhật (Kanji / Kana):</label>
                <textarea v-model="line.japaneseText" rows="2" placeholder="Ví dụ: 届かない言葉だけが" class="jp-text-box"></textarea>
              </div>

              <div class="field-col">
                <label>Cách đọc Furigana (Hiragana):</label>
                <input type="text" v-model="line.furiganaReading" placeholder="とどかないことばだけが" />
              </div>

              <div class="field-col">
                <label>Phiên âm Romaji:</label>
                <input type="text" v-model="line.romajiReading" placeholder="todokanai kotoba dake ga" />
              </div>

              <div class="field-col">
                <label>Dịch sát nghĩa tiếng Việt (Literal):</label>
                <input type="text" v-model="line.literalTranslation" placeholder="Chỉ có những lời không thể chạm tới" />
              </div>

              <div class="field-col">
                <label>Dịch tự nhiên / phóng tác tiếng Việt (Poetic):</label>
                <input type="text" v-model="line.poeticTranslation" placeholder="Chỉ còn lại những tâm sự chẳng thể giãi bày" />
              </div>

              <div class="field-col full-width">
                <label>Giải thích ẩn dụ & Ngữ cảnh bài hát:</label>
                <input type="text" v-model="line.metaphorExplanation" placeholder="Ẩn dụ về sự chia ly, tiếng gọi không có lời hồi đáp..." />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- TAB 4: MUSICAL ARRANGEMENT -->
      <div v-if="currentTab === 'arrangement' && analysis" class="tab-pane">
        <div class="pane-header">
          <div>
            <h4>Hòa âm & Nhạc cụ (Musical Arrangement)</h4>
            <p class="muted small">Phân tích sắc thái Guitar, Bass, Drums, Keyboard, bè Vocal và sự chuyển dịch cao trào.</p>
          </div>
          <div class="pane-actions">
            <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('ARRANGEMENT')">🤖 AI phân tích hòa âm</button>
            <button class="btn soft" @click="saveCoreAnalysis">Lưu nội dung</button>
          </div>
        </div>
        <textarea
          v-model="analysis.musicalArrangement"
          rows="12"
          placeholder="Phân tích: Guitar đôi (lead/rhythm), câu chạy Bass, nhịp trống dồn dập, cách vocal nhả chữ và truyền tải năng lượng..."
          class="analysis-textarea"
        ></textarea>
      </div>

      <!-- TAB 5: OVERALL INTERPRETATION -->
      <div v-if="currentTab === 'interpretation' && analysis" class="tab-pane">
        <div class="pane-header">
          <div>
            <h4>Ý nghĩa tổng thể & Đánh giá ca khúc</h4>
            <p class="muted small">Tổng hợp thông điệp nghệ thuật, sự đồng điệu giữa lời hát, cấu trúc và hòa âm.</p>
          </div>
          <div class="pane-actions">
            <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('INTERPRETATION')">🤖 AI tổng kết ý nghĩa</button>
            <button class="btn soft" @click="saveCoreAnalysis">Lưu nội dung</button>
          </div>
        </div>
        <textarea
          v-model="analysis.overallInterpretation"
          rows="12"
          placeholder="Đánh giá tổng thể: Ca khúc mang ý nghĩa gì với người nghe? Điểm thăng hoa nhất trong toàn bộ tác phẩm là gì?..."
          class="analysis-textarea"
        ></textarea>
      </div>

      <!-- TAB 6: SOURCES -->
      <div v-if="currentTab === 'sources' && analysis" class="tab-pane">
        <div class="pane-header">
          <div>
            <h4>Nguồn tham khảo & Dữ kiện đối chứng</h4>
            <p class="muted small">Lưu lại nguồn gốc thông tin: Phỏng vấn nghệ sĩ, nhà sản xuất, bài phân tích chuyên môn.</p>
          </div>
          <div class="pane-actions">
            <button class="btn soft mini-ai-btn" :disabled="isAiRunning" @click="triggerAiResearch('SOURCES')">🤖 AI tìm nguồn tham khảo</button>
          </div>
        </div>

        <!-- Add Source Form -->
        <div class="add-source-box">
          <h5>+ Thêm nguồn tham khảo mới</h5>
          <div class="source-form-row">
            <select v-model="newSource.sourceType" class="source-type-select">
              <option value="VERIFIED_OFFICIAL">✓ Nguồn chính thức (Phỏng vấn, Nhà sản xuất)</option>
              <option value="MUSIC_ANALYSIS">🎼 Phân tích âm nhạc chuyên môn</option>
              <option value="INTERPRETATION">💡 Diễn giải & Đánh giá cộng đồng</option>
              <option value="UNVERIFIED">❓ Thông tin chưa xác minh</option>
            </select>
            <input type="text" v-model="newSource.title" placeholder="Tiêu đề nguồn (ví dụ: Animate Times Interview #176110)" class="source-title-input" />
            <input type="text" v-model="newSource.url" placeholder="URL liên kết" class="source-url-input" />
          </div>
          <div class="source-form-row">
            <input type="text" v-model="newSource.citedClaim" placeholder="Dữ kiện hoặc trích dẫn từ nguồn này..." class="source-claim-input" />
            <input type="text" v-model="newSource.author" placeholder="Tác giả / Nhà báo" class="source-author-input" />
            <button class="btn primary" @click="addSource" :disabled="saving">Thêm nguồn</button>
          </div>
        </div>

        <!-- Sources List -->
        <div class="sources-list">
          <div v-for="src in analysis.sources" :key="src.id" class="source-item-card">
            <div class="source-card-header">
              <span :class="`source-badge source-${src.sourceType?.toLowerCase()}`">
                {{ src.sourceType }}
              </span>
              <a v-if="src.url" :href="src.url" target="_blank" class="source-title-link"><b>{{ src.title }}</b> ↗</a>
              <span v-else class="source-title"><b>{{ src.title }}</b></span>
              <button class="delete-btn mini" @click="deleteSource(src.id)">✕</button>
            </div>
            <p v-if="src.citedClaim" class="source-claim">“{{ src.citedClaim }}”</p>
            <div class="source-footer">
              <small v-if="src.author" class="muted">Tác giả: {{ src.author }}</small>
            </div>
          </div>
        </div>
      </div>

      <!-- TAB 7: PERSONAL NOTES -->
      <div v-if="currentTab === 'notes' && analysis" class="tab-pane">
        <div class="pane-header">
          <div>
            <h4>Ghi chú & Cảm nhận cá nhân (Admin Notes)</h4>
            <p class="muted small">Lưu lại cảm xúc, đánh giá và góc nhìn riêng của Quản trị viên.</p>
          </div>
        </div>

        <!-- Add note box -->
        <div class="add-note-box">
          <textarea
            v-model="newNoteContent"
            rows="3"
            placeholder="Viết cảm nhận của bạn về giai điệu, lời hát hoặc kỷ niệm liên quan đến bài hát này..."
            class="note-input"
          ></textarea>
          <button class="btn primary" @click="addNote" :disabled="saving">+ Thêm ghi chú</button>
        </div>

        <!-- Notes list -->
        <div class="notes-list">
          <div v-for="n in analysis.notes" :key="n.id" class="note-card">
            <div class="note-card-header">
              <b>{{ n.authorName || 'Admin' }}</b>
              <button class="delete-btn mini" @click="deleteNote(n.id)">✕</button>
            </div>
            <p class="note-content">{{ n.noteContent }}</p>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
.song-analysis-container {
  padding: 16px;
  background: var(--surface, #ffffff);
  border-radius: var(--radius, 8px);
  color: var(--ink, #1e293b);
}

/* 403 Forbidden */
.forbidden-card {
  text-align: center;
  padding: 60px 24px;
  max-width: 540px;
  margin: 40px auto;
  border: 1px dashed var(--line, #e2e8f0);
  border-radius: 12px;
  background: var(--pale, #f8fafc);
}
.forbidden-icon {
  font-size: 54px;
  margin-bottom: 16px;
}
.forbidden-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 24px;
}

/* Workspace Header */
.workspace-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line, #e2e8f0);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.studio-badge {
  font-weight: 800;
  font-size: 13px;
  letter-spacing: 0.05em;
  background: rgba(99, 102, 241, 0.12);
  color: var(--blue, #4f46e5);
  padding: 6px 12px;
  border-radius: 6px;
}
.song-selector-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}
.song-select, .song-filter-input {
  padding: 8px 12px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 6px;
  background: var(--pale, #f8fafc);
  color: var(--ink, #1e293b);
  font-size: 14px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.status-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.status-tag {
  padding: 6px 10px;
  border-radius: 6px;
  font-weight: 600;
  border: 1px solid var(--line, #e2e8f0);
}
.status-draft { background: #fef3c7; color: #92400e; }
.status-analyzing { background: #dbeafe; color: #1e40af; }
.status-completed { background: #d1fae5; color: #065f46; }
.status-failed { background: #fee2e2; color: #991b1b; }

/* Song Overview Card */
.song-overview-card {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  background: var(--pale, #f8fafc);
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 10px;
  padding: 16px;
  margin: 16px 0;
}
@media (max-width: 900px) {
  .song-overview-card { grid-template-columns: 1fr; }
}
.song-meta-box {
  display: flex;
  gap: 16px;
  align-items: center;
}
.song-cover-thumb {
  width: 96px;
  height: 96px;
  border-radius: 8px;
  object-fit: cover;
  box-shadow: var(--shadow, 0 1px 3px rgba(0,0,0,0.1));
}
.song-info-text h3 {
  margin: 0 0 4px 0;
  font-size: 18px;
}
.romaji-title {
  margin: 0 0 4px 0;
  font-size: 13px;
  color: var(--blue, #4f46e5);
}
.artist-genre {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: var(--muted, #64748b);
}
.genre-pill {
  background: rgba(0,0,0,0.06);
  padding: 2px 6px;
  border-radius: 4px;
}
.external-links {
  display: flex;
  gap: 8px;
}
.link-pill {
  font-size: 11px;
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 4px;
  font-weight: 600;
}
.link-pill.spotify { background: #1db954; color: #fff; }
.link-pill.youtube { background: #ff0000; color: #fff; }

/* Audio & Timestamp marker */
.audio-marker-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
}
.audio-marker-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted, #64748b);
}
.current-time-badge {
  background: var(--blue, #4f46e5);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: monospace;
}
.native-audio {
  width: 100%;
  height: 38px;
}
.version-tabs-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 6px;
}
.version-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted, #64748b);
}
.version-chip {
  padding: 4px 10px;
  font-size: 12px;
  border: 1px solid var(--line, #e2e8f0);
  background: #fff;
  border-radius: 14px;
  cursor: pointer;
}
.version-chip.active {
  background: var(--blue, #4f46e5);
  color: #fff;
  border-color: var(--blue, #4f46e5);
}

/* Tabs */
.analysis-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  border-bottom: 2px solid var(--line, #e2e8f0);
  padding-bottom: 2px;
  margin: 16px 0;
}
.analysis-tabs button {
  background: none;
  border: none;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--muted, #64748b);
  cursor: pointer;
  border-bottom: 3px solid transparent;
  white-space: nowrap;
}
.analysis-tabs button.active {
  color: var(--blue, #4f46e5);
  border-bottom-color: var(--blue, #4f46e5);
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
  margin: 0 0 4px 0;
  font-size: 16px;
}
.pane-actions {
  display: flex;
  gap: 10px;
}
.analysis-textarea {
  width: 100%;
  padding: 14px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  background: var(--pale, #f8fafc);
  color: var(--ink, #1e293b);
  resize: vertical;
}

/* Visual Timeline */
.visual-timeline {
  display: flex;
  height: 40px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--line, #e2e8f0);
  gap: 1px;
}
.timeline-segment {
  background: rgba(99, 102, 241, 0.85);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  font-size: 10px;
  cursor: pointer;
  overflow: hidden;
  padding: 2px;
  transition: opacity 0.2s;
}
.timeline-segment:hover {
  opacity: 0.9;
}
.timeline-segment:nth-child(even) {
  background: rgba(79, 70, 229, 0.95);
}
.seg-name {
  font-weight: bold;
  white-space: nowrap;
}
.seg-time {
  font-size: 9px;
  opacity: 0.8;
}

/* Section Cards */
.section-cards-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.section-item-card {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 8px;
  padding: 12px;
  background: var(--pale, #f8fafc);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.section-card-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.sec-type-badge select, .sec-label-input {
  padding: 6px 10px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 6px;
  font-size: 13px;
  background: #fff;
}
.sec-label-input {
  font-weight: bold;
  flex: 1;
  min-width: 140px;
}
.timestamp-group {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
}
.time-input {
  width: 90px;
  padding: 6px 8px;
  font-family: monospace;
  font-size: 12px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 4px;
  text-align: center;
  background: #fff;
}
.time-input.small {
  width: 76px;
}
.stamp-btn {
  background: rgba(99, 102, 241, 0.1);
  color: var(--blue, #4f46e5);
  border: 1px solid rgba(99, 102, 241, 0.3);
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}
.stamp-btn:hover {
  background: rgba(99, 102, 241, 0.2);
}
.stamp-btn.mini {
  padding: 2px 6px;
  font-size: 10px;
}
.intensity-slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}
.intensity-range {
  flex: 1;
  max-width: 240px;
}
.sec-notes-input {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 6px;
  background: #fff;
  resize: vertical;
}

/* Lyrics Grid */
.lyrics-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.lyric-card {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 8px;
  padding: 12px;
  background: var(--pale, #f8fafc);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.lyric-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.line-index {
  font-weight: bold;
  font-size: 13px;
  color: var(--blue, #4f46e5);
}
.timestamp-inline {
  display: flex;
  align-items: center;
  gap: 6px;
}
.play-seg-btn {
  background: var(--green, #059669);
  color: #fff;
  border: none;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: bold;
  cursor: pointer;
}
.lyric-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 10px;
}
.field-col {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-col.full-width {
  grid-column: 1 / -1;
}
.field-col label {
  font-size: 11px;
  font-weight: 600;
  color: var(--muted, #64748b);
}
.field-col input, .field-col textarea {
  padding: 8px 10px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
}
.jp-text-box {
  font-size: 15px;
  font-weight: 600;
}

/* Sources & Notes */
.add-source-box, .add-note-box {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 8px;
  padding: 14px;
  background: var(--pale, #f8fafc);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.source-form-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.source-form-row input, .source-form-row select {
  padding: 8px 10px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 6px;
  font-size: 13px;
  background: #fff;
}
.source-title-input { flex: 2; }
.source-url-input { flex: 2; }
.source-claim-input { flex: 3; }
.source-author-input { flex: 1; }
.sources-list, .notes-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.source-item-card, .note-card {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}
.source-card-header, .note-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.source-badge {
  font-size: 10px;
  font-weight: 800;
  padding: 2px 6px;
  border-radius: 4px;
}
.source-verified_official { background: #d1fae5; color: #065f46; }
.source-music_analysis { background: #e0e7ff; color: #3730a3; }
.source-interpretation { background: #fef3c7; color: #92400e; }
.source-unverified { background: #f1f5f9; color: #475569; }
.source-claim {
  margin: 8px 0;
  font-style: italic;
  color: var(--ink, #1e293b);
  font-size: 13px;
}
.delete-btn {
  background: none;
  border: none;
  color: var(--red, #dc2626);
  font-size: 16px;
  cursor: pointer;
  padding: 2px 6px;
}
.delete-btn.mini { font-size: 13px; }
.note-input {
  width: 100%;
  padding: 10px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 6px;
  font-size: 13px;
  resize: vertical;
}

/* AI Buttons */
.ai-btn {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(168, 85, 247, 0.15));
  border: 1px solid rgba(168, 85, 247, 0.4);
  color: #7c3aed;
  font-weight: 700;
  transition: all 0.2s;
}
.ai-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.25), rgba(168, 85, 247, 0.25));
  box-shadow: 0 0 10px rgba(168, 85, 247, 0.3);
}
.mini-ai-btn {
  background: rgba(124, 58, 237, 0.08);
  border: 1px solid rgba(124, 58, 237, 0.25);
  color: #7c3aed;
  font-size: 12px;
  font-weight: 600;
  padding: 6px 12px;
}
.mini-ai-btn:hover:not(:disabled) {
  background: rgba(124, 58, 237, 0.16);
}
</style>
