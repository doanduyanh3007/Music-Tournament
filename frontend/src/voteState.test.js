import { test } from 'node:test'
import assert from 'node:assert/strict'
import { selectedVoteSong, isVotingOpen, canChangeVote } from './voteState.js'
const tournament = { active:true }
const match = { songA:{id:1,title:'A'}, songB:{id:2,title:'B'}, hasVoted:true, myVote:{songId:1}, canChangeVote:true }

test('the original selection is recovered from the server after refresh or another-tab change', () => {
  assert.equal(selectedVoteSong(match).title, 'A')
  assert.equal(selectedVoteSong({...match,myVote:{songId:2}}).title, 'B')
  assert.equal(canChangeVote(tournament,match), true)
})
test('closed, finalized, expired, inactive and incomplete matches cannot be edited', () => {
  for (const changed of [{closed:true},{winner:{id:1}},{songB:null}]) {
    assert.equal(canChangeVote(tournament,{...match,...changed}), false)
  }
  assert.equal(canChangeVote({active:false},match), false)
  assert.equal(canChangeVote({active:true,endTime:'2026-01-01T00:00:00Z'},match,Date.parse('2026-01-01T00:00:00Z')), false)
  assert.equal(isVotingOpen({active:true,endTime:'2026-01-01T00:00:00Z'},match,Date.parse('2025-12-31T23:59:59Z')), true)
})
test('the change button requires the server-owned vote and server edit permission', () => {
  for (const changed of [{myVote:null},{myVote:{songId:999}},{hasVoted:false},{canChangeVote:false}]) {
    assert.equal(canChangeVote(tournament,{...match,...changed}), false)
  }
})
