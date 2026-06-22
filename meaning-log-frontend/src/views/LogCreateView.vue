<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createLog, type MeaningLogRequest } from '../api/logs'
import LogForm from '../components/LogForm.vue'
import { LEGACY_NEW_LOG_DRAFT_STORAGE_KEY, NEW_LOG_DRAFT_STORAGE_KEY } from '../constants/app'

const router = useRouter()
const submitting = ref(false)
const draftKey = NEW_LOG_DRAFT_STORAGE_KEY

const handleSubmit = async (value: MeaningLogRequest) => {
  submitting.value = true
  try {
    await createLog(value)
    localStorage.removeItem(draftKey)
    localStorage.removeItem(LEGACY_NEW_LOG_DRAFT_STORAGE_KEY)
    ElMessage.success('已经替你收好啦')
    router.push({ name: 'home' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="page-panel narrow-panel">
    <div class="page-heading">
      <div>
        <p class="eyebrow">New Entry</p>
        <h2>新增日志</h2>
      </div>

      <el-button @click="$router.back()">返回</el-button>
    </div>

    <LogForm :draft-key="draftKey" :submitting="submitting" @submit="handleSubmit" @cancel="$router.back()" />
  </section>
</template>
