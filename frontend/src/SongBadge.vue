<script setup>
import { computed, inject } from 'vue'
import { getSongStatus } from './championship.js'
const props = defineProps({ song: Object, match: Object })
const championship = inject('championship', null)
const status = computed(() => getSongStatus(championship?.value, props.song, props.match))
</script>
<template>
  <span v-if="status" class="song-status-badge" :class="{'grand-champion-badge':status==='GRAND CHAMPION','finalist-badge':status==='FINALIST','match-winner-badge':status==='WINNER'}"><span v-if="status==='GRAND CHAMPION'" aria-hidden="true">♛ </span>{{ status }}</span>
</template>
