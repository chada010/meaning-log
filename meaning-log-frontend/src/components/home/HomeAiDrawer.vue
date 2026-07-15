<script setup lang="ts">
import { Check, Promotion, RefreshLeft } from '@element-plus/icons-vue'
import { computed, nextTick, ref, watch } from 'vue'
import type { AiChatMessage, AiSuggestion, MeaningLog } from '../../api/logs'
import { AI_CHAT_MESSAGE_MAX_LENGTH } from '../../constants/app'
import { displayLogTitle } from '../../utils/logDisplay'

const props = defineProps<{
  applyLoading: boolean
  chatInput: string
  chatLoading: boolean
  messages: AiChatMessage[]
  previewSuggestion?: AiSuggestion
  previewTagList: string[]
  selectedLog?: MeaningLog
  visible: boolean
  undoAvailable: boolean
}>()

const emit = defineEmits<{
  apply: []
  send: []
  undo: []
  'update:chatInput': [value: string]
  'update:visible': [value: boolean]
}>()

const chatBodyRef = ref<HTMLElement>()

const drawerVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const chatInputModel = computed({
  get: () => props.chatInput,
  set: (value: string) => emit('update:chatInput', value),
})

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

watch(
  () => props.messages,
  () => {
    void scrollChatToBottom()
  },
  { deep: true, flush: 'post' },
)

watch(
  () => [props.visible, props.selectedLog?.id, props.previewSuggestion?.summary],
  () => {
    void scrollChatToBottom()
  },
  { flush: 'post' },
)
</script>

<template>
  <el-drawer v-model="drawerVisible" title="小记" size="420px" append-to-body>
    <div v-if="selectedLog" class="xiaoji-panel">
      <div ref="chatBodyRef" class="xiaoji-messages">
        <div
          v-for="(message, index) in messages"
          :key="`${message.id}-${index}`"
          class="xiaoji-message"
          :class="`xiaoji-message-${message.role}`"
        >
          {{ message.content }}
        </div>
      </div>

      <div class="xiaoji-current">
        <strong>当前日志</strong>
        <p>{{ displayLogTitle(selectedLog) }}｜{{ selectedLog.aiSummary || '还没有总结，可以直接告诉小记你想要什么样的总结。' }}</p>
      </div>

      <div v-if="previewSuggestion" class="xiaoji-preview">
        <div class="xiaoji-preview-heading">
          <strong>预览稿</strong>
          <div class="button-row">
            <el-button type="primary" :icon="Check" :loading="applyLoading" @click="emit('apply')">
              应用到总结
            </el-button>
            <el-button :icon="RefreshLeft" :loading="applyLoading" :disabled="!undoAvailable" @click="emit('undo')">
              撤销
            </el-button>
          </div>
        </div>
        <h3>{{ previewSuggestion.title }}</h3>
        <p>{{ previewSuggestion.summary }}</p>
        <div v-if="previewTagList.length" class="tag-row">
          <el-tag v-for="tag in previewTagList" :key="tag" effect="light">{{ tag }}</el-tag>
        </div>
      </div>

      <div class="xiaoji-input">
        <el-input
          v-model="chatInputModel"
          type="textarea"
          :rows="4"
          :maxlength="AI_CHAT_MESSAGE_MAX_LENGTH"
          show-word-limit
          placeholder="比如：这条总结再简洁一点，保留今天最重要的感受。"
          @keydown.ctrl.enter.prevent="emit('send')"
        />
        <div class="button-row xiaoji-actions">
          <el-button
            v-if="undoAvailable"
            :icon="RefreshLeft"
            :loading="applyLoading"
            @click="emit('undo')"
          >
            撤销
          </el-button>
          <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="emit('send')">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>
