<script setup lang="ts">
import { ref } from 'vue'
import type { AiReport } from '../../api/logs'
import AiReportHistoryList from './AiReportHistoryList.vue'

defineProps<{
  reports: AiReport[]
  activeId?: number
  formatTime: (value?: string) => string
}>()

const emit = defineEmits<{
  select: [report: AiReport]
}>()

const visible = ref(false)

const openDrawer = () => {
  visible.value = true
}

const handleSelect = (item: AiReport) => {
  emit('select', item)
  visible.value = false
}

defineExpose({ openDrawer })
</script>

<template>
  <el-drawer
    v-model="visible"
    class="report-history-drawer"
    direction="btt"
    size="auto"
    title="历史报告"
  >
    <AiReportHistoryList
      :reports="reports"
      :active-id="activeId"
      :format-time="formatTime"
      @select="handleSelect"
    />
  </el-drawer>
</template>
