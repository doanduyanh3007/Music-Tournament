<script setup>
import { ref, onMounted, watch } from 'vue'

const props = defineProps({
  isAdmin: { type: Boolean, default: false }
})

const activeTab = ref('library') // 'library' | 'playlists'

// --- Songs Library State ---
const songs = ref([])
const totalElements = ref(0)
const totalPages = ref(1)
const currentPage = ref(0)
const pageSize = ref(12)
const loading = ref(false)

const searchQuery = ref('')
const selectedGenre = ref('')
const selectedDiffTier = ref('')
const activeOnly = ref(false)
const availableGenres = ref([])

// Playing audio preview
const playingSongId = ref(null)
const audioElement = ref(null)

// --- Modal Add / Edit Song ---
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

// --- Playlist Sync State ---
const playlists = ref([])
const newPlaylistUrl = ref('')
const newPlaylistTitle = ref('')
const playlistError = ref('')
const addingPlaylist = ref(false)
const syncPollingInterval = ref(null)

// --- Alerts / Notices ---
const alertMessage = ref('')
const alertType = ref('success') // 'success' | 'error'

function showAlert(msg, type = 'success') {
  alertMessage.value = msg
  alertType.value = type
  setTimeout(() => {
    if (alertMessage.value === msg) alertMessage.value = ''
  }, 4500)
}

// Fetch songs
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

    const params = new URLSearchParams({
      page: page.toString(),
      size: pageSize.value.toString(),
      query: searchQuery.value.trim(),
      genre: selectedGenre.value,
      activeOnly: activeOnly.value ? 'true' : 'false'
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

// Fetch genres
async function fetchGenres() {
  try {
    const res = await fetch('/api/quiz/songs/genres')
    if (res.ok) {
      availableGenres.value = await res.json()
    }
  } catch (e) {}
}

// Fetch playlists
async function fetchPlaylists() {
  try {
    const res = await fetch('/api/quiz/playlists')
    if (res.ok) {
      playlists.value = await res.json()
    }
  } catch (e) {}
}

// Toggle audio preview
function toggleAudio(song) {
  if (!song.previewUrl) {
    showAlert('Bài hát chưa có link MP3 xem trước (có thể nghe qua link YouTube)', 'error')
    return
  }
  if (playingSongId.value === song.id) {
    if (audioElement.value) {
      audioElement.value.pause()
      audioElement.value = null
    }
    playingSongId.value = null
  } else {
    if (audioElement.value) {
      audioElement.value.pause()
    }
    const audio = new Audio(song.previewUrl)
    audio.play().catch(() => showAlert('Trình duyệt chặn tự động phát audio', 'error'))
    audio.onended = () => {
      playingSongId.value = null
      audioElement.value = null
    }
    audioElement.value = audio
    playingSongId.value = song.id
  }
}

// Toggle active
async function toggleActive(song) {
  try {
    const res = await fetch(`/api/quiz/songs/${song.id}/toggle-active`, { method: 'PATCH' })
    if (res.ok) {
      const data = await res.json()
      song.active = data.active
      showAlert(song.active ? `Đã bật bài hát: ${song.title}` : `Đã tắt bài hát: ${song.title}`)
    }
  } catch (e) {
    showAlert('Không thể thay đổi trạng thái bài hát', 'error')
  }
}

// Open modal to add song
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

// Open modal to edit song
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

// Save song
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

// Delete song
async function deleteSong(song) {
  if (!confirm(`Bạn có chắc chắn muốn xóa bài hát "${song.title}" khỏi kho nhạc không?`)) return
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

// Import from tournament
async function importFromTournaments() {
  if (!confirm('Sao chép toàn bộ bài hát từ các giải đấu hiện có vào kho Quiz? Hệ thống sẽ tự động lọc trùng và lấy preview audio.')) return
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

// Add Playlist
async function addPlaylist() {
  if (!newPlaylistUrl.value.trim()) {
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
        url: newPlaylistUrl.value.trim(),
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
    showAlert('Đã thêm playlist! Nhấn "Đồng bộ ngay" để tải các bài hát.')
    await fetchPlaylists()
    // Auto-trigger sync
    triggerPlaylistSync(created.id)
  } catch (e) {
    playlistError.value = e.message
  } finally {
    addingPlaylist.value = false
  }
}

// Trigger Playlist Sync
async function triggerPlaylistSync(playlistId) {
  try {
    const res = await fetch(`/api/quiz/playlists/${playlistId}/sync`, { method: 'POST' })
    if (res.ok) {
      showAlert('Đang bắt đầu đồng bộ bài hát ngầm...')
      fetchPlaylists()
    }
  } catch (e) {
    showAlert('Lỗi khi kích hoạt đồng bộ', 'error')
  }
}

// Delete Playlist
async function deletePlaylist(playlist) {
  if (!confirm(`Xóa playlist "${playlist.title}"? Các bài hát đã đồng bộ vào kho vẫn sẽ được giữ lại.`)) return
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

// Watchers
watch([searchQuery, selectedGenre, selectedDiffTier, activeOnly], () => {
  fetchSongs(0)
})

onMounted(() => {
  fetchSongs(0)
  fetchGenres()
  fetchPlaylists()

  // Poll playlists status every 5 seconds if tab is playlists or has syncing
  syncPollingInterval.value = setInterval(() => {
    if (playlists.value.some(p => p.syncStatus === 'SYNCING')) {
      fetchPlaylists()
      fetchSongs(currentPage.value)
    }
  }, 4000)
})
</script>

<template>
  <div class="music-manager-root">
    <!-- Header banner -->
    <div class="manager-header">
      <div class="manager-title-area">
        <div class="title-with-pill">
          <h2>🎵 Quản Lý Kho Nhạc & Playlist</h2>
          <span class="arena-tag">QUIZ ENGINE</span>
        </div>
        <p class="manager-desc">
          Kho bài hát dùng chung cho chế độ Music Quiz & Tournament. Hỗ trợ đồng bộ tự động từ YouTube / Spotify, chuẩn hóa độ khó và phát hiện bài trùng.
        </p>
      </div>

      <div class="manager-header-actions">
        <button class="anime-btn btn-secondary" @click="importFromTournaments" :disabled="loading" title="Nhập tất cả bài hát từ các giải đấu hiện tại">
          ⚡ Nhập từ Giải đấu
        </button>
        <button class="anime-btn btn-primary" @click="openAddSongModal">
          + Thêm bài hát thủ công
        </button>
      </div>
    </div>

    <!-- Toast Notice / Error -->
    <div v-if="alertMessage" class="manager-alert" :class="alertType">
      <span>{{ alertType === 'error' ? '⚠️' : '✨' }}</span>
      <span>{{ alertMessage }}</span>
      <button class="close-alert-btn" @click="alertMessage = ''">×</button>
    </div>

    <!-- Manager Tabs -->
    <div class="manager-nav-tabs">
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
        <span>🔄 Đồng bộ Playlist (YouTube / Spotify)</span>
        <span class="tab-counter">{{ playlists.length }}</span>
      </button>
    </div>

    <!-- TAB 1: KHO BÀI HÁT -->
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

        <div class="filter-field checkbox-field">
          <label class="toggle-switch">
            <input type="checkbox" v-model="activeOnly" />
            <span class="slider"></span>
          </label>
          <span class="toggle-label">Chỉ bài đang bật</span>
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
        <p>Hãy thử xóa bộ lọc, thêm bài hát thủ công hoặc nhập từ giải đấu.</p>
        <button class="anime-btn btn-primary" @click="openAddSongModal">+ Thêm bài hát ngay</button>
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
              @click="toggleAudio(song)"
              :title="playingSongId === song.id ? 'Tạm dừng nghe thử' : 'Nghe thử 30s preview MP3'"
            >
              {{ playingSongId === song.id ? '⏸' : '▶' }}
            </button>
          </div>

          <!-- Info -->
          <div class="song-main-info">
            <div class="song-title-row">
              <h4 class="song-title" :title="song.title">{{ song.title }}</h4>
              <span class="diff-badge" :class="getTierBadgeClass(song.difficulty)">
                {{ getTierLabel(song.difficulty) }} ({{ song.difficulty }})
              </span>
            </div>
            <div class="song-artist-row">
              <span class="artist-name">👤 {{ song.artist }}</span>
              <span class="genre-tag">🏷️ {{ song.genre || 'General' }}</span>
            </div>
            <div v-if="song.aliases" class="song-aliases-preview" :title="'Biệt danh / Tên khác: ' + song.aliases">
              <small class="aliases-text">Tên khác: {{ song.aliases }}</small>
            </div>
          </div>

          <!-- Actions -->
          <div class="song-card-actions">
            <!-- Toggle Active -->
            <button 
              class="action-icon-btn active-toggle-btn" 
              :class="{ 'is-active': song.active }" 
              @click="toggleActive(song)"
              :title="song.active ? 'Bài đang bật trong Quiz (bấm để tắt)' : 'Bài đang tắt (bấm để bật)'"
            >
              {{ song.active ? '🟢 Bật' : '⚪ Tắt' }}
            </button>

            <!-- YouTube / Link -->
            <a 
              v-if="song.youtubeUrl" 
              :href="song.youtubeUrl" 
              target="_blank" 
              class="action-icon-btn yt-link-btn" 
              title="Mở trên YouTube"
            >
              ▶️ YT
            </a>

            <!-- Edit -->
            <button 
              class="action-icon-btn edit-btn" 
              @click="openEditSongModal(song)" 
              title="Chỉnh sửa thông tin / độ khó"
            >
              ✏️ Sửa
            </button>

            <!-- Delete -->
            <button 
              class="action-icon-btn delete-btn" 
              @click="deleteSong(song)" 
              title="Xóa khỏi kho"
            >
              🗑️
            </button>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="manager-pagination">
        <button 
          class="page-btn" 
          :disabled="currentPage === 0" 
          @click="fetchSongs(currentPage - 1)"
        >
          ◀ Trang trước
        </button>
        <span class="page-indicator">Trang {{ currentPage + 1 }} / {{ totalPages }} ({{ totalElements }} bài)</span>
        <button 
          class="page-btn" 
          :disabled="currentPage >= totalPages - 1" 
          @click="fetchSongs(currentPage + 1)"
        >
          Trang sau ▶
        </button>
      </div>
    </div>

    <!-- TAB 2: ĐỒNG BỘ PLAYLIST -->
    <div v-else class="tab-content playlist-pane">
      <!-- Add Playlist Card -->
      <div class="playlist-form-card">
        <h3>🔗 Thêm Playlist để Đồng bộ Tự động</h3>
        <p class="muted">Hỗ trợ dán đường dẫn Playlist công khai từ <b>YouTube</b> hoặc <b>Spotify</b>. Hệ thống tự động phân tách bài hát, gán preview nhạc 30s và đánh giá độ khó.</p>
        
        <div class="playlist-input-grid">
          <div class="input-group">
            <label>Đường dẫn Playlist / Video URL *</label>
            <input 
              type="text" 
              v-model="newPlaylistUrl" 
              placeholder="VD: https://www.youtube.com/playlist?list=PL... hoặc track Spotify"
              class="anime-input"
            />
          </div>
          <div class="input-group">
            <label>Tên gợi nhớ (tùy chọn)</label>
            <input 
              type="text" 
              v-model="newPlaylistTitle" 
              placeholder="VD: Anime Openings 2026, Best J-Pop Hits"
              class="anime-input"
            />
          </div>
        </div>

        <div v-if="playlistError" class="modal-error-msg">⚠️ {{ playlistError }}</div>

        <div class="form-btn-row">
          <button 
            class="anime-btn btn-primary" 
            :disabled="addingPlaylist" 
            @click="addPlaylist"
          >
            {{ addingPlaylist ? 'Đang thêm...' : '+ Thêm & Đồng bộ ngay' }}
          </button>
        </div>
      </div>

      <!-- Playlist List -->
      <div class="playlists-list-section">
        <h3>📋 Danh sách Playlist Đã Liên Kết</h3>

        <div v-if="playlists.length === 0" class="manager-empty-state">
          <div class="empty-icon">📑</div>
          <p>Chưa có playlist nào được thêm. Hãy dán link playlist YouTube ở trên để đồng bộ hàng trăm bài hát cùng lúc!</p>
        </div>

        <div v-else class="playlist-cards-container">
          <div v-for="pl in playlists" :key="pl.id" class="playlist-card">
            <div class="pl-top-row">
              <span class="pl-source-pill" :class="pl.source">
                {{ pl.source === 'youtube' ? '▶ YouTube' : pl.source === 'spotify' ? '🎧 Spotify' : '🔗 Link' }}
              </span>
              <span class="pl-status-pill" :class="pl.syncStatus.toLowerCase()">
                <span v-if="pl.syncStatus === 'SYNCING'" class="spin-icon">🔄</span>
                {{ pl.syncStatus === 'SYNCING' ? 'Đang đồng bộ...' : pl.syncStatus === 'COMPLETED' ? 'Đã đồng bộ' : pl.syncStatus === 'ERROR' ? 'Lỗi' : 'Chờ đồng bộ' }}
              </span>
            </div>

            <h4 class="pl-title">{{ pl.title }}</h4>
            <a :href="pl.sourceUrl" target="_blank" class="pl-link">{{ pl.sourceUrl }}</a>

            <div class="pl-meta-info">
              <span><b>{{ pl.itemCount }}</b> bài hát</span>
              <span v-if="pl.lastSyncAt">Cập nhật: {{ new Date(pl.lastSyncAt).toLocaleString('vi-VN') }}</span>
            </div>

            <div v-if="pl.lastErrorMessage" class="pl-error-box">
              ⚠️ {{ pl.lastErrorMessage }}
            </div>

            <div class="pl-card-actions">
              <button 
                class="anime-btn btn-secondary sync-btn" 
                :disabled="pl.syncStatus === 'SYNCING'" 
                @click="triggerPlaylistSync(pl.id)"
              >
                {{ pl.syncStatus === 'SYNCING' ? 'Đang chạy ngầm...' : '🔄 Đồng bộ lại' }}
              </button>
              <button 
                class="anime-btn btn-danger-ghost" 
                @click="deletePlaylist(pl)"
              >
                Xóa
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- MODAL ADD / EDIT SONG -->
    <div v-if="showSongModal" class="manager-modal-backdrop" @click.self="showSongModal = false">
      <div class="manager-modal">
        <div class="modal-head">
          <h3>{{ isEditing ? '✏️ Chỉnh sửa bài hát' : '✨ Thêm bài hát thủ công' }}</h3>
          <button class="close-btn" @click="showSongModal = false">×</button>
        </div>

        <div class="modal-body">
          <div class="form-row">
            <div class="input-group">
              <label>Tên bài hát *</label>
              <input type="text" v-model="songForm.title" class="anime-input" placeholder="VD: Gurenge, Sparkle, Blue Bird..." />
            </div>
            <div class="input-group">
              <label>Nghệ sĩ *</label>
              <input type="text" v-model="songForm.artist" class="anime-input" placeholder="VD: LiSA, RADWIMPS, Eve..." />
            </div>
          </div>

          <div class="input-group">
            <label>Biệt danh / Aliases (cách viết khác, tên tiếng Nhật, Romaji)</label>
            <input 
              type="text" 
              v-model="songForm.aliases" 
              class="anime-input" 
              placeholder="VD: 紅蓮華, Red Lotus, Kimetsu no Yaiba OP 1 (phân cách bằng dấu phẩy)"
            />
            <small class="input-hint">Hệ thống sẽ chấp nhận các biệt danh này khi người chơi trả lời câu đố.</small>
          </div>

          <div class="form-row">
            <div class="input-group">
              <label>Thể loại (Genre)</label>
              <input type="text" v-model="songForm.genre" class="anime-input" placeholder="VD: Anime, J-Pop, Rock..." />
            </div>

            <div class="input-group">
              <label>Độ khó: <b>{{ songForm.difficulty }}</b> ({{ getTierLabel(songForm.difficulty) }})</label>
              <input 
                type="range" 
                min="0" 
                max="100" 
                v-model.number="songForm.difficulty" 
                class="diff-slider" 
              />
              <div class="slider-scale">
                <span>Easy (0)</span>
                <span>Normal (40)</span>
                <span>Hard (65)</span>
                <span>Master (100)</span>
              </div>
            </div>
          </div>

          <div class="form-row">
            <div class="input-group">
              <label>Link Audio Preview MP3 (30 giây)</label>
              <input type="text" v-model="songForm.previewUrl" class="anime-input" placeholder="Tự động tìm kiếm nếu để trống" />
            </div>
            <div class="input-group">
              <label>Link YouTube</label>
              <input type="text" v-model="songForm.youtubeUrl" class="anime-input" placeholder="https://www.youtube.com/watch?v=..." />
            </div>
          </div>

          <div class="input-group">
            <label>Ảnh bìa (Artwork URL)</label>
            <input type="text" v-model="songForm.artworkUrl" class="anime-input" placeholder="https://... (tự động điền nếu để trống)" />
          </div>

          <div v-if="songModalError" class="modal-error-msg">⚠️ {{ songModalError }}</div>
        </div>

        <div class="modal-foot">
          <button class="anime-btn btn-secondary" @click="showSongModal = false">Hủy</button>
          <button class="anime-btn btn-primary" :disabled="savingSong" @click="saveSong">
            {{ savingSong ? 'Đang lưu...' : 'Lưu bài hát' }}
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
  animation: fadeIn 0.25s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.manager-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  background: linear-gradient(135deg, rgba(26, 20, 48, 0.85) 0%, rgba(13, 11, 24, 0.95) 100%);
  border: 1px solid rgba(168, 85, 247, 0.25);
  border-radius: 14px;
  padding: 22px 28px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
}

.title-with-pill {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-with-pill h2 {
  margin: 0;
  font-size: 1.55rem;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: 0.5px;
}

.arena-tag {
  background: linear-gradient(135deg, #a855f7, #6366f1);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 6px;
  letter-spacing: 1px;
}

.manager-desc {
  margin: 6px 0 0;
  font-size: 0.92rem;
  color: #94a3b8;
  max-width: 650px;
}

.manager-header-actions {
  display: flex;
  gap: 12px;
}

/* Alert */
.manager-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-radius: 8px;
  font-size: 0.92rem;
  font-weight: 600;
}
.manager-alert.success {
  background: rgba(16, 185, 129, 0.15);
  border: 1px solid rgba(16, 185, 129, 0.4);
  color: #34d399;
}
.manager-alert.error {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #f87171;
}
.close-alert-btn {
  margin-left: auto;
  background: transparent;
  border: none;
  color: inherit;
  font-size: 1.2rem;
  cursor: pointer;
}

/* Nav Tabs */
.manager-nav-tabs {
  display: flex;
  gap: 10px;
  border-bottom: 2px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 2px;
}

.nav-tab-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  border: none;
  border-bottom: 3px solid transparent;
  color: #94a3b8;
  font-size: 1rem;
  font-weight: 600;
  padding: 10px 18px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-tab-btn:hover {
  color: #f8fafc;
}

.nav-tab-btn.active {
  color: #c084fc;
  border-bottom-color: #a855f7;
}

.tab-counter {
  background: rgba(168, 85, 247, 0.2);
  color: #d8b4fe;
  font-size: 0.75rem;
  padding: 2px 7px;
  border-radius: 10px;
}

/* Filter Card */
.filter-bar-card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  background: rgba(18, 16, 32, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 14px 18px;
}

.filter-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-field label {
  font-size: 0.85rem;
  color: #94a3b8;
  font-weight: 600;
}

.search-field {
  flex: 1;
  min-width: 240px;
  position: relative;
}

.field-icon {
  position: absolute;
  left: 10px;
  pointer-events: none;
  font-size: 0.9rem;
}

.search-field input {
  padding-left: 32px;
  padding-right: 28px;
  width: 100%;
}

.clear-input-btn {
  position: absolute;
  right: 10px;
  background: transparent;
  border: none;
  color: #64748b;
  cursor: pointer;
  font-size: 1.1rem;
}

.clear-input-btn:hover { color: #f8fafc; }

.anime-input, .anime-select {
  background: rgba(10, 8, 20, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  color: #f8fafc;
  padding: 8px 12px;
  font-size: 0.9rem;
  outline: none;
  transition: border-color 0.2s;
}

.anime-input:focus, .anime-select:focus {
  border-color: #a855f7;
  box-shadow: 0 0 0 2px rgba(168, 85, 247, 0.2);
}

.checkbox-field {
  margin-left: auto;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
}

.toggle-switch input { opacity: 0; width: 0; height: 0; }
.slider {
  position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
  background-color: #334155;
  transition: .3s;
  border-radius: 20px;
}
.slider:before {
  position: absolute; content: ""; height: 14px; width: 14px; left: 3px; bottom: 3px;
  background-color: white;
  transition: .3s;
  border-radius: 50%;
}
input:checked + .slider { background-color: #a855f7; }
input:checked + .slider:before { transform: translateX(16px); }

.toggle-label {
  font-size: 0.85rem;
  color: #cbd5e1;
}

/* Songs Grid */
.songs-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.song-card-row {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(22, 19, 39, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 10px;
  padding: 10px 16px;
  transition: all 0.2s ease;
}

.song-card-row:hover {
  background: rgba(30, 26, 54, 0.85);
  border-color: rgba(168, 85, 247, 0.35);
  transform: translateY(-1px);
}

.song-card-row.inactive {
  opacity: 0.55;
  filter: grayscale(0.5);
}

.song-cover-box {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
  background: #0f0d1a;
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
  font-size: 1.1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}

.song-cover-box:hover .play-audio-btn, .play-audio-btn.playing {
  opacity: 1;
}

.play-audio-btn.playing {
  background: rgba(168, 85, 247, 0.7);
}

.song-main-info {
  flex: 1;
  min-width: 0;
}

.song-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.song-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.diff-badge {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 6px;
  text-transform: uppercase;
}

.badge-easy { background: rgba(16, 185, 129, 0.2); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); }
.badge-normal { background: rgba(59, 130, 246, 0.2); color: #60a5fa; border: 1px solid rgba(59, 130, 246, 0.3); }
.badge-hard { background: rgba(245, 158, 11, 0.2); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.3); }
.badge-expert { background: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); }
.badge-master { background: rgba(168, 85, 247, 0.25); color: #c084fc; border: 1px solid rgba(168, 85, 247, 0.4); }

.song-artist-row {
  display: flex;
  gap: 12px;
  margin-top: 3px;
  font-size: 0.85rem;
  color: #94a3b8;
}

.genre-tag {
  color: #cbd5e1;
}

.song-aliases-preview {
  margin-top: 2px;
  color: #64748b;
  font-size: 0.78rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.song-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-icon-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #cbd5e1;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 0.82rem;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.15s;
}

.action-icon-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.active-toggle-btn.is-active {
  color: #34d399;
  border-color: rgba(16, 185, 129, 0.3);
}

.yt-link-btn {
  color: #f87171;
}

.edit-btn:hover {
  border-color: #60a5fa;
  color: #60a5fa;
}

.delete-btn:hover {
  border-color: #ef4444;
  color: #ef4444;
}

/* Pagination */
.manager-pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.page-btn {
  background: rgba(168, 85, 247, 0.15);
  border: 1px solid rgba(168, 85, 247, 0.3);
  color: #d8b4fe;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: rgba(168, 85, 247, 0.3);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-indicator {
  font-size: 0.88rem;
  color: #94a3b8;
}

/* Tab 2: Playlists */
.playlist-form-card {
  background: rgba(18, 16, 32, 0.75);
  border: 1px solid rgba(168, 85, 247, 0.2);
  border-radius: 12px;
  padding: 22px;
}

.playlist-form-card h3 {
  margin: 0 0 6px;
  font-size: 1.2rem;
  color: #f8fafc;
}

.playlist-input-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
  margin-top: 14px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.input-group label {
  font-size: 0.85rem;
  color: #94a3b8;
  font-weight: 600;
}

.input-hint {
  font-size: 0.75rem;
  color: #64748b;
}

.form-btn-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.playlists-list-section {
  margin-top: 26px;
}

.playlists-list-section h3 {
  margin-bottom: 14px;
  font-size: 1.15rem;
  color: #f8fafc;
}

.playlist-cards-container {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.playlist-card {
  background: rgba(22, 19, 40, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pl-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pl-source-pill {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
}
.pl-source-pill.youtube { background: rgba(239, 68, 68, 0.2); color: #f87171; }
.pl-source-pill.spotify { background: rgba(16, 185, 129, 0.2); color: #34d399; }

.pl-status-pill {
  font-size: 0.75rem;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 600;
}
.pl-status-pill.completed { background: rgba(16, 185, 129, 0.15); color: #34d399; }
.pl-status-pill.syncing { background: rgba(245, 158, 11, 0.15); color: #fbbf24; }
.pl-status-pill.error { background: rgba(239, 68, 68, 0.15); color: #f87171; }
.pl-status-pill.idle { background: rgba(100, 116, 139, 0.2); color: #94a3b8; }

.spin-icon {
  display: inline-block;
  animation: spin 1s infinite linear;
}
@keyframes spin { 100% { transform: rotate(360deg); } }

.pl-title {
  margin: 0;
  font-size: 1.05rem;
  color: #f1f5f9;
}

.pl-link {
  font-size: 0.8rem;
  color: #60a5fa;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pl-meta-info {
  display: flex;
  justify-content: space-between;
  font-size: 0.8rem;
  color: #94a3b8;
  margin-top: 4px;
}

.pl-error-box {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.25);
  color: #f87171;
  padding: 8px;
  border-radius: 6px;
  font-size: 0.78rem;
}

.pl-card-actions {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

/* Modal */
.manager-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
  padding: 20px;
}

.manager-modal {
  background: #141124;
  border: 1px solid rgba(168, 85, 247, 0.3);
  border-radius: 14px;
  width: 100%;
  max-width: 620px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.7);
  display: flex;
  flex-direction: column;
}

.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.modal-head h3 { margin: 0; font-size: 1.25rem; color: #f8fafc; }
.close-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 1.5rem;
  cursor: pointer;
}
.close-btn:hover { color: #fff; }

.modal-body {
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 70vh;
  overflow-y: auto;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.diff-slider {
  accent-color: #a855f7;
  width: 100%;
  cursor: pointer;
}

.slider-scale {
  display: flex;
  justify-content: space-between;
  font-size: 0.72rem;
  color: #64748b;
  margin-top: 2px;
}

.modal-error-msg {
  color: #f87171;
  font-size: 0.85rem;
  background: rgba(239, 68, 68, 0.1);
  padding: 8px 12px;
  border-radius: 6px;
}

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

/* General Buttons */
.anime-btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.2s ease;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #a855f7 0%, #7c3aed 100%);
  color: #ffffff;
  box-shadow: 0 2px 10px rgba(168, 85, 247, 0.35);
}

.btn-primary:hover:not(:disabled) {
  filter: brightness(1.15);
  transform: translateY(-1px);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #e2e8f0;
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.14);
}

.btn-danger-ghost {
  background: transparent;
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 6px 12px;
}

.btn-danger-ghost:hover {
  background: rgba(239, 68, 68, 0.15);
}

.manager-loading-state, .manager-empty-state {
  text-align: center;
  padding: 48px 20px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 2.8rem;
  margin-bottom: 12px;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(168, 85, 247, 0.2);
  border-top-color: #a855f7;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto 12px;
}

@media (max-width: 768px) {
  .manager-header {
    flex-direction: column;
    align-items: stretch;
  }
  .playlist-input-grid {
    grid-template-columns: 1fr;
  }
  .form-row {
    grid-template-columns: 1fr;
  }
  .song-card-row {
    flex-direction: column;
    align-items: stretch;
  }
  .song-card-actions {
    justify-content: flex-end;
  }
}

@media (prefers-reduced-motion: reduce) {
  .music-manager-root,
  .spinner,
  .spin-icon,
  .song-card-row {
    animation: none !important;
    transition: none !important;
  }
}
</style>
