<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, ChatDotRound, Check, Delete, Edit, MagicStick, Position, Promotion, RefreshLeft, Star } from '@element-plus/icons-vue'
import { useLogDetail } from '../composables/useLogDetail'
import { publishLog } from '../api/community'

const props = defineProps<{ id: number }>()
const {
  aiLoading, applyLoading, applyPreview, chatInput, chatLoading, chatMessages, chatVisible,
  formatTime, generateGentleTitle, goEdit, goLog, handleDelete, handleGenerateAi, lastAiSnapshot, loading,
  log, navigation, openChat, organizeMessyLog, previewImage, previewSuggestion, previewTagList,
  renderedLogContent, router, sendChatMessage, setChatBody, streamingText, tagList, toggleFavorite, undoAiApply,
} = useLogDetail(props)

const publishing = ref(false)

const handlePublish = async () => {
  try {
    await ElMessageBox.confirm('确定要把这条日志发布到社区吗？', '发布到社区', {
      confirmButtonText: '发布',
      cancelButtonText: '再想想',
      type: 'info',
    })
  } catch {
    return
  }
  publishing.value = true
  try {
    const { data } = await publishLog(props.id)
    ElMessage.success('已发布到社区')
    router.push({ name: 'community-post', params: { id: data.publicLogId } })
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <section v-loading="loading" class="page-panel">
    <template v-if="log">
      <div class="page-heading">
        <div><p class="eyebrow">{{ log.logDate }}</p><h2>{{ log.title }}</h2></div>
        <div class="button-row">
          <el-button @click="router.push({ name: 'home' })">返回列表</el-button>
          <el-button plain :icon="ArrowLeft" :disabled="!navigation?.previous" @click="goLog(navigation?.previous?.id)">上一条</el-button>
          <el-button plain :icon="ArrowRight" :disabled="!navigation?.next" @click="goLog(navigation?.next?.id)">下一条</el-button>
          <el-button type="success" :icon="MagicStick" :loading="aiLoading" @click="handleGenerateAi">AI 生成</el-button>
          <el-button :type="log.favorite ? 'warning' : 'default'" :icon="Star" @click="toggleFavorite">{{ log.favorite ? '已收藏' : '收藏' }}</el-button>
          <el-button type="success" plain :icon="Position" :loading="publishing" @click="handlePublish">发布到社区</el-button>
          <el-button type="primary" plain :icon="ChatDotRound" @click="openChat">小记</el-button>
          <el-button type="primary" :icon="Edit" @click="goEdit">编辑</el-button>
          <el-button type="danger" plain :icon="Delete" @click="handleDelete">删除</el-button>
        </div>
      </div>
      <div class="detail-meta">
        <el-tag v-if="log.mood" effect="light">{{ log.mood }}</el-tag>
        <span>创建：{{ formatTime(log.createdAt) }}</span><span>更新：{{ formatTime(log.updatedAt) }}</span>
      </div>
      <article class="detail-content markdown-preview" v-html="renderedLogContent" />
      <div v-if="log.images?.length" class="detail-image-grid">
        <img v-for="image in log.images" :key="image.id || image.dataUrl" :src="image.dataUrl" :alt="image.fileName || '日志图片'" @click="previewImage = image.dataUrl" />
      </div>
      <p v-if="log.images?.some((image) => image.caption)" class="detail-image-caption">
        <span v-for="(image, index) in log.images" :key="`${image.id}-caption`">{{ image.caption ? `第${index + 1}张：${image.caption}` : '' }}</span>
      </p>
      <section class="ai-section">
        <div class="section-heading">
          <div><p class="eyebrow">AI Insights</p><h2>小记的温柔整理</h2></div>
          <div class="button-row">
            <el-button :icon="MagicStick" :loading="aiLoading" @click="handleGenerateAi">整理这篇</el-button>
            <el-button :loading="chatLoading" @click="generateGentleTitle">起个温柔标题</el-button>
            <el-button :loading="chatLoading" @click="organizeMessyLog">整理清晰版</el-button>
            <el-button type="primary" plain :icon="ChatDotRound" @click="openChat">和小记聊聊</el-button>
            <el-button v-if="lastAiSnapshot" plain :icon="RefreshLeft" :loading="applyLoading" @click="undoAiApply">撤销</el-button>
          </div>
        </div>
        <div v-if="aiLoading && streamingText" class="ai-streaming"><p class="eyebrow">小记正在整理…</p><pre class="ai-streaming-text">{{ streamingText }}<span class="cursor">▋</span></pre></div>
        <el-empty v-else-if="!log.aiTitle && !log.aiSummary && !log.aiTags" description="还没有 AI 分析，点击 AI 生成，让小记帮你轻轻整理一下。" />
        <div v-else class="ai-grid">
          <div class="ai-block"><h3>AI 标题</h3><p>{{ log.aiTitle || '暂无' }}</p></div>
          <div class="ai-block"><h3>AI 标签</h3><div v-if="tagList.length" class="tag-row"><el-tag v-for="tag in tagList" :key="tag" effect="light">{{ tag }}</el-tag></div><p v-else>暂无</p></div>
          <div class="ai-block ai-block-wide"><h3>AI 总结</h3><p>{{ log.aiSummary || '暂无' }}</p></div>
        </div>
      </section>
      <el-drawer v-model="chatVisible" title="小记" size="420px" append-to-body>
        <div class="xiaoji-panel">
          <div :ref="setChatBody" class="xiaoji-messages"><div v-for="(message, index) in chatMessages" :key="index" class="xiaoji-message" :class="`xiaoji-message-${message.role}`">{{ message.content }}</div></div>
          <div class="xiaoji-current"><strong>当前总结</strong><p>{{ log.aiSummary || '还没有总结，可以先生成一次，或直接告诉小记你想要什么样的总结。' }}</p></div>
          <div v-if="previewSuggestion" class="xiaoji-preview">
            <div class="xiaoji-preview-heading"><strong>预览稿</strong><div class="button-row">
              <el-button type="primary" :icon="Check" :loading="applyLoading" @click="applyPreview">应用到总结</el-button>
              <el-button :icon="RefreshLeft" :loading="applyLoading" @click="undoAiApply" :disabled="!lastAiSnapshot">撤销</el-button>
            </div></div>
            <h3>{{ previewSuggestion.title }}</h3><p>{{ previewSuggestion.summary }}</p>
            <div v-if="previewTagList.length" class="tag-row"><el-tag v-for="tag in previewTagList" :key="tag" effect="light">{{ tag }}</el-tag></div>
          </div>
          <div class="xiaoji-input">
            <el-input v-model="chatInput" type="textarea" :rows="4" maxlength="300" show-word-limit placeholder="比如：写得更具体一点，少一点可爱，多一点复盘感。" @keydown.ctrl.enter.prevent="sendChatMessage" />
            <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="sendChatMessage">发送</el-button>
          </div>
        </div>
      </el-drawer>
      <el-dialog :model-value="Boolean(previewImage)" class="image-preview-dialog" width="min(920px, 92vw)" append-to-body @close="previewImage = undefined">
        <img v-if="previewImage" :src="previewImage" alt="图片预览" />
      </el-dialog>
    </template>
    <el-empty v-else-if="!loading" description="日志不存在" />
  </section>
</template>
