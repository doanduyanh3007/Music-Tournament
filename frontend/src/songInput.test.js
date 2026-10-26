import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseSongs, uniqueSongs, formatSong } from './songInput.js'

test('deduplicates Unicode, case and spaces without dropping covers by different artists', () => {
  const result = uniqueSongs(parseSongs('Idol — YOASOBI\nＩＤＯＬ — yoasobi\nIdol — Other artist\nIdol   Song — A\nIdol Song — A'))
  assert.equal(result.unique.length, 3)
  assert.equal(result.duplicates.length, 2)
})
test('recognizes recording links and preserves search links shared by different songs', () => {
  const result = uniqueSongs(parseSongs('A — A | https://youtu.be/ZRtdQ81jPUQ\nB — B | https://www.youtube.com/watch?v=ZRtdQ81jPUQ&t=1\nC — C | https://youtube.com/results?search_query=Idol\nD — D | https://youtube.com/results?search_query=Idol'))
  assert.equal(result.duplicates.length, 1)
  assert.equal(result.unique.length, 3)
  const tracks = uniqueSongs(parseSongs('A — A | https://open.spotify.com/track/abc123?si=x\nB — B | https://open.spotify.com/intl-vi/track/abc123'))
  assert.equal(tracks.duplicates.length, 1)
})
test('normalizes numbered and Excel inputs for the API and roundtrips links', () => {
  const songs = parseSongs('1. Idol — YOASOBI | https://youtu.be/ZRtdQ81jPUQ\nGurenge\tLiSA\thttps://example.com/song\nSolo title\n\n')
  assert.equal(songs[0].title, 'Idol')
  assert.equal(songs[1].artist, 'LiSA')
  assert.equal(songs[1].link, 'https://example.com/song')
  assert.equal(formatSong(songs[2]), 'Solo title')
  assert.deepEqual(parseSongs(songs.map(formatSong).join('\n')).map(formatSong), songs.map(formatSong))
})
