<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

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

// Seeding & Bracket setup
const seedingMode = ref('custom') // 'custom' (theo thứ tự sắp xếp) | 'shuffle' (ngẫu nhiên)
const createTab = ref('input') // 'input' | 'preview'
const initialOpenOption = ref('all') // 'all' | 'round1' | 'round1_2'

// File upload ref
const fileInput = ref(null)

// 1. Inline Music Player state
const activePlayerSong = ref(null)

// 2. Share & QR state
const showQrModal = ref(false)

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

async function openTournament(id) {
  loading.value = true; error.value = ''
  try {
    tournament.value = await api(`/tournaments/${id}?voterKey=${encodeURIComponent(voterKey)}`)
    selectedRound.value = firstPendingRound()
    page.value = 'rounds'
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
const parsedSongs = computed(() => {
  return songText.value.split(/\r?\n/).map((line, idx) => {
    const raw = line.trim()
    if (!raw) return null
    let link = null
    let songPart = raw
    const pipeIdx = raw.lastIndexOf('|')
    if (pipeIdx > 0) {
      const maybeLnk = raw.substring(pipeIdx + 1).trim()
      if (maybeLnk.startsWith('http://') || maybeLnk.startsWith('https://')) {
        link = maybeLnk
        songPart = raw.substring(0, pipeIdx).trim()
      }
    }
    // Support tab-separated (from Excel/Sheets)
    const tabParts = songPart.split('\t')
    let title = ''
    let artist = ''
    if (tabParts.length >= 2) {
      title = tabParts[0].trim()
      artist = tabParts[1].trim()
      if (tabParts.length >= 3 && !link && tabParts[2].trim().startsWith('http')) {
        link = tabParts[2].trim()
      }
    } else {
      // Strip leading numbering: "1. ", "01 - ", "[1] "
      const clean = songPart.replace(/^\s*(?:\[?\d+\]?[\.\-\)\s]+)/, '').trim()
      const parts = clean.split(/\s+[—–-]\s+/, 2)
      title = parts[0]?.trim() || clean
      artist = parts[1]?.trim() || ''
    }
    return { id: idx, title, artist, link, raw }
  }).filter(Boolean)
})

// Bracket Calculation Helper
const bracketStats = computed(() => {
  const count = parsedSongs.value.length
  if (count < 2) return null
  if (count > 48) {
    const half1 = Math.floor(count / 2)
    const half2 = count - half1
    const matchesRound1 = Math.ceil(half1 / 2)
    const matchesRound2 = Math.ceil(half2 / 2)
    return { count, isSplit: true, half1, half2, matchesRound1, matchesRound2 }
  }
  let size = 1
  while (size < count) size *= 2
  const byes = size - count
  const rounds = Math.log2(size)
  return { count, isSplit: false, size, byes, rounds }
})

// Preview of Initial Pairings (Vòng 1 hoặc Vòng loại 1 & 2)
const previewPairings = computed(() => {
  const songs = [...parsedSongs.value]
  if (songs.length < 2) return []
  const count = songs.length
  const pairs = []
  let pos = 1

  if (count > 48) {
    // Chia 2 vòng loại
    const half1 = Math.floor(count / 2)
    const group1 = songs.slice(0, half1)
    const group2 = songs.slice(half1)

    // Nhóm 1: Vòng loại 1
    for (let i = 0; i < group1.length; i += 2) {
      pairs.push({
        num: pos++,
        roundLabel: 'Vòng loại 1',
        songA: group1[i],
        songB: i + 1 < group1.length ? group1[i + 1] : null,
        isBye: i + 1 >= group1.length,
        indexA: i,
        indexB: i + 1 < group1.length ? i + 1 : -1
      })
    }
    // Nhóm 2: Vòng loại 2
    for (let i = 0; i < group2.length; i += 2) {
      pairs.push({
        num: pos++,
        roundLabel: 'Vòng loại 2',
        songA: group2[i],
        songB: i + 1 < group2.length ? group2[i + 1] : null,
        isBye: i + 1 >= group2.length,
        indexA: half1 + i,
        indexB: i + 1 < group2.length ? half1 + i + 1 : -1
      })
    }
    return pairs
  }

  // Chuẩn <= 48 bài
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
  flash('Đã xáo trộn ngẫu nhiên toàn bộ danh sách bài hát! 🔀')
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
  }
  flash(`Đã nạp bộ mẫu chuẩn ${count} bài!`)
}

// ─── Create Tournament with Seeding & Round Config ───
async function createTournament() {
  error.value = ''
  const songs = parsedSongs.value.map(s => s.raw)
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

async function vote(match, song) {
  if (!tournament.value || match.hasVoted || match.winner || match.closed) return
  error.value = ''
  try {
    tournament.value = await api(`/tournaments/${tournament.value.id}/matches/${match.id}/vote`, {
      method: 'POST', body: JSON.stringify({ voterKey, songId: song.id })
    })
    selectedRound.value = Math.min(selectedRound.value, tournament.value.rounds.length - 1)
    await loadTournaments()
    flash(`Đã ghi nhận vote cho “${song.title}”`)
  } catch (e) {
    error.value = e.message
    await refreshCurrent()
  }
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
    tournament.value = updated
  } catch (_) {}
}

// ─── Round Management (Open / Close Voting / Finalize) ───
async function toggleRoundVoting(roundIndex, currentlyClosed) {
  if (!tournament.value) return
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
  activePlayerSong.value = song
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
const completedMatches = computed(() => (tournament.value?.rounds || []).flatMap(r => r.matches).filter(m => m.winner && m.songA && m.songB).length)
const totalMatches = computed(() => (tournament.value?.rounds || []).flatMap(r => r.matches).filter(m => m.songA && m.songB).length)
const progress = computed(() => totalMatches.value ? Math.round(completedMatches.value / totalMatches.value * 100) : 0)

// World Cup style two-sided Bracket Layout (Nhánh Trái ➔ Chung kết ở Giữa ➔ Nhánh Phải)
const worldCupBracket = computed(() => {
  const rounds = tournament.value?.rounds || []
  if (!rounds.length) return null
  const numRounds = rounds.length
  if (numRounds < 2) {
    return { isSplit: false, rounds }
  }

  const finalRound = rounds[numRounds - 1]
  const leftColumns = []
  const rightColumns = []

  // Các vòng từ 0 đến numRounds - 2 chia 2 nửa: Trái (A) & Phải (B)
  for (let r = 0; r < numRounds - 1; r++) {
    const round = rounds[r]
    const allMatches = round.matches.filter(m => m.songA || m.songB)
    const mid = Math.ceil(allMatches.length / 2)
    const leftMatches = allMatches.slice(0, mid)
    const rightMatches = allMatches.slice(mid)

    leftColumns.push({
      roundIndex: round.index,
      name: round.name,
      closed: round.closed,
      completed: round.completed,
      matches: leftMatches,
      side: 'left'
    })

    rightColumns.push({
      roundIndex: round.index,
      name: round.name,
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

// Polling interval
let liveTimer = null

onMounted(() => {
  loadTournaments()
  checkUrlHash()
  window.addEventListener('hashchange', checkUrlHash)
  liveTimer = setInterval(() => {
    if (tournament.value && tournament.value.active && (page.value === 'rounds' || page.value === 'results')) {
      silentRefresh()
    }
  }, 4000)
})

onUnmounted(() => {
  if (liveTimer) clearInterval(liveTimer)
  window.removeEventListener('hashchange', checkUrlHash)
})
</script>

<template>
  <header class="topbar">
    <div class="brand-mark">♫</div>
    <div class="brand-copy"><h1>Music Tournament</h1><p>Giải đấu bài hát · tìm ra quán quân</p></div>
    <div class="top-spacer"></div><span class="top-slogan">Cùng chọn ra bài hát hay nhất! ♪</span>
    <button class="btn top-btn" @click="exportResults" :disabled="!tournament">⇩ Xuất kết quả</button>
  </header>

  <div class="shell">
    <aside class="sidebar">
      <button v-for="item in [{id:'home',icon:'⌂',label:'Tổng quan & Tạo giải'},{id:'songs',icon:'♫',label:'Danh sách bài hát'},{id:'rounds',icon:'♧',label:'Vòng đấu'},{id:'results',icon:'▥',label:'Kết quả'},{id:'settings',icon:'⚙',label:'Quản trị'}]"
        :key="item.id" class="nav-item" :class="{active:page===item.id}" @click="page=item.id">
        <span>{{ item.icon }}</span>{{ item.label }}
      </button>
      <div class="side-quote">“Âm nhạc không chỉ là giai điệu, mà còn là cảm xúc kết nối chúng ta.”<div class="sound-wave">∿∿∿∿∿ ♡</div></div>
    </aside>

    <main class="main">
      <div class="page-heading">
        <div>
          <p class="eyebrow">MUSIC TOURNAMENT ENGINE / PRO BRACKETS</p>
          <h2>{{ page==='home'?'Tạo & Quản lý giải đấu':page==='songs'?'Danh sách bài hát':page==='rounds'?'Sơ đồ vòng đấu':page==='results'?'Kết quả giải đấu':'Quản trị giải đấu' }}</h2>
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

      <!-- PAGE: HOME (CREATION & TOURNAMENTS) -->
      <section v-if="page==='home'" class="dashboard-grid">
        <div class="panel create-panel advanced-create">
          <div class="section-head">
            <div><span class="mini-icon">✦</span><h3>Tạo giải đấu chuyên nghiệp</h3></div>
            <div class="create-sub-tabs">
              <button class="sub-tab-btn" :class="{active:createTab==='input'}" @click="createTab='input'">📝 1. Nhập bài hát</button>
              <button class="sub-tab-btn" :class="{active:createTab==='preview'}" @click="createTab='preview'" :disabled="parsedSongs.length < 2">
                ⑂ 2. Xếp nhánh & cặp đấu ({{ parsedSongs.length }})
              </button>
            </div>
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
              </div>
              <button v-if="parsedSongs.length > 1" class="btn mini-btn" @click="shuffleSongList" title="Xáo trộn danh sách bài">🔀 Xáo trộn</button>
            </div>

            <label class="textarea-label">
              <span>Danh sách bài hát <span class="muted small">(mỗi dòng 1 bài: Tên — Nghệ sĩ | link YouTube/Spotify hoặc copy từ Excel)</span></span>
              <span v-if="bracketStats" class="bracket-badge" :class="{perfect:!bracketStats.isSplit && bracketStats.byes===0}">
                <template v-if="bracketStats.isSplit">
                  ⚡ {{ bracketStats.count }} bài (>48 bài) ➔ Tự động chia 2 vòng: Vòng loại 1 ({{ bracketStats.half1 }} bài/{{ bracketStats.matchesRound1 }} trận) & Vòng loại 2 ({{ bracketStats.half2 }} bài/{{ bracketStats.matchesRound2 }} trận)
                </template>
                <template v-else>
                  {{ bracketStats.count }} bài ➔ Nhánh {{ bracketStats.size }} ({{ bracketStats.byes === 0 ? 'Chuẩn nhánh, 0 vé vào thẳng' : bracketStats.byes + ' vé vào thẳng' }})
                </template>
              </span>
            </label>
            <textarea v-model="songText" rows="9" placeholder="Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg&#10;Gurenge — LiSA | https://youtu.be/CwkzK-F0Y00&#10;Blue Bird — Ikimono-gakari&#10;Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ"></textarea>

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
              <button class="btn soft" @click="createTab='preview'" :disabled="parsedSongs.length < 2">
                👉 Xem trước & Tùy chỉnh cặp đấu ({{ parsedSongs.length }} bài)
              </button>
              <button class="btn primary" @click="createTournament" :disabled="loading || parsedSongs.length < 2">
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

            <div class="preview-pairings-list">
              <div v-for="pair in previewPairings" :key="pair.num" class="preview-pair-card" :class="{byeCard:pair.isBye}">
                <div class="pair-tag">
                  <span>{{ pair.roundLabel ? pair.roundLabel + ' · ' : '' }}TRẬN {{ pair.num }}</span>
                  <span v-if="pair.isBye" class="bye-label">Vé vào thẳng vòng sau</span>
                </div>
                
                <!-- Song A -->
                <div class="pair-song-row">
                  <span class="seed-num">#{{ pair.indexA + 1 }}</span>
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
            <span class="album small-album">♫</span>
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

            <div v-if="currentRound" class="match-grid">
              <article v-for="match in currentRound.matches.filter(m=>m.songA||m.songB)" :key="match.id" class="match-card" :class="{matchLocked:match.closed}">
                <div class="match-top">
                  <span>TRẬN {{ match.position+1 }}</span>
                  <span v-if="match.winner" class="match-state won">✓ Đã có kết quả</span>
                  <span v-else-if="match.closed" class="match-state closedState">🔒 Đã đóng vote</span>
                  <span v-else class="match-state live">● Đang vote</span>
                </div>
                <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="match-song" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}">
                  <template v-if="song">
                    <span class="song-cover">♫</span>
                    <div class="song-meta">
                      <b>{{ song.title }}</b>
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
          </div>

          <!-- MODE 2: BRACKET TREE VIEW (WORLD CUP 2-WING BRACKET) -->
          <div v-else class="bracket-tree-wrapper world-cup-stadium">
            <div class="stadium-header">
              <span class="stadium-badge">NHÁNH THI ĐẤU</span>
              <h3 class="stadium-title">{{ tournament.name.toUpperCase() }}</h3>
              <div class="stadium-glow"></div>
            </div>

            <div class="bracket-tree-scroll world-cup-tree">
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
                        <span>#{{ match.position + 1 }}</span>
                        <span v-if="match.winner" class="mini-status won">✓ Xong</span>
                        <span v-else-if="match.closed" class="mini-status closed">🔒 Đóng</span>
                        <span v-else class="mini-status live">Vote</span>
                      </div>
                      <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="tree-match-song" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}">
                        <template v-if="song">
                          <div class="tree-song-text">
                            <b :title="song.title">{{ song.title }}</b>
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
                    <div v-for="match in worldCupBracket.centerRound.matches.filter(m=>m.songA||m.songB)" :key="match.id" class="tree-match-card final-glow-card" :class="{treeMatchLocked:match.closed}">
                      <div class="tree-match-header final-header-bar">
                        <span>TRẬN TRANH CÚP VÀNG</span>
                        <span v-if="match.winner" class="mini-status won">🏆 QUÁN QUÂN: {{ match.winner.title }}</span>
                        <span v-else-if="match.closed" class="mini-status closed">🔒 Đóng vote</span>
                        <span v-else class="mini-status live">● ĐANG BÌNH CHỌN</span>
                      </div>
                      <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="tree-match-song final-song-row" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}">
                        <template v-if="song">
                          <div class="tree-song-text">
                            <b :title="song.title">{{ song.title }}</b>
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
                        <span>#{{ match.position + 1 }}</span>
                        <span v-if="match.winner" class="mini-status won">✓ Xong</span>
                        <span v-else-if="match.closed" class="mini-status closed">🔒 Đóng</span>
                        <span v-else class="mini-status live">Vote</span>
                      </div>
                      <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="tree-match-song" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}">
                        <template v-if="song">
                          <div class="tree-song-text">
                            <b :title="song.title">{{ song.title }}</b>
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

          <div v-if="champion" class="champion-banner"><div class="champion-cup">🏆</div><div><small>CHAMPION</small><h3>{{ champion.title }}</h3><p>{{ champion.artist }} · Quán quân của giải đấu!</p></div></div>
          <div v-if="showAdmin" class="admin-tools">
            <h3>🔐 Quản trị toàn giải</h3>
            <div class="admin-actions">
              <button class="btn soft" @click="toggleTournament">{{ tournament.active ? 'Đóng toàn bộ giải' : 'Mở lại giải' }}</button>
            </div>
          </div>
          <button class="admin-toggle" @click="showAdmin=!showAdmin">{{ showAdmin ? 'Ẩn công cụ quản trị' : '⚙ Cài đặt đóng/mở toàn giải' }}</button>
        </template>
      </section>

      <!-- PAGE: RESULTS -->
      <section v-else-if="page==='results'" class="panel">
        <div class="section-head"><div><span class="mini-icon">🏆</span><h3>Bảng kết quả</h3></div><button class="btn primary" @click="exportResults" :disabled="!tournament">⇩ Xuất TXT</button></div>
        <p v-if="!tournament" class="muted">Chọn giải đấu từ trang Tổng quan để xem kết quả.</p>
        <template v-else>
          <div v-if="champion" class="champion-banner"><div class="champion-cup">🏆</div><div><small>QUÁN QUÂN</small><h3>{{ champion.title }}</h3><p>{{ champion.artist }}</p></div></div>
          <div v-for="round in tournament.rounds" :key="round.index" class="result-round"><h4>{{ round.name }}</h4><div v-for="m in round.matches.filter(m=>m.songA||m.songB)" :key="m.id" class="result-match"><div><b>{{ m.songA?.title || 'Chưa xác định' }}</b><small>{{ m.songA?.artist }}</small></div><span class="score">{{ m.votesA }} : {{ m.votesB }}</span><div><b>{{ m.songB?.title || 'Vào thẳng' }}</b><small>{{ m.songB?.artist }}</small></div><strong class="winner-label">{{ m.winner?.title || 'Chưa quyết định' }}</strong></div></div>
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

  <footer class="footer">♫ Made for music fans <span>·</span> Music Tournament Pro 2026</footer>
</template>
