function sameSong(a, b) { return a?.id != null && a.id === b?.id }
function validWinner(match) {
  return match?.winner && (sameSong(match.winner, match.songA) || sameSong(match.winner, match.songB))
}

export function getChampionship(tournament) {
  const rounds = [...(tournament?.rounds || [])].sort((a, b) => a.index - b.index)
  const finalRound = rounds.at(-1)
  const final = finalRound?.matches?.length === 1 ? finalRound.matches[0] : null
  const ready = final?.songA && final?.songB && !sameSong(final.songA, final.songB)
  const unresolved = rounds.some(r => r.matches.some(m => m.songA && m.songB && !validWinner(m)))
  const champion = ready && validWinner(final) && !unresolved && sameSong(tournament?.champion, final.winner)
    ? final.winner : null
  const route = []
  if (champion) {
    for (const round of rounds) {
      for (const match of round.matches) {
        const sideA = sameSong(champion, match.songA)
        if (!sideA && !sameSong(champion, match.songB)) continue
        const opponent = sideA ? match.songB : match.songA
        const won = sameSong(champion, match.winner)
        const wildcard = !won && tournament.wildcards?.some(w => sameSong(champion, w.song) && w.fromRoundIndex === round.index)
        route.push({ matchId: match.id, roundIndex: round.index, roundName: round.name,
          opponent, outcome: won ? (opponent ? 'win' : 'bye') : wildcard ? 'wildcard' : 'loss',
          votesFor: (sideA ? match.votesA : match.votesB) || 0,
          votesAgainst: (sideA ? match.votesB : match.votesA) || 0 })
      }
    }
  }
  return { champion, final, finalRound, finalists: ready && !champion && !validWinner(final) ? [final.songA.id, final.songB.id] : [], route,
    opponentsDefeated: new Set(route.filter(m => m.outcome === 'win').map(m => m.opponent.id)).size,
    roundsCleared: new Set(route.filter(m => ['win', 'bye', 'wildcard'].includes(m.outcome)).map(m => m.roundIndex)).size,
    totalEntrants: tournament?.songs?.length || 0,
    resultKey: champion ? `${tournament.id}:${final.id}:${champion.id}` : null }
}

export function getSongStatus(state, song, match) {
  if (sameSong(song, state?.champion)) return 'GRAND CHAMPION'
  if (state?.finalists?.includes(song?.id)) return 'FINALIST'
  if (song && match?.songA && match?.songB && sameSong(song, match.winner)) return 'WINNER'
  return ''
}

// Store one result per tournament. An observed reset makes a new coronation eligible.
export function createCoronationTracker(storage) {
  const key = 'musicTournamentCoronationsV2'
  let seen = {}
  try { seen = JSON.parse(storage?.getItem(key) || '{}') || {} } catch {}
  const save = () => { try { storage?.setItem(key, JSON.stringify(seen)) } catch {} }
  return {
    claim(tournamentId, resultKey) {
      if (!resultKey || seen[tournamentId] === resultKey) return false
      seen[tournamentId] = resultKey
      save()
      return true
    },
    reset(tournamentId) {
      if (seen[tournamentId]) { delete seen[tournamentId]; save() }
    }
  }
}
