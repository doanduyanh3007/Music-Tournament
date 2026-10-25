<script setup>
import { computed, onMounted, ref } from 'vue'

const API = '/api'
const voterKey = getVoterKey()
const tournaments = ref([])
const tournament = ref(null)
const selectedRound = ref(0)
const page = ref('home')
const loading = ref(false)
const error = ref('')
const notice = ref('')
const tournamentName = ref('Anime & J-Music Cup')
const songText = ref('')
const adminToken = ref('admin-demo-2026')
const endTime = ref('')
const showAdmin = ref(false)

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
  setTimeout(() => { if (notice.value === message) notice.value = '' }, 3200)
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
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
function firstPendingRound() {
  const rounds = tournament.value?.rounds || []
  const pending = rounds.findIndex(r => r.matches.some(m => m.songA && m.songB && !m.winner && !m.closed))
  return pending >= 0 ? pending : Math.max(0, rounds.length - 1)
}
async function createTournament() {
  error.value = ''
  const songLines = songText.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean)
  if (songLines.length < 2) { error.value = 'Nhập ít nhất 2 bài hát, mỗi bài một dòng nhé.'; return }
  loading.value = true
  try {
    const created = await api('/tournaments', { method: 'POST', body: JSON.stringify({ name: tournamentName.value.trim() || 'Music Tournament', songs: songLines }) })
    await loadTournaments()
    await openTournament(created.id)
    flash('Tạo giải thành công! Bắt đầu bình chọn thôi 🎵')
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
async function adminAction(action, roundIndex) {
  if (!tournament.value) return
  try {
    tournament.value = await api(`/tournaments/${tournament.value.id}/admin/rounds/${roundIndex}/${action}`, {
      method: 'POST'
    })
    await loadTournaments(); flash(action === 'close' ? 'Đã đóng vòng đấu.' : 'Đã mở lại vòng đấu.')
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
async function saveEndTime() {
  if (!tournament.value || !endTime.value) { error.value = 'Chọn thời điểm kết thúc trước nhé.'; return }
  try {
    tournament.value = await api(`/tournaments/${tournament.value.id}/admin/end-time`, {
      method: 'PUT',
      body: JSON.stringify({ endTime: new Date(endTime.value).toISOString() })
    })
    flash('Đã lưu thời gian kết thúc.')
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
const currentRound = computed(() => tournament.value?.rounds?.[selectedRound.value] || null)
const champion = computed(() => tournament.value?.champion || null)
const completedMatches = computed(() => (tournament.value?.rounds || []).flatMap(r => r.matches).filter(m => m.winner && m.songA && m.songB).length)
const totalMatches = computed(() => (tournament.value?.rounds || []).flatMap(r => r.matches).filter(m => m.songA && m.songB).length)
const progress = computed(() => totalMatches.value ? Math.round(completedMatches.value / totalMatches.value * 100) : 0)
onMounted(loadTournaments)
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
      <button v-for="item in [{id:'home',icon:'⌂',label:'Tổng quan'},{id:'songs',icon:'♫',label:'Danh sách bài hát'},{id:'rounds',icon:'♧',label:'Vòng đấu'},{id:'results',icon:'▥',label:'Kết quả'},{id:'settings',icon:'⚙',label:'Quản trị'}]"
        :key="item.id" class="nav-item" :class="{active:page===item.id}" @click="page=item.id">
        <span>{{ item.icon }}</span>{{ item.label }}
      </button>
      <div class="side-quote">“Âm nhạc không chỉ là giai điệu, mà còn là cảm xúc kết nối chúng ta.”<div class="sound-wave">∿∿∿∿∿ ♡</div></div>
    </aside>

    <main class="main">
      <div class="page-heading">
        <div>
          <p class="eyebrow">MUSIC COMMUNITY / VOTE TOGETHER</p>
          <h2>{{ page==='home'?'Chào mừng đến giải đấu':page==='songs'?'Danh sách bài hát':page==='rounds'?'Sơ đồ vòng đấu':page==='results'?'Kết quả giải đấu':'Quản trị giải đấu' }}</h2>
          <p class="muted">Vote một lần mỗi cặp; quản trị viên chốt vòng theo tổng phiếu.</p>
        </div>
        <span class="status-pill" :class="{off:tournament&&!tournament.active}">{{ tournament ? (tournament.active?'● Đang mở vote':'● Giải đã đóng') : '● Sẵn sàng' }}</span>
      </div>

      <div v-if="error" class="alert error"><span>⚠</span>{{ error }}<button @click="error=''">×</button></div>
      <div v-if="notice" class="alert success"><span>✓</span>{{ notice }}<button @click="notice=''">×</button></div>

      <section v-if="page==='home'" class="dashboard-grid">
        <div class="panel create-panel">
          <div class="section-head"><div><span class="mini-icon">✦</span><h3>Tạo giải đấu mới</h3></div><span class="tag">NEW TOURNAMENT</span></div>
          <p class="muted">Tạo giải riêng với bất kỳ số lượng bài hát nào. Hệ thống tự chia nhánh loại trực tiếp.</p>
          <label>Tên giải đấu</label><input v-model="tournamentName" placeholder="Ví dụ: Anime Song Championship">
          <label>Danh sách bài hát <span class="muted small">(mỗi dòng: Tên — Nghệ sĩ | link nhạc)</span></label>
          <textarea v-model="songText" rows="8" placeholder="Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg&#10;Gurenge — LiSA | https://youtu.be/CwkzK-F0Y00&#10;Blue Bird — Ikimono-gakari&#10;Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ"></textarea>
          <div class="form-foot"><span class="muted small">{{ songText.split(/\r?\n/).filter(s=>s.trim()).length }} bài đã nhập · Link nhạc là tùy chọn</span><button class="btn primary" @click="createTournament" :disabled="loading">{{ loading?'Đang tạo…':'＋ Tạo giải đấu' }}</button></div>
          <button class="text-btn" @click="songText='Sparkle — RADWIMPS | https://youtu.be/a2GujJZfXpg\nGurenge — LiSA | https://youtu.be/CwkzK-F0Y00\nBlue Bird — Ikimono-gakari | https://youtu.be/bM3OAPfrjzk\nKaikai Kitan — Eve | https://youtu.be/K_7To_y9IAM\nIdol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ\nUnravel — TK from 凛として時雨 | https://youtu.be/7aMOurgDB-o\nSilhouette — KANA-BOON | https://youtu.be/dlFA0Zq1k2A\nAgain — YUI | https://youtu.be/2DYYVp4QXew'">Điền thử danh sách anime mẫu ↗</button>
        </div>
        <div class="panel info-panel">
          <div class="section-head"><div><span class="mini-icon violet">▦</span><h3>Tổng quan</h3></div></div>
          <div class="stat"><span>Giải đấu đã tạo</span><b>{{ tournaments.length }}</b></div>
          <div class="stat"><span>Giải đang mở</span><b>{{ tournaments.filter(t=>t.active).length }}</b></div>
          <div class="stat"><span>Thể thức</span><b>Loại trực tiếp</b></div>
          <div class="stat"><span>Cách tính</span><b>Phiếu vote</b></div>
          <div class="tip-box"><span>💡</span><p><b>Mẹo nhỏ</b><br>48 bài sẽ có 16 bài được vào thẳng vòng 32. Khi quản trị viên đóng vòng, bài nhiều phiếu hơn sẽ đi tiếp tự động.</p></div>
        </div>
        <div class="panel tournament-list-panel">
          <div class="section-head"><div><span class="mini-icon">♫</span><h3>Giải đấu gần đây</h3></div><button class="text-btn" @click="loadTournaments">↻ Làm mới</button></div>
          <div v-if="!tournaments.length" class="empty-state"><div class="empty-note">♫</div><b>Chưa có giải đấu nào</b><p>Tạo giải đầu tiên của bạn ở khung bên trái nhé.</p></div>
          <button v-for="t in tournaments" :key="t.id" class="tournament-row" @click="openTournament(t.id)">
            <span class="album">♫</span><span class="tournament-info"><b>{{ t.name }}</b><small>{{ t.songCount }} bài · {{ t.completedMatches }} trận đã có kết quả</small></span><span class="row-status" :class="{closed:!t.active}">{{ t.active?'Đang mở':'Đã đóng' }}</span><span>›</span>
          </button>
        </div>
      </section>

      <section v-else-if="page==='songs'" class="panel">
        <div class="section-head"><div><span class="mini-icon">♫</span><h3>{{ tournament?.name || 'Chọn một giải đấu' }}</h3></div><button class="btn soft" @click="page='home'">＋ Tạo giải mới</button></div>
        <p v-if="!tournament" class="muted">Chọn giải đấu từ trang Tổng quan để xem danh sách bài hát.</p>
        <div v-else class="song-grid">
          <div v-for="(song,i) in tournament.songs" :key="song.id" class="library-song"><span class="song-index">{{ String(i+1).padStart(2,'0') }}</span><span class="album small-album">♫</span><div><b>{{ song.title }}</b><small>{{ song.artist || 'Chưa nhập nghệ sĩ' }}</small></div><a v-if="song.link" :href="song.link" target="_blank" class="btn song-link-btn" title="Nghe nhạc">▶ Nghe</a></div>
        </div>
      </section>

      <section v-else-if="page==='rounds'" class="panel bracket-panel">
        <div class="section-head"><div><span class="mini-icon violet">♧</span><h3>{{ tournament?.name || 'Sơ đồ vòng đấu' }}</h3></div><div class="head-actions"><button class="btn soft" @click="refreshCurrent" :disabled="!tournament">↻ Làm mới</button><button class="btn primary" @click="page='home'">＋ Tạo giải</button></div></div>
        <p v-if="!tournament" class="muted">Hãy chọn một giải từ trang Tổng quan.</p>
        <template v-else>
          <div class="tournament-summary"><div><span class="muted small">TỔNG SỐ BÀI</span><b>{{ tournament.songs.length }}</b></div><div><span class="muted small">TIẾN ĐỘ</span><b>{{ progress }}%</b></div><div class="summary-progress"><div class="progress-track"><i :style="{width:progress+'%'}"></i></div><span class="muted small">{{ completedMatches }}/{{ totalMatches }} trận có kết quả</span></div></div>
          <div class="round-tabs"><button v-for="(round,i) in tournament.rounds" :key="round.index" :class="{active:selectedRound===i}" @click="selectedRound=i"><b>{{ round.name }}</b><small>{{ round.matches.filter(m=>m.songA||m.songB).length }} cặp</small></button></div>
          <div v-if="currentRound" class="match-grid">
            <article v-for="match in currentRound.matches.filter(m=>m.songA||m.songB)" :key="match.id" class="match-card">
              <div class="match-top"><span>TRẬN {{ match.position+1 }}</span><span v-if="match.winner" class="match-state won">✓ Đã có kết quả</span><span v-else-if="match.closed" class="match-state">Đã đóng</span><span v-else class="match-state live">● Đang vote</span></div>
              <div v-for="(song,side) in [match.songA,match.songB]" :key="side" class="match-song" :class="{winner:match.winner?.id===song?.id,mutedSong:match.winner&&match.winner.id!==song?.id}" >
                <template v-if="song"><span class="song-cover">♫</span><div class="song-meta"><b>{{ song.title }}</b><small>{{ song.artist || 'Chưa nhập nghệ sĩ' }}</small><div class="vote-bar"><i :style="{width:((match['votes'+(side===0?'A':'B')]+1)/(match.votesA+match.votesB+2)*100)+'%'}"></i></div></div><div class="vote-side"><small>{{ match['votes'+(side===0?'A':'B')] }} vote</small><a v-if="song.link" :href="song.link" target="_blank" class="btn song-link-btn" title="Nghe nhạc">▶</a><button class="btn vote-btn" :class="{selected:match.winner?.id===song.id}" :disabled="!tournament.active||match.closed||match.winner||match.hasVoted||!match.songB" @click="vote(match,song)">{{ match.winner?.id===song.id?'✓ Thắng':'Vote' }}</button></div></template>
                <div v-else class="tbd">Đang chờ kết quả vòng trước…</div>
              </div>
              <div v-if="match.hasVoted&&!match.winner" class="match-foot">✓ Bạn đã bình chọn cặp này</div>
              <div v-else-if="match.bye" class="match-foot">Tự động vào vòng tiếp theo</div>
            </article>
          </div>
          <div v-if="champion" class="champion-banner"><div class="champion-cup">🏆</div><div><small>CHAMPION</small><h3>{{ champion.title }}</h3><p>{{ champion.artist }} · Quán quân của giải đấu!</p></div></div>
          <div v-if="showAdmin" class="admin-tools"><h3>🔐 Công cụ quản trị</h3><label>Mã quản trị</label><input v-model="adminToken" type="password" placeholder="Nhập mã quản trị"><div class="admin-actions"><button class="btn soft" @click="adminAction('open',selectedRound)">Mở vòng này</button><button class="btn danger" @click="adminAction('close',selectedRound)">Đóng vòng này</button><button class="btn soft" @click="toggleTournament">{{ tournament.active?'Đóng giải':'Mở giải' }}</button></div></div>
          <button class="admin-toggle" @click="showAdmin=!showAdmin">{{ showAdmin?'Ẩn công cụ quản trị':'🔐 Hiện công cụ quản trị' }}</button>
        </template>
      </section>

      <section v-else-if="page==='results'" class="panel">
        <div class="section-head"><div><span class="mini-icon">🏆</span><h3>Bảng kết quả</h3></div><button class="btn primary" @click="exportResults" :disabled="!tournament">⇩ Xuất TXT</button></div>
        <p v-if="!tournament" class="muted">Chọn giải đấu từ trang Tổng quan để xem kết quả.</p>
        <template v-else>
          <div v-if="champion" class="champion-banner"><div class="champion-cup">🏆</div><div><small>QUÁN QUÂN</small><h3>{{ champion.title }}</h3><p>{{ champion.artist }}</p></div></div>
          <div v-for="round in tournament.rounds" :key="round.index" class="result-round"><h4>{{ round.name }}</h4><div v-for="m in round.matches.filter(m=>m.songA||m.songB)" :key="m.id" class="result-match"><div><b>{{ m.songA?.title || 'Chưa xác định' }}</b><small>{{ m.songA?.artist }}</small></div><span class="score">{{ m.votesA }} : {{ m.votesB }}</span><div><b>{{ m.songB?.title || 'Vào thẳng' }}</b><small>{{ m.songB?.artist }}</small></div><strong class="winner-label">{{ m.winner?.title || 'Chưa quyết định' }}</strong></div></div>
        </template>
      </section>

      <section v-else class="panel settings-panel">
        <div class="section-head"><div><span class="mini-icon violet">⚙</span><h3>Cài đặt & quản trị</h3></div></div>
        <p class="muted">Mã quản trị mặc định cho bản chạy local là <code>admin-demo-2026</code>. Hãy đổi mã trước khi triển khai công khai.</p>
        <label>Mã quản trị</label><input v-model="adminToken" type="password">
        <button class="btn primary" @click="showAdmin=true;page='rounds'">Mở công cụ quản trị vòng đấu</button>
        <div class="tip-box"><span>🛡</span><p>Vote được ghi vào database. Mỗi trình duyệt có một mã voter riêng; để chống gian lận tốt hơn khi mở công khai, nên thêm đăng nhập hoặc xác thực tài khoản.</p></div>
      </section>
    </main>
  </div>
  <footer class="footer">♫ Made for music fans <span>·</span> Local development build</footer>
</template>
