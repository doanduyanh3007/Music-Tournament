import { test } from 'node:test'
import assert from 'node:assert/strict'
import { getChampionship, getSongStatus, createCoronationTracker } from './championship.js'

function bracket(count = 64) {
  const songs = Array.from({ length: count }, (_, i) => ({ id: i + 1, title: `Track ${i + 1}`, artist: `Artist ${i + 1}` }))
  const rounds = []
  let entrants = songs, id = 1
  while (entrants.length > 1) {
    const matches = []
    for (let i = 0; i < entrants.length; i += 2) matches.push({ id: id++, songA: entrants[i], songB: entrants[i+1], winner: entrants[i], votesA: 5, votesB: 2 })
    rounds.push({ index: rounds.length, name: `Round ${entrants.length}`, matches })
    entrants = matches.map(m => m.winner)
  }
  return { id: 7, songs, rounds, champion: entrants[0], wildcards: [] }
}

test('64-song champion follows six actual wins and six actual opponents', () => {
  const t = bracket(), s = getChampionship(t)
  assert.equal(s.champion.id, 1)
  assert.equal(s.opponentsDefeated, 6)
  assert.equal(s.roundsCleared, 6)
  assert.equal(s.totalEntrants, 64)
  assert.deepEqual(s.route.map(r => r.opponent.id), [2,3,5,9,17,33])
  assert.equal(s.route.at(-1).votesFor, 5)
  assert.equal(getSongStatus(s,t.songs[0],t.rounds[0].matches[0]), 'GRAND CHAMPION')
  assert.equal(getSongStatus(s,t.songs[2],t.rounds[0].matches[1]), 'WINNER')
})
test('in-progress finals have two finalists, never a provisional grand champion', () => {
  const t = bracket(8), final = t.rounds.at(-1).matches[0]
  final.winner = null
  const s = getChampionship(t) // Even a stale API champion must not create a coronation.
  assert.equal(s.champion, null)
  assert.equal(s.route.length, 0)
  assert.equal(getSongStatus(s,final.songA), 'FINALIST')
  assert.equal(getSongStatus(s,final.songB), 'FINALIST')
  assert.equal(getSongStatus(s,t.songs[1]), '')
})
test('one-sided pending final and stale or invalid winners cannot become champion', () => {
  for (const change of [t => { t.rounds.at(-1).matches[0].songB = null }, t => { t.rounds[0].matches[0].winner = null }, t => { t.champion = null }, t => { t.rounds.at(-1).matches[0].winner = {id:999} }]) {
    const t = bracket(8); change(t)
    assert.equal(getChampionship(t).champion, null)
  }
})
test('byes are passed rounds, never fabricated defeated opponents', () => {
  const t = bracket(4), m = t.rounds[0].matches[0]
  m.songB = null; m.bye = true; t.songs.pop()
  const s = getChampionship(t)
  assert.equal(s.route[0].outcome, 'bye')
  assert.equal(s.opponentsDefeated, 1)
  assert.equal(s.roundsCleared, 2)
  assert.equal(s.totalEntrants, 3)
})
test('wildcard routes show a real loss and return, without adding a fictitious win', () => {
  const t = bracket(8), m = t.rounds[0].matches[0]
  m.winner = m.songB
  t.wildcards = [{song:t.champion,fromRoundIndex:0}]
  const s = getChampionship(t)
  assert.equal(s.route[0].outcome, 'wildcard')
  assert.equal(s.opponentsDefeated, 2)
  assert.equal(s.roundsCleared, 3)
})
test('coronation runs once across polling, navigation and reload, and again after reset', () => {
  const values = new Map(), storage = {getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v)}
  const tracker = createCoronationTracker(storage), t = bracket(8), key = getChampionship(t).resultKey
  assert.equal(tracker.claim(t.id,key), true)
  assert.equal(tracker.claim(t.id,key), false)
  assert.equal(createCoronationTracker(storage).claim(t.id,key), false)
  tracker.reset(t.id)
  assert.equal(tracker.claim(t.id,key), true)
  assert.equal(tracker.claim(t.id,null), false)
})
test('coronation degrades safely when browser storage is unavailable', () => {
  const tracker = createCoronationTracker({ getItem(){throw Error()},setItem(){throw Error()} })
  assert.equal(tracker.claim(1,'result'), true)
  assert.equal(tracker.claim(1,'result'), false)
})
test('a reset removes the journey, crown and finalist badges from the former champion', () => {
  const t = bracket(8), formerChampion = t.champion
  t.champion = null
  for (const round of t.rounds) for (const match of round.matches) {
    match.winner = null
    if (round.index > 0) { match.songA = null; match.songB = null }
  }
  const reset = getChampionship(t)
  assert.equal(reset.champion, null)
  assert.equal(reset.route.length, 0)
  assert.equal(reset.finalists.length, 0)
  assert.equal(getSongStatus(reset,formerChampion), '')
})
