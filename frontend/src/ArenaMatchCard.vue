<script setup>
import { ref, computed, watch } from 'vue'
import SongBadge from './SongBadge.vue'
import VoteSelection from './VoteSelection.vue'

const props = defineProps({
  match: { type: Object, required: true },
  isFinalRound: Boolean,
  tournament: Object,
  isChampion: Function,
  isWildcardSong: Function,
  isVotingOpen: Function,
  canChangeVote: Function,
  getSongArtwork: Function,
  isSpotifyLink: Function,
})
const emit = defineEmits(['vote', 'change-vote', 'play-song', 'open-details'])

const justVoted = ref(false)
const imgErrors = ref({})

function handleImgError(songId) {
  if (songId) {
    imgErrors.value[songId] = true
  }
}

watch(() => props.match.hasVoted, (now, was) => {
  if (now && !was) {
    justVoted.value = true
    setTimeout(() => { justVoted.value = false }, 1200)
  }
})

const songs = computed(() => [props.match.songA, props.match.songB])

const myVoteSide = computed(() => {
  const mv = props.match.myVote
  if (!mv) return null
  if (props.match.songA?.id === mv.songId) return 0
  if (props.match.songB?.id === mv.songId) return 1
  return null
})

const winnerSide = computed(() => {
  if (!props.match.winner) return null
  if (props.match.songA?.id === props.match.winner.id) return 0
  if (props.match.songB?.id === props.match.winner.id) return 1
  return null
})

const totalVotes = computed(() => (props.match.votesA ?? 0) + (props.match.votesB ?? 0))

function voteCount(side) {
  return side === 0 ? (props.match.votesA ?? 0) : (props.match.votesB ?? 0)
}

function votePct(side) {
  const total = totalVotes.value
  if (total === 0) return 50
  return Math.round((voteCount(side) / total) * 100)
}

function cardClass(side) {
  const won = winnerSide.value === side
  const lost = winnerSide.value !== null && winnerSide.value !== side
  const mine = myVoteSide.value === side
  return {
    'arena-card--winner': won,
    'arena-card--loser': lost,
    'arena-card--mine': mine && !props.match.winner,
    'arena-card--unchosen': myVoteSide.value !== null && myVoteSide.value !== side && !props.match.winner,
    'arena-card--champion': props.isChampion?.(songs.value[side]),
  }
}

const canVote = computed(() =>
  props.tournament?.active &&
  !props.match.closed &&
  !props.match.winner &&
  !props.match.hasVoted &&
  props.match.songB
)

function handleVote(song) {
  if (canVote.value && song) emit('vote', props.match, song)
}
</script>

<template>
  <article class="arena-match" :class="{
    'arena-match--locked': match.closed,
    'arena-match--finale': isFinalRound,
    'arena-match--just-voted': justVoted,
  }">
    <!-- ── Header ─────────────────────────────────────── -->
    <div class="arena-header">
      <div class="arena-header-left">
        <span class="arena-match-label">
          {{ isFinalRound ? '🏆 CHUNG KẾT' : 'TRẬN ' + (match.position + 1) }}
        </span>
        <span v-if="match.isWildcard" class="arena-tag arena-tag--wc">⭐ Wildcard</span>
      </div>
      <div class="arena-header-right">
        <span v-if="match.winner" class="arena-status arena-status--won">✓ Đã có kết quả</span>
        <span v-else-if="match.closed" class="arena-status arena-status--closed">🔒 Đã đóng</span>
        <span v-else class="arena-status arena-status--live">
          <span class="arena-live-dot"></span>Đang vote
        </span>
        <button class="arena-detail-btn" @click="emit('open-details', match)">Chi tiết</button>
      </div>
    </div>

    <!-- ── Two-column arena field ─────────────────────── -->
    <div class="arena-field">
      <!-- Card A -->
      <div class="arena-card" :class="cardClass(0)" @click="handleVote(songs[0])">
        <template v-if="songs[0]">
          <div class="arena-artwork">
            <img
              v-if="!imgErrors[songs[0].id] && getSongArtwork(songs[0])"
              :src="getSongArtwork(songs[0])"
              :alt="songs[0].title"
              class="arena-art-img"
              @error="handleImgError(songs[0].id)"
            />
            <div v-else class="arena-art-fallback">
              <span class="arena-fallback-icon">♫</span>
            </div>
            <span v-if="isChampion?.(songs[0])" class="arena-crown">♛</span>
            <span v-if="winnerSide === 0" class="arena-winner-chip">THẮNG</span>
          </div>

          <div class="arena-info">
            <div class="arena-song-title" :title="songs[0].title">
              {{ songs[0].title }}
              <SongBadge :song="songs[0]" :match="match" />
              <span v-if="isWildcardSong?.(songs[0].id)" class="arena-wc-star">⭐</span>
            </div>
            <div class="arena-song-artist" :title="songs[0].artist">{{ songs[0].artist || '—' }}</div>

            <div class="arena-vote-row">
              <div class="arena-votebar">
                <div class="arena-votebar-fill" :style="{ width: votePct(0) + '%' }"></div>
              </div>
              <span class="arena-vote-count">{{ voteCount(0) }} phiếu ({{ votePct(0) }}%)</span>
            </div>
          </div>

          <div class="arena-actions">
            <button
              v-if="songs[0].link"
              class="arena-play-btn"
              :class="{ 'arena-play-btn--spotify': isSpotifyLink(songs[0].link) }"
              :title="isSpotifyLink(songs[0].link) ? 'Nghe Spotify' : 'Nghe YouTube'"
              @click.stop="emit('play-song', songs[0])"
            >
              {{ isSpotifyLink(songs[0].link) ? '🟢' : '▶' }}
            </button>

            <button
              class="arena-vote-btn"
              :class="{
                'arena-vote-btn--selected': match.winner?.id === songs[0].id,
                'arena-vote-btn--mine': myVoteSide === 0 && !match.winner,
                'arena-vote-btn--disabled': !canVote && !match.winner,
              }"
              :disabled="!canVote && !match.winner"
              @click.stop="handleVote(songs[0])"
            >
              <template v-if="match.winner?.id === songs[0].id">
                {{ isChampion?.(songs[0]) ? '♛ CHAMPION' : '✓ WINNER' }}
              </template>
              <template v-else-if="match.closed">Đã đóng</template>
              <template v-else-if="myVoteSide === 0">✓ Đã chọn</template>
              <template v-else>Vote</template>
            </button>
          </div>
        </template>
        <div v-else class="arena-tbd">⏳ Chờ vòng trước…</div>
      </div>

      <!-- VS divider -->
      <div class="arena-vs" :class="{ 'arena-vs--live': !match.winner && !match.closed }">
        <div class="arena-vs-circle">
          <span>VS</span>
        </div>
      </div>

      <!-- Card B -->
      <div class="arena-card" :class="cardClass(1)" @click="handleVote(songs[1])">
        <template v-if="songs[1]">
          <div class="arena-artwork">
            <img
              v-if="!imgErrors[songs[1].id] && getSongArtwork(songs[1])"
              :src="getSongArtwork(songs[1])"
              :alt="songs[1].title"
              class="arena-art-img"
              @error="handleImgError(songs[1].id)"
            />
            <div v-else class="arena-art-fallback">
              <span class="arena-fallback-icon">♫</span>
            </div>
            <span v-if="isChampion?.(songs[1])" class="arena-crown">♛</span>
            <span v-if="winnerSide === 1" class="arena-winner-chip">THẮNG</span>
          </div>

          <div class="arena-info">
            <div class="arena-song-title" :title="songs[1].title">
              {{ songs[1].title }}
              <SongBadge :song="songs[1]" :match="match" />
              <span v-if="isWildcardSong?.(songs[1].id)" class="arena-wc-star">⭐</span>
            </div>
            <div class="arena-song-artist" :title="songs[1].artist">{{ songs[1].artist || '—' }}</div>

            <div class="arena-vote-row">
              <div class="arena-votebar">
                <div class="arena-votebar-fill" :style="{ width: votePct(1) + '%' }"></div>
              </div>
              <span class="arena-vote-count">{{ voteCount(1) }} phiếu ({{ votePct(1) }}%)</span>
            </div>
          </div>

          <div class="arena-actions">
            <button
              v-if="songs[1].link"
              class="arena-play-btn"
              :class="{ 'arena-play-btn--spotify': isSpotifyLink(songs[1].link) }"
              :title="isSpotifyLink(songs[1].link) ? 'Nghe Spotify' : 'Nghe YouTube'"
              @click.stop="emit('play-song', songs[1])"
            >
              {{ isSpotifyLink(songs[1].link) ? '🟢' : '▶' }}
            </button>

            <button
              class="arena-vote-btn"
              :class="{
                'arena-vote-btn--selected': match.winner?.id === songs[1].id,
                'arena-vote-btn--mine': myVoteSide === 1 && !match.winner,
                'arena-vote-btn--disabled': !canVote && !match.winner,
              }"
              :disabled="!canVote && !match.winner"
              @click.stop="handleVote(songs[1])"
            >
              <template v-if="match.winner?.id === songs[1].id">
                {{ isChampion?.(songs[1]) ? '♛ CHAMPION' : '✓ WINNER' }}
              </template>
              <template v-else-if="match.closed">Đã đóng</template>
              <template v-else-if="myVoteSide === 1">✓ Đã chọn</template>
              <template v-else>Vote</template>
            </button>
          </div>
        </template>
        <div v-else class="arena-tbd">⏳ Chờ vòng trước…</div>
      </div>
    </div>

    <!-- ── Footer ─────────────────────────────────────── -->
    <div v-if="match.hasVoted || match.bye" class="arena-footer">
      <VoteSelection
        v-if="match.hasVoted"
        :match="match"
        :can-change="canChangeVote(tournament, match)"
        compact
        @change="emit('change-vote', match)"
      />
      <div v-else-if="match.bye" class="arena-bye-note">↑ Tự động vào vòng tiếp theo</div>
    </div>
  </article>
</template>
