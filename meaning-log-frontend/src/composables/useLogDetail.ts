import { computed, nextTick, onMounted, ref, type ComponentPublicInstance, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  applyLogAi,
  deleteLog,
  getLogAiChat,
  getLogDetail,
  getLogNavigation,
  updateLogFavorite,
  type AiChatMessage,
  type AiSuggestion,
  type LogNavigation,
  type MeaningLog,
} from '../api/logs'
import { runLogAnalyzeTask, runLogRefineTask } from '../api/aiTask'
import { renderMarkdown } from '../utils/markdown'

const defaultChatMessages: AiChatMessage[] = [{
  id: 0,
  role: 'assistant',
  content: '我是小记。你可以告诉我想把总结改得更简短、更具体、更温柔，或者更像某种口吻。',
  createdAt: '',
}]

export function useLogDetail(props: { id: number }) {
  const router = useRouter()
  const log = ref<MeaningLog>()
  const navigation = ref<LogNavigation>()
  const loading = ref(false)
  const aiLoading = ref(false)
  const streamingText = ref('')
  const chatVisible = ref(false)
  const chatLoading = ref(false)
  const applyLoading = ref(false)
  const chatInput = ref('')
  const chatBodyRef = ref<HTMLElement>()
  const previewSuggestion = ref<AiSuggestion>()
  const lastAiSnapshot = ref<AiSuggestion>()
  const previewImage = ref<string>()

  const tagList = computed(() => log.value?.aiTags?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? [])
  const previewTagList = computed(() => previewSuggestion.value?.tags.filter(Boolean) ?? [])
  const renderedLogContent = computed(() => renderMarkdown(log.value?.content ?? ''))
  const currentAiSnapshot = (): AiSuggestion => ({
    title: log.value?.aiTitle || '',
    summary: log.value?.aiSummary || '',
    tags: log.value?.aiTags?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? [],
  })

  const loadDetail = async () => {
    loading.value = true
    try {
      chatVisible.value = false
      previewSuggestion.value = undefined
      chatMessages.value = [...defaultChatMessages]
      const [{ data: detail }, { data: nav }] = await Promise.all([getLogDetail(props.id), getLogNavigation(props.id)])
      log.value = detail
      navigation.value = nav
    } finally {
      loading.value = false
    }
  }

  const goEdit = () => router.push({ name: 'log-edit', params: { id: props.id } })
  const goLog = (id?: number) => {
    if (id) {
      router.push({ name: 'log-detail', params: { id } })
    }
  }

  const handleGenerateAi = async () => {
    aiLoading.value = true
    streamingText.value = '小记正在整理…'
    try {
      lastAiSnapshot.value = currentAiSnapshot()
      const suggestion = await runLogAnalyzeTask(props.id)
      streamingText.value = ''
      const { data } = await applyLogAi(props.id, suggestion)
      log.value = data
      ElMessage.success('小记已经整理好啦')
    } catch {
      streamingText.value = ''
      ElMessage.error('AI 服务暂时不可用，日志仍可正常记录')
    } finally {
      aiLoading.value = false
    }
  }

  const chatMessages = ref<AiChatMessage[]>([...defaultChatMessages])
  const scrollChatToBottom = async () => {
    await nextTick()
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  }
  const setChatBody = (value: Element | ComponentPublicInstance | null) => {
    chatBodyRef.value = value instanceof HTMLElement ? value : undefined
  }
  const requestLogSuggestion = async (message: string, successMessage: string) => {
    chatVisible.value = true
    chatMessages.value.push({ id: Date.now(), role: 'user', content: message, createdAt: '' })
    chatLoading.value = true
    await scrollChatToBottom()
    try {
      const response = await runLogRefineTask(props.id, message)
      previewSuggestion.value = response.suggestion
      const { data } = await getLogAiChat(props.id)
      chatMessages.value = data.messages.length ? data.messages : chatMessages.value
      ElMessage.success(successMessage)
    } catch {
      ElMessage.error('AI 服务暂时不可用，日志仍可正常记录')
    } finally {
      chatLoading.value = false
      await scrollChatToBottom()
    }
  }
  const generateGentleTitle = () => requestLogSuggestion(
    '请只重点帮我生成一个更温柔、有画面感、适合回顾的标题，同时保留简短总结和标签。',
    '小记整理出一版预览了',
  )
  const organizeMessyLog = () => requestLogSuggestion(
    '请把这篇有点混乱的记录整理成更清晰的版本：提炼主线、保留真实细节，语气温柔自然，不要夸张。',
    '小记整理出一版预览了',
  )

  const toggleFavorite = async () => {
    if (!log.value) {
      return
    }
    const { data } = await updateLogFavorite(log.value.id, !log.value.favorite)
    log.value = { ...log.value, favorite: data.favorite }
    ElMessage.success(data.favorite ? '已收藏' : '已取消收藏')
  }
  const openChat = async () => {
    chatVisible.value = true
    try {
      const { data } = await getLogAiChat(props.id)
      chatMessages.value = data.messages.length ? data.messages : [...defaultChatMessages]
    } finally {
      await scrollChatToBottom()
    }
  }
  const sendChatMessage = async () => {
    const message = chatInput.value.trim()
    if (!message) {
      return
    }
    chatInput.value = ''
    await requestLogSuggestion(message, '小记生成了一版预览')
  }
  const applyPreview = async () => {
    if (!previewSuggestion.value) {
      return
    }
    applyLoading.value = true
    try {
      lastAiSnapshot.value = currentAiSnapshot()
      const { data } = await applyLogAi(props.id, previewSuggestion.value)
      log.value = data
      previewSuggestion.value = undefined
      chatMessages.value.push({
        id: Date.now(), role: 'assistant',
        content: '这版已经保存到日志里了。如果刚保存完又觉得不对，可以点撤销回到上一版。', createdAt: '',
      })
      ElMessage.success('已应用到总结')
    } finally {
      applyLoading.value = false
      await scrollChatToBottom()
    }
  }
  const undoAiApply = async () => {
    if (!lastAiSnapshot.value) {
      return
    }
    applyLoading.value = true
    try {
      const redoSnapshot = currentAiSnapshot()
      const { data } = await applyLogAi(props.id, lastAiSnapshot.value)
      log.value = data
      lastAiSnapshot.value = redoSnapshot
      chatMessages.value.push({ id: Date.now(), role: 'assistant', content: '已经撤回到上一版总结了。', createdAt: '' })
      ElMessage.success('已撤销')
    } finally {
      applyLoading.value = false
      await scrollChatToBottom()
    }
  }
  const handleDelete = async () => {
    if (!log.value) {
      return
    }
    await ElMessageBox.confirm(`确定删除「${log.value.title}」吗？`, '删除日志', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
    await deleteLog(log.value.id)
    ElMessage.success('已删除')
    router.push({ name: 'home' })
  }
  const formatTime = (value: string) => value.replace('T', ' ').slice(0, 16)

  onMounted(loadDetail)
  watch(() => props.id, loadDetail)

  return {
    aiLoading, applyLoading, applyPreview, chatBodyRef, chatInput, chatLoading, chatMessages, chatVisible,
    formatTime, generateGentleTitle, goEdit, goLog, handleDelete, handleGenerateAi, lastAiSnapshot, loading,
    log, navigation, openChat, organizeMessyLog, previewImage, previewSuggestion, previewTagList,
    renderedLogContent, router, sendChatMessage, setChatBody, streamingText, tagList, toggleFavorite, undoAiApply,
  }
}
