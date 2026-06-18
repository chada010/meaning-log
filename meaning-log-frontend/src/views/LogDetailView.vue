<script setup lang="ts">
import { ArrowLeft, ArrowRight, ChatDotRound, Check, Delete, Edit, MagicStick, Promotion, RefreshLeft, Star } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  applyLogAi,
  chatWithLogAiStream,
  deleteLog,
  generateLogAiStream,
  getLogAiChat,
  getLogDetail,
  getLogNavigation,
  updateLogFavorite,
  type AiChatMessage,
  type AiSuggestion,
  type LogNavigation,
  type MeaningLog,
} from '../api/logs'

const props = defineProps<{
  id: number
}>()

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
const defaultChatMessages: AiChatMessage[] = [
  {
    id: 0,
    role: 'assistant',
    content: '我是小记。你可以告诉我想把总结改得更简短、更具体、更温柔，或者更像某种口吻。',
    createdAt: '',
  },
]
const chatMessages = ref<AiChatMessage[]>([...defaultChatMessages])

const tagList = computed(() => {
  if (!log.value?.aiTags) {
    return []
  }

  return log.value.aiTags.split(',').map((tag) => tag.trim()).filter(Boolean)
})

const previewTagList = computed(() => previewSuggestion.value?.tags.filter(Boolean) ?? [])

const escapeHtml = (value: string) => value
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const renderInlineMarkdown = (value: string) => escapeHtml(value)
  .replace(/`([^`]+)`/g, '<code>$1</code>')
  .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  .replace(/\*([^*]+)\*/g, '<em>$1</em>')

const renderMarkdown = (value: string) => {
  const lines = value.split(/\r?\n/)
  const blocks: string[] = []
  let listItems: string[] = []

  const flushList = () => {
    if (!listItems.length) {
      return
    }
    blocks.push(`<ul>${listItems.join('')}</ul>`)
    listItems = []
  }

  for (const line of lines) {
    const trimmed = line.trim()
    if (!trimmed) {
      flushList()
      blocks.push('<br>')
      continue
    }

    const heading = trimmed.match(/^(#{1,3})\s+(.+)$/)
    if (heading) {
      flushList()
      const level = heading[1].length + 2
      blocks.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`)
      continue
    }

    const list = trimmed.match(/^[-*]\s+(.+)$/)
    if (list) {
      listItems.push(`<li>${renderInlineMarkdown(list[1])}</li>`)
      continue
    }

    flushList()
    blocks.push(`<p>${renderInlineMarkdown(line)}</p>`)
  }

  flushList()
  return blocks.join('')
}

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
    const [{ data: detail }, { data: nav }] = await Promise.all([
      getLogDetail(props.id),
      getLogNavigation(props.id),
    ])
    log.value = detail
    navigation.value = nav
  } finally {
    loading.value = false
  }
}

const goEdit = () => {
  router.push({ name: 'log-edit', params: { id: props.id } })
}

const goLog = (id?: number) => {
  if (id) {
    router.push({ name: 'log-detail', params: { id } })
  }
}

const handleGenerateAi = async () => {
  aiLoading.value = true
  streamingText.value = ''
  try {
    lastAiSnapshot.value = currentAiSnapshot()
    const suggestion = await generateLogAiStream(props.id, chunk => {
      streamingText.value += chunk
    })
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

const askXiaojiForLog = async (message: string) => {
  chatVisible.value = true
  chatMessages.value.push({ id: Date.now(), role: 'user', content: message, createdAt: '' })
  chatLoading.value = true
  await scrollChatToBottom()

  try {
    const suggestion = await chatWithLogAiStream(props.id, message)
    previewSuggestion.value = suggestion
    const { data } = await getLogAiChat(props.id)
    chatMessages.value = data.messages.length ? data.messages : chatMessages.value
    ElMessage.success('小记整理出一版预览了')
  } catch {
    ElMessage.error('AI 服务暂时不可用，日志仍可正常记录')
  } finally {
    chatLoading.value = false
    await scrollChatToBottom()
  }
}

const generateGentleTitle = () => {
  askXiaojiForLog('请只重点帮我生成一个更温柔、有画面感、适合回顾的标题，同时保留简短总结和标签。')
}

const organizeMessyLog = () => {
  askXiaojiForLog('请把这篇有点混乱的记录整理成更清晰的版本：提炼主线、保留真实细节，语气温柔自然，不要夸张。')
}

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

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const sendChatMessage = async () => {
  const message = chatInput.value.trim()
  if (!message) {
    return
  }

  chatMessages.value.push({ id: Date.now(), role: 'user', content: message, createdAt: '' })
  chatInput.value = ''
  chatLoading.value = true
  await scrollChatToBottom()

  try {
    const suggestion = await chatWithLogAiStream(props.id, message)
    previewSuggestion.value = suggestion
    const { data } = await getLogAiChat(props.id)
    chatMessages.value = data.messages.length ? data.messages : chatMessages.value
    ElMessage.success('小记生成了一版预览')
  } catch {
    ElMessage.error('AI 服务暂时不可用，日志仍可正常记录')
  } finally {
    chatLoading.value = false
    await scrollChatToBottom()
  }
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
      id: Date.now(),
      role: 'assistant',
      content: '这版已经保存到日志里了。如果刚保存完又觉得不对，可以点撤销回到上一版。',
      createdAt: '',
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
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })

  await deleteLog(log.value.id)
  ElMessage.success('已删除')
  router.push({ name: 'home' })
}

const formatTime = (value: string) => value.replace('T', ' ').slice(0, 16)

onMounted(loadDetail)

watch(() => props.id, loadDetail)
</script>

<template>
  <section v-loading="loading" class="page-panel">
    <template v-if="log">
      <div class="page-heading">
        <div>
          <p class="eyebrow">{{ log.logDate }}</p>
          <h2>{{ log.title }}</h2>
        </div>

        <div class="button-row">
          <el-button @click="router.push({ name: 'home' })">返回列表</el-button>
          <el-button
            plain
            :icon="ArrowLeft"
            :disabled="!navigation?.previous"
            @click="goLog(navigation?.previous?.id)"
          >
            上一条
          </el-button>
          <el-button
            plain
            :icon="ArrowRight"
            :disabled="!navigation?.next"
            @click="goLog(navigation?.next?.id)"
          >
            下一条
          </el-button>
          <el-button type="success" :icon="MagicStick" :loading="aiLoading" @click="handleGenerateAi">
            AI 生成
          </el-button>
          <el-button
            :type="log.favorite ? 'warning' : 'default'"
            :icon="Star"
            @click="toggleFavorite"
          >
            {{ log.favorite ? '已收藏' : '收藏' }}
          </el-button>
          <el-button type="primary" plain :icon="ChatDotRound" @click="openChat">小记</el-button>
          <el-button type="primary" :icon="Edit" @click="goEdit">编辑</el-button>
          <el-button type="danger" plain :icon="Delete" @click="handleDelete">删除</el-button>
        </div>
      </div>

      <div class="detail-meta">
        <el-tag v-if="log.mood" effect="light">{{ log.mood }}</el-tag>
        <span>创建：{{ formatTime(log.createdAt) }}</span>
        <span>更新：{{ formatTime(log.updatedAt) }}</span>
      </div>

      <article class="detail-content markdown-preview" v-html="renderedLogContent" />

      <div v-if="log.images?.length" class="detail-image-grid">
        <img
          v-for="image in log.images"
          :key="image.id || image.dataUrl"
          :src="image.dataUrl"
          :alt="image.fileName || '日志图片'"
          @click="previewImage = image.dataUrl"
        />
      </div>
      <p v-if="log.images?.some((image) => image.caption)" class="detail-image-caption">
        <span v-for="(image, index) in log.images" :key="`${image.id}-caption`">
          {{ image.caption ? `第${index + 1}张：${image.caption}` : '' }}
        </span>
      </p>

      <section class="ai-section">
        <div class="section-heading">
          <div>
            <p class="eyebrow">AI Insights</p>
            <h2>小记的温柔整理</h2>
          </div>
          <div class="button-row">
            <el-button :icon="MagicStick" :loading="aiLoading" @click="handleGenerateAi">
              整理这篇
            </el-button>
            <el-button :loading="chatLoading" @click="generateGentleTitle">
              起个温柔标题
            </el-button>
            <el-button :loading="chatLoading" @click="organizeMessyLog">
              整理清晰版
            </el-button>
            <el-button type="primary" plain :icon="ChatDotRound" @click="openChat">
              和小记聊聊
            </el-button>
            <el-button
              v-if="lastAiSnapshot"
              plain
              :icon="RefreshLeft"
              :loading="applyLoading"
              @click="undoAiApply"
            >
              撤销
            </el-button>
          </div>
        </div>

        <div v-if="aiLoading && streamingText" class="ai-streaming">
          <p class="eyebrow">小记正在整理…</p>
          <pre class="ai-streaming-text">{{ streamingText }}<span class="cursor">▋</span></pre>
        </div>

        <el-empty
          v-else-if="!log.aiTitle && !log.aiSummary && !log.aiTags"
          description="还没有 AI 分析，点击 AI 生成，让小记帮你轻轻整理一下。"
        />

        <div v-else class="ai-grid">
          <div class="ai-block">
            <h3>AI 标题</h3>
            <p>{{ log.aiTitle || '暂无' }}</p>
          </div>

          <div class="ai-block">
            <h3>AI 标签</h3>
            <div v-if="tagList.length" class="tag-row">
              <el-tag v-for="tag in tagList" :key="tag" effect="light">{{ tag }}</el-tag>
            </div>
            <p v-else>暂无</p>
          </div>

          <div class="ai-block ai-block-wide">
            <h3>AI 总结</h3>
            <p>{{ log.aiSummary || '暂无' }}</p>
          </div>
        </div>
      </section>

      <el-drawer v-model="chatVisible" title="小记" size="420px" append-to-body>
        <div class="xiaoji-panel">
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
            <strong>当前总结</strong>
            <p>{{ log.aiSummary || '还没有总结，可以先生成一次，或直接告诉小记你想要什么样的总结。' }}</p>
          </div>

          <div v-if="previewSuggestion" class="xiaoji-preview">
            <div class="xiaoji-preview-heading">
              <strong>预览稿</strong>
              <div class="button-row">
                <el-button type="primary" :icon="Check" :loading="applyLoading" @click="applyPreview">
                  应用到总结
                </el-button>
                <el-button :icon="RefreshLeft" :loading="applyLoading" @click="undoAiApply" :disabled="!lastAiSnapshot">
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
              placeholder="比如：写得更具体一点，少一点可爱，多一点复盘感。"
              @keydown.ctrl.enter.prevent="sendChatMessage"
            />
            <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="sendChatMessage">
              发送
            </el-button>
          </div>
        </div>
      </el-drawer>

      <el-dialog
        :model-value="Boolean(previewImage)"
        class="image-preview-dialog"
        width="min(920px, 92vw)"
        append-to-body
        @close="previewImage = undefined"
      >
        <img v-if="previewImage" :src="previewImage" alt="图片预览" />
      </el-dialog>
    </template>

    <el-empty v-else-if="!loading" description="日志不存在" />
  </section>
</template>
