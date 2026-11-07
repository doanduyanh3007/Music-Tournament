<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { winnerEmbed } from './winnerMedia.js'
import { recordingKey } from './songInput.js'

const props = defineProps({
  isAdmin: { type: Boolean, default: false }
})

// Tab: 'solo' | 'multiplayer'
const activeMainTab = ref('solo')

// Game states: 'lobby' | 'playing' | 'round_result' | 'countdown' | 'game_over'
const gameState = ref('lobby')

// --- Player Identity ---
const playerKey = ref(localStorage.getItem('musicTournamentVoterKey') || 'player_' + Math.random().toString(36).substring(2, 9))
const displayName = ref(localStorage.getItem('musicTournamentDisplayName') || 'Game Thủ')

// --- 3-Second Countdown & Intermission State ---
const countdownSeconds = ref(3)
const isCountingDown = ref(false)
let countdownTimer = null
const isReplayingAnswer = ref(false)
const lastCountdownSec = ref(null)

// Audio Fallback state
const audioHasError = ref(false)
const fallbackEmbedUrl = ref(null)

function getResolvedArtwork(res, q) {
  if (res?.officialArtwork && /^https?:\/\//i.test(res.officialArtwork)) return res.officialArtwork
  if (q?.artworkUrl && /^https?:\/\//i.test(q.artworkUrl)) return q.artworkUrl
  const yt = res?.officialYoutubeUrl || q?.youtubeUrl || res?.audioUrl || q?.audioUrl
  if (yt) {
    const key = recordingKey(yt)
    if (key?.startsWith('youtube:')) {
      return `https://img.youtube.com/vi/${key.slice(8)}/hqdefault.jpg`
    }
  }
  return '/logo.png'
}

function playCountdownBeep(isFinal = false) {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.type = 'sine'
    osc.frequency.setValueAtTime(isFinal ? 880 : 440, ctx.currentTime)
    gain.gain.setValueAtTime(0.12, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + (isFinal ? 0.35 : 0.15))
    osc.start()
    osc.stop(ctx.currentTime + (isFinal ? 0.35 : 0.15))
  } catch (e) {}
}

function startSoloCountdown(callback) {
  stopAudio()
  isCountingDown.value = true
  countdownSeconds.value = 3
  gameState.value = 'countdown'
  playCountdownBeep(false)

  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdownSeconds.value -= 1
    if (countdownSeconds.value > 0) {
      playCountdownBeep(false)
    } else {
      playCountdownBeep(true)
      clearInterval(countdownTimer)
      countdownTimer = null
      setTimeout(() => {
        isCountingDown.value = false
        callback()
      }, 350)
    }
  }, 1000)
}

// --- Solo Configuration ---
const selectedMode = ref('TEN_QUESTIONS') // 'TEN_QUESTIONS' | 'ENDLESS' | 'DAILY_CHALLENGE' | 'BLIND_RANDOM' | 'CATEGORY'
const selectedTarget = ref('TITLE') // 'TITLE' | 'ARTIST'
const selectedPoolId = ref('')
const selectedDifficulty = ref('')
const selectedTimeLimit = ref(20)
const questionCountInput = ref(10)

// --- Multiplayer Configuration & State ---
const mpRoomCodeInput = ref('')
const mpRoomNameInput = ref('')
const currentRoom = ref(null) // room state from backend
const isHost = computed(() => currentRoom.value?.isHost)
const mpPollingInterval = ref(null)

// Data
const availablePools = ref([])
const sessionId = ref(null)
const currentQuestion = ref(null)
const lastResult = ref(null)
const summary = ref(null)

// Gameplay runtime state
const playerAnswer = ref('')
const lastSubmittedAnswer = ref('')

function getRomajiTitle(item) {
  if (!item) return ''
  if (item.romajiTitle && item.romajiTitle.trim()) {
    const r = item.romajiTitle.trim()
    const orig = (item.officialTitle || item.title || '').trim()
    if (r.toLowerCase() !== orig.toLowerCase()) {
      // Chỉ hiển thị nếu đã được phiên âm sạch (không còn dính chữ Hán Kanji) và độ dài hợp lý
      if (!/[\u4e00-\u9faf]/.test(r) && r.length < 40) {
        return r
      }
    }
  }
  if (item.aliases && hasJapanese(item.officialTitle || item.title)) {
    const parts = item.aliases.split(/[,;|\n]+/).map(p => p.trim()).filter(Boolean)
    const latin = parts.find(p => !hasJapanese(p) && p.length < 40)
    if (latin) return latin
  }
  return ''
}

function hasJapanese(text) {
  if (!text) return false
  return /[\u3040-\u30ff\u4e00-\u9faf]/.test(text)
}

function isAnswerCorrect(result) {
  if (!result) return false
  return result.status === 'EXACT' || result.status === 'CORRECT' || (result.earnedPoints && result.earnedPoints > 0)
}
const answerFeedback = ref(null)
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

// ==========================================
// SOLO GAME LOGIC
// ==========================================

async function startSoloGame() {
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

    if (!res.ok) throw new Error('Không thể khởi tạo trận đấu Solo')
    const data = await res.json()

    sessionId.value = data.sessionId
    currentScore.value = 0
    currentStreak.value = 0
    startSoloCountdown(() => {
      setupQuestion(data.question)
    })
  } catch (err) {
    alert(err.message)
  }
}

// ==========================================
// MULTIPLAYER LOGIC
// ==========================================

async function createMultiplayerRoom() {
  try {
    const payload = {
      playerKey: playerKey.value,
      displayName: displayName.value,
      roomName: mpRoomNameInput.value.trim() || 'Phòng Quiz Arena của ' + displayName.value,
      mode: selectedMode.value,
      targetType: selectedTarget.value,
      timeLimit: Number(selectedTimeLimit.value),
      questionCount: Number(questionCountInput.value),
      poolId: selectedPoolId.value ? Number(selectedPoolId.value) : null,
      difficultyTier: selectedDifficulty.value ? Number(selectedDifficulty.value) : null
    }

    const res = await fetch('/api/quiz/multiplayer/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    if (!res.ok) throw new Error('Không thể tạo phòng đấu')
    const roomState = await res.json()
    currentRoom.value = roomState
    startMultiplayerPolling(roomState.roomCode)
  } catch (e) {
    alert(e.message)
  }
}

async function joinMultiplayerRoom() {
  const code = mpRoomCodeInput.value.trim().toUpperCase()
  if (!code) {
    alert('Vui lòng nhập mã phòng (6 ký tự)')
    return
  }

  try {
    const res = await fetch(`/api/quiz/multiplayer/${code}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        playerKey: playerKey.value,
        displayName: displayName.value
      })
    })

    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || 'Không thể tham gia phòng')
    }

    const roomState = await res.json()
    currentRoom.value = roomState
    startMultiplayerPolling(code)
  } catch (e) {
    alert(e.message)
  }
}

async function toggleReady() {
  if (!currentRoom.value) return
  try {
    const res = await fetch(`/api/quiz/multiplayer/${currentRoom.value.roomCode}/ready`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerKey: playerKey.value })
    })
    if (res.ok) {
      currentRoom.value = await res.json()
    }
  } catch (e) {}
}

async function startMultiplayerGame() {
  if (!currentRoom.value || !isHost.value) return
  try {
    const res = await fetch(`/api/quiz/multiplayer/${currentRoom.value.roomCode}/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerKey: playerKey.value })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || 'Lỗi bắt đầu trận đấu')
    }
    currentRoom.value = await res.json()
  } catch (e) {
    alert(e.message)
  }
}

async function advanceMultiplayerRound() {
  if (!currentRoom.value || !isHost.value) return
  try {
    const res = await fetch(`/api/quiz/multiplayer/${currentRoom.value.roomCode}/next`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerKey: playerKey.value })
    })
    if (res.ok) {
      currentRoom.value = await res.json()
    }
  } catch (e) {}
}

function startMultiplayerPolling(code) {
  stopMultiplayerPolling()
  mpPollingInterval.value = setInterval(async () => {
    try {
      const res = await fetch(`/api/quiz/multiplayer/${code}/state?playerKey=${encodeURIComponent(playerKey.value)}`)
      if (res.ok) {
        const state = await res.json()
        currentRoom.value = state

        // Handle question audio playback when question changes
        if (state.status === 'PLAYING') {
          if (!currentQuestion.value || currentQuestion.value.questionId !== state.questionId) {
            isReplayingAnswer.value = false
            setupMultiplayerQuestion(state)
          } else {
            timeRemaining.value = state.timeRemaining
          }
        } else if (state.status === 'ROUND_RESULT') {
          stopTimer()
          if (!isReplayingAnswer.value) {
            isReplayingAnswer.value = true
            stopAudio()
            const replayUrl = state.audioUrl || state.officialYoutubeUrl
            playAudio(replayUrl, false)
          }
        } else if (state.status === 'COUNTDOWN') {
          stopAudio()
          stopTimer()
          isReplayingAnswer.value = false
          if (lastCountdownSec.value !== state.countdownRemaining) {
            lastCountdownSec.value = state.countdownRemaining
            playCountdownBeep(state.countdownRemaining === 1)
          }
        }
      }
    } catch (e) {}
  }, 1200)
}

function stopMultiplayerPolling() {
  if (mpPollingInterval.value) {
    clearInterval(mpPollingInterval.value)
    mpPollingInterval.value = null
  }
}

function setupMultiplayerQuestion(state) {
  currentQuestion.value = {
    questionId: state.questionId,
    questionNumber: state.currentQuestionIndex,
    totalQuestions: state.totalQuestions,
    audioUrl: state.audioUrl,
    genre: state.genre,
    difficultyTier: state.difficultyTier,
    targetType: state.targetType
  }
  playerAnswer.value = ''
  answerFeedback.value = null
  hintsUnlocked.value = []
  totalTime.value = state.timeLimit
  timeRemaining.value = state.timeRemaining
  questionStartTime.value = Date.now()

  playAudio(state.audioUrl)
}

function copyRoomCode() {
  if (!currentRoom.value) return
  navigator.clipboard?.writeText(currentRoom.value.roomCode)
  alert(`Đã copy mã phòng: ${currentRoom.value.roomCode}`)
}

function leaveMultiplayerRoom() {
  stopMultiplayerPolling()
  stopAudio()
  currentRoom.value = null
}

// ==========================================
// COMMON GAMEPLAY
// ==========================================

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

function startTimer() {
  stopTimer()
  timerInterval.value = setInterval(() => {
    if (timeRemaining.value > 0) {
      timeRemaining.value -= 1
    } else {
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

function playAudio(url, loop = false) {
  stopAudio()
  audioHasError.value = false
  fallbackEmbedUrl.value = null
  if (!url) {
    applyAudioFallback()
    return
  }
  if (url.includes('youtube.com') || url.includes('youtu.be')) {
    applyYoutubeEmbed(url)
    return
  }
  try {
    const audio = new Audio(url)
    audio.preload = 'auto'
    audio.loop = loop

    audio.onerror = () => {
      console.warn('Audio preview error for:', url)
      applyAudioFallback()
    }

    audio.play().then(() => {
      isAudioPlaying.value = true
    }).catch(err => {
      console.warn('Audio play failed:', err)
      applyAudioFallback()
    })

    audio.onended = () => {
      if (!loop) isAudioPlaying.value = false
    }

    currentAudio.value = audio
  } catch (e) {
    applyAudioFallback()
  }
}

function applyAudioFallback() {
  const yt = currentQuestion.value?.youtubeUrl || lastResult.value?.officialYoutubeUrl || currentRoom.value?.officialYoutubeUrl
  if (yt) {
    applyYoutubeEmbed(yt)
  } else {
    isAudioPlaying.value = false
    audioHasError.value = true
  }
}

function applyYoutubeEmbed(youtubeUrl) {
  const embed = winnerEmbed(youtubeUrl)
  if (embed?.url) {
    fallbackEmbedUrl.value = embed.url
    isAudioPlaying.value = true
  } else {
    isAudioPlaying.value = false
    audioHasError.value = true
  }
}

function stopAudio() {
  if (currentAudio.value) {
    currentAudio.value.pause()
    currentAudio.value = null
  }
  fallbackEmbedUrl.value = null
  isAudioPlaying.value = false
}

function toggleReplayAudio(item) {
  if (isAudioPlaying.value) {
    stopAudio()
  } else {
    const url = item?.audioUrl || item?.officialPreviewUrl || item?.officialYoutubeUrl
    playAudio(url, false)
  }
}

async function submitAnswer() {
  const ans = playerAnswer.value.trim()
  if (!ans || isSubmitting.value) return
  lastSubmittedAnswer.value = ans
  isSubmitting.value = true

  if (activeMainTab.value === 'multiplayer') {
    // Multiplayer Submit
    try {
      const res = await fetch(`/api/quiz/multiplayer/${currentRoom.value.roomCode}/answer`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          playerKey: playerKey.value,
          questionId: currentRoom.value.questionId,
          answer: ans
        })
      })
      if (!res.ok) throw new Error('Lỗi gửi đáp án')
      const data = await res.json()
      if (data.status === 'ALMOST') {
        answerFeedback.value = { type: 'almost', message: data.message }
      } else {
        answerFeedback.value = {
          type: data.status.toLowerCase(),
          message: data.message,
          earned: data.earnedPoints
        }
      }
    } catch (e) {
      alert(e.message)
    } finally {
      isSubmitting.value = false
    }
    return
  }

  // Solo Submit
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
      answerFeedback.value = {
        type: 'almost',
        message: data.message || 'Gần đúng rồi! Hãy kiểm tra lại lỗi chính tả.'
      }
      isSubmitting.value = false
      return
    }

    stopTimer()
    currentScore.value = data.currentScore
    currentStreak.value = data.currentStreak
    lastResult.value = data

    if (data.isFinished) {
      summary.value = data.summary
    }

    gameState.value = 'round_result'

    // REPLAY ANSWER SONG FROM BEGINNING ON INTERMISSION
    stopAudio()
    const replayUrl = data.audioUrl || data.officialYoutubeUrl
    if (replayUrl) {
      playAudio(replayUrl, false)
    }
  } catch (err) {
    alert(err.message)
  } finally {
    isSubmitting.value = false
  }
}

async function skipQuestion() {
  if (activeMainTab.value === 'multiplayer') return
  if (!sessionId.value || !currentQuestion.value) return
  lastSubmittedAnswer.value = playerAnswer.value.trim()
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

      // REPLAY ANSWER SONG FROM BEGINNING ON INTERMISSION
      stopAudio()
      const replayUrl = data.audioUrl || data.officialYoutubeUrl
      if (replayUrl) {
        playAudio(replayUrl, false)
      }
    }
  } catch (e) {}
}

function handleTimeUp() {
  skipQuestion()
}

async function requestHint(type) {
  if (activeMainTab.value === 'multiplayer') {
    if (!currentRoom.value) return
    try {
      const res = await fetch(`/api/quiz/multiplayer/${currentRoom.value.roomCode}/hint`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerKey: playerKey.value, hintType: type })
      })
      if (res.ok) {
        const data = await res.json()
        hintsUnlocked.value.push(data.hintText)
      }
    } catch (e) {}
    return
  }

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

async function nextQuestion() {
  if (lastResult.value?.isFinished) {
    gameState.value = 'game_over'
    return
  }

  // 3-SECOND PREPARATION COUNTDOWN
  startSoloCountdown(async () => {
    try {
      const res = await fetch(`/api/quiz/game/${sessionId.value}/next`)
      if (res.ok) {
        const data = await res.json()
        setupQuestion(data)
      }
    } catch (e) {
      alert('Không thể tải câu hỏi tiếp theo')
    }
  })
}

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
  stopMultiplayerPolling()
})
</script>

<template>
  <div class="quiz-arena-root">
    <!-- TOP ARENA BANNER -->
    <div class="arena-top-banner">
      <div class="banner-title">
        <h2>⚡ ミュージック・クイズ</h2>
      </div>

      <!-- Main Mode Switch (Solo vs Multiplayer) -->
      <div class="main-mode-toggle" v-if="!currentRoom && gameState === 'lobby'">
        <button 
          class="mode-switch-btn" 
          :class="{ active: activeMainTab === 'solo' }"
          @click="activeMainTab = 'solo'"
        >
          👤 Chơi Đơn (Solo)
        </button>
        <button 
          class="mode-switch-btn" 
          :class="{ active: activeMainTab === 'multiplayer' }"
          @click="activeMainTab = 'multiplayer'"
        >
          ⚔️ Đấu Nhiều Người (Multiplayer)
        </button>
      </div>

      <!-- Scores chip if playing Solo -->
      <div class="banner-scores" v-if="activeMainTab === 'solo' && (gameState === 'playing' || gameState === 'round_result')">
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

    <!-- ============================================================ -->
    <!-- TAB 1: SOLO LOBBY & GAMEPLAY                                  -->
    <!-- ============================================================ -->
    <template v-if="activeMainTab === 'solo'">
      <!-- SOLO LOBBY -->
      <div v-if="gameState === 'lobby'" class="arena-lobby-panel">
        <div class="lobby-welcome">
          <h3>CHỌN CHẾ ĐỘ THỬ THÁCH SOLO</h3>
          <p class="muted">Lắng nghe giai điệu và đoán tên bài hát hoặc nghệ sĩ nhanh nhất có thể để giành điểm First Blood và Speed Bonus!</p>
        </div>

        <div class="modes-grid">
          <div class="mode-card" :class="{ active: selectedMode === 'TEN_QUESTIONS' }" @click="selectedMode = 'TEN_QUESTIONS'">
            <div class="mode-icon">🎯</div>
            <h4>10 Câu Hỏi</h4>
            <p>Thể thức xếp hạng tiêu chuẩn. 10 bài hát ngẫu nhiên tính điểm tốc độ.</p>
          </div>
          <div class="mode-card" :class="{ active: selectedMode === 'ENDLESS' }" @click="selectedMode = 'ENDLESS'">
            <div class="mode-icon">♾️</div>
            <h4>Endless Vô Tận</h4>
            <p>Chơi liên tục không giới hạn số câu. Thử thách độ bền và trí nhớ!</p>
          </div>
          <div class="mode-card" :class="{ active: selectedMode === 'DAILY_CHALLENGE' }" @click="selectedMode = 'DAILY_CHALLENGE'">
            <div class="mode-icon">📅</div>
            <h4>Daily Challenge</h4>
            <p>5 bài hát đặc biệt của hôm nay. Cùng một đề bài cho tất cả game thủ.</p>
          </div>
          <div class="mode-card" :class="{ active: selectedMode === 'BLIND_RANDOM' }" @click="selectedMode = 'BLIND_RANDOM'">
            <div class="mode-icon">🙈</div>
            <h4>Đoán Mù (Blind)</h4>
            <p>Che hoàn toàn ảnh bìa và thông tin gợi ý. Chỉ dùng đôi tai để chiến thắng.</p>
          </div>
          <div class="mode-card" :class="{ active: selectedMode === 'CATEGORY' }" @click="selectedMode = 'CATEGORY'">
            <div class="mode-icon">🗂️</div>
            <h4>Mystery Pool</h4>
            <p>Chọn riêng pool bài hát theo chủ đề (Anime, J-Pop, Vocaloid...).</p>
          </div>
        </div>

        <div class="lobby-options-card">
          <div class="option-item">
            <label>Mục tiêu đoán:</label>
            <div class="pill-group">
              <button class="pill-choice-btn" :class="{ active: selectedTarget === 'TITLE' }" @click="selectedTarget = 'TITLE'">🎵 Tên bài hát</button>
              <button class="pill-choice-btn" :class="{ active: selectedTarget === 'ARTIST' }" @click="selectedTarget = 'ARTIST'">👤 Nghệ sĩ</button>
            </div>
          </div>
          <div class="option-item">
            <label>Thời gian mỗi câu:</label>
            <div class="pill-group">
              <button v-for="t in [15, 20, 30]" :key="t" class="pill-choice-btn" :class="{ active: selectedTimeLimit === t }" @click="selectedTimeLimit = t">⏱️ {{ t }}s</button>
            </div>
          </div>
          <div v-if="selectedMode === 'CATEGORY' || availablePools.length > 0" class="option-item">
            <label>Mystery Pool:</label>
            <select v-model="selectedPoolId" class="arena-select">
              <option value="">Toàn bộ kho nhạc</option>
              <option v-for="p in availablePools" :key="p.id" :value="p.id">{{ p.name }} ({{ p.songCount }} bài)</option>
            </select>
          </div>
        </div>

        <div class="start-btn-row">
          <button class="arena-hero-start-btn" @click="startSoloGame">⚔️ BẮT ĐẦU TRẬN ĐẤU</button>
        </div>
      </div>

      <!-- SOLO GAMEPLAY -->
      <div v-else-if="gameState === 'playing' || gameState === 'round_result' || gameState === 'countdown'" class="arena-play-container">
        <!-- 3-Second Preparation Countdown Overlay -->
        <div v-if="gameState === 'countdown'" class="countdown-preparation-overlay">
          <div class="countdown-card">
            <div class="countdown-badge">SẮP PHÁT GIAI ĐIỆU MỚI</div>
            <div class="countdown-big-number">
              <span class="animated-number">{{ countdownSeconds }}</span>
            </div>
            <p class="countdown-tip">
              <span v-if="countdownSeconds === 3">🎧 Đeo tai nghe và sẵn sàng...</span>
              <span v-else-if="countdownSeconds === 2">⚡ Nhớ tên bài hát hoặc nghệ sĩ...</span>
              <span v-else>🚀 CHIẾN THÔI!</span>
            </p>
          </div>
        </div>

        <div class="play-meta-bar">
          <span class="q-counter">CÂU HỎI {{ currentQuestion?.questionNumber }} / {{ currentQuestion?.totalQuestions }}</span>
          <div class="meta-right">
            <span class="tier-pill">{{ currentQuestion?.difficultyTier }}</span>
            <span class="genre-pill">🏷️ {{ currentQuestion?.genre }}</span>
          </div>
        </div>

        <div class="arena-timer-track">
          <div class="arena-timer-fill" :class="{ urgent: timeRemaining <= 5 }" :style="{ width: ((timeRemaining / totalTime) * 100) + '%' }"></div>
          <span class="timer-countdown-text">{{ timeRemaining }}s</span>
        </div>

        <div class="arena-stage-box">
          <div class="vinyl-disc-wrapper" :class="{ spinning: isAudioPlaying }">
            <div class="vinyl-grooves">
              <div class="vinyl-center-art">
                <!-- If round ended / Intermission: reveal the real cover! -->
                <img 
                  v-if="gameState === 'round_result'" 
                  :src="getResolvedArtwork(lastResult, currentQuestion)" 
                  alt="Artwork Revealed" 
                  class="disc-art-img" 
                />
                <!-- If playing: show artwork only if NOT blind -->
                <img 
                  v-else-if="!currentQuestion?.isBlind && currentQuestion?.artworkUrl" 
                  :src="currentQuestion?.artworkUrl" 
                  alt="Artwork" 
                  class="disc-art-img" 
                />
                <div v-else class="disc-art-placeholder"><span>{{ currentQuestion?.isBlind ? '❓' : '🎵' }}</span></div>
              </div>
            </div>
          </div>
          <div class="sound-wave-bars" :class="{ active: isAudioPlaying }"><span v-for="n in 12" :key="n" class="bar"></span></div>

          <p v-if="gameState === 'round_result'" class="revealed-stage-notice">
            ✨ {{ lastResult?.officialTitle }} — {{ lastResult?.officialArtist }}
          </p>
          <p v-else-if="currentQuestion?.isBlind" class="blind-notice">🙈 Chế độ Đoán Mù: Ảnh bìa đã được bảo mật!</p>
        </div>

        <!-- Result Card / Intermission -->
        <div v-if="gameState === 'round_result'" class="round-result-card intermission-mode" :class="lastResult?.status?.toLowerCase()">
          <div class="result-badge-row">
            <span v-if="lastResult?.status === 'EXACT' || lastResult?.status === 'CORRECT'" class="res-badge win">
              ✨ CHÍNH XÁC! (+{{ lastResult?.earnedPoints }} điểm)
            </span>
            <span v-else class="res-badge fail">❌ HẾT GIỜ HOẶC BỎ QUA</span>
            <span v-if="lastResult?.firstBlood" class="fb-pill-banner">
              🩸⚡ FIRST BLOOD! Trả lời thần tốc (+50đ)
            </span>
          </div>

          <div class="official-answer-reveal">
            <img :src="getResolvedArtwork(lastResult, currentQuestion)" class="revealed-cover" alt="Official" />
            <div class="revealed-text">
              <h3>
                {{ lastResult?.officialTitle }}
                <span v-if="getRomajiTitle(lastResult)" class="romaji-badge">({{ getRomajiTitle(lastResult) }})</span>
              </h3>
              <p class="artist-reveal">👤 {{ lastResult?.officialArtist }}</p>

              <!-- Player's submitted answer -->
              <div class="user-answer-display-row" :class="isAnswerCorrect(lastResult) ? 'correct' : 'wrong'">
                <span class="user-ans-label">💬 Đáp án của bạn:</span>
                <b v-if="lastSubmittedAnswer" class="user-ans-text">{{ lastSubmittedAnswer }}</b>
                <em v-else class="user-ans-empty">(Chưa nhập đáp án / Hết giờ)</em>
              </div>

              <small v-if="lastResult?.aliases" class="aliases-reveal">Tên khác: {{ lastResult?.aliases }}</small>

              <!-- Audio Replay Control in Intermission -->
              <div class="intermission-audio-bar">
                <button class="replay-audio-btn" @click="toggleReplayAudio(lastResult)">
                  {{ isAudioPlaying ? '⏸ Tạm dừng nghe lại' : '▶ Nghe lại từ đầu' }}
                </button>
                <a v-if="lastResult?.officialYoutubeUrl" :href="lastResult.officialYoutubeUrl" target="_blank" rel="noopener noreferrer" class="link-external-btn">
                  🔴 YouTube
                </a>
              </div>
            </div>
          </div>

          <!-- Points Breakdown -->
          <div class="points-breakdown-bar" v-if="lastResult?.earnedPoints > 0">
            <div class="breakdown-item"><small>Cơ bản</small> <b>+{{ lastResult?.basePoints || 100 }}</b></div>
            <div class="breakdown-item" v-if="lastResult?.speedBonus"><small>Tốc độ</small> <b>+{{ lastResult.speedBonus }}</b></div>
            <div class="breakdown-item" v-if="lastResult?.streakBonus"><small>Chuỗi</small> <b>+{{ lastResult.streakBonus }}</b></div>
            <div class="breakdown-item fb" v-if="lastResult?.firstBlood"><small>First Blood</small> <b>+50</b></div>
            <div class="breakdown-item penalty" v-if="lastResult?.hintPenalty"><small>Dùng gợi ý</small> <b>-{{ lastResult.hintPenalty }}</b></div>
          </div>

          <!-- Solo Mini Stats -->
          <div class="solo-intermission-stats">
            <div class="mini-stat-chip">
              <span>Tổng điểm:</span>
              <b class="gold">{{ currentScore }}</b>
            </div>
            <div class="mini-stat-chip">
              <span>Chuỗi thắng:</span>
              <b class="flame">🔥 {{ currentStreak }}</b>
            </div>
            <div class="mini-stat-chip">
              <span>Tiến độ:</span>
              <b>{{ currentQuestion?.questionNumber }} / {{ currentQuestion?.totalQuestions }}</b>
            </div>
          </div>

          <button class="arena-hero-start-btn next-q-btn" @click="nextQuestion">
            {{ lastResult?.isFinished ? '🏆 Xem Tổng Kết Trận Đấu' : 'Câu Tiếp Theo ▶ (3s Chuẩn Bị)' }}
          </button>
        </div>

        <!-- Controls -->
        <div v-else-if="gameState === 'playing'" class="arena-answer-controls">
          <div v-if="answerFeedback?.type === 'almost'" class="almost-banner"><span>⚡ {{ answerFeedback.message }}</span></div>
          <div class="input-action-row">
            <input type="text" v-model="playerAnswer" :placeholder="currentQuestion?.targetType === 'ARTIST' ? 'Nhập tên nghệ sĩ hoặc nhóm nhạc...' : 'Nhập tên bài hát...'" class="arena-guess-input" autofocus @keydown.enter="submitAnswer" />
            <button class="submit-guess-btn" :disabled="isSubmitting || !playerAnswer.trim()" @click="submitAnswer">GỬI ĐÁP ÁN ↵</button>
          </div>
          <div class="hints-and-tools-row">
            <div class="hint-buttons-group">
              <span class="hint-label">Gợi ý (-25đ):</span>
              <button class="tool-btn" @click="requestHint('FIRST_LETTER')">💡 Chữ đầu</button>
              <button class="tool-btn" @click="requestHint('LENGTH_INFO')">📏 Số ký tự</button>
              <button class="tool-btn" @click="requestHint('ARTIST_OR_GENRE')">{{ currentQuestion?.targetType === 'ARTIST' ? '🏷️ Thể loại' : '👤 Nghệ sĩ' }}</button>
            </div>
            <button class="skip-btn" @click="skipQuestion">Bỏ qua ⏭</button>
          </div>
          <div v-if="hintsUnlocked.length > 0" class="unlocked-hints-list">
            <div v-for="(h, idx) in hintsUnlocked" :key="idx" class="hint-bubble"><span>✨ {{ h }}</span></div>
          </div>
        </div>
      </div>

      <!-- SOLO GAME OVER -->
      <div v-else-if="gameState === 'game_over'" class="arena-summary-panel">
        <div class="grade-circle" :class="'grade-' + performanceGrade">
          <span class="grade-letter">{{ performanceGrade }}</span>
          <span class="grade-label">XẾP HẠNG</span>
        </div>
        <h2>TRẬN ĐẤU KẾT THÚC!</h2>
        <div class="summary-stats-grid">
          <div class="stat-card"><span class="stat-val gold">{{ summary?.finalScore }}</span><span class="stat-lbl">TỔNG ĐIỂM</span></div>
          <div class="stat-card"><span class="stat-val cyan">{{ summary?.correctCount }} / {{ summary?.totalQuestions }}</span><span class="stat-lbl">SỐ CÂU ĐÚNG</span></div>
          <div class="stat-card"><span class="stat-val purple">{{ summary?.accuracyPercent }}%</span><span class="stat-lbl">ĐỘ CHÍNH XÁC</span></div>
          <div class="stat-card"><span class="stat-val flame">🔥 {{ summary?.maxStreak }}</span><span class="stat-lbl">MAX STREAK</span></div>
        </div>
        <div class="summary-actions-row">
          <button class="arena-hero-start-btn" @click="startSoloGame">🔄 Chơi lại ngay</button>
          <button class="arena-secondary-btn" @click="gameState = 'lobby'">🏛️ Đổi chế độ chơi</button>
        </div>
      </div>
    </template>

    <!-- ============================================================ -->
    <!-- TAB 2: MULTIPLAYER ARENA ROOM                                -->
    <!-- ============================================================ -->
    <template v-else>
      <!-- NOT IN ROOM: Create or Join -->
      <div v-if="!currentRoom" class="mp-room-setup-grid">
        <!-- CREATE ROOM CARD -->
        <div class="mp-setup-card">
          <div class="setup-head">
            <span class="setup-icon">👑</span>
            <h3>Tạo Phòng Đấu Mới</h3>
          </div>
          <p class="muted">Tạo phòng riêng, chia sẻ mã phòng để cùng bạn bè so tài nghe nhạc thời gian thực.</p>

          <div class="setup-form-inputs">
            <label>Tên phòng:</label>
            <input type="text" v-model="mpRoomNameInput" placeholder="VD: Anime Openings Arena, Đấu Trường J-Pop" class="arena-input-styled" />

            <div class="grid-2-col">
              <div>
                <label>Mục tiêu:</label>
                <select v-model="selectedTarget" class="arena-select">
                  <option value="TITLE">Đoán Tên bài hát</option>
                  <option value="ARTIST">Đoán Nghệ sĩ</option>
                </select>
              </div>
              <div>
                <label>Thời gian / câu:</label>
                <select v-model.number="selectedTimeLimit" class="arena-select">
                  <option :value="15">15 giây</option>
                  <option :value="20">20 giây</option>
                  <option :value="30">30 giây</option>
                </select>
              </div>
            </div>

            <div class="grid-2-col">
              <div>
                <label>Số lượng câu:</label>
                <select v-model.number="questionCountInput" class="arena-select">
                  <option :value="5">5 câu</option>
                  <option :value="10">10 câu</option>
                  <option :value="15">15 câu</option>
                </select>
              </div>
              <div>
                <label>Mystery Pool:</label>
                <select v-model="selectedPoolId" class="arena-select">
                  <option value="">Toàn bộ kho nhạc</option>
                  <option v-for="p in availablePools" :key="p.id" :value="p.id">{{ p.name }}</option>
                </select>
              </div>
            </div>

            <button class="arena-hero-start-btn" @click="createMultiplayerRoom">✨ TẠO PHÒNG NGAY</button>
          </div>
        </div>

        <!-- JOIN ROOM CARD -->
        <div class="mp-setup-card join-card">
          <div class="setup-head">
            <span class="setup-icon">🚪</span>
            <h3>Tham Gia Phòng Có Sẵn</h3>
          </div>
          <p class="muted">Nhập mã phòng 6 ký tự được bạn bè chia sẻ để tham chiến.</p>

          <div class="setup-form-inputs join-inputs">
            <label>Mã phòng (Room Code):</label>
            <input 
              type="text" 
              v-model="mpRoomCodeInput" 
              placeholder="VD: QZ7821" 
              maxlength="6"
              class="arena-input-styled room-code-input"
              @keydown.enter="joinMultiplayerRoom"
            />
            <button class="arena-hero-start-btn join-btn" @click="joinMultiplayerRoom">
              🚀 VÀO PHÒNG
            </button>
          </div>
        </div>
      </div>

      <!-- IN ROOM: LOBBY, PLAYING, ROUND RESULT, OR GAME OVER -->
      <div v-else class="mp-room-active-container">
        <!-- Room Status Header -->
        <div class="mp-room-header">
          <div class="room-info">
            <div class="room-title-badge">
              <h3>{{ currentRoom.roomName }}</h3>
              <span class="room-code-tag" @click="copyRoomCode" title="Bấm để copy mã phòng">
                MÃ: {{ currentRoom.roomCode }} 📋
              </span>
            </div>
            <p class="muted">{{ currentRoom.players?.length }} người trong phòng • {{ currentRoom.totalQuestions }} câu • {{ currentRoom.timeLimit }}s/câu</p>
          </div>

          <button class="btn mini-btn danger-btn leave-room-btn" @click="leaveMultiplayerRoom">Rời phòng</button>
        </div>

        <!-- MULTIPLAYER LOBBY (WAITING ROOM) -->
        <div v-if="currentRoom.status === 'LOBBY'" class="mp-waiting-room">
          <div class="waiting-players-grid">
            <div v-for="p in currentRoom.players" :key="p.playerKey" class="player-lobby-card" :class="{ isMe: p.playerKey === playerKey }">
              <span class="p-avatar">{{ p.isHost ? '👑' : '👤' }}</span>
              <div class="p-info">
                <b>{{ p.displayName }}</b>
                <span class="p-status" :class="{ ready: p.isReady }">
                  {{ p.isHost ? 'Chủ phòng' : p.isReady ? '🟢 Đã sẵn sàng' : '⚪ Đang đợi...' }}
                </span>
              </div>
            </div>
          </div>

          <div class="mp-lobby-actions">
            <button v-if="!isHost" class="arena-hero-start-btn ready-btn" @click="toggleReady">
              SẴN SÀNG CHIẾN ĐẤU
            </button>
            <button v-if="isHost" class="arena-hero-start-btn start-match-btn" @click="startMultiplayerGame">
              ⚔️ BẮT ĐẦU TRẬN ĐẤU ({{ currentRoom.players?.length }} NGƯỜI)
            </button>
          </div>
        </div>

        <!-- MULTIPLAYER PLAYING, ROUND RESULT, OR COUNTDOWN -->
        <div v-else-if="currentRoom.status === 'PLAYING' || currentRoom.status === 'ROUND_RESULT' || currentRoom.status === 'COUNTDOWN'" class="mp-battlefield-layout">
          <!-- 3-Second Synchronized Preparation Countdown Overlay -->
          <div v-if="currentRoom.status === 'COUNTDOWN'" class="countdown-preparation-overlay">
            <div class="countdown-card">
              <div class="countdown-badge">SẮP PHÁT GIAI ĐIỆU MỚI (ĐỒNG BỘ PHÒNG)</div>
              <div class="countdown-big-number">
                <span class="animated-number">{{ currentRoom.countdownRemaining || 3 }}</span>
              </div>
              <p class="countdown-tip">
                <span v-if="(currentRoom.countdownRemaining || 3) === 3">🎧 Đeo tai nghe và sẵn sàng...</span>
                <span v-else-if="(currentRoom.countdownRemaining || 3) === 2">⚡ Nhớ tên bài hát hoặc nghệ sĩ...</span>
                <span v-else>🚀 CHIẾN THÔI!</span>
              </p>
            </div>
          </div>

          <!-- Left: Game arena stage & answering -->
          <div class="mp-main-battle-pane">
            <div class="play-meta-bar">
              <span class="q-counter">CÂU HỎI {{ currentRoom.currentQuestionIndex }} / {{ currentRoom.totalQuestions }}</span>
              <div class="meta-right">
                <span class="tier-pill">{{ currentQuestion?.difficultyTier || 'Arena' }}</span>
                <span class="genre-pill">🏷️ {{ currentQuestion?.genre || 'Music' }}</span>
              </div>
            </div>

            <!-- Timer Bar -->
            <div class="arena-timer-track">
              <div class="arena-timer-fill" :class="{ urgent: timeRemaining <= 5 }" :style="{ width: ((timeRemaining / totalTime) * 100) + '%' }"></div>
              <span class="timer-countdown-text">{{ timeRemaining }}s</span>
            </div>

            <!-- Anonymous Alert: Someone Solved! -->
            <div v-if="currentRoom.someoneSolved && currentRoom.status === 'PLAYING'" class="someone-solved-banner">
              <span class="pulse-icon">⚡</span>
              <span>{{ currentRoom.notificationMessage || 'Có người đã giải được câu hỏi!' }}</span>
            </div>

            <!-- Vinyl Stage -->
            <div class="arena-stage-box">
              <div class="vinyl-disc-wrapper" :class="{ spinning: isAudioPlaying }">
                <div class="vinyl-grooves">
                  <div class="vinyl-center-art">
                    <img v-if="currentRoom.status === 'ROUND_RESULT'" :src="getResolvedArtwork(currentRoom, currentQuestion)" alt="Cover" class="disc-art-img" />
                    <div v-else class="disc-art-placeholder"><span>🎵</span></div>
                  </div>
                </div>
              </div>
              <div class="sound-wave-bars" :class="{ active: isAudioPlaying }"><span v-for="n in 12" :key="n" class="bar"></span></div>
              <p v-if="currentRoom.status === 'ROUND_RESULT'" class="revealed-stage-notice">
                ✨ {{ currentRoom.officialTitle }} — {{ currentRoom.officialArtist }}
              </p>
            </div>

            <!-- ROUND RESULT REVEAL / INTERMISSION -->
            <div v-if="currentRoom.status === 'ROUND_RESULT'" class="round-result-card intermission-mode">
              <div v-if="currentRoom.firstBloodWinner" class="first-blood-announcement">
                🩸 FIRST BLOOD THUỘC VỀ: <b>{{ currentRoom.firstBloodWinner }}</b> (+50đ Thưởng)
              </div>
              <div class="official-answer-reveal">
                <img :src="getResolvedArtwork(currentRoom, currentQuestion)" class="revealed-cover" alt="Official" />
                <div class="revealed-text">
                  <h3>
                    {{ currentRoom.officialTitle }}
                    <span v-if="getRomajiTitle(currentRoom)" class="romaji-badge">({{ getRomajiTitle(currentRoom) }})</span>
                  </h3>
                  <p class="artist-reveal">👤 {{ currentRoom.officialArtist }}</p>

                  <!-- Player's submitted answer in multiplayer -->
                  <div class="user-answer-display-row" :class="myMultiplayerAnswerStatus === 'CORRECT' ? 'correct' : 'wrong'">
                    <span class="user-ans-label">💬 Đáp án của bạn:</span>
                    <b v-if="lastSubmittedAnswer" class="user-ans-text">{{ lastSubmittedAnswer }}</b>
                    <em v-else class="user-ans-empty">(Chưa nhập đáp án / Hết giờ)</em>
                  </div>

                  <small v-if="currentRoom.aliases" class="aliases-reveal">Tên khác: {{ currentRoom.aliases }}</small>

                  <!-- Audio Replay Control in Intermission -->
                  <div class="intermission-audio-bar">
                    <button class="replay-audio-btn" @click="toggleReplayAudio(currentRoom)">
                      {{ isAudioPlaying ? '⏸ Tạm dừng nghe lại' : '▶ Nghe lại từ đầu' }}
                    </button>
                    <a v-if="currentRoom.officialYoutubeUrl" :href="currentRoom.officialYoutubeUrl" target="_blank" rel="noopener noreferrer" class="link-external-btn">
                      🔴 YouTube
                    </a>
                  </div>
                </div>
              </div>

              <!-- Intermission Timer Banner -->
              <div class="intermission-timer-banner">
                <span class="timer-icon">⏳</span>
                <span>Khoảng nghỉ: Còn <b>{{ currentRoom.intermissionRemaining != null ? currentRoom.intermissionRemaining : 10 }}s</b> trước khi chuyển câu tự động</span>
              </div>

              <div v-if="isHost" class="host-next-row">
                <button class="arena-hero-start-btn next-q-btn" @click="advanceMultiplayerRound">
                  {{ currentRoom.currentQuestionIndex >= currentRoom.totalQuestions ? '🏆 Xem Bảng Xếp Hạng Chung Cuộc' : 'Chuyển Câu Tiếp Theo ▶' }}
                </button>
              </div>
              <p v-else class="waiting-host-text">⏳ Đang trong khoảng nghỉ... Hệ thống sẽ tự động chuyển câu hoặc khi Chủ phòng bấm tiếp tục.</p>
            </div>

            <!-- ANSWER CONTROLS -->
            <div v-else-if="currentRoom.status === 'PLAYING'" class="arena-answer-controls">
              <div v-if="answerFeedback" class="almost-banner" :class="answerFeedback.type">
                <span>{{ answerFeedback.message }}</span>
              </div>
              <div class="input-action-row">
                <input 
                  type="text" 
                  v-model="playerAnswer" 
                  :placeholder="currentRoom.targetType === 'ARTIST' ? 'Nhập tên nghệ sĩ hoặc nhóm nhạc...' : 'Nhập tên bài hát...'" 
                  class="arena-guess-input" 
                  autofocus 
                  @keydown.enter="submitAnswer" 
                />
                <button class="submit-guess-btn" :disabled="isSubmitting || !playerAnswer.trim()" @click="submitAnswer">
                  GỬI ĐÁP ÁN ↵
                </button>
              </div>
              <div class="hints-and-tools-row">
                <div class="hint-buttons-group">
                  <span class="hint-label">Gợi ý (-25đ):</span>
                  <button class="tool-btn" @click="requestHint('FIRST_LETTER')">💡 Chữ đầu</button>
                  <button class="tool-btn" @click="requestHint('LENGTH_INFO')">📏 Số từ</button>
                </div>
              </div>
              <div v-if="hintsUnlocked.length > 0" class="unlocked-hints-list">
                <div v-for="(h, idx) in hintsUnlocked" :key="idx" class="hint-bubble"><span>✨ {{ h }}</span></div>
              </div>
            </div>
          </div>

          <!-- Right Sidebar: Live Multiplayer Leaderboard -->
          <div class="mp-live-leaderboard-sidebar">
            <h4>🏆 BẢNG ĐIỂM TRẬN ĐẤU</h4>
            <div class="live-players-list">
              <div 
                v-for="(p, rank) in currentRoom.players" 
                :key="p.playerKey" 
                class="live-player-row"
                :class="{ 
                  me: p.playerKey === playerKey,
                  answered: p.hasAnswered,
                  firstBloodWinner: p.gotFirstBlood
                }"
              >
                <div class="p-rank">#{{ rank + 1 }}</div>
                <div class="p-name-col">
                  <b>{{ p.displayName }}</b>
                  <div class="p-badges">
                    <span v-if="p.firstBloodCount > 0" class="fb-badge">🩸 {{ p.firstBloodCount }}</span>
                    <span v-if="p.hasAnswered && currentRoom.status === 'PLAYING'" class="answered-badge">✓ Đã nộp</span>
                    <span v-if="currentRoom.status === 'ROUND_RESULT' && p.roundPointsEarned > 0" class="earned-badge">+{{ p.roundPointsEarned }}</span>
                  </div>
                </div>
                <div class="p-score-col">{{ p.score }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- MULTIPLAYER GAME OVER PODIUM -->
        <div v-else-if="currentRoom.status === 'GAME_OVER'" class="mp-game-over-podium">
          <h2>🏆 KẾT QUẢ CHUNG CUỘC</h2>
          <div class="podium-cards-row">
            <div v-for="(p, idx) in currentRoom.players.slice(0, 3)" :key="p.playerKey" class="podium-card" :class="'place-' + (idx + 1)">
              <span class="podium-medal">{{ idx === 0 ? '🥇' : idx === 1 ? '🥈' : '🥉' }}</span>
              <h3>{{ p.displayName }}</h3>
              <span class="podium-score">{{ p.score }} điểm</span>
              <small>🩸 {{ p.firstBloodCount }} First Blood</small>
            </div>
          </div>
          <button class="arena-hero-start-btn" @click="leaveMultiplayerRoom">Quay lại sảnh đấu</button>
        </div>
      </div>
    </template>

    <!-- Hidden iframe for YouTube audio fallback when MP3 preview is unavailable or broken -->
    <iframe 
      v-if="fallbackEmbedUrl" 
      :src="fallbackEmbedUrl" 
      allow="autoplay" 
      style="position:absolute; width:1px; height:1px; opacity:0; pointer-events:none;"
    ></iframe>
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

.main-mode-toggle {
  display: flex;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  padding: 4px;
  gap: 4px;
}

.mode-switch-btn {
  background: transparent;
  border: none;
  color: #94a3b8;
  padding: 8px 16px;
  border-radius: 8px;
  font-weight: 700;
  font-size: 0.88rem;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-switch-btn.active {
  background: linear-gradient(135deg, #a855f7, #6366f1);
  color: #ffffff;
  box-shadow: 0 2px 10px rgba(168, 85, 247, 0.4);
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

/* Lobby Grid */
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

.mode-icon { font-size: 2.2rem; margin-bottom: 10px; }
.mode-card h4 { margin: 0 0 6px; font-size: 1.05rem; color: #f8fafc; }
.mode-card p { margin: 0; font-size: 0.8rem; color: #94a3b8; line-height: 1.4; }

.lobby-options-card {
  background: rgba(18, 15, 34, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.option-item { display: flex; flex-direction: column; gap: 8px; }
.option-item label { font-size: 0.85rem; font-weight: 700; color: #94a3b8; }

.pill-group { display: flex; gap: 8px; flex-wrap: wrap; }
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

.start-btn-row { display: flex; justify-content: center; margin-top: 10px; }

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

/* Gameplay Common */
.arena-play-container { display: flex; flex-direction: column; gap: 18px; }
.play-meta-bar { display: flex; justify-content: space-between; align-items: center; }
.q-counter { font-size: 0.95rem; font-weight: 800; color: #f8fafc; letter-spacing: 1px; }
.meta-right { display: flex; gap: 10px; }
.tier-pill {
  background: rgba(168, 85, 247, 0.2);
  color: #d8b4fe;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  text-transform: uppercase;
}
.genre-pill { background: rgba(255, 255, 255, 0.08); color: #cbd5e1; font-size: 0.75rem; padding: 3px 8px; border-radius: 6px; }

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
@keyframes pulseTimer { from { filter: brightness(1); } to { filter: brightness(1.4); } }
.timer-countdown-text { position: absolute; right: 8px; top: -20px; font-size: 0.85rem; font-weight: 800; color: #e2e8f0; }

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
.vinyl-disc-wrapper.spinning { animation: spinDisc 4s linear infinite; }
@keyframes spinDisc { 100% { transform: rotate(360deg); } }
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
.disc-art-img { width: 100%; height: 100%; object-fit: cover; }
.disc-art-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 1.8rem; }

.sound-wave-bars { display: flex; align-items: flex-end; gap: 4px; height: 28px; }
.sound-wave-bars .bar { width: 4px; height: 6px; background: #a855f7; border-radius: 2px; }
.sound-wave-bars.active .bar { animation: waveAnim 0.7s infinite alternate ease-in-out; }
.sound-wave-bars.active .bar:nth-child(2n) { animation-delay: 0.15s; height: 16px; background: #06b6d4; }
.sound-wave-bars.active .bar:nth-child(3n) { animation-delay: 0.3s; height: 24px; background: #fbbf24; }
.sound-wave-bars.active .bar:nth-child(4n) { animation-delay: 0.45s; height: 10px; }
@keyframes waveAnim { 0% { height: 4px; } 100% { height: 26px; } }
.blind-notice { margin: 0; font-size: 0.85rem; color: #fbbf24; }

/* Answering */
.arena-answer-controls { display: flex; flex-direction: column; gap: 12px; }
.almost-banner {
  background: rgba(245, 158, 11, 0.2);
  border: 1px solid rgba(245, 158, 11, 0.4);
  color: #fbbf24;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 700;
}
.input-action-row { display: flex; gap: 10px; }
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
}
.arena-guess-input:focus { border-color: #06b6d4; box-shadow: 0 0 16px rgba(6, 182, 212, 0.3); }
.submit-guess-btn {
  background: linear-gradient(135deg, #a855f7 0%, #6366f1 100%);
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 0 24px;
  font-size: 1rem;
  font-weight: 800;
  cursor: pointer;
}
.hints-and-tools-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
.hint-buttons-group { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.hint-label { font-size: 0.8rem; color: #94a3b8; }
.tool-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #cbd5e1;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 0.8rem;
  cursor: pointer;
}
.skip-btn {
  background: transparent;
  border: 1px solid rgba(239, 68, 68, 0.3);
  color: #f87171;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 0.82rem;
  cursor: pointer;
}
.unlocked-hints-list { display: flex; flex-direction: column; gap: 6px; margin-top: 4px; }
.hint-bubble {
  background: rgba(168, 85, 247, 0.12);
  border-left: 3px solid #a855f7;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 0.85rem;
  color: #e2e8f0;
}

/* Round Result */
.round-result-card {
  background: rgba(20, 16, 38, 0.9);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.res-badge { font-size: 1.05rem; font-weight: 800; padding: 6px 14px; border-radius: 8px; display: inline-block; }
.res-badge.win { background: rgba(16, 185, 129, 0.2); color: #34d399; }
.res-badge.fail { background: rgba(239, 68, 68, 0.2); color: #f87171; }
.official-answer-reveal { display: flex; align-items: center; gap: 16px; }
.revealed-cover { width: 72px; height: 72px; border-radius: 10px; object-fit: cover; }
.revealed-text h3 { margin: 0; font-size: 1.3rem; color: #f8fafc; }
.artist-reveal { margin: 4px 0 0; color: #94a3b8; font-size: 0.95rem; }
.aliases-reveal { color: #64748b; font-size: 0.8rem; display: block; margin-top: 2px; }
.next-q-btn { margin-top: 10px; }

/* Summary */
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
.grade-letter { font-size: 2.5rem; font-weight: 900; line-height: 1; }
.grade-label { font-size: 0.65rem; font-weight: 800; }
.summary-stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; width: 100%; max-width: 640px; margin: 16px 0; }
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
.summary-actions-row { display: flex; gap: 14px; margin-top: 10px; }
.arena-secondary-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #cbd5e1;
  padding: 12px 24px;
  border-radius: 10px;
  font-weight: 700;
  cursor: pointer;
}

/* ============================================================ */
/* MULTIPLAYER STYLES                                           */
/* ============================================================ */
.mp-room-setup-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 20px;
}

.mp-setup-card {
  background: rgba(18, 15, 34, 0.75);
  border: 1px solid rgba(168, 85, 247, 0.25);
  border-radius: 14px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.setup-head { display: flex; align-items: center; gap: 10px; }
.setup-head h3 { margin: 0; font-size: 1.25rem; color: #f8fafc; }
.setup-icon { font-size: 1.6rem; }

.setup-form-inputs { display: flex; flex-direction: column; gap: 12px; margin-top: 6px; }
.setup-form-inputs label { font-size: 0.85rem; font-weight: 700; color: #94a3b8; }
.arena-input-styled {
  background: rgba(10, 8, 20, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: #f8fafc;
  padding: 10px 14px;
  font-size: 0.95rem;
  outline: none;
}
.arena-input-styled:focus { border-color: #a855f7; }

.grid-2-col { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

.join-card { justify-content: space-between; }
.join-inputs { margin-top: 16px; }
.room-code-input {
  text-align: center;
  font-size: 1.6rem;
  letter-spacing: 4px;
  font-weight: 800;
  text-transform: uppercase;
}
.join-btn { margin-top: 10px; }

/* In Room */
.mp-room-active-container {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.mp-room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(22, 18, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 14px 20px;
}
.room-title-badge { display: flex; align-items: center; gap: 12px; }
.room-title-badge h3 { margin: 0; font-size: 1.2rem; color: #f8fafc; }
.room-code-tag {
  background: rgba(168, 85, 247, 0.2);
  color: #c084fc;
  font-size: 0.82rem;
  font-weight: 800;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px dashed #a855f7;
  cursor: pointer;
}
.leave-room-btn {
  background: transparent;
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

/* Waiting Room */
.mp-waiting-room {
  background: rgba(16, 13, 30, 0.65);
  border: 1px solid rgba(168, 85, 247, 0.2);
  border-radius: 14px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.waiting-players-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
.player-lobby-card {
  background: rgba(26, 22, 48, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.player-lobby-card.isMe { border-color: #a855f7; box-shadow: 0 0 14px rgba(168, 85, 247, 0.25); }
.p-avatar { font-size: 1.6rem; }
.p-info { display: flex; flex-direction: column; gap: 2px; }
.p-info b { font-size: 0.95rem; color: #f8fafc; }
.p-status { font-size: 0.78rem; color: #94a3b8; }
.p-status.ready { color: #34d399; font-weight: 700; }
.mp-lobby-actions { display: flex; justify-content: center; }

/* Battlefield Layout */
.mp-battlefield-layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
}

.mp-main-battle-pane {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.someone-solved-banner {
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.25) 0%, rgba(168, 85, 247, 0.25) 100%);
  border: 1px solid rgba(6, 182, 212, 0.4);
  color: #38bdf8;
  padding: 10px 16px;
  border-radius: 8px;
  font-weight: 800;
  display: flex;
  align-items: center;
  gap: 8px;
  animation: pulse 1s infinite alternate;
}
@keyframes pulse { from { opacity: 0.85; } to { opacity: 1; } }

.first-blood-announcement {
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #f87171;
  padding: 8px 14px;
  border-radius: 6px;
  font-weight: 800;
  text-align: center;
}

.waiting-host-text {
  text-align: center;
  color: #94a3b8;
  font-style: italic;
  margin: 10px 0 0;
}

/* Sidebar Leaderboard */
.mp-live-leaderboard-sidebar {
  background: rgba(18, 15, 34, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: fit-content;
}
.mp-live-leaderboard-sidebar h4 {
  margin: 0;
  font-size: 0.95rem;
  color: #f8fafc;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 10px;
}
.live-players-list { display: flex; flex-direction: column; gap: 8px; }
.live-player-row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(25, 21, 46, 0.6);
  border-radius: 8px;
  padding: 8px 10px;
  border: 1px solid transparent;
}
.live-player-row.me { border-color: rgba(168, 85, 247, 0.4); }
.live-player-row.firstBloodWinner { border-color: rgba(239, 68, 68, 0.5); }
.p-rank { font-size: 0.85rem; font-weight: 800; color: #94a3b8; width: 24px; }
.p-name-col { flex: 1; min-width: 0; }
.p-name-col b {
  font-size: 0.88rem;
  color: #f1f5f9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}
.p-badges { display: flex; gap: 4px; margin-top: 2px; }
.fb-badge { background: rgba(239, 68, 68, 0.2); color: #f87171; font-size: 0.65rem; padding: 1px 4px; border-radius: 4px; font-weight: 700; }
.answered-badge { background: rgba(16, 185, 129, 0.2); color: #34d399; font-size: 0.65rem; padding: 1px 4px; border-radius: 4px; }
.earned-badge { background: rgba(245, 158, 11, 0.2); color: #fbbf24; font-size: 0.65rem; padding: 1px 4px; border-radius: 4px; font-weight: 700; }
.p-score-col { font-size: 1rem; font-weight: 800; color: #fbbf24; }

/* Game Over Podium */
.mp-game-over-podium {
  background: linear-gradient(135deg, rgba(20, 16, 40, 0.95) 0%, rgba(10, 8, 22, 0.98) 100%);
  border: 1px solid rgba(168, 85, 247, 0.3);
  border-radius: 16px;
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}
.mp-game-over-podium h2 { margin: 0; color: #f8fafc; font-size: 1.8rem; letter-spacing: 1px; }
.podium-cards-row { display: flex; justify-content: center; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
.podium-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 160px;
}
.podium-card.place-1 {
  border-color: #fbbf24;
  box-shadow: 0 0 30px rgba(251, 191, 36, 0.3);
  order: 2;
  transform: translateY(-14px);
}
.podium-card.place-2 { border-color: #94a3b8; order: 1; }
.podium-card.place-3 { border-color: #b45309; order: 3; }
.podium-medal { font-size: 2.2rem; margin-bottom: 6px; }
.podium-card h3 { margin: 0; font-size: 1.15rem; color: #f8fafc; }
.podium-score { font-size: 1.3rem; font-weight: 800; color: #fbbf24; margin: 4px 0; }

/* =================================================== */
/* 3-SECOND PREPARATION COUNTDOWN OVERLAY              */
/* =================================================== */
.countdown-preparation-overlay {
  position: absolute;
  inset: 0;
  background: rgba(10, 8, 22, 0.94);
  backdrop-filter: blur(12px);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  animation: fadeIn 0.2s ease;
}

.countdown-card {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.countdown-badge {
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  padding: 4px 16px;
  background: rgba(168, 85, 247, 0.25);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.5);
  border-radius: 999px;
}

.countdown-big-number {
  font-size: 5.5rem;
  font-weight: 900;
  line-height: 1;
  background: linear-gradient(135deg, #f8fafc 0%, #c084fc 50%, #06b6d4 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 0 30px rgba(168, 85, 247, 0.7));
  animation: pulseScale 0.95s infinite ease-in-out;
}

@keyframes pulseScale {
  0% { transform: scale(0.92); opacity: 0.85; }
  50% { transform: scale(1.12); opacity: 1; filter: drop-shadow(0 0 40px rgba(6, 182, 212, 0.95)); }
  100% { transform: scale(0.92); opacity: 0.85; }
}

.countdown-tip {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: #e2e8f0;
}

/* Intermission Extras */
.revealed-stage-notice {
  font-size: 1rem;
  font-weight: 800;
  background: linear-gradient(90deg, #38bdf8, #c084fc);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  text-align: center;
  margin: 10px 0 0;
}

.fb-pill-banner {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.25), rgba(249, 115, 22, 0.25));
  border: 1px solid rgba(239, 68, 68, 0.6);
  color: #fca5a5;
  font-size: 0.82rem;
  font-weight: 800;
  padding: 4px 12px;
  border-radius: 999px;
  box-shadow: 0 0 15px rgba(239, 68, 68, 0.3);
}

.intermission-audio-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.replay-audio-btn {
  background: rgba(168, 85, 247, 0.25);
  border: 1px solid rgba(168, 85, 247, 0.5);
  color: #f1f5f9;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}

.replay-audio-btn:hover {
  background: rgba(168, 85, 247, 0.45);
  color: #fff;
}

.link-external-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #cbd5e1;
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 0.8rem;
  font-weight: 700;
  text-decoration: none;
  transition: all 0.2s;
}

.link-external-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.points-breakdown-bar {
  display: flex;
  gap: 12px;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 8px;
  padding: 8px 14px;
  flex-wrap: wrap;
}

.breakdown-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.breakdown-item small {
  font-size: 0.7rem;
  color: #94a3b8;
}

.breakdown-item b {
  font-size: 0.88rem;
  color: #34d399;
}

.breakdown-item.fb b { color: #f87171; }
.breakdown-item.penalty b { color: #fb923c; }

.solo-intermission-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.mini-stat-chip {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 6px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.84rem;
}

.intermission-timer-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(6, 182, 212, 0.15);
  border: 1px solid rgba(6, 182, 212, 0.35);
  color: #67e8f9;
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 0.86rem;
  font-weight: 600;
}

@media (max-width: 860px) {
  .mp-room-setup-grid { grid-template-columns: 1fr; }
  .mp-battlefield-layout { grid-template-columns: 1fr; }
  .arena-top-banner { flex-direction: column; align-items: stretch; }
  .banner-scores { justify-content: flex-end; }
  .input-action-row { flex-direction: column; }
  .submit-guess-btn { padding: 12px 18px; }
}

@media (prefers-reduced-motion: reduce) {
  .quiz-arena-root,
  .mode-card,
  .round-result-card,
  .someone-solved-banner,
  .arena-timer-fill.urgent,
  .vinyl-disc-wrapper.spinning,
  .sound-wave-bars.active .bar {
    animation: none !important;
    transition: none !important;
  }
  .vinyl-disc-wrapper.spinning {
    transform: none !important;
  }
}

/* Romaji & Player Answer Display */
.romaji-badge {
  display: inline-block;
  font-size: 0.82em;
  font-weight: 500;
  color: #38bdf8;
  margin-left: 6px;
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.3);
  padding: 1px 8px;
  border-radius: 6px;
}

.romaji-stage {
  color: #38bdf8;
  font-weight: 600;
  margin-left: 4px;
}

.user-answer-display-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 6px 0 10px 0;
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 0.9rem;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  width: fit-content;
}

.user-answer-display-row.correct {
  background: rgba(34, 197, 94, 0.12);
  border-color: rgba(34, 197, 94, 0.35);
  color: #86efac;
}

.user-answer-display-row.wrong {
  background: rgba(239, 68, 68, 0.12);
  border-color: rgba(239, 68, 68, 0.35);
  color: #fca5a5;
}

.user-ans-label {
  font-weight: 600;
  color: #94a3b8;
}

.user-ans-text {
  font-weight: 700;
  letter-spacing: 0.3px;
}

.user-ans-empty {
  font-style: italic;
  opacity: 0.75;
}
</style>
