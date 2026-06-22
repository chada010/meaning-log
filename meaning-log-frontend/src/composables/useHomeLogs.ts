import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createLog,
  deleteLog,
  getAiTags,
  getLogs,
  updateLogFavorite,
  type MeaningLog,
} from '../api/logs'

const getToday = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const buildQuickTitle = (content: string) => {
  const firstLine = content.split(/\r?\n/).find((line) => line.trim())?.trim() ?? '今天的记录'
  return firstLine.length > 30 ? `${firstLine.slice(0, 30)}...` : firstLine
}

export const useHomeLogs = () => {
  const router = useRouter()

  const logs = ref<MeaningLog[]>([])
  const selectedDate = ref('')
  const keyword = ref('')
  const selectedTag = ref('')
  const favoriteOnly = ref(false)
  const tagOptions = ref<string[]>([])
  const loading = ref(false)
  const quickContent = ref('')
  const quickSaving = ref(false)
  const lastQuickLog = ref<MeaningLog>()

  const totalCount = computed(() => logs.value.length)
  const aiCount = computed(() => logs.value.filter((log) => log.aiSummary || log.aiTags).length)
  const latestDate = computed(() => logs.value[0]?.logDate || '尚未记录')
  const quickCanSave = computed(() => Boolean(quickContent.value.trim()) && !quickSaving.value)

  const goCreateLog = () => {
    router.push({ name: 'log-create' })
  }

  const goDetail = (id: number) => {
    router.push({ name: 'log-detail', params: { id } })
  }

  const goEdit = (id: number) => {
    router.push({ name: 'log-edit', params: { id } })
  }

  const handleRowDblClick = (log: MeaningLog) => {
    goDetail(log.id)
  }

  const resetFiltersForLatest = () => {
    selectedDate.value = ''
    keyword.value = ''
    selectedTag.value = ''
    favoriteOnly.value = false
  }

  const loadLogs = async () => {
    loading.value = true
    try {
      const { data } = await getLogs({
        date: selectedDate.value || undefined,
        keyword: keyword.value.trim() || undefined,
        tag: selectedTag.value || undefined,
        favorite: favoriteOnly.value ? true : undefined,
      })
      logs.value = data
    } finally {
      loading.value = false
    }
  }

  const resetFilters = async () => {
    resetFiltersForLatest()
    await loadLogs()
  }

  const loadTagOptions = async () => {
    const { data } = await getAiTags()
    tagOptions.value = data
  }

  const saveQuickLog = async () => {
    const content = quickContent.value.trim()
    if (!content) {
      ElMessage.warning('先写一点今天发生了什么。')
      return
    }

    quickSaving.value = true
    try {
      const { data } = await createLog({
        title: buildQuickTitle(content),
        content,
        logDate: getToday(),
        favorite: false,
        images: [],
      })
      lastQuickLog.value = data
      quickContent.value = ''
      resetFiltersForLatest()
      await loadLogs()
      ElMessage.success('已保存，后面可以再补情绪和图片。')
    } finally {
      quickSaving.value = false
    }
  }

  const toggleFavorite = async (log: MeaningLog) => {
    const { data } = await updateLogFavorite(log.id, !log.favorite)
    const index = logs.value.findIndex((item) => item.id === log.id)
    if (index >= 0) {
      logs.value.splice(index, 1, data)
    }
    ElMessage.success(data.favorite ? '已收藏' : '已取消收藏')
  }

  const deleteEntry = async (log: MeaningLog) => {
    await ElMessageBox.confirm(`确定删除「${log.title}」吗？`, '删除日志', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })

    await deleteLog(log.id)
    ElMessage.success('已删除')
    await loadLogs()
  }

  return {
    aiCount,
    deleteEntry,
    favoriteOnly,
    goCreateLog,
    goDetail,
    goEdit,
    handleRowDblClick,
    keyword,
    lastQuickLog,
    latestDate,
    loadLogs,
    loadTagOptions,
    loading,
    logs,
    quickCanSave,
    quickContent,
    quickSaving,
    resetFilters,
    saveQuickLog,
    selectedDate,
    selectedTag,
    tagOptions,
    toggleFavorite,
    totalCount,
  }
}
