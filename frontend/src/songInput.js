export function normalizeText(value = '') {
  return value.normalize('NFKC').replace(/[\s\p{Z}]+/gu, ' ').trim().toLowerCase()
}

export function recordingKey(link) {
  if (!link) return null
  try {
    const url = new URL(link)
    const host = url.hostname.replace(/^www\./, '')
    let video
    if (host === 'youtu.be') video = url.pathname.slice(1).split('/')[0]
    if (['youtube.com', 'm.youtube.com'].includes(host)) {
      if (url.pathname === '/watch') video = url.searchParams.get('v')
      else video = url.pathname.match(/^\/(?:embed|shorts)\/([A-Za-z0-9_-]{11})\/?$/)?.[1]
    }
    if (video && /^[A-Za-z0-9_-]{11}$/.test(video)) return `youtube:${video}`
    if (host === 'open.spotify.com') {
      const track = url.pathname.match(/^\/(?:intl-[^/]+\/)?track\/([A-Za-z0-9]+)\/?$/)?.[1]
      if (track) return `spotify:${track}`
    }
  } catch {}
  return null
}

export function songKey(song) {
  return `${normalizeText(song.title)}\0${normalizeText(song.artist)}`
}

export function parseSongs(text) {
  return text.split(/\r?\n/).map((line, id) => {
    const raw = line.trim()
    if (!raw) return null
    let songPart = raw, link = null
    const pipe = raw.lastIndexOf('|')
    if (pipe > 0 && /^https?:\/\//.test(raw.slice(pipe + 1).trim())) {
      link = raw.slice(pipe + 1).trim()
      songPart = raw.slice(0, pipe).trim()
    }
    const tabs = songPart.split('\t')
    const parts = tabs.length >= 2 ? tabs : songPart.replace(/^\s*(?:\[?\d+\]?[.\-)\s]+)/, '').trim().split(/\s+[—–-]\s+/, 2)
    const title = parts[0].trim(), artist = parts[1]?.trim() || ''
    if (!link && /^https?:\/\//.test(tabs[2]?.trim() || '')) link = tabs[2].trim()
    return title ? { id, title, artist, link, raw } : null
  }).filter(Boolean)
}

export function uniqueSongs(songs) {
  const metadata = new Set(), recordings = new Set(), unique = [], duplicates = []
  for (const song of songs) {
    const key = songKey(song), recording = recordingKey(song.link)
    const duplicate = metadata.has(key) || (recording && recordings.has(recording))
    metadata.add(key)
    if (recording) recordings.add(recording)
    ;(duplicate ? duplicates : unique).push(song)
  }
  return { unique, duplicates }
}

export function formatSong(song) {
  return `${song.title}${song.artist ? ` — ${song.artist}` : ''}${song.link ? ` | ${song.link}` : ''}`
}
