import { normalizeText, recordingKey } from './songInput.js'

export function findWinnerPreview(song, results) {
  // A cover or a different artist must never be announced as the winning recording.
  if (!song.artist?.trim()) return null
  return results.find(item => normalizeText(item.title) === normalizeText(song.title)
    && normalizeText(item.artist) === normalizeText(song.artist)
    && /^https:\/\//.test(item.previewUrl || '')) || null
}

export function winnerEmbed(link) {
  const key = recordingKey(link)
  if (key?.startsWith('youtube:')) {
    return { provider: 'YouTube', url: `https://www.youtube-nocookie.com/embed/${key.slice(8)}?autoplay=1&start=0&end=30&rel=0` }
  }
  if (key?.startsWith('spotify:')) {
    return { provider: 'Spotify', url: `https://open.spotify.com/embed/track/${key.slice(8)}?theme=0` }
  }
  return null
}

export function winnerLink(song) {
  if (/^https?:\/\//i.test(song.link || '')) return song.link
  return `https://www.youtube.com/results?search_query=${encodeURIComponent(`${song.title} ${song.artist || ''}`.trim())}`
}
