<script setup lang="ts">
import { computed, type ComponentPublicInstance } from 'vue'
import { Check, Promotion, RefreshLeft } from '@element-plus/icons-vue'
import type { AiChatMessage, AiReport } from '../../api/logs'

const props = defineProps<{
  applyLoading: boolean
  chatInput: string
  chatLoading: boolean
  chatMessages: AiChatMessage[]
  lastReportSnapshot?: AiReport
  previewReport?: AiReport
  previewTagList: string[]
  report?: AiReport
  setChatBody?: (element: Element | null) => void
  visible: boolean
}>()
const emit = defineEmits<{
  apply: []
  send: []
  undo: []
  'update:chatInput': [value: string]
  'update:visible': [value: boolean]
}>()
const input = computed({ get: () => props.chatInput, set: (value: string) => emit('update:chatInput', value) })
const setChatBody = (value: Element | ComponentPublicInstance | null) => {
  props.setChatBody?.(value instanceof Element ? value : null)
}
</script>

<template>
  <el-drawer :model-value="visible" title="小记修改报告" size="460px" append-to-body @update:model-value="emit('update:visible', $event)">
    <div class="xiaoji-panel">
      <div :ref="setChatBody" class="xiaoji-messages">
        <div v-for="message in chatMessages" :key="message.id || `${message.role}-${message.content}`" class="xiaoji-message" :class="`xiaoji-message-${message.role}`">{{ message.content }}</div>
      </div>
      <div class="xiaoji-current"><strong>当前报告</strong><p>{{ report?.title }}｜{{ report?.summary }}</p></div>
      <div v-if="previewReport" class="xiaoji-preview">
        <div class="xiaoji-preview-heading"><strong>报告预览稿</strong><div class="button-row">
          <el-button type="primary" :icon="Check" :loading="applyLoading" @click="emit('apply')">应用到报告</el-button>
          <el-button :icon="RefreshLeft" :loading="applyLoading" :disabled="!lastReportSnapshot" @click="emit('undo')">撤销</el-button>
        </div></div>
        <h3>{{ previewReport.title }}</h3><p>{{ previewReport.summary }}</p>
        <div v-if="previewTagList.length" class="tag-row"><el-tag v-for="tag in previewTagList" :key="tag" effect="light">{{ tag }}</el-tag></div>
      </div>
      <div class="xiaoji-input">
        <el-input v-model="input" type="textarea" :rows="4" maxlength="400" show-word-limit placeholder="比如：这份周报再短一点，更突出情绪变化和下周可以继续的小事。" @keydown.ctrl.enter.prevent="emit('send')" />
        <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="emit('send')">发送</el-button>
      </div>
    </div>
  </el-drawer>
</template>
