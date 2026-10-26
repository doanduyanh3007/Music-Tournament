<script setup>
import { computed } from 'vue'
import { selectedVoteSong } from './voteState.js'
const props = defineProps({ match: { type: Object, required: true }, canChange: Boolean, compact: Boolean })
const emit = defineEmits(['change'])
const selected = computed(() => selectedVoteSong(props.match))
</script>
<template>
  <div v-if="match.hasVoted" class="vote-selection" :class="{compact}">
    <span>✓ Bạn đã chọn: <b>{{ selected?.title || 'Lựa chọn đã lưu' }}</b></span>
    <button v-if="canChange" type="button" class="btn soft" :aria-label="`Đổi lựa chọn cho trận ${match.position + 1}`" @click="emit('change')">Đổi lựa chọn</button>
    <small v-else>Phiếu đã khóa</small>
  </div>
</template>
<style scoped>
.vote-selection { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:8px; padding-top:10px; margin-top:8px; border-top:1px solid var(--line); font-size:11px; color:var(--muted); }
.vote-selection > span { min-width:0; overflow-wrap:anywhere; flex:1; }
.vote-selection b { color:var(--ink); }
.vote-selection .btn { padding:5px 9px; font-size:11px; }
.vote-selection small { font-size:10px; }
.vote-selection.compact { font-size:9px; gap:5px; padding-top:6px; }
.vote-selection.compact .btn { font-size:9px; padding:3px 6px; }
</style>
