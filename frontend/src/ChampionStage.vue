<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { findWinnerPreview, winnerEmbed, winnerLink } from './winnerMedia.js'

const props = defineProps({ song: { type: Object, required: true }, tournamentName: String, artwork: String })
const emit = defineEmits(['before-play', 'share'])
const audio = ref(null)
const media = ref(null)
const loading = ref(false)
const playing = ref(false)
const embedActive = ref(false)
const playbackMessage = ref('')
const celebrating = ref(false)
const embed = computed(() => winnerEmbed(props.song.link))
const artwork = computed(() => media.value?.artwork || props.artwork || '/logo.png')
let controller, celebrationTimer, disposed = false

async function celebrate() {
  clearTimeout(celebrationTimer)
  celebrating.value = false
  await nextTick()
  if (disposed) return
  celebrating.value = true
  celebrationTimer = setTimeout(() => { celebrating.value = false }, 8000)
}

function stop() {
  audio.value?.pause()
  playing.value = false
  embedActive.value = false
}

async function play() {
  emit('before-play')
  playbackMessage.value = ''
  if (media.value?.previewUrl && audio.value) {
    try {
      audio.value.currentTime = 0
      await audio.value.play()
    } catch {
      playbackMessage.value = 'Nhấn nút phát trên trình nghe bên dưới để nghe đoạn nhạc chiến thắng.'
    }
  } else if (embed.value) {
    embedActive.value = true
    playbackMessage.value = `Phát lại qua ${embed.value.provider}. Nhấn Play trong trình phát nếu nhạc chưa bắt đầu.`
  }
}

function limitExcerpt() {
  if (audio.value?.currentTime >= 30) stop()
}

async function loadMedia() {
  controller?.abort()
  const request = new AbortController()
  controller = request
  stop()
  media.value = null
  playbackMessage.value = ''
  loading.value = true
  celebrate()
  const timeout = setTimeout(() => request.abort(), 12000)
  try {
    const response = await fetch(`/api/search?q=${encodeURIComponent(`${props.song.title} ${props.song.artist || ''}`)}&limit=20`, { signal: request.signal })
    if (response.ok) {
      const data = await response.json()
      if (controller !== request || disposed) return
      media.value = findWinnerPreview(props.song, data.results || [])
    }
  } catch { /* The winning song remains visible when the preview service is unavailable. */ }
  finally {
    clearTimeout(timeout)
    if (controller === request && !disposed) loading.value = false
  }
  if (controller !== request || disposed) return
  await nextTick()
  if (media.value?.previewUrl) await play()
  else playbackMessage.value = embed.value
    ? 'Phát lại khoảnh khắc chiến thắng bằng nút bên dưới.'
    : 'Chưa có đoạn nghe thử cho bản thu này. Bạn có thể mở bài hát để nghe.'
}

// Polling replaces the song object every few seconds; only a different recording restarts playback.
watch([() => props.song.id, () => props.song.title, () => props.song.artist, () => props.song.link], loadMedia, { immediate: true })
onBeforeUnmount(() => {
  disposed = true
  controller?.abort()
  clearTimeout(celebrationTimer)
  stop()
})
defineExpose({ stop })

function confettiStyle(n) {
  return { '--x': `${(n * 37) % 100}%`, '--delay': `${((n * 13) % 30) / 10}s`, '--duration': `${3 + (n % 4) * .4}s`, '--drift': `${(n % 2 ? 1 : -1) * (20 + n % 60)}px`, '--confetti-color': ['#fcd34d', '#a5b4fc', '#6ee7b7', '#f9a8d4', '#fff'][n % 5] }
}
</script>

<template>
  <section class="winner-stage" :class="{celebrating, 'winner-is-playing':playing || embedActive}" aria-label="Vinh danh quán quân">
    <div v-if="celebrating" class="winner-confetti" aria-hidden="true"><i v-for="n in 54" :key="n" :style="confettiStyle(n)"></i></div>
    <div v-if="celebrating" class="winner-fireworks" aria-hidden="true"><i v-for="n in 4" :key="n" :style="{'--burst-delay': `${n * .6}s`}">✧</i></div>
    <div class="winner-stage-top"><span>✦ MUSIC TOURNAMENT</span><span class="winner-champion-chip">🏆 QUÁN QUÂN</span></div>
    <div class="winner-stage-grid">
      <div class="winner-turntable" aria-hidden="true">
        <div class="winner-record-halo"></div>
        <div class="winner-record"><div class="winner-record-label"><img :src="artwork" alt="" @error="$event.target.src='/logo.png'"><span></span></div></div>
        <div class="winner-tonearm"></div>
        <span class="winner-record-caption">THE WINNING RECORD</span>
      </div>
      <div class="winner-copy">
        <p class="winner-overline">VÀ NGƯỜI CHIẾN THẮNG LÀ…</p>
        <h2 class="winner-song-title" aria-live="polite">{{ song.title }}</h2>
        <p class="winner-artist">{{ song.artist || 'Nghệ sĩ chưa được cập nhật' }}</p>
        <p class="winner-tournament">Quán quân {{ tournamentName }}</p>
        <div class="winner-equalizer" :class="{active:playing || embedActive}" aria-hidden="true"><i v-for="n in 18" :key="n" :style="{'--bar-height': `${12 + (n * 17) % 33}px`, '--bar-delay': `${n * .06}s`}"></i></div>
        <div class="winner-actions">
          <button v-if="media?.previewUrl || embed" class="winner-play-button" :disabled="loading" @click="playing || embedActive ? stop() : play()">{{ playing || embedActive ? 'Ⅱ Dừng nhạc' : '▶ Phát lại bài chiến thắng' }}</button>
          <button class="winner-secondary-button" @click="celebrate">✦ Tung pháo hoa</button>
          <button class="winner-secondary-button" @click="emit('share')">Sao chép danh hiệu</button>
        </div>
        <p v-if="loading" class="winner-media-note" role="status">Đang chuẩn bị đoạn nhạc chiến thắng…</p>
        <p v-else-if="playbackMessage" class="winner-media-note" role="status">{{ playbackMessage }}</p>
        <a :href="winnerLink(song)" target="_blank" rel="noopener noreferrer" class="winner-source-link">Mở bài hát ↗</a>
      </div>
    </div>
    <div v-if="media?.previewUrl" class="winner-audio-panel"><span>♫ Đoạn nhạc chiến thắng · tối đa 30 giây</span>
      <audio ref="audio" :src="media.previewUrl" controls preload="none" @play="emit('before-play'); playing=true" @pause="playing=false" @ended="playing=false" @timeupdate="limitExcerpt" @error="playing=false; playbackMessage='Không thể tải đoạn nhạc lúc này. Hãy thử lại hoặc mở bài hát.'"></audio>
    </div>
    <div v-if="embedActive && embed" class="winner-embed-panel"><iframe :src="embed.url" :title="`Phát lại ${song.title} trên ${embed.provider}`" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe></div>
  </section>
</template>
