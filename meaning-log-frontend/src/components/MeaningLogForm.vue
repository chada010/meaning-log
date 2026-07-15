<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { MeaningLogForm } from '../types/meaningLog'

const props = defineProps<{
  initialValue?: MeaningLogForm
  submitText: string
}>()

const emit = defineEmits<{
  submit: [value: MeaningLogForm]
  cancel: []
}>()

const formRef = ref<FormInstance>()
const form = reactive<MeaningLogForm>({
  logDate: props.initialValue?.logDate ?? new Date().toISOString().slice(0, 10),
  title: props.initialValue?.title ?? '',
  content: props.initialValue?.content ?? '',
})

const rules: FormRules<MeaningLogForm> = {
  logDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  title: [{ max: 50, message: '标题不能超过 50 个字符', trigger: 'blur' }],
  content: [{ required: true, message: '请输入有意义的事情', trigger: 'blur' }],
}

const handleSubmit = async () => {
  if (!formRef.value) {
    return
  }

  await formRef.value.validate()
  emit('submit', { ...form })
}
</script>

<template>
  <el-form ref="formRef" class="log-form" :model="form" :rules="rules" label-position="top">
    <el-form-item label="日期" prop="logDate">
      <el-date-picker
        v-model="form.logDate"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
      />
    </el-form-item>

    <el-form-item label="标题（可选）" prop="title">
      <el-input v-model="form.title" maxlength="50" placeholder="不写也行，小记会替你想一个" show-word-limit />
    </el-form-item>

    <el-form-item label="有意义的事情" prop="content">
      <el-input
        v-model="form.content"
        type="textarea"
        :rows="8"
        maxlength="1000"
        placeholder="写下今天发生了什么、为什么它对你有意义。"
        show-word-limit
      />
    </el-form-item>

    <div class="form-actions">
      <el-button @click="emit('cancel')">取消</el-button>
      <el-button type="primary" @click="handleSubmit">{{ submitText }}</el-button>
    </div>
  </el-form>
</template>
