<script setup lang="ts">
import { Calendar, ChatDotRound, Check, Delete, Edit, Plus, Promotion, RefreshLeft, Search, Star, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  applyLogAi,
  chatWithLogAi,
  createLog,
  deleteLog,
  getAiTags,
  getLogAiChat,
  getLogs,
  updateLogFavorite,
  type AiChatMessage,
  type AiSuggestion,
  type MeaningLog,
} from '../api/logs'

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
const chatVisible = ref(false)
const chatLoading = ref(false)
const applyLoading = ref(false)
const chatInput = ref('')
const chatBodyRef = ref<HTMLElement>()
const selectedLog = ref<MeaningLog>()
const previewSuggestion = ref<AiSuggestion>()
const lastAiSnapshot = ref<AiSuggestion>()
const emptyChatMessage: AiChatMessage = {
  id: 0,
  role: 'assistant',
  content: '我是小记。先在列表里选一条日志，再告诉我你想怎样调整它的总结。',
  createdAt: '',
}
const chatMessages = ref<AiChatMessage[]>([
  {
    id: 0,
    role: 'assistant',
    content: '我是小记。先在列表里选一条日志，再告诉我你想怎样调整它的总结。',
    createdAt: '',
  },
])

const totalCount = computed(() => logs.value.length)
const aiCount = computed(() => logs.value.filter((log) => log.aiSummary || log.aiTags).length)
const latestDate = computed(() => logs.value[0]?.logDate || '尚未记录')
const previewTagList = computed(() => previewSuggestion.value?.tags.filter(Boolean) ?? [])
const quickCanSave = computed(() => Boolean(quickContent.value.trim()) && !quickSaving.value)

const goCreateLog = () => {
  router.push({ name: 'log-create' })
}

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
    if (selectedLog.value) {
      selectedLog.value = logs.value.find((log) => log.id === selectedLog.value?.id)
    }
  } finally {
    loading.value = false
  }
}

const resetDate = () => {
  selectedDate.value = ''
  keyword.value = ''
  selectedTag.value = ''
  favoriteOnly.value = false
  loadLogs()
}

const loadTagOptions = async () => {
  const { data } = await getAiTags()
  tagOptions.value = data
}

const goDetail = (id: number) => {
  router.push({ name: 'log-detail', params: { id } })
}

const goEdit = (id: number) => {
  router.push({ name: 'log-edit', params: { id } })
}

const handleRowDblClick = (row: MeaningLog) => {
  goDetail(row.id)
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

const currentAiSnapshot = (log?: MeaningLog): AiSuggestion => ({
  title: log?.aiTitle || '',
  summary: log?.aiSummary || '',
  tags: log?.aiTags?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? [],
})

const resetChatState = (log: MeaningLog, messages?: AiChatMessage[]) => {
  selectedLog.value = log
  previewSuggestion.value = undefined
  lastAiSnapshot.value = undefined
  chatInput.value = ''
  chatMessages.value = messages?.length ? messages : [
    {
      id: 0,
      role: 'assistant',
      content: `正在整理「${log.title}」。你可以告诉我想把总结改得更短、更具体，或者换一种语气。`,
      createdAt: '',
    },
  ]
}

const openChat = async (log?: MeaningLog) => {
  const targetLog = log ?? logs.value[0]
  if (!targetLog) {
    ElMessage.info('先写一条日志，再来找小记聊聊。')
    return
  }

  resetChatState(targetLog, [emptyChatMessage])
  chatVisible.value = true
  try {
    const { data } = await getLogAiChat(targetLog.id)
    resetChatState(targetLog, data.messages)
  } finally {
    await scrollChatToBottom()
  }
}

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const replaceLogInList = (updatedLog: MeaningLog) => {
  const index = logs.value.findIndex((log) => log.id === updatedLog.id)
  if (index >= 0) {
    logs.value.splice(index, 1, updatedLog)
  }
  selectedLog.value = updatedLog
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
  await scrollChatToBottom()

  try {
    const { data } = await chatWithLogAi(selectedLog.value.id, message)
    previewSuggestion.value = data.suggestion
    chatMessages.value = data.messages.length ? data.messages : chatMessages.value
    ElMessage.success('小记生成了一版预览')
  } finally {
    chatLoading.value = false
    await scrollChatToBottom()
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
    await scrollChatToBottom()
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
    chatMessages.value.push({ id: Date.now(), role: 'assistant', content: '已经撤回到上一版总结了。', createdAt: '' })
    ElMessage.success('已撤销')
  } finally {
    applyLoading.value = false
    await scrollChatToBottom()
  }
}

const handleDelete = async (log: MeaningLog) => {
  await ElMessageBox.confirm(`确定删除「${log.title}」吗？`, '删除日志', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })

  await deleteLog(log.id)
  ElMessage.success('已删除')
  if (selectedLog.value?.id === log.id) {
    chatVisible.value = false
    selectedLog.value = undefined
  }
  await loadLogs()
}

const formatTime = (value: string) => value?.replace('T', ' ').slice(0, 16)

const previewContent = (value: string) => {
  const text = value.replace(/\s+/g, ' ').trim()
  return text.length > 96 ? `${text.slice(0, 96)}...` : text
}

const splitTags = (value?: string) => value?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? []

onMounted(async () => {
  await Promise.all([loadLogs(), loadTagOptions()])
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
          v-model="quickContent"
          type="textarea"
          :rows="4"
          maxlength="1000"
          resize="none"
          placeholder="今天发生了什么？"
          @keydown.ctrl.enter.prevent="saveQuickLog"
          @keydown.meta.enter.prevent="saveQuickLog"
        />
        <div class="quick-log-actions">
          <span>先写文字，标签、图片、情绪之后再补。Ctrl + Enter 快速保存。</span>
          <div class="button-row">
            <el-button
              v-if="lastQuickLog"
              :icon="Edit"
              @click="goEdit(lastQuickLog.id)"
            >
              补充细节
            </el-button>
            <el-button
              type="primary"
              :icon="Check"
              :loading="quickSaving"
              :disabled="!quickCanSave"
              @click="saveQuickLog"
            >
              保存
            </el-button>
          </div>
        </div>
      </div>
    </div>
    <div class="hero-actions">
      <el-button size="large" :icon="Plus" @click="goCreateLog">完整表单</el-button>
      <el-button size="large" :icon="ChatDotRound" @click="openChat()">和小记聊聊</el-button>
    </div>
  </section>

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

  <section class="page-panel journal-panel">
    <div class="page-heading journal-heading">
      <div>
        <p class="eyebrow">Journal</p>
        <h2>日志列表</h2>
      </div>
      <el-button type="primary" :icon="Plus" @click="goCreateLog">新日志</el-button>
    </div>

    <div class="toolbar journal-toolbar">
      <el-input
        v-model="keyword"
        class="search-input"
        clearable
        :prefix-icon="Search"
        placeholder="搜索标题、正文、AI 总结"
        @keyup.enter="loadLogs"
        @clear="loadLogs"
      />
      <el-select
        v-model="selectedTag"
        class="tag-filter"
        clearable
        filterable
        placeholder="标签"
        @change="loadLogs"
        @clear="loadLogs"
      >
        <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
      </el-select>
      <el-date-picker
        v-model="selectedDate"
        class="date-filter"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="日期"
      />
      <el-switch
        v-model="favoriteOnly"
        active-text="收藏"
        @change="loadLogs"
      />
      <el-button :icon="Calendar" @click="loadLogs">筛选</el-button>
      <el-button @click="resetDate">清空</el-button>
    </div>

    <div class="desktop-only">
      <el-table
        v-loading="loading"
        class="journal-table"
        :data="logs"
        empty-text="还没有日志，先写下一件小小的好事吧。"
        @row-dblclick="handleRowDblClick"
      >
        <el-table-column label="日志" min-width="360">
          <template #default="{ row }">
            <div class="journal-title-cell">
              <div class="journal-title-line">
                <button type="button" @click="goDetail(row.id)">{{ row.title }}</button>
                <el-tag v-if="row.favorite" type="warning" effect="light">收藏</el-tag>
              </div>
              <p>{{ previewContent(row.content) }}</p>
              <div class="journal-row-meta">
                <span>{{ row.logDate }}</span>
                <span v-if="row.mood">{{ row.mood }}</span>
                <span v-if="row.images?.length">{{ row.images.length }} 张图片</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="AI 标签" min-width="170">
          <template #default="{ row }">
            <div v-if="splitTags(row.aiTags).length" class="compact-tag-row">
              <el-tag v-for="tag in splitTags(row.aiTags).slice(0, 3)" :key="tag" effect="light">{{ tag }}</el-tag>
            </div>
            <span v-else class="muted">待整理</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新" width="150">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="245" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                size="small"
                text
                :title="row.favorite ? '取消收藏' : '收藏'"
                :icon="Star"
                :type="row.favorite ? 'warning' : 'default'"
                @click="toggleFavorite(row)"
              />
              <el-button size="small" text :icon="View" @click="goDetail(row.id)">详情</el-button>
              <el-button size="small" text type="primary" :icon="ChatDotRound" @click="openChat(row)">小记</el-button>
              <el-button size="small" text :icon="Edit" @click="goEdit(row.id)">编辑</el-button>
              <el-button size="small" text title="删除" type="danger" :icon="Delete" @click="handleDelete(row)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="!loading" class="mobile-only log-card-list">
      <p v-if="!logs.length" class="log-card-empty">还没有日志，先写下一件小小的好事吧。</p>
      <div v-for="log in logs" :key="log.id" class="log-card">
        <div class="log-card-header">
          <span class="log-card-date">{{ log.logDate }}</span>
          <div class="log-card-tags">
            <el-tag v-if="log.favorite" type="warning" effect="light" size="small">收藏</el-tag>
            <el-tag
              v-for="tag in splitTags(log.aiTags).slice(0, 2)"
              :key="tag"
              effect="light"
              size="small"
            >{{ tag }}</el-tag>
          </div>
        </div>
        <button class="log-card-title" type="button" @click="goDetail(log.id)">{{ log.title }}</button>
        <p class="log-card-summary">{{ previewContent(log.content) }}</p>
        <div class="log-card-actions">
          <el-button size="small" text :icon="View" @click="goDetail(log.id)">详情</el-button>
          <el-button size="small" text type="primary" :icon="ChatDotRound" @click="openChat(log)">小记</el-button>
          <el-button size="small" text :icon="Edit" @click="goEdit(log.id)">编辑</el-button>
          <el-button
            size="small"
            text
            :icon="Star"
            :type="log.favorite ? 'warning' : 'default'"
            @click="toggleFavorite(log)"
          />
          <el-button size="small" text type="danger" :icon="Delete" @click="handleDelete(log)" />
        </div>
      </div>
    </div>
  </section>

  <el-drawer v-model="chatVisible" title="小记" size="420px" append-to-body>
    <div v-if="selectedLog" class="xiaoji-panel">
      <div ref="chatBodyRef" class="xiaoji-messages">
        <div
          v-for="(message, index) in chatMessages"
          :key="index"
          class="xiaoji-message"
          :class="`xiaoji-message-${message.role}`"
        >
          {{ message.content }}
        </div>
      </div>

      <div class="xiaoji-current">
        <strong>当前日志</strong>
        <p>{{ selectedLog.title }}｜{{ selectedLog.aiSummary || '还没有总结，可以直接告诉小记你想要什么样的总结。' }}</p>
      </div>

      <div v-if="previewSuggestion" class="xiaoji-preview">
        <div class="xiaoji-preview-heading">
          <strong>预览稿</strong>
          <div class="button-row">
            <el-button type="primary" :icon="Check" :loading="applyLoading" @click="applyPreview">
              应用到总结
            </el-button>
            <el-button :icon="RefreshLeft" :loading="applyLoading" :disabled="!lastAiSnapshot" @click="undoAiApply">
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
          v-model="chatInput"
          type="textarea"
          :rows="4"
          maxlength="300"
          show-word-limit
          placeholder="比如：这条总结再简洁一点，保留今天最重要的感受。"
          @keydown.ctrl.enter.prevent="sendChatMessage"
        />
        <div class="button-row xiaoji-actions">
          <el-button
            v-if="lastAiSnapshot"
            :icon="RefreshLeft"
            :loading="applyLoading"
            @click="undoAiApply"
          >
            撤销
          </el-button>
          <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="sendChatMessage">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>
