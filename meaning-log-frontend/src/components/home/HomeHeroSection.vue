<script setup lang="ts">
import { ChatDotRound, Check, Edit, Plus } from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { MeaningLog } from '../../api/logs'

const props = defineProps<{
  quickCanSave: boolean
  quickContent: string
  quickSaving: boolean
  lastQuickLog?: MeaningLog
}>()

const emit = defineEmits<{
  createLog: []
  editQuickLog: [id: number]
  openChat: []
  saveQuickLog: []
  'update:quickContent': [value: string]
}>()

const quickContentModel = computed({
  get: () => props.quickContent,
  set: (value: string) => emit('update:quickContent', value),
})
</script>

<template>
  <section class="dashboard-hero">
    <div>
      <p class="eyebrow">Meaning Log</p>
      <h1>把今天值得记住的微光，轻轻收好。</h1>
      <p class="hero-copy">
        记录每天有意义的事情，让小记帮你整理标题、总结和温柔的回顾。
      </p>
      <div class="quick-log-box">
        <el-input
          v-model="quickContentModel"
          type="textarea"
          :rows="4"
          maxlength="1000"
          resize="none"
          placeholder="今天发生了什么？"
          @keydown.ctrl.enter.prevent="emit('saveQuickLog')"
          @keydown.meta.enter.prevent="emit('saveQuickLog')"
        />
        <div class="quick-log-actions">
          <span>先写文字，标签、图片、情绪之后再补。Ctrl + Enter 快速保存。</span>
          <div class="button-row">
            <el-button
              v-if="lastQuickLog"
              :icon="Edit"
              @click="emit('editQuickLog', lastQuickLog.id)"
            >
              补充细节
            </el-button>
            <el-button
              type="primary"
              :icon="Check"
              :loading="quickSaving"
              :disabled="!quickCanSave"
              @click="emit('saveQuickLog')"
            >
              保存
            </el-button>
          </div>
        </div>
      </div>
    </div>
    <div class="hero-actions">
      <el-button size="large" :icon="Plus" @click="emit('createLog')">完整表单</el-button>
      <el-button size="large" :icon="ChatDotRound" @click="emit('openChat')">和小记聊聊</el-button>
    </div>
  </section>
</template>
