<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getLogDetail, updateLog, type MeaningLog, type MeaningLogRequest } from '../api/logs'
import LogForm from '../components/LogForm.vue'
import { getEditLogDraftStorageKey } from '../constants/app'

const props = defineProps<{
  id: number
}>()

const router = useRouter()
const log = ref<MeaningLog>()
const loading = ref(false)
const submitting = ref(false)

const initialValue = computed<MeaningLogRequest | undefined>(() => {
  if (!log.value) {
    return undefined
  }

  return {
    title: log.value.title ?? undefined,
    content: log.value.content,
    logDate: log.value.logDate,
    mood: log.value.mood ?? undefined,
    favorite: log.value.favorite,
    images: log.value.images,
  }
})

const loadDetail = async () => {
  loading.value = true
  try {
    const { data } = await getLogDetail(props.id)
    log.value = data
  } finally {
    loading.value = false
  }
}

const handleSubmit = async (value: MeaningLogRequest) => {
  submitting.value = true
  try {
    await updateLog(props.id, value)
    localStorage.removeItem(getEditLogDraftStorageKey(props.id))
    ElMessage.success('改动已经轻轻放回去了')
    router.push({ name: 'log-detail', params: { id: props.id } })
  } finally {
    submitting.value = false
  }
}

onMounted(loadDetail)
</script>

<template>
  <section v-loading="loading" class="page-panel narrow-panel">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Edit Entry</p>
        <h2>编辑日志</h2>
      </div>

      <el-button @click="$router.back()">返回</el-button>
    </div>

    <LogForm
      v-if="initialValue"
      :initial-value="initialValue"
      :draft-key="getEditLogDraftStorageKey(props.id)"
      :submitting="submitting"
      @submit="handleSubmit"
      @cancel="$router.back()"
    />

    <el-empty v-else-if="!loading" description="日志不存在" />
  </section>
</template>
