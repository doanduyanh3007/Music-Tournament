export function selectedVoteSong(match) {
  return [match?.songA, match?.songB].find(song => song && song.id === match?.myVote?.songId) || null
}

export function isVotingOpen(tournament, match, now = Date.now()) {
  return Boolean(tournament?.active && match && !match.closed && !match.winner && match.songA && match.songB
    && (!tournament.endTime || Date.parse(tournament.endTime) > now))
}

export function canChangeVote(tournament, match, now = Date.now()) {
  return Boolean(isVotingOpen(tournament, match, now) && match.hasVoted && match.canChangeVote && selectedVoteSong(match))
}
