import { ref } from 'vue'

const savedVol = parseFloat(localStorage.getItem('musicTournamentVolume') || '0.8')
const savedMute = localStorage.getItem('musicTournamentMuted') === 'true'

export const globalVolume = ref(isNaN(savedVol) ? 0.8 : Math.max(0, Math.min(1, savedVol)))
export const isMuted = ref(savedMute)

// Keep track of active audio elements
const activeAudios = new Set()

export function getEffectiveVolume() {
  return isMuted.value ? 0 : globalVolume.value
}

export function setGlobalVolume(val) {
  const clamped = Math.max(0, Math.min(1, val))
  globalVolume.value = clamped
  if (clamped > 0 && isMuted.value) {
    isMuted.value = false
    localStorage.setItem('musicTournamentMuted', 'false')
  }
  localStorage.setItem('musicTournamentVolume', clamped.toString())
  applyToAllAudios()
}

export function toggleGlobalMute() {
  isMuted.value = !isMuted.value
  localStorage.setItem('musicTournamentMuted', isMuted.value ? 'true' : 'false')
  applyToAllAudios()
}

export function applyToAllAudios() {
  const vol = getEffectiveVolume()
  window.__appMusicVolume = vol

  for (const audio of activeAudios) {
    try {
      audio.volume = vol
    } catch (_) {}
  }

  if (typeof document !== 'undefined') {
    document.querySelectorAll('audio').forEach(audio => {
      try {
        audio.volume = vol
      } catch (_) {}
    })
  }
}

// Hook HTMLAudioElement.prototype.play once
if (typeof window !== 'undefined' && !window.__globalAudioPatched) {
  window.__globalAudioPatched = true
  window.__appMusicVolume = getEffectiveVolume()

  const origPlay = HTMLAudioElement.prototype.play
  HTMLAudioElement.prototype.play = function (...args) {
    try {
      this.volume = getEffectiveVolume()
      activeAudios.add(this)
      const cleanup = () => activeAudios.delete(this)
      this.addEventListener('ended', cleanup, { once: true })
      this.addEventListener('pause', cleanup, { once: true })
    } catch (_) {}
    return origPlay.apply(this, args)
  }
}

// Pleasant test chime using Web Audio API
export function playTestChime() {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) return
    const ctx = new AudioContextClass()
    const now = ctx.currentTime
    const vol = getEffectiveVolume()
    if (vol <= 0) return

    const masterGain = ctx.createGain()
    masterGain.gain.setValueAtTime(vol * 0.18, now)
    masterGain.gain.exponentialRampToValueAtTime(0.0001, now + 0.4)
    masterGain.connect(ctx.destination)

    // Two harmonious notes (E5 659.25Hz, B5 987.77Hz) for a clean modern chime
    ;[659.25, 987.77].forEach((freq, idx) => {
      const osc = ctx.createOscillator()
      osc.type = 'sine'
      osc.frequency.setValueAtTime(freq, now + idx * 0.08)
      osc.connect(masterGain)
      osc.start(now + idx * 0.08)
      osc.stop(now + 0.4)
    })
  } catch (_) {}
}
