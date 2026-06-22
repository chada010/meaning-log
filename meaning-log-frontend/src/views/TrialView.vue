<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { isAxiosError } from 'axios'
import LogForm from '../components/LogForm.vue'
import type { MeaningLogRequest } from '../api/logs'
import { analyzeTrialLogStream, savePendingTrial, type TrialAiResult } from '../api/trial'
import { TRIAL_DRAFT_STORAGE_KEY } from '../constants/app'

const router = useRouter()
const draftKey = TRIAL_DRAFT_STORAGE_KEY

const analyzing = ref(false)
const aiResult = ref<TrialAiResult>()
const lastDraft = ref<MeaningLogRequest>()

const handleAnalyze = async (value: MeaningLogRequest) => {
  lastDraft.value = value
  analyzing.value = true
  try {
    aiResult.value = await analyzeTrialLogStream(value)
    ElMessage.success('小记替你整理好啦，看看喜不喜欢～')
  } catch (error) {
    if (error instanceof Error && error.message.includes('429')) {
      ElMessage.warning('试用整理太频繁啦，注册后可以无限使用')
    } else if (isAxiosError(error) && error.response?.status === 429) {
      ElMessage.warning('试用整理太频繁啦，注册后可以无限使用')
    }
    // 其余错误已由 http 拦截器统一提示
  } finally {
    analyzing.value = false
  }
}

const goRegister = () => {
  if (lastDraft.value) {
    savePendingTrial({ value: lastDraft.value, ai: aiResult.value })
  }
  router.push({ name: 'register' })
}

const goLogin = () => {
  if (lastDraft.value) {
    savePendingTrial({ value: lastDraft.value, ai: aiResult.value })
  }
  router.push({ name: 'login' })
}
</script>

<template>
  <section class="page-panel narrow-panel">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Try it first</p>
        <h2>先随便写写</h2>
        <p class="trial-lead">不用注册，先写一条小记，体验一次小记的 AI 整理。觉得好用，再注册把它收好。</p>
      </div>

      <el-button @click="goLogin">已有账号，去登录</el-button>
    </div>

    <LogForm
      :draft-key="draftKey"
      submit-label="让小记整理一下"
      :submitting="analyzing"
      @submit="handleAnalyze"
      @cancel="goLogin"
    />

    <div v-if="aiResult" class="trial-result">
      <p class="eyebrow">小记整理后的样子</p>
      <h3>{{ aiResult.title }}</h3>
      <p class="trial-summary">{{ aiResult.summary }}</p>
      <div v-if="aiResult.tags?.length" class="trial-tags">
        <el-tag v-for="tag in aiResult.tags" :key="tag" effect="light">{{ tag }}</el-tag>
      </div>

      <div class="trial-cta">
        <el-button type="primary" @click="goRegister">注册账号，保存这条小记</el-button>
        <el-button @click="goLogin">已有账号，去登录</el-button>
      </div>
      <p class="trial-hint">注册成功后，这条小记和上面的整理结果会自动保存为你的第一条日志。</p>
    </div>
  </section>
</template>

<style scoped>
.trial-lead {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  max-width: 520px;
}

.trial-result {
  margin-top: 24px;
  padding: 20px 22px;
  border-radius: 16px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.trial-result h3 {
  margin: 6px 0 10px;
}

.trial-summary {
  color: var(--el-text-color-regular);
  line-height: 1.7;
  white-space: pre-wrap;
}

.trial-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.trial-cta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 20px;
}

.trial-hint {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
