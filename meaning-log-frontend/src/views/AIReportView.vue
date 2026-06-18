<script setup lang="ts">
import { ChatDotRound, Check, Compass, MagicStick, Promotion, RefreshLeft, TrendCharts } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  applyAiReport,
  chatWithAiReportStream,
  generateAiReport,
  generateDailySummary,
  getAiReportChat,
  getAiReports,
  type AiChatMessage,
  type AiReport,
} from '../api/logs'

type ReportMode = 'weekly' | 'mood' | 'themes' | 'daily' | 'monthly' | 'custom'

interface ReportForm {
  mode: ReportMode
  date: string
  range: string[]
}

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

const defaultChatMessages: AiChatMessage[] = [
  {
    id: 0,
    role: 'assistant',
    content: '我是小记。你可以告诉我想把这份报告改得更短、更具体、更像复盘，或者换一种更自然的语气。',
    createdAt: '',
  },
]
const chatMessages = ref<AiChatMessage[]>([...defaultChatMessages])

const today = new Date().toISOString().slice(0, 10)
const form = reactive<ReportForm>({
  mode: 'weekly',
  date: today,
  range: [],
})

const rules: FormRules<ReportForm> = {
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  range: [{ required: true, message: '请选择日期范围', trigger: 'change' }],
}

const title = computed(() => {
  if (form.mode === 'mood') {
    return '最近情绪趋势'
  }
  if (form.mode === 'themes') {
    return '最近反复在意的事'
  }
  if (form.mode === 'weekly') {
    return '小记每周总结'
  }
  if (form.mode === 'monthly') {
    return '小记月度回顾'
  }
  if (form.mode === 'custom') {
    return '小记区间复盘'
  }
  return 'AI 当天总结'
})

const tagList = computed(() => splitTags(report.value?.tags))
const previewTagList = computed(() => splitTags(previewReport.value?.tags))

const splitTags = (tags?: string) => {
  if (!tags) {
    return []
  }
  return tags.split(',').map((tag) => tag.trim()).filter(Boolean)
}

const setRecentRange = (days: number) => {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - days + 1)
  form.range = [start.toISOString().slice(0, 10), end.toISOString().slice(0, 10)]
}

const companionCards = [
  {
    mode: 'weekly' as ReportMode,
    icon: MagicStick,
    title: '每周总结',
    description: '把这周发生的事、情绪变化和小进展轻轻收拢。',
    days: 7,
  },
  {
    mode: 'mood' as ReportMode,
    icon: TrendCharts,
    title: '最近情绪趋势',
    description: '看看最近哪些时刻让你紧绷，哪些事又把你接住。',
    days: 14,
  },
  {
    mode: 'themes' as ReportMode,
    icon: Compass,
    title: '我反复在意什么',
    description: '从多篇日志里找出反复出现的牵挂、消耗和期待。',
    days: 30,
  },
]

const selectCompanionCard = (mode: ReportMode, days: number) => {
  form.mode = mode
  setRecentRange(days)
}

const handleModeChange = () => {
  if (form.mode === 'weekly') {
    setRecentRange(7)
  }

  if (form.mode === 'mood') {
    setRecentRange(14)
  }

  if (form.mode === 'themes') {
    setRecentRange(30)
  }

  if (form.mode === 'monthly') {
    setRecentRange(30)
  }
}

const loadReports = async () => {
  historyLoading.value = true
  try {
    const { data } = await getAiReports()
    reports.value = data
    if (!report.value && data.length) {
      report.value = data[0]
    }
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
    if (form.mode === 'daily') {
      const { data } = await generateDailySummary(form.date)
      report.value = data
    } else {
      const [startDate, endDate] = form.range
      const { data } = await generateAiReport(startDate, endDate, title.value)
      report.value = data
    }
    previewReport.value = undefined
    lastReportSnapshot.value = undefined
    await loadReports()
    ElMessage.success('小记已经生成并保存报告啦')
  } finally {
    loading.value = false
  }
}

const currentReportSnapshot = (): AiReport | undefined => {
  if (!report.value) {
    return undefined
  }
  return { ...report.value }
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

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const sendChatMessage = async () => {
  const message = chatInput.value.trim()
  if (!message || !report.value?.id) {
    return
  }

  chatMessages.value.push({ id: Date.now(), role: 'user', content: message, createdAt: '' })
  chatInput.value = ''
  chatLoading.value = true
  await scrollChatToBottom()

  try {
    const suggestion = await chatWithAiReportStream(report.value.id, message)
    previewReport.value = suggestion
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
  if (!report.value?.id || !previewReport.value) {
    return
  }

  applyLoading.value = true
  try {
    lastReportSnapshot.value = currentReportSnapshot()
    const { data } = await applyAiReport(report.value.id, {
      ...previewReport.value,
      period: report.value.period,
    })
    report.value = data
    previewReport.value = undefined
    await loadReports()
    chatMessages.value.push({
      id: Date.now(),
      role: 'assistant',
      content: '这版已经保存到历史报告里了。如果刚保存完又觉得不对，可以点撤销回到上一版。',
      createdAt: '',
    })
    ElMessage.success('已应用到报告')
  } finally {
    applyLoading.value = false
    await scrollChatToBottom()
  }
}

const undoReportApply = async () => {
  if (!report.value?.id || !lastReportSnapshot.value) {
    return
  }

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

onMounted(async () => {
  setRecentRange(7)
  await loadReports()
})
</script>

<template>
  <section class="report-workspace">
    <aside class="report-history-panel" v-loading="historyLoading">
      <div class="report-history-heading">
        <div>
          <p class="eyebrow">History</p>
          <h2>历史报告</h2>
        </div>
      </div>

      <div class="report-history-list">
        <button
          v-for="item in reports"
          :key="item.id"
          class="report-history-item"
          :class="{ active: item.id === report?.id }"
          @click="selectReport(item)"
        >
          <strong>{{ item.title }}</strong>
          <span>{{ item.period }}</span>
          <small>{{ formatTime(item.createdAt) }}</small>
        </button>
      </div>
    </aside>

    <section class="page-panel report-page">
      <div class="page-heading">
        <div>
          <p class="eyebrow">Xiaoji Review</p>
          <h2>小记陪你回看最近</h2>
        </div>
      </div>

      <div class="companion-card-grid">
        <button
          v-for="card in companionCards"
          :key="card.mode"
          class="companion-card"
          :class="{ active: form.mode === card.mode }"
          type="button"
          @click="selectCompanionCard(card.mode, card.days)"
        >
          <component :is="card.icon" class="companion-card-icon" />
          <strong>{{ card.title }}</strong>
          <span>{{ card.description }}</span>
        </button>
      </div>

      <el-form ref="formRef" class="report-form" :model="form" :rules="rules" label-position="top">
        <el-form-item label="也可以自己选">
          <el-radio-group v-model="form.mode" @change="handleModeChange">
            <el-radio-button label="weekly">周总结</el-radio-button>
            <el-radio-button label="mood">情绪趋势</el-radio-button>
            <el-radio-button label="themes">反复在意</el-radio-button>
            <el-radio-button label="daily">当天</el-radio-button>
            <el-radio-button label="monthly">月度</el-radio-button>
            <el-radio-button label="custom">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.mode === 'daily'" label="日期" prop="date">
          <el-date-picker
            v-model="form.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
          />
        </el-form-item>

        <el-form-item v-else label="日期范围" prop="range">
          <el-date-picker
            v-model="form.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>

        <el-button type="primary" :icon="MagicStick" :loading="loading" @click="generate">
          让小记陪我看一看
        </el-button>
      </el-form>

      <el-empty v-if="!report" description="选一个方向，小记会从多篇日志里帮你看见最近的自己。" />

      <section v-else class="report-result">
        <div class="report-result-heading">
          <div>
            <p class="eyebrow">{{ report.period }}</p>
            <h2>{{ report.title }}</h2>
          </div>
          <div class="button-row">
            <el-button type="primary" plain :icon="ChatDotRound" @click="openChat">
              和小记修改
            </el-button>
            <el-button
              v-if="lastReportSnapshot"
              plain
              :icon="RefreshLeft"
              :loading="applyLoading"
              @click="undoReportApply"
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
    </section>
  </section>

  <el-drawer v-model="chatVisible" title="小记修改报告" size="460px" append-to-body>
    <div class="xiaoji-panel">
      <div ref="chatBodyRef" class="xiaoji-messages">
        <div
          v-for="message in chatMessages"
          :key="message.id || `${message.role}-${message.content}`"
          class="xiaoji-message"
          :class="`xiaoji-message-${message.role}`"
        >
          {{ message.content }}
        </div>
      </div>

      <div class="xiaoji-current">
        <strong>当前报告</strong>
        <p>{{ report?.title }}｜{{ report?.summary }}</p>
      </div>

      <div v-if="previewReport" class="xiaoji-preview">
        <div class="xiaoji-preview-heading">
          <strong>报告预览稿</strong>
          <div class="button-row">
            <el-button type="primary" :icon="Check" :loading="applyLoading" @click="applyPreview">
              应用到报告
            </el-button>
            <el-button :icon="RefreshLeft" :loading="applyLoading" :disabled="!lastReportSnapshot" @click="undoReportApply">
              撤销
            </el-button>
          </div>
        </div>
        <h3>{{ previewReport.title }}</h3>
        <p>{{ previewReport.summary }}</p>
        <div v-if="previewTagList.length" class="tag-row">
          <el-tag v-for="tag in previewTagList" :key="tag" effect="light">{{ tag }}</el-tag>
        </div>
      </div>

      <div class="xiaoji-input">
        <el-input
          v-model="chatInput"
          type="textarea"
          :rows="4"
          maxlength="400"
          show-word-limit
          placeholder="比如：这份周报再短一点，更突出情绪变化和下周可以继续的小事。"
          @keydown.ctrl.enter.prevent="sendChatMessage"
        />
        <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="sendChatMessage">
          发送
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>
