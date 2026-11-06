<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  isAdmin: { type: Boolean, default: false }
})

// Game states: 'lobby' | 'playing' | 'round_result' | 'game_over'
const gameState = ref('lobby')

// Game Configuration
const selectedMode = ref('TEN_QUESTIONS') // 'TEN_QUESTIONS' | 'ENDLESS' | 'DAILY_CHALLENGE' | 'BLIND_RANDOM' | 'CATEGORY'
const selectedTarget = ref('TITLE') // 'TITLE' | 'ARTIST'
const selectedPoolId = ref('')
const selectedDifficulty = ref('')
const selectedTimeLimit = ref(20) // 15, 20, 30 seconds
const questionCountInput = ref(10)

// Data
const availablePools = ref([])
const sessionId = ref(null)
const currentQuestion = ref(null)
const lastResult = ref(null)
const summary = ref(null)

// Gameplay runtime state
const playerAnswer = ref('')
const answerFeedback = ref(null) // { status, message, earned, ... }
const hintsUnlocked = ref([])
const isSubmitting = ref(false)
const timeRemaining = ref(20)
const totalTime = ref(20)
const timerInterval = ref(null)
const questionStartTime = ref(0)
const currentAudio = ref(null)
const isAudioPlaying = ref(false)

// Streak & Score
const currentScore = ref(0)
const currentStreak = ref(0)

// Load Pools
async function loadPools() {
  try {
    const res = await fetch('/api/quiz/pools')
    if (res.ok) {
      availablePools.value = await res.json()
    }
  } catch (e) {}
}

// Start Game
async function startGame() {
  try {
    const payload = {
      mode: selectedMode.value,
      targetType: selectedTarget.value,
      timeLimit: Number(selectedTimeLimit.value),
      poolId: selectedPoolId.value ? Number(selectedPoolId.value) : null,
      difficultyTier: selectedDifficulty.value ? Number(selectedDifficulty.value) : null,
      questionCount: selectedMode.value === 'ENDLESS' ? 999 : Number(questionCountInput.value)
    }

    const res = await fetch('/api/quiz/game/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    if (!res.ok) throw new Error('Không thể khởi tạo trận đấu Quiz')
    const data = await res.json()

    sessionId.value = data.sessionId
    currentScore.value = 0
    currentStreak.value = 0
    setupQuestion(data.question)
    gameState.value = 'playing'
  } catch (err) {
    alert(err.message)
  }
}

// Setup a new question
function setupQuestion(q) {
  if (q.isFinished) {
    summary.value = q.summary
    gameState.value = 'game_over'
    stopAudio()
    stopTimer()
    return
  }

  currentQuestion.value = q
  playerAnswer.value = ''
  answerFeedback.value = null
  hintsUnlocked.value = []
  totalTime.value = q.timeLimit || 20
  timeRemaining.value = totalTime.value
  questionStartTime.value = Date.now()
  gameState.value = 'playing'

  startTimer()
  playAudio(q.audioUrl)
}

// Timer Logic
function startTimer() {
  stopTimer()
  timerInterval.value = setInterval(() => {
    if (timeRemaining.value > 0) {
      timeRemaining.value -= 1
    } else {
      // Time is up!
      handleTimeUp()
    }
  }, 1000)
}

function stopTimer() {
  if (timerInterval.value) {
    clearInterval(timerInterval.value)
    timerInterval.value = null
  }
}

// Audio Playback
function playAudio(url) {
  stopAudio()
  if (!url) return

  // If YouTube URL and not direct MP3
  if (url.includes('youtube.com') || url.includes('youtu.be')) {
    isAudioPlaying.value = true
    return
  }

  try {
    const audio = new Audio(url)
    audio.play().then(() => {
      isAudioPlaying.value = true
    }).catch(() => {
      isAudioPlaying.value = false
    })
    audio.onended = () => { isAudioPlaying.value = false }
    currentAudio.value = audio
  } catch (e) {
    isAudioPlaying.value = false
  }
}

function stopAudio() {
  if (currentAudio.value) {
    currentAudio.value.pause()
    currentAudio.value = null
  }
  isAudioPlaying.value = false
}

// Submit Answer
async function submitAnswer() {
  const ans = playerAnswer.value.trim()
  if (!ans || isSubmitting.value || !sessionId.value) return

  isSubmitting.value = true
  const timeTakenMs = Date.now() - questionStartTime.value

  try {
    const res = await fetch(`/api/quiz/game/${sessionId.value}/answer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        questionId: currentQuestion.value.questionId,
        answer: ans,
        timeTakenMs
      })
    })

    if (!res.ok) throw new Error('Lỗi gửi đáp án')
    const data = await res.json()

    if (data.status === 'ALMOST') {
      // Near match: show hint without locking round
      answerFeedback.value = {
        type: 'almost',
        message: data.message || 'Gần đúng rồi! Hãy kiểm tra lại lỗi chính tả.'
      }
      isSubmitting.value = false
      return
    }

    // Question resolved: correct or wrong
    stopTimer()
    currentScore.value = data.currentScore
    currentStreak.value = data.currentStreak
    lastResult.value = data

    if (data.isFinished) {
      summary.value = data.summary
    }

    gameState.value = 'round_result'
  } catch (err) {
    alert(err.message)
  } finally {
    isSubmitting.value = false
  }
}

// Skip Question
async function skipQuestion() {
  if (!sessionId.value || !currentQuestion.value) return
  stopTimer()

  try {
    const res = await fetch(`/api/quiz/game/${sessionId.value}/skip`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ questionId: currentQuestion.value.questionId })
    })

    if (res.ok) {
      const data = await res.json()
      lastResult.value = data
      currentStreak.value = 0
      if (data.isFinished) {
        summary.value = data.summary
      }
      gameState.value = 'round_result'
    }
  } catch (e) {}
}

function handleTimeUp() {
  skipQuestion()
}

// Request Hint
async function requestHint(type) {
  if (!sessionId.value || !currentQuestion.value) return
  try {
    const res = await fetch(`/api/quiz/game/${sessionId.value}/hint`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        questionId: currentQuestion.value.questionId,
        hintType: type
      })
    })

    if (res.ok) {
      const data = await res.json()
      hintsUnlocked.value.push(data.hintText)
    }
  } catch (e) {}
}

// Next Question
async function nextQuestion() {
  if (lastResult.value?.isFinished) {
    gameState.value = 'game_over'
    return
  }

  try {
    const res = await fetch(`/api/quiz/game/${sessionId.value}/next`)
    if (res.ok) {
      const data = await res.json()
      setupQuestion(data)
    }
  } catch (e) {
    alert('Không thể tải câu hỏi tiếp theo')
  }
}

// Performance Grade
const performanceGrade = computed(() => {
  if (!summary.value) return 'A'
  const acc = summary.value.accuracyPercent
  if (acc >= 90) return 'S'
  if (acc >= 75) return 'A'
  if (acc >= 50) return 'B'
  return 'C'
})

onMounted(() => {
  loadPools()
})

onUnmounted(() => {
  stopTimer()
  stopAudio()
})
</script>

<template>
  <div class="quiz-arena-root">
    <!-- ARENA BANNER HEADER -->
    <div class="arena-top-banner">
      <div class="banner-title">
        <span class="arena-pill">BETA 2.0</span>
        <h2>⚡ Music Quiz: Dark Anime Arena</h2>
      </div>
      <div class="banner-scores" v-if="gameState === 'playing' || gameState === 'round_result'">
        <div class="score-chip">
          <span class="chip-label">ĐIỂM SỐ</span>
          <span class="chip-value gold">{{ currentScore }}</span>
        </div>
        <div class="score-chip">
          <span class="chip-label">STREAK</span>
          <span class="chip-value purple">🔥 {{ currentStreak }}</span>
        </div>
      </div>
    </div>

    <!-- 1. LOBBY / SETUP SCREEN -->
    <div v-if="gameState === 'lobby'" class="arena-lobby-panel">
      <div class="lobby-welcome">
        <h3>CHỌN CHẾ ĐỘ THỬ THÁCH</h3>
        <p class="muted">Lắng nghe giai điệu và đoán tên bài hát hoặc nghệ sĩ nhanh nhất có thể để giành điểm First Blood và Speed Bonus!</p>
      </div>

      <!-- Mode Selection Grid -->
      <div class="modes-grid">
        <div 
          class="mode-card" 
          :class="{ active: selectedMode === 'TEN_QUESTIONS' }"
          @click="selectedMode = 'TEN_QUESTIONS'"
        >
          <div class="mode-icon">🎯</div>
          <h4>10 Câu Hỏi</h4>
          <p>Thể thức xếp hạng tiêu chuẩn. 10 bài hát ngẫu nhiên tính điểm tốc độ.</p>
        </div>

        <div 
          class="mode-card" 
          :class="{ active: selectedMode === 'ENDLESS' }"
          @click="selectedMode = 'ENDLESS'"
        >
          <div class="mode-icon">♾️</div>
          <h4>Endless Vô Tận</h4>
          <p>Chơi liên tục không giới hạn số câu. Thử thách độ bền và trí nhớ!</p>
        </div>

        <div 
          class="mode-card" 
          :class="{ active: selectedMode === 'DAILY_CHALLENGE' }"
          @click="selectedMode = 'DAILY_CHALLENGE'"
        >
          <div class="mode-icon">📅</div>
          <h4>Daily Challenge</h4>
          <p>5 bài hát đặc biệt của hôm nay. Cùng một đề bài cho tất cả game thủ.</p>
        </div>

        <div 
          class="mode-card" 
          :class="{ active: selectedMode === 'BLIND_RANDOM' }"
          @click="selectedMode = 'BLIND_RANDOM'"
        >
          <div class="mode-icon">🙈</div>
          <h4>Đoán Mù (Blind)</h4>
          <p>Che hoàn toàn ảnh bìa và thông tin gợi ý. Chỉ dùng đôi tai để chiến thắng.</p>
        </div>

        <div 
          class="mode-card" 
          :class="{ active: selectedMode === 'CATEGORY' }"
          @click="selectedMode = 'CATEGORY'"
        >
          <div class="mode-icon">🗂️</div>
          <h4>Mystery Pool</h4>
          <p>Chọn riêng pool bài hát theo chủ đề (Anime, J-Pop, Vocaloid...).</p>
        </div>
      </div>

      <!-- Settings Row -->
      <div class="lobby-options-card">
        <div class="option-item">
          <label>Mục tiêu đoán:</label>
          <div class="pill-group">
            <button 
              class="pill-choice-btn" 
              :class="{ active: selectedTarget === 'TITLE' }"
              @click="selectedTarget = 'TITLE'"
            >
              🎵 Đoán Tên bài hát
            </button>
            <button 
              class="pill-choice-btn" 
              :class="{ active: selectedTarget === 'ARTIST' }"
              @click="selectedTarget = 'ARTIST'"
            >
              👤 Đoán Nghệ sĩ
            </button>
          </div>
        </div>

        <div class="option-item">
          <label>Thời gian mỗi câu:</label>
          <div class="pill-group">
            <button 
              v-for="t in [15, 20, 30]" 
              :key="t" 
              class="pill-choice-btn"
              :class="{ active: selectedTimeLimit === t }"
              @click="selectedTimeLimit = t"
            >
              ⏱️ {{ t }}s
            </button>
          </div>
        </div>

        <div v-if="selectedMode === 'CATEGORY' || availablePools.length > 0" class="option-item">
          <label>Mystery Pool:</label>
          <select v-model="selectedPoolId" class="arena-select">
            <option value="">Toàn bộ kho nhạc</option>
            <option v-for="p in availablePools" :key="p.id" :value="p.id">
              {{ p.name }} ({{ p.songCount }} bài)
            </option>
          </select>
        </div>
      </div>

      <!-- Start Button -->
      <div class="start-btn-row">
        <button class="arena-hero-start-btn" @click="startGame">
          ⚔️ BẮT ĐẦU TRẬN ĐẤU
        </button>
      </div>
    </div>

    <!-- 2. GAMEPLAY SCREEN -->
    <div v-else-if="gameState === 'playing' || gameState === 'round_result'" class="arena-play-container">
      <!-- Progress Bar & Timer -->
      <div class="play-meta-bar">
        <span class="q-counter">
          CÂU HỎI {{ currentQuestion?.questionNumber }} / {{ currentQuestion?.totalQuestions }}
        </span>
        <div class="meta-right">
          <span class="tier-pill">{{ currentQuestion?.difficultyTier }}</span>
          <span class="genre-pill">🏷️ {{ currentQuestion?.genre }}</span>
        </div>
      </div>

      <!-- Timer Bar -->
      <div class="arena-timer-track">
        <div 
          class="arena-timer-fill" 
          :class="{ urgent: timeRemaining <= 5 }"
          :style="{ width: ((timeRemaining / totalTime) * 100) + '%' }"
        ></div>
        <span class="timer-countdown-text">{{ timeRemaining }}s</span>
      </div>

      <!-- Center Stage: Vinyl Disc Visualizer & Cover -->
      <div class="arena-stage-box">
        <div class="vinyl-disc-wrapper" :class="{ spinning: isAudioPlaying }">
          <div class="vinyl-grooves">
            <div class="vinyl-center-art">
              <img 
                v-if="!currentQuestion?.isBlind && currentQuestion?.artworkUrl" 
                :src="currentQuestion?.artworkUrl" 
                alt="Artwork" 
                class="disc-art-img"
              />
              <div v-else class="disc-art-placeholder">
                <span>{{ currentQuestion?.isBlind ? '❓' : '🎵' }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Audio Waves Visualizer -->
        <div class="sound-wave-bars" :class="{ active: isAudioPlaying }">
          <span v-for="n in 12" :key="n" class="bar"></span>
        </div>

        <!-- Blind Mask Notice -->
        <p v-if="currentQuestion?.isBlind" class="blind-notice">
          🙈 Chế độ Đoán Mù: Ảnh bìa đã được bảo mật!
        </p>
      </div>

      <!-- Result Reveal (if in round_result) -->
      <div v-if="gameState === 'round_result'" class="round-result-card" :class="lastResult?.status?.toLowerCase()">
        <div class="result-badge-row">
          <span v-if="lastResult?.status === 'EXACT' || lastResult?.status === 'CORRECT'" class="res-badge win">
            ✨ CHÍNH XÁC! (+{{ lastResult?.earnedPoints }} điểm)
          </span>
          <span v-else class="res-badge fail">
            ❌ HẾT GIỜ HOẶC BỎ QUA
          </span>
        </div>

        <div class="official-answer-reveal">
          <img 
            :src="lastResult?.officialArtwork || 'https://img.youtube.com/vi/' + (lastResult?.officialYoutubeUrl ? lastResult?.officialYoutubeUrl.slice(-11) : '') + '/hqdefault.jpg'" 
            class="revealed-cover" 
            alt="Official" 
          />
          <div class="revealed-text">
            <h3>{{ lastResult?.officialTitle }}</h3>
            <p class="artist-reveal">👤 {{ lastResult?.officialArtist }}</p>
            <small v-if="lastResult?.aliases" class="aliases-reveal">Tên khác: {{ lastResult?.aliases }}</small>
          </div>
        </div>

        <button class="arena-hero-start-btn next-q-btn" @click="nextQuestion">
          {{ lastResult?.isFinished ? '🏆 Xem Tổng Kết Trận Đấu' : 'Câu Tiếp Theo ▶' }}
        </button>
      </div>

      <!-- Answering Controls (if in playing state) -->
      <div v-else class="arena-answer-controls">
        <!-- "Almost" alert -->
        <div v-if="answerFeedback?.type === 'almost'" class="almost-banner">
          <span>⚡ {{ answerFeedback.message }}</span>
        </div>

        <div class="input-action-row">
          <input 
            type="text" 
            v-model="playerAnswer"
            :placeholder="currentQuestion?.targetType === 'ARTIST' ? 'Nhập tên nghệ sĩ hoặc nhóm nhạc...' : 'Nhập tên bài hát...'"
            class="arena-guess-input"
            autofocus
            @keydown.enter="submitAnswer"
          />
          <button class="submit-guess-btn" :disabled="isSubmitting || !playerAnswer.trim()" @click="submitAnswer">
            GỬI ĐÁP ÁN ↵
          </button>
        </div>

        <!-- Hints & Skip Row -->
        <div class="hints-and-tools-row">
          <div class="hint-buttons-group">
            <span class="hint-label">Gợi ý (-25đ):</span>
            <button class="tool-btn" @click="requestHint('FIRST_LETTER')">💡 Chữ đầu</button>
            <button class="tool-btn" @click="requestHint('LENGTH_INFO')">📏 Số ký tự</button>
            <button class="tool-btn" @click="requestHint('ARTIST_OR_GENRE')">
              {{ currentQuestion?.targetType === 'ARTIST' ? '🏷️ Thể loại' : '👤 Nghệ sĩ' }}
            </button>
          </div>

          <button class="skip-btn" @click="skipQuestion">Bỏ qua ⏭</button>
        </div>

        <!-- Display Unlocked Hints -->
        <div v-if="hintsUnlocked.length > 0" class="unlocked-hints-list">
          <div v-for="(h, idx) in hintsUnlocked" :key="idx" class="hint-bubble">
            <span>✨ {{ h }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. GAME OVER / SUMMARY SCREEN -->
    <div v-else-if="gameState === 'game_over'" class="arena-summary-panel">
      <div class="grade-circle" :class="'grade-' + performanceGrade">
        <span class="grade-letter">{{ performanceGrade }}</span>
        <span class="grade-label">XẾP HẠNG</span>
      </div>

      <h2>TRẬN ĐẤU KẾT THÚC!</h2>
      <p class="muted">Thành tích của bạn đã được ghi nhận vào hệ thống.</p>

      <div class="summary-stats-grid">
        <div class="stat-card">
          <span class="stat-val gold">{{ summary?.finalScore }}</span>
          <span class="stat-lbl">TỔNG ĐIỂM</span>
        </div>
        <div class="stat-card">
          <span class="stat-val cyan">{{ summary?.correctCount }} / {{ summary?.totalQuestions }}</span>
          <span class="stat-lbl">SỐ CÂU ĐÚNG</span>
        </div>
        <div class="stat-card">
          <span class="stat-val purple">{{ summary?.accuracyPercent }}%</span>
          <span class="stat-lbl">ĐỘ CHÍNH XÁC</span>
        </div>
        <div class="stat-card">
          <span class="stat-val flame">🔥 {{ summary?.maxStreak }}</span>
          <span class="stat-lbl">MAX STREAK</span>
        </div>
      </div>

      <div class="summary-actions-row">
        <button class="arena-hero-start-btn" @click="startGame">
          🔄 Chơi lại ngay
        </button>
        <button class="arena-secondary-btn" @click="gameState = 'lobby'">
          🏛️ Đổi chế độ chơi
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.quiz-arena-root {
  display: flex;
  flex-direction: column;
  gap: 20px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Banner */
.arena-top-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  background: linear-gradient(135deg, rgba(20, 16, 40, 0.9) 0%, rgba(10, 8, 22, 0.95) 100%);
  border: 1px solid rgba(168, 85, 247, 0.3);
  border-radius: 14px;
  padding: 18px 24px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
}

.banner-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.arena-pill {
  background: linear-gradient(135deg, #06b6d4, #3b82f6);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 6px;
  letter-spacing: 1px;
}

.banner-title h2 {
  margin: 0;
  font-size: 1.45rem;
  color: #f8fafc;
  letter-spacing: 0.5px;
}

.banner-scores {
  display: flex;
  gap: 14px;
}

.score-chip {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 6px 14px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.chip-label { font-size: 0.7rem; color: #94a3b8; font-weight: 700; }
.chip-value { font-size: 1.25rem; font-weight: 800; }
.chip-value.gold { color: #fbbf24; }
.chip-value.purple { color: #c084fc; }

/* 1. Lobby */
.arena-lobby-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.lobby-welcome h3 {
  margin: 0 0 6px;
  color: #f8fafc;
  font-size: 1.3rem;
  letter-spacing: 0.5px;
}

.modes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 16px;
}

.mode-card {
  background: rgba(22, 19, 42, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px 16px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.mode-card:hover {
  background: rgba(36, 30, 68, 0.8);
  border-color: rgba(168, 85, 247, 0.4);
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(168, 85, 247, 0.2);
}

.mode-card.active {
  background: linear-gradient(135deg, rgba(88, 28, 135, 0.5) 0%, rgba(30, 27, 75, 0.8) 100%);
  border-color: #a855f7;
  box-shadow: 0 0 20px rgba(168, 85, 247, 0.35);
}

.mode-icon {
  font-size: 2.2rem;
  margin-bottom: 10px;
}

.mode-card h4 {
  margin: 0 0 6px;
  font-size: 1.05rem;
  color: #f8fafc;
}

.mode-card p {
  margin: 0;
  font-size: 0.8rem;
  color: #94a3b8;
  line-height: 1.4;
}

.lobby-options-card {
  background: rgba(18, 15, 34, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.option-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-item label {
  font-size: 0.85rem;
  font-weight: 700;
  color: #94a3b8;
}

.pill-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pill-choice-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #cbd5e1;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.pill-choice-btn:hover { background: rgba(255, 255, 255, 0.12); }
.pill-choice-btn.active {
  background: rgba(168, 85, 247, 0.25);
  border-color: #a855f7;
  color: #f8fafc;
}

.arena-select {
  background: rgba(10, 8, 20, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: #f8fafc;
  padding: 8px 14px;
  font-size: 0.9rem;
  outline: none;
}

.start-btn-row {
  display: flex;
  justify-content: center;
  margin-top: 10px;
}

.arena-hero-start-btn {
  background: linear-gradient(135deg, #a855f7 0%, #06b6d4 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 14px 38px;
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: 1px;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: 0 4px 20px rgba(168, 85, 247, 0.4);
}

.arena-hero-start-btn:hover {
  filter: brightness(1.15);
  transform: translateY(-2px);
  box-shadow: 0 6px 28px rgba(6, 182, 212, 0.5);
}

/* 2. Gameplay */
.arena-play-container {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.play-meta-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.q-counter {
  font-size: 0.95rem;
  font-weight: 800;
  color: #f8fafc;
  letter-spacing: 1px;
}

.meta-right {
  display: flex;
  gap: 10px;
}

.tier-pill {
  background: rgba(168, 85, 247, 0.2);
  color: #d8b4fe;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  text-transform: uppercase;
}

.genre-pill {
  background: rgba(255, 255, 255, 0.08);
  color: #cbd5e1;
  font-size: 0.75rem;
  padding: 3px 8px;
  border-radius: 6px;
}

/* Timer */
.arena-timer-track {
  position: relative;
  background: rgba(255, 255, 255, 0.08);
  height: 14px;
  border-radius: 8px;
  overflow: hidden;
}

.arena-timer-fill {
  height: 100%;
  background: linear-gradient(90deg, #06b6d4, #a855f7);
  transition: width 1s linear;
}

.arena-timer-fill.urgent {
  background: linear-gradient(90deg, #ef4444, #f59e0b);
  animation: pulseTimer 0.6s infinite alternate;
}

@keyframes pulseTimer {
  from { filter: brightness(1); }
  to { filter: brightness(1.4); }
}

.timer-countdown-text {
  position: absolute;
  right: 8px;
  top: -20px;
  font-size: 0.85rem;
  font-weight: 800;
  color: #e2e8f0;
}

/* Vinyl Stage */
.arena-stage-box {
  background: radial-gradient(circle at center, rgba(35, 28, 64, 0.7) 0%, rgba(13, 11, 24, 0.95) 100%);
  border: 1px solid rgba(168, 85, 247, 0.25);
  border-radius: 14px;
  padding: 32px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  box-shadow: inset 0 0 40px rgba(0, 0, 0, 0.6);
}

.vinyl-disc-wrapper {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  background: #111;
  padding: 6px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.8), 0 0 15px rgba(168, 85, 247, 0.3);
}

.vinyl-disc-wrapper.spinning {
  animation: spinDisc 4s linear infinite;
}

@keyframes spinDisc {
  100% { transform: rotate(360deg); }
}

.vinyl-grooves {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2px dashed rgba(255, 255, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
}

.vinyl-center-art {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid #222;
  background: #0f0c1b;
}

.disc-art-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.disc-art-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.8rem;
}

.sound-wave-bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 28px;
}

.sound-wave-bars .bar {
  width: 4px;
  height: 6px;
  background: #a855f7;
  border-radius: 2px;
  transition: height 0.2s;
}

.sound-wave-bars.active .bar {
  animation: waveAnim 0.7s infinite alternate ease-in-out;
}

.sound-wave-bars.active .bar:nth-child(2n) { animation-delay: 0.15s; height: 16px; background: #06b6d4; }
.sound-wave-bars.active .bar:nth-child(3n) { animation-delay: 0.3s; height: 24px; background: #fbbf24; }
.sound-wave-bars.active .bar:nth-child(4n) { animation-delay: 0.45s; height: 10px; }

@keyframes waveAnim {
  0% { height: 4px; }
  100% { height: 26px; }
}

.blind-notice {
  margin: 0;
  font-size: 0.85rem;
  color: #fbbf24;
}

/* Answering Controls */
.arena-answer-controls {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.almost-banner {
  background: rgba(245, 158, 11, 0.2);
  border: 1px solid rgba(245, 158, 11, 0.4);
  color: #fbbf24;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 700;
  animation: shake 0.3s ease;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}

.input-action-row {
  display: flex;
  gap: 10px;
}

.arena-guess-input {
  flex: 1;
  background: rgba(14, 11, 26, 0.9);
  border: 2px solid rgba(168, 85, 247, 0.4);
  border-radius: 10px;
  padding: 14px 18px;
  color: #f8fafc;
  font-size: 1.1rem;
  font-weight: 600;
  outline: none;
  transition: all 0.2s;
}

.arena-guess-input:focus {
  border-color: #06b6d4;
  box-shadow: 0 0 16px rgba(6, 182, 212, 0.3);
}

.submit-guess-btn {
  background: linear-gradient(135deg, #a855f7 0%, #6366f1 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 0 24px;
  font-size: 1rem;
  font-weight: 800;
  cursor: pointer;
  transition: filter 0.2s;
}

.submit-guess-btn:hover:not(:disabled) {
  filter: brightness(1.2);
}

.hints-and-tools-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.hint-buttons-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.hint-label {
  font-size: 0.8rem;
  color: #94a3b8;
}

.tool-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #cbd5e1;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 0.8rem;
  cursor: pointer;
}

.tool-btn:hover { background: rgba(255, 255, 255, 0.12); }

.skip-btn {
  background: transparent;
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #f87171;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 0.82rem;
  cursor: pointer;
}

.skip-btn:hover { background: rgba(239, 68, 68, 0.15); }

.unlocked-hints-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.hint-bubble {
  background: rgba(168, 85, 247, 0.12);
  border-left: 3px solid #a855f7;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 0.85rem;
  color: #e2e8f0;
}

/* Round Result Card */
.round-result-card {
  background: rgba(20, 16, 38, 0.9);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  animation: fadeIn 0.25s ease;
}

.round-result-card.exact, .round-result-card.correct {
  border-color: rgba(16, 185, 129, 0.4);
  box-shadow: 0 0 24px rgba(16, 185, 129, 0.2);
}

.round-result-card.skipped, .round-result-card.wrong {
  border-color: rgba(239, 68, 68, 0.4);
}

.res-badge {
  font-size: 1.05rem;
  font-weight: 800;
  padding: 6px 14px;
  border-radius: 8px;
  display: inline-block;
}
.res-badge.win { background: rgba(16, 185, 129, 0.2); color: #34d399; }
.res-badge.fail { background: rgba(239, 68, 68, 0.2); color: #f87171; }

.official-answer-reveal {
  display: flex;
  align-items: center;
  gap: 16px;
}

.revealed-cover {
  width: 72px;
  height: 72px;
  border-radius: 10px;
  object-fit: cover;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.5);
}

.revealed-text h3 {
  margin: 0;
  font-size: 1.3rem;
  color: #f8fafc;
}

.artist-reveal {
  margin: 4px 0 0;
  color: #94a3b8;
  font-size: 0.95rem;
}

.aliases-reveal {
  color: #64748b;
  font-size: 0.8rem;
  display: block;
  margin-top: 2px;
}

.next-q-btn {
  margin-top: 10px;
}

/* 3. Summary */
.arena-summary-panel {
  background: linear-gradient(135deg, rgba(20, 16, 40, 0.9) 0%, rgba(10, 8, 22, 0.95) 100%);
  border: 1px solid rgba(168, 85, 247, 0.3);
  border-radius: 16px;
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 14px;
}

.grade-circle {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 3px solid;
}
.grade-circle.grade-S { border-color: #fbbf24; color: #fbbf24; box-shadow: 0 0 30px rgba(251, 191, 36, 0.4); }
.grade-circle.grade-A { border-color: #a855f7; color: #a855f7; box-shadow: 0 0 25px rgba(168, 85, 247, 0.35); }
.grade-circle.grade-B { border-color: #06b6d4; color: #06b6d4; }
.grade-circle.grade-C { border-color: #94a3b8; color: #94a3b8; }

.grade-letter { font-size: 2.5rem; font-weight: 900; line-height: 1; }
.grade-label { font-size: 0.65rem; font-weight: 800; letter-spacing: 1px; }

.summary-stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  width: 100%;
  max-width: 640px;
  margin: 16px 0;
}

.stat-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-val { font-size: 1.4rem; font-weight: 800; }
.stat-val.gold { color: #fbbf24; }
.stat-val.cyan { color: #06b6d4; }
.stat-val.purple { color: #c084fc; }
.stat-val.flame { color: #f87171; }
.stat-lbl { font-size: 0.7rem; color: #94a3b8; font-weight: 700; margin-top: 4px; }

.summary-actions-row {
  display: flex;
  gap: 14px;
  margin-top: 10px;
}

.arena-secondary-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #cbd5e1;
  padding: 12px 24px;
  border-radius: 10px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.arena-secondary-btn:hover {
  background: rgba(255, 255, 255, 0.15);
}

@media (max-width: 640px) {
  .summary-stats-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
