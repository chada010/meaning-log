<script setup lang="ts">
import type { AiReport } from '../../api/logs'

defineProps<{
  reports: AiReport[]
  activeId?: number
  formatTime: (value?: string) => string
}>()

const emit = defineEmits<{
  select: [report: AiReport]
}>()
</script>

<template>
  <div class="report-history-list">
    <p v-if="!reports.length" class="report-history-empty">
      还没有历史报告，试着生成第一份吧。
    </p>
    <button
      v-for="item in reports"
      :key="item.id"
      class="report-history-item"
      :class="{ active: item.id === activeId }"
      type="button"
      @click="emit('select', item)"
    >
      <strong>{{ item.title }}</strong>
      <span>{{ item.period }}</span>
      <small>{{ formatTime(item.createdAt) }}</small>
    </button>
  </div>
</template>
