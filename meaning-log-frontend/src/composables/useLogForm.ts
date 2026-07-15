import { computed, onBeforeUnmount, onMounted, reactive, ref, type ComponentPublicInstance, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { LogImageRequest, MeaningLogRequest } from '../api/logs'
import { parseLogDraft, writeLogDraft } from '../utils/logDraft'
import { formatLocalDate } from '../utils/localDate'
import { renderMarkdown } from '../utils/markdown'

interface LogFormProps {
  initialValue?: MeaningLogRequest
  draftKey?: string
}

interface LogFormEmit {
  (event: 'submit', value: MeaningLogRequest): void
}

const rules: FormRules<MeaningLogRequest> = {
  title: [{ max: 100, message: '标题不能超过 100 个字符', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
  logDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  mood: [{ max: 30, message: '心情不能超过 30 个字符', trigger: 'blur' }],
}

export function useLogForm(props: LogFormProps, emit: LogFormEmit) {
  const formRef = ref<FormInstance>()
  const imageInputRef = ref<HTMLInputElement>()
  const form = reactive<MeaningLogRequest>({
    title: '',
    content: '',
    logDate: formatLocalDate(new Date()),
    mood: '',
    favorite: false,
    images: [],
  })
  const restoredDraft = ref(false)
  const draggedImageIndex = ref<number>()
  const contentMode = ref<'write' | 'preview'>('write')
  const draftStatus = ref('草稿会自动保存，图片不会进入草稿')
  const skipDraftOnUnmount = ref(false)
  let draftTimer: ReturnType<typeof window.setTimeout> | undefined

  const writeDraft = (value: MeaningLogRequest) => {
    if (!props.draftKey || skipDraftOnUnmount.value) {
      return 'removed' as const
    }
    return writeLogDraft(localStorage, props.draftKey, value)
  }

  watch(() => props.initialValue, (value) => {
    if (!value || restoredDraft.value) {
      return
    }
    form.title = value.title || ''
    form.content = value.content
    form.logDate = value.logDate
    form.mood = value.mood || ''
    form.favorite = value.favorite ?? false
    form.images = value.images ? [...value.images] : []
  }, { immediate: true })

  onMounted(() => {
    if (!props.draftKey) {
      return
    }
    const rawDraft = localStorage.getItem(props.draftKey)
    if (!rawDraft) {
      return
    }
    try {
      const draft = parseLogDraft(rawDraft)
      if (!draft || (!draft.title?.trim() && !draft.content?.trim() && !draft.mood?.trim() && !draft.favorite)) {
        localStorage.removeItem(props.draftKey)
        return
      }
      form.title = draft.title || form.title
      form.content = draft.content || form.content
      form.logDate = draft.logDate || form.logDate
      form.mood = draft.mood || ''
      form.favorite = draft.favorite ?? form.favorite
      restoredDraft.value = true
      draftStatus.value = draft.imageCount > 0
        ? '已恢复文本草稿，图片需要重新选择'
        : '已接上上次没写完的草稿'
      ElMessage.info(draft.imageCount > 0 ? '已恢复文本草稿，图片不会自动恢复' : '接上次没写完的地方了')
    } catch {
      localStorage.removeItem(props.draftKey)
    }
  })

  watch(form, (value) => {
    if (!props.draftKey) {
      return
    }
    draftStatus.value = '正在保存草稿...'
    if (draftTimer) {
      window.clearTimeout(draftTimer)
    }
    draftTimer = window.setTimeout(() => {
      const result = writeDraft(value)
      if (result === 'quota-exceeded') {
        draftStatus.value = '存储空间不足，文本草稿保存失败'
        return
      }
      if (result === 'failed') {
        draftStatus.value = '草稿保存失败，请保留当前页面'
        return
      }
      draftStatus.value = result === 'saved'
        ? `文本草稿已自动保存 ${new Date().toLocaleTimeString('zh-CN', {
            hour: '2-digit', minute: '2-digit',
          })}`
        : '草稿会自动保存，图片不会进入草稿'
    }, 450)
  }, { deep: true })

  onBeforeUnmount(() => {
    if (draftTimer) {
      window.clearTimeout(draftTimer)
    }
    writeDraft(form)
  })

  const renderedContent = computed(() => renderMarkdown(form.content))
  const setFormRef = (value: Element | ComponentPublicInstance | null) => {
    formRef.value = value as FormInstance | undefined
  }
  const setImageInputRef = (value: Element | ComponentPublicInstance | null) => {
    imageInputRef.value = value instanceof HTMLInputElement ? value : undefined
  }
  const openImagePicker = () => imageInputRef.value?.click()

  const readFileAsDataUrl = (file: File) => new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })

  const compressImage = async (file: File) => {
    if (file.type === 'image/gif') {
      return { dataUrl: await readFileAsDataUrl(file), contentType: file.type, fileSize: file.size }
    }
    const sourceDataUrl = await readFileAsDataUrl(file)
    const image = await new Promise<HTMLImageElement>((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = reject
      img.src = sourceDataUrl
    })
    const scale = Math.min(1, 1600 / Math.max(image.width, image.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(image.width * scale))
    canvas.height = Math.max(1, Math.round(image.height * scale))
    canvas.getContext('2d')?.drawImage(image, 0, 0, canvas.width, canvas.height)
    const dataUrl = canvas.toDataURL('image/jpeg', 0.78)
    return {
      dataUrl,
      contentType: 'image/jpeg',
      fileSize: Math.round((dataUrl.length - dataUrl.indexOf(',') - 1) * 0.75),
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
    const nextImages: LogImageRequest[] = []
    for (const file of files.slice(0, remaining)) {
      if (!file.type.startsWith('image/')) {
        ElMessage.warning(`${file.name} 不是图片文件`)
        continue
      }
      const compressed = await compressImage(file)
      if (compressed.fileSize > 2 * 1024 * 1024) {
        ElMessage.warning(`${file.name} 压缩后仍超过 2MB`)
        continue
      }
      nextImages.push({ fileName: file.name, caption: '', ...compressed })
    }
    form.images = [...(form.images ?? []), ...nextImages]
  }

  const removeImage = (index: number) => {
    form.images = (form.images ?? []).filter((_, imageIndex) => imageIndex !== index)
  }
  const handleDragStart = (index: number) => { draggedImageIndex.value = index }
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
      title: form.title?.trim() || undefined,
      content: form.content.trim(),
      logDate: form.logDate,
      mood: form.mood?.trim() || undefined,
      favorite: form.favorite,
      images: form.images?.map((image) => ({
        fileName: image.fileName,
        caption: image.caption,
        contentType: image.contentType,
        dataUrl: image.dataUrl,
      })),
    })
  }

  return {
    contentMode, draftStatus, form, formRef, handleDragStart, handleDrop, handleImageChange,
    handleSubmit, openImagePicker, removeImage, renderedContent, rules, setFormRef, setImageInputRef,
  }
}
