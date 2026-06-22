<script setup lang="ts">
defineProps<{
  aiCount: number
  latestDate: string
  totalCount: number
}>()
</script>

<template>
  <section class="stats-grid">
    <div class="stat-card">
      <span>当前列表</span>
      <strong>{{ totalCount }}</strong>
      <small>条日志</small>
    </div>
    <div class="stat-card">
      <span>AI 已整理</span>
      <strong>{{ aiCount }}</strong>
      <small>条记录</small>
    </div>
    <div class="stat-card">
      <span>最近记录</span>
      <strong class="stat-date">{{ latestDate }}</strong>
      <small>继续保持</small>
    </div>
  </section>
</template>
