<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { LogImage, MeaningLogRequest } from '../api/logs'

interface LogDraft {
  value: MeaningLogRequest
  savedAt: number
}

const props = defineProps<{
  initialValue?: MeaningLogRequest
  draftKey?: string
  submitting?: boolean
  submitLabel?: string
}>()

const emit = defineEmits<{
  submit: [value: MeaningLogRequest]
  cancel: []
}>()

const formRef = ref<FormInstance>()
const imageInputRef = ref<HTMLInputElement>()

const form = reactive<MeaningLogRequest>({
  title: '',
  content: '',
  logDate: new Date().toISOString().slice(0, 10),
  mood: '',
  favorite: false,
  images: [],
})
const restoredDraft = ref(false)
const draggedImageIndex = ref<number>()
const contentMode = ref<'write' | 'preview'>('write')
const draftStatus = ref('草稿会自动保存')
const skipDraftOnUnmount = ref(false)
let draftTimer: ReturnType<typeof window.setTimeout> | undefined

const rules: FormRules<MeaningLogRequest> = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过 100 个字符', trigger: 'blur' },
  ],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  logDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  mood: [{ max: 30, message: '心情不能超过 30 个字符', trigger: 'blur' }],
}

const isEmptyDraft = (value: MeaningLogRequest) => (
  !value.title?.trim() &&
  !value.content?.trim() &&
  !value.mood?.trim() &&
  !value.favorite &&
  !value.images?.length
)

const readDraft = (rawDraft: string): MeaningLogRequest | undefined => {
  const parsed = JSON.parse(rawDraft) as Partial<LogDraft> | MeaningLogRequest

  if ('value' in parsed && parsed.value && typeof parsed.savedAt === 'number') {
    return parsed.value
  }

  return undefined
}

const writeDraft = (value: MeaningLogRequest) => {
  if (!props.draftKey || skipDraftOnUnmount.value) {
    return
  }

  if (isEmptyDraft(value)) {
    localStorage.removeItem(props.draftKey)
    return
  }

  const draft: LogDraft = {
    value,
    savedAt: Date.now(),
  }
  localStorage.setItem(props.draftKey, JSON.stringify(draft))
}

watch(
  () => props.initialValue,
  (value) => {
    if (!value) {
      return
    }
    if (restoredDraft.value) {
      return
    }

    form.title = value.title
    form.content = value.content
    form.logDate = value.logDate
    form.mood = value.mood || ''
    form.favorite = value.favorite ?? false
    form.images = value.images ? [...value.images] : []
  },
  { immediate: true },
)

onMounted(() => {
  if (!props.draftKey) {
    return
  }

  const rawDraft = localStorage.getItem(props.draftKey)
  if (!rawDraft) {
    return
  }

  try {
    const draft = readDraft(rawDraft)
    if (!draft || isEmptyDraft(draft)) {
      localStorage.removeItem(props.draftKey)
      return
    }

    form.title = draft.title || form.title
    form.content = draft.content || form.content
    form.logDate = draft.logDate || form.logDate
    form.mood = draft.mood || ''
    form.favorite = draft.favorite ?? form.favorite
    form.images = draft.images || form.images
    restoredDraft.value = true
    draftStatus.value = '已接上上次没写完的草稿'
    ElMessage.info('接上次没写完的地方了')
  } catch {
    localStorage.removeItem(props.draftKey)
  }
})

watch(
  form,
  (value) => {
    if (!props.draftKey) {
      return
    }
    draftStatus.value = '正在保存草稿...'
    if (draftTimer) {
      window.clearTimeout(draftTimer)
    }
    draftTimer = window.setTimeout(() => {
      writeDraft(value)
      draftStatus.value = `草稿已自动保存 ${new Date().toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
      })}`
    }, 450)
  },
  { deep: true },
)

onBeforeUnmount(() => {
  if (draftTimer) {
    window.clearTimeout(draftTimer)
  }
  writeDraft(form)
})

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

const renderedContent = computed(() => {
  const lines = form.content.split(/\r?\n/)
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
})

const openImagePicker = () => {
  imageInputRef.value?.click()
}

const readFileAsDataUrl = (file: File) => {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

const compressImage = async (file: File) => {
  if (file.type === 'image/gif') {
    return {
      dataUrl: await readFileAsDataUrl(file),
      contentType: file.type,
      fileSize: file.size,
    }
  }

  const sourceDataUrl = await readFileAsDataUrl(file)
  const image = await new Promise<HTMLImageElement>((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = sourceDataUrl
  })

  const maxSide = 1600
  const scale = Math.min(1, maxSide / Math.max(image.width, image.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(image.width * scale))
  canvas.height = Math.max(1, Math.round(image.height * scale))
  const context = canvas.getContext('2d')
  context?.drawImage(image, 0, 0, canvas.width, canvas.height)

  const dataUrl = canvas.toDataURL('image/jpeg', 0.78)
  const fileSize = Math.round((dataUrl.length - dataUrl.indexOf(',') - 1) * 0.75)
  return {
    dataUrl,
    contentType: 'image/jpeg',
    fileSize,
  }
}

const handleImageChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''

  if (!files.length) {
    return
  }

  const remaining = 3 - (form.images?.length ?? 0)
  if (remaining <= 0) {
    ElMessage.warning('最多上传3张图片')
    return
  }

  const acceptedFiles = files.slice(0, remaining)
  const nextImages: LogImage[] = []

  for (const file of acceptedFiles) {
    if (!file.type.startsWith('image/')) {
      ElMessage.warning(`${file.name} 不是图片文件`)
      continue
    }

    const compressed = await compressImage(file)
    if (compressed.fileSize > 2 * 1024 * 1024) {
      ElMessage.warning(`${file.name} 压缩后仍超过 2MB`)
      continue
    }

    nextImages.push({
      fileName: file.name,
      caption: '',
      contentType: compressed.contentType,
      fileSize: compressed.fileSize,
      dataUrl: compressed.dataUrl,
    })
  }

  form.images = [...(form.images ?? []), ...nextImages]
}

const removeImage = (index: number) => {
  form.images = (form.images ?? []).filter((_, imageIndex) => imageIndex !== index)
}

const handleDragStart = (index: number) => {
  draggedImageIndex.value = index
}

const handleDrop = (targetIndex: number) => {
  if (draggedImageIndex.value === undefined || draggedImageIndex.value === targetIndex || !form.images) {
    return
  }

  const nextImages = [...form.images]
  const [draggedImage] = nextImages.splice(draggedImageIndex.value, 1)
  nextImages.splice(targetIndex, 0, draggedImage)
  form.images = nextImages
  draggedImageIndex.value = undefined
}

const handleSubmit = async () => {
  if (!formRef.value) {
    return
  }

  await formRef.value.validate()
  skipDraftOnUnmount.value = true
  if (draftTimer) {
    window.clearTimeout(draftTimer)
  }
  emit('submit', {
    title: form.title.trim(),
    content: form.content.trim(),
    logDate: form.logDate,
    mood: form.mood?.trim() || undefined,
    favorite: form.favorite,
    images: form.images,
  })
}
</script>

<template>
  <el-form ref="formRef" class="log-form" :model="form" :rules="rules" label-position="top">
    <el-form-item label="标题" prop="title">
      <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="今天有什么值得记住的小事？" />
    </el-form-item>

    <div class="form-row">
      <el-form-item label="日期" prop="logDate">
        <el-date-picker
          v-model="form.logDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
        />
      </el-form-item>

      <el-form-item label="心情" prop="mood">
        <el-input v-model="form.mood" maxlength="30" show-word-limit placeholder="开心、平静、充实..." />
      </el-form-item>
    </div>

    <el-form-item label="收藏">
      <el-switch v-model="form.favorite" active-text="收藏这条重要记录" />
    </el-form-item>

    <el-form-item label="内容" prop="content">
      <div class="writing-space">
        <div class="writing-toolbar">
          <span>{{ draftStatus }}</span>
          <el-segmented
            v-model="contentMode"
            :options="[
              { label: '书写', value: 'write' },
              { label: '预览', value: 'preview' },
            ]"
          />
        </div>
        <el-input
          v-if="contentMode === 'write'"
          v-model="form.content"
          class="journal-textarea"
          type="textarea"
          :rows="14"
          resize="none"
          placeholder="可以像写给自己看的信一样写下来。支持换行，也可以用 # 小标题、- 列表、**加重**。"
        />
        <div v-else class="markdown-preview" v-html="renderedContent || '<p class=&quot;muted&quot;>还没有内容，先慢慢写一点。</p>'" />
      </div>
    </el-form-item>

    <el-form-item label="图片">
      <div class="image-upload-area">
        <input
          ref="imageInputRef"
          class="image-file-input"
          type="file"
          accept="image/*"
          multiple
          @change="handleImageChange"
        />
        <button class="image-upload-button" type="button" @click="openImagePicker">
          添加图片
        </button>
        <p class="image-upload-tip">可选，最多3张。上传后可以先看预览，也可以给每张图留一句说明。</p>
        <div v-if="form.images?.length" class="image-preview-grid">
          <div
            v-for="(image, index) in form.images"
            :key="`${image.fileName}-${index}`"
            class="image-preview-item"
            draggable="true"
            @dragstart="handleDragStart(index)"
            @dragover.prevent
            @drop="handleDrop(index)"
          >
            <img :src="image.dataUrl" :alt="image.fileName || '日志图片'" />
            <el-input
              v-model="image.caption"
              maxlength="160"
              placeholder="给这张图留一句话"
            />
            <button type="button" @click="removeImage(index)">删除</button>
          </div>
        </div>
      </div>
    </el-form-item>

    <div class="form-actions">
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ submitLabel ?? '保存日志' }}</el-button>
    </div>
  </el-form>
</template>
