import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Compass, MagicStick, TrendCharts } from '@element-plus/icons-vue'
import {
  applyAiReport,
  getAiReportChat,
  getAiReports,
  type AiChatMessage,
  type AiReport,
} from '../api/logs'
import { runDailySummaryTask, runPeriodReportTask, runReportRefineTask } from '../api/aiTask'

type ReportMode = 'weekly' | 'mood' | 'themes' | 'daily' | 'monthly' | 'custom'
interface ReportForm { mode: ReportMode; date: string; range: string[] }

const defaultChatMessages: AiChatMessage[] = [{
  id: 0, role: 'assistant',
  content: '我是小记。你可以告诉我想把这份报告改得更短、更具体、更像复盘，或者换一种更自然的语气。',
  createdAt: '',
}]

export function useAiReport() {
  const formRef = ref<FormInstance>()
  const loading = ref(false)
  const historyLoading = ref(false)
  const chatVisible = ref(false)
  const chatLoading = ref(false)
  const applyLoading = ref(false)
  const chatInput = ref('')
  const chatBodyRef = ref<HTMLElement>()
  const reports = ref<AiReport[]>([])
  const report = ref<AiReport>()
  const previewReport = ref<AiReport>()
  const lastReportSnapshot = ref<AiReport>()
  const form = reactive<ReportForm>({ mode: 'weekly', date: new Date().toISOString().slice(0, 10), range: [] })
  const rules: FormRules<ReportForm> = {
    date: [{ required: true, message: '请选择日期', trigger: 'change' }],
    range: [{ required: true, message: '请选择日期范围', trigger: 'change' }],
  }
  const title = computed(() => ({
    mood: '最近情绪趋势', themes: '最近反复在意的事', weekly: '小记每周总结',
    monthly: '小记月度回顾', custom: '小记区间复盘', daily: 'AI 当天总结',
  })[form.mode])
  const splitTags = (tags?: string) => tags?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? []
  const tagList = computed(() => splitTags(report.value?.tags))
  const previewTagList = computed(() => splitTags(previewReport.value?.tags))
  const companionCards = [
    { mode: 'weekly' as ReportMode, icon: MagicStick, title: '每周总结', description: '把这周发生的事、情绪变化和小进展轻轻收拢。', days: 7 },
    { mode: 'mood' as ReportMode, icon: TrendCharts, title: '最近情绪趋势', description: '看看最近哪些时刻让你紧绷，哪些事又把你接住。', days: 14 },
    { mode: 'themes' as ReportMode, icon: Compass, title: '我反复在意什么', description: '从多篇日志里找出反复出现的牵挂、消耗和期待。', days: 30 },
  ]
  const setRecentRange = (days: number) => {
    const end = new Date()
    const start = new Date()
    start.setDate(end.getDate() - days + 1)
    form.range = [start.toISOString().slice(0, 10), end.toISOString().slice(0, 10)]
  }
  const selectCompanionCard = (mode: ReportMode, days: number) => { form.mode = mode; setRecentRange(days) }
  const handleModeChange = () => {
    const days: Partial<Record<ReportMode, number>> = { weekly: 7, mood: 14, themes: 30, monthly: 30 }
    const selectedDays = days[form.mode]
    if (selectedDays) setRecentRange(selectedDays)
  }
  const loadReports = async () => {
    historyLoading.value = true
    try {
      const { data } = await getAiReports()
      reports.value = data
      if (!report.value && data.length) report.value = data[0]
    } finally {
      historyLoading.value = false
    }
  }
  const selectReport = (item: AiReport) => {
    report.value = item
    previewReport.value = undefined
    lastReportSnapshot.value = undefined
    chatMessages.value = [...defaultChatMessages]
  }
  const generate = async () => {
    await formRef.value?.validate()
    loading.value = true
    try {
      report.value = form.mode === 'daily'
        ? await runDailySummaryTask(form.date)
        : await runPeriodReportTask(form.range[0], form.range[1], title.value)
      previewReport.value = undefined
      lastReportSnapshot.value = undefined
      await loadReports()
      ElMessage.success('小记已经生成并保存报告啦')
    } finally {
      loading.value = false
    }
  }
  const currentReportSnapshot = () => report.value ? { ...report.value } : undefined
  const chatMessages = ref<AiChatMessage[]>([...defaultChatMessages])
  const scrollChatToBottom = async () => {
    await nextTick()
    if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
  const setChatBody = (element: Element | null) => {
    chatBodyRef.value = element instanceof HTMLElement ? element : undefined
  }
  const openChat = async () => {
    if (!report.value?.id) {
      ElMessage.info('先生成或选择一份历史报告。')
      return
    }
    chatVisible.value = true
    try {
      const { data } = await getAiReportChat(report.value.id)
      chatMessages.value = data.messages.length ? data.messages : [...defaultChatMessages]
    } finally {
      await scrollChatToBottom()
    }
  }
  const sendChatMessage = async () => {
    const message = chatInput.value.trim()
    if (!message || !report.value?.id) return
    chatMessages.value.push({ id: Date.now(), role: 'user', content: message, createdAt: '' })
    chatInput.value = ''
    chatLoading.value = true
    await scrollChatToBottom()
    try {
      const response = await runReportRefineTask(report.value.id, message)
      previewReport.value = response.reportSuggestion
      const { data } = await getAiReportChat(report.value.id)
      chatMessages.value = data.messages.length ? data.messages : chatMessages.value
      ElMessage.success('小记生成了一版报告预览')
    } catch {
      ElMessage.error('AI 服务暂时不可用，报告仍可正常查看')
    } finally {
      chatLoading.value = false
      await scrollChatToBottom()
    }
  }
  const applyPreview = async () => {
    if (!report.value?.id || !previewReport.value) return
    applyLoading.value = true
    try {
      lastReportSnapshot.value = currentReportSnapshot()
      const { data } = await applyAiReport(report.value.id, { ...previewReport.value, period: report.value.period })
      report.value = data
      previewReport.value = undefined
      await loadReports()
      chatMessages.value.push({ id: Date.now(), role: 'assistant', content: '这版已经保存到历史报告里了。如果刚保存完又觉得不对，可以点撤销回到上一版。', createdAt: '' })
      ElMessage.success('已应用到报告')
    } finally {
      applyLoading.value = false
      await scrollChatToBottom()
    }
  }
  const undoReportApply = async () => {
    if (!report.value?.id || !lastReportSnapshot.value) return
    applyLoading.value = true
    try {
      const redoSnapshot = currentReportSnapshot()
      const { data } = await applyAiReport(report.value.id, lastReportSnapshot.value)
      report.value = data
      lastReportSnapshot.value = redoSnapshot
      await loadReports()
      chatMessages.value.push({ id: Date.now(), role: 'assistant', content: '已经撤回到上一版报告了。', createdAt: '' })
      ElMessage.success('已撤销')
    } finally {
      applyLoading.value = false
      await scrollChatToBottom()
    }
  }
  const formatTime = (value?: string) => value?.replace('T', ' ').slice(0, 16) || ''

  onMounted(async () => { setRecentRange(7); await loadReports() })
  return {
    applyLoading, applyPreview, chatBodyRef, chatInput, chatLoading, chatMessages, chatVisible, companionCards,
    form, formRef, formatTime, generate, handleModeChange, historyLoading, lastReportSnapshot, loading,
    openChat, previewReport, previewTagList, report, reports, rules, selectCompanionCard, selectReport,
    sendChatMessage, setChatBody, tagList, undoReportApply,
  }
}
