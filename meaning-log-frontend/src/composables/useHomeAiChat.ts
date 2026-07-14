import { ElMessage } from 'element-plus'
import { computed, ref, type Ref } from 'vue'
import {
  applyLogAi,
  getLogAiChat,
  type AiChatMessage,
  type AiSuggestion,
  type MeaningLog,
} from '../api/logs'
import { runLogRefineTask } from '../api/aiTask'

const EMPTY_CHAT_MESSAGE: AiChatMessage = {
  id: 0,
  role: 'assistant',
  content: '我是小记。先在列表里选一条日志，再告诉我你想怎样调整它的总结。',
  createdAt: '',
}

const currentAiSnapshot = (log?: MeaningLog): AiSuggestion => ({
  title: log?.aiTitle || '',
  summary: log?.aiSummary || '',
  tags: log?.aiTags?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? [],
})

export const useHomeAiChat = (logs: Ref<MeaningLog[]>) => {
  const chatVisible = ref(false)
  const chatLoading = ref(false)
  const applyLoading = ref(false)
  const chatInput = ref('')
  const selectedLog = ref<MeaningLog>()
  const previewSuggestion = ref<AiSuggestion>()
  const lastAiSnapshot = ref<AiSuggestion>()
  const chatMessages = ref<AiChatMessage[]>([EMPTY_CHAT_MESSAGE])

  const previewTagList = computed(() => previewSuggestion.value?.tags.filter(Boolean) ?? [])

  const resetDrawerState = () => {
    chatVisible.value = false
    chatLoading.value = false
    applyLoading.value = false
    chatInput.value = ''
    selectedLog.value = undefined
    previewSuggestion.value = undefined
    lastAiSnapshot.value = undefined
    chatMessages.value = [EMPTY_CHAT_MESSAGE]
  }

  const resetChatState = (log: MeaningLog, messages?: AiChatMessage[]) => {
    selectedLog.value = log
    previewSuggestion.value = undefined
    lastAiSnapshot.value = undefined
    chatInput.value = ''
    chatMessages.value = messages?.length
      ? messages
      : [
          {
            id: 0,
            role: 'assistant',
            content: `正在整理「${log.title}」。你可以告诉我想把总结改得更短、更具体，或者换一种语气。`,
            createdAt: '',
          },
        ]
  }

  const replaceLogInList = (updatedLog: MeaningLog) => {
    const index = logs.value.findIndex((log) => log.id === updatedLog.id)
    if (index >= 0) {
      logs.value.splice(index, 1, updatedLog)
    }
    selectedLog.value = updatedLog
  }

  const syncSelectedLog = (nextLogs = logs.value) => {
    if (!selectedLog.value) {
      return
    }

    const matchedLog = nextLogs.find((log) => log.id === selectedLog.value?.id)
    if (!matchedLog) {
      resetDrawerState()
      return
    }

    selectedLog.value = matchedLog
  }

  const openChat = async (log?: MeaningLog) => {
    const targetLog = log ?? logs.value[0]
    if (!targetLog) {
      ElMessage.info('先写一条日志，再来找小记聊聊。')
      return
    }

    resetChatState(targetLog, [EMPTY_CHAT_MESSAGE])
    chatVisible.value = true

    try {
      const { data } = await getLogAiChat(targetLog.id)
      resetChatState(targetLog, data.messages)
    } finally {
      syncSelectedLog()
    }
  }

  const sendChatMessage = async () => {
    if (!selectedLog.value) {
      return
    }

    const message = chatInput.value.trim()
    if (!message) {
      return
    }

    chatMessages.value.push({ id: Date.now(), role: 'user', content: message, createdAt: '' })
    chatInput.value = ''
    chatLoading.value = true

    try {
      const response = await runLogRefineTask(selectedLog.value.id, message)
      previewSuggestion.value = response.suggestion
      chatMessages.value = response.messages.length ? response.messages : chatMessages.value
      ElMessage.success('小记生成了一版预览')
    } finally {
      chatLoading.value = false
    }
  }

  const applyPreview = async () => {
    if (!selectedLog.value || !previewSuggestion.value) {
      return
    }

    applyLoading.value = true
    try {
      lastAiSnapshot.value = currentAiSnapshot(selectedLog.value)
      const { data } = await applyLogAi(selectedLog.value.id, previewSuggestion.value)
      replaceLogInList(data)
      previewSuggestion.value = undefined
      chatMessages.value.push({
        id: Date.now(),
        role: 'assistant',
        content: '这版已经保存到列表里的 AI 总结了，刚保存完不满意也可以撤销。',
        createdAt: '',
      })
      ElMessage.success('已应用到总结')
    } finally {
      applyLoading.value = false
    }
  }

  const undoAiApply = async () => {
    if (!selectedLog.value || !lastAiSnapshot.value) {
      return
    }

    applyLoading.value = true
    try {
      const redoSnapshot = currentAiSnapshot(selectedLog.value)
      const { data } = await applyLogAi(selectedLog.value.id, lastAiSnapshot.value)
      replaceLogInList(data)
      lastAiSnapshot.value = redoSnapshot
      chatMessages.value.push({
        id: Date.now(),
        role: 'assistant',
        content: '已经撤回到上一版总结了。',
        createdAt: '',
      })
      ElMessage.success('已撤销')
    } finally {
      applyLoading.value = false
    }
  }

  const handleDeletedLog = (logId: number) => {
    if (selectedLog.value?.id === logId) {
      resetDrawerState()
    }
  }

  return {
    applyLoading,
    applyPreview,
    chatInput,
    chatLoading,
    chatMessages,
    chatVisible,
    handleDeletedLog,
    lastAiSnapshot,
    openChat,
    previewSuggestion,
    previewTagList,
    selectedLog,
    sendChatMessage,
    syncSelectedLog,
    undoAiApply,
  }
}
