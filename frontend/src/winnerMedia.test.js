import { test } from 'node:test'
import assert from 'node:assert/strict'
import { findWinnerPreview, winnerEmbed, winnerLink } from './winnerMedia.js'

test('winner preview matches the winning title and artist, preserving covers', () => {
  const cover = { title:'Idol', artist:'Other artist', previewUrl:'https://example.com/cover.m4a' }
  const original = { title:'ＩＤＯＬ', artist:'yoasobi', previewUrl:'https://example.com/original.m4a' }
  assert.equal(findWinnerPreview({title:'Idol', artist:'YOASOBI'}, [cover, original]), original)
  assert.equal(findWinnerPreview({title:'Idol', artist:''}, [cover, original]), null)
  assert.equal(findWinnerPreview({title:'Idol', artist:'YOASOBI'}, [cover]), null)
})
test('only recording links produce embeds, and YouTube playback is a 30-second excerpt', () => {
  const video = winnerEmbed('https://youtu.be/ZRtdQ81jPUQ?t=50')
  assert.match(video.url, /start=0&end=30/)
  assert.equal(video.provider, 'YouTube')
  assert.equal(winnerEmbed('https://youtube.com/results?search_query=Idol'), null)
  assert.equal(winnerEmbed('https://example.com/watch?v=ZRtdQ81jPUQ'), null)
  assert.equal(winnerEmbed('https://open.spotify.com/intl-vi/track/abc123').provider, 'Spotify')
})
test('missing or invalid links fall back to a song search, never a script URL', () => {
  assert.match(winnerLink({title:'Idol', artist:'YOASOBI', link:'javascript:alert(1)'}), /^https:\/\/www.youtube.com\/results/)
  assert.equal(winnerLink({title:'Idol', link:'https://youtu.be/ZRtdQ81jPUQ'}), 'https://youtu.be/ZRtdQ81jPUQ')
})
