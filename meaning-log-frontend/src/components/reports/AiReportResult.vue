<script setup lang="ts">
import { ChatDotRound, RefreshLeft } from '@element-plus/icons-vue'
import type { AiReport } from '../../api/logs'

defineProps<{
  report?: AiReport
  tagList: string[]
  applyLoading: boolean
  canUndo: boolean
}>()

const emit = defineEmits<{
  openChat: []
  undo: []
}>()
</script>

<template>
  <el-empty
    v-if="!report"
    class="report-empty"
    description="选一个方向，小记会从多篇日志里帮你看见最近的自己。"
  />
  <section v-else class="report-result">
    <div class="report-result-heading">
      <div class="report-result-title">
        <p class="eyebrow">{{ report.period }}</p>
        <h2>{{ report.title }}</h2>
      </div>
      <div class="button-row report-result-actions">
        <el-button type="primary" plain :icon="ChatDotRound" @click="emit('openChat')">
          和小记修改
        </el-button>
        <el-button
          v-if="canUndo"
          plain
          :icon="RefreshLeft"
          :loading="applyLoading"
          @click="emit('undo')"
        >
          撤销
        </el-button>
      </div>
    </div>
    <p class="report-summary">{{ report.summary }}</p>
    <div v-if="tagList.length" class="tag-row">
      <el-tag v-for="tag in tagList" :key="tag" effect="light">{{ tag }}</el-tag>
    </div>
  </section>
</template>
