<script setup lang="ts">
import { useLogForm } from '../composables/useLogForm'
import type { MeaningLogRequest } from '../api/logs'

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

const {
  contentMode, draftStatus, form, handleDragStart, handleDrop, handleImageChange,
  handleSubmit, openImagePicker, removeImage, renderedContent, rules, setFormRef, setImageInputRef,
} = useLogForm(props, emit)
</script>

<template>
  <el-form :ref="setFormRef" class="log-form" :model="form" :rules="rules" label-position="top">
    <el-form-item label="标题（可选）" prop="title">
      <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="不写也行，小记会替你想一个" />
    </el-form-item>
    <div class="form-row">
      <el-form-item label="日期" prop="logDate">
        <el-date-picker v-model="form.logDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
      </el-form-item>
      <el-form-item label="心情" prop="mood">
        <el-input v-model="form.mood" maxlength="30" show-word-limit placeholder="开心、平静、充实..." />
      </el-form-item>
    </div>
    <el-form-item label="收藏"><el-switch v-model="form.favorite" active-text="收藏这条重要记录" /></el-form-item>
    <el-form-item label="内容" prop="content">
      <div class="writing-space">
        <div class="writing-toolbar">
          <span>{{ draftStatus }}</span>
          <el-segmented v-model="contentMode" :options="[{ label: '书写', value: 'write' }, { label: '预览', value: 'preview' }]" />
        </div>
        <el-input
          v-if="contentMode === 'write'" v-model="form.content" class="journal-textarea" type="textarea"
          :rows="14" resize="none" placeholder="可以像写给自己看的信一样写下来。支持换行，也可以用 # 小标题、- 列表、**加重**。"
        />
        <div v-else class="markdown-preview" v-html="renderedContent || '<p class=&quot;muted&quot;>还没有内容，先慢慢写一点。</p>'" />
      </div>
    </el-form-item>
    <el-form-item label="图片">
      <div class="image-upload-area">
        <input :ref="setImageInputRef" class="image-file-input" type="file" accept="image/*" multiple @change="handleImageChange" />
        <button class="image-upload-button" type="button" @click="openImagePicker">添加图片</button>
        <p class="image-upload-tip">可选，最多3张。上传后可以先看预览，也可以给每张图留一句说明。</p>
        <div v-if="form.images?.length" class="image-preview-grid">
          <div
            v-for="(image, index) in form.images" :key="`${image.fileName}-${index}`" class="image-preview-item"
            draggable="true" @dragstart="handleDragStart(index)" @dragover.prevent @drop="handleDrop(index)"
          >
            <img :src="image.dataUrl" :alt="image.fileName || '日志图片'" />
            <el-input v-model="image.caption" maxlength="160" placeholder="给这张图留一句话" />
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
