<script setup lang="ts">
import { ChatDotRound, Plus, Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  getXiaojiMessages,
  getXiaojiSessions,
  type AiChatMessage,
  type AiChatSession,
} from '../api/logs'
import { runXiaojiChatTask } from '../api/aiTask'

const sessions = ref<AiChatSession[]>([])
const messages = ref<AiChatMessage[]>([])
const activeSessionId = ref<number>()
const chatInput = ref('')
const loading = ref(false)
const sending = ref(false)
const chatBodyRef = ref<HTMLElement>()

const activeSession = computed(() => sessions.value.find((session) => session.id === activeSessionId.value))

const welcomeMessages: AiChatMessage[] = [
  {
    id: 0,
    role: 'assistant',
    content: '我是小记。这里不用绑定某篇日志，你可以直接和我聊今天发生了什么、心里在想什么，或者只是想找个安静的地方说说话。',
    createdAt: '',
  },
]

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const loadSessions = async () => {
  loading.value = true
  try {
    const { data } = await getXiaojiSessions()
    sessions.value = data
    if (!activeSessionId.value && sessions.value.length) {
      await openSession(sessions.value[0])
    }
    if (!sessions.value.length) {
      messages.value = [...welcomeMessages]
    }
  } finally {
    loading.value = false
  }
}

const openSession = async (session: AiChatSession) => {
  activeSessionId.value = session.id
  const { data } = await getXiaojiMessages(session.id)
  messages.value = data.length ? data : [...welcomeMessages]
  await scrollChatToBottom()
}

const startNewChat = () => {
  activeSessionId.value = undefined
  messages.value = [...welcomeMessages]
  chatInput.value = ''
}

const sendMessage = async () => {
  const message = chatInput.value.trim()
  if (!message) {
    return
  }

  messages.value.push({ id: Date.now(), role: 'user', content: message, createdAt: '' })
  chatInput.value = ''
  sending.value = true

  const placeholderIndex = messages.value.length
  messages.value.push({ id: Date.now() + 1, role: 'assistant', content: '小记正在思考…', createdAt: '' })
  await scrollChatToBottom()

  try {
    const response = await runXiaojiChatTask(message, activeSessionId.value)
    if (response.sessionId) {
      activeSessionId.value = response.sessionId
    }
    messages.value[placeholderIndex].content = response.reply || '小记暂时没有回应，请稍后再试。'
    await loadSessions()
  } catch {
    messages.value[placeholderIndex].content = '小记暂时没有回应，请稍后再试。'
    ElMessage.error('AI 服务暂时不可用，日志仍可正常记录')
  } finally {
    sending.value = false
    await scrollChatToBottom()
  }
}

const formatTime = (value: string) => value?.replace('T', ' ').slice(0, 16)

onMounted(async () => {
  await loadSessions()
  if (!messages.value.length) {
    messages.value = [...welcomeMessages]
  }
})
</script>

<template>
  <section class="xiaoji-chat-page">
    <aside class="xiaoji-session-panel" v-loading="loading">
      <div class="xiaoji-session-heading">
        <div>
          <p class="eyebrow">Xiaoji</p>
          <h2>小记对话</h2>
        </div>
        <el-button circle :icon="Plus" @click="startNewChat" />
      </div>

      <div class="xiaoji-session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          class="xiaoji-session-item"
          :class="{ active: session.id === activeSessionId }"
          @click="openSession(session)"
        >
          <ChatDotRound class="session-icon" />
          <span>{{ session.title }}</span>
          <small>{{ formatTime(session.updatedAt) }}</small>
        </button>
      </div>
    </aside>

    <section class="xiaoji-chat-panel">
      <div class="xiaoji-chat-heading">
        <div>
          <p class="eyebrow">Companion Chat</p>
          <h2>{{ activeSession?.title || '新的小记对话' }}</h2>
        </div>
      </div>

      <div ref="chatBodyRef" class="xiaoji-chat-messages">
        <div
          v-for="message in messages"
          :key="message.id || `${message.role}-${message.content}`"
          class="xiaoji-message"
          :class="`xiaoji-message-${message.role}`"
        >
          {{ message.content }}
        </div>
      </div>

      <div class="xiaoji-chat-input">
        <el-input
          v-model="chatInput"
          type="textarea"
          :rows="4"
          maxlength="600"
          show-word-limit
          placeholder="和小记说说吧。比如：今天有点累，但又不知道为什么。"
          @keydown.ctrl.enter.prevent="sendMessage"
        />
        <el-button type="primary" :icon="Promotion" :loading="sending" @click="sendMessage">
          发送
        </el-button>
      </div>
    </section>
  </section>
</template>
