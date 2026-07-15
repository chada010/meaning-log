<script setup lang="ts">
import { Clock } from '@element-plus/icons-vue'

defineProps<{
  historyCount: number
}>()

const emit = defineEmits<{
  openHistory: []
}>()
</script>

<template>
  <section class="report-hero-section">
    <div class="report-hero-copy">
      <p class="eyebrow">Xiaoji Review</p>
      <h1>小记陪你回看最近</h1>
      <p class="hero-copy">
        选一段时间或一个视角，让小记从多篇日志里帮你看见牵挂、变化和小小的进展。
      </p>
    </div>
    <el-button
      class="report-hero-history-btn mobile-only"
      :icon="Clock"
      @click="emit('openHistory')"
    >
      历史报告
      <span v-if="historyCount" class="report-hero-history-count">{{ historyCount }}</span>
    </el-button>
  </section>
</template>
