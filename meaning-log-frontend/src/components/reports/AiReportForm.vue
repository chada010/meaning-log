<script setup lang="ts">
import { MagicStick } from '@element-plus/icons-vue'
import type { ComponentPublicInstance } from 'vue'
import type { FormRules } from 'element-plus'

interface ReportForm {
  mode: 'weekly' | 'mood' | 'themes' | 'daily' | 'monthly' | 'custom'
  date: string
  range: string[]
}

defineProps<{
  form: ReportForm
  rules: FormRules<ReportForm>
  loading: boolean
  setFormRef: (value: Element | ComponentPublicInstance | null) => void
}>()

const emit = defineEmits<{
  modeChange: []
  generate: []
}>()
</script>

<template>
  <el-form
    :ref="setFormRef"
    class="report-form"
    :model="form"
    :rules="rules"
    label-position="top"
  >
    <el-form-item label="也可以自己选">
      <el-radio-group v-model="form.mode" class="report-mode-picker" @change="emit('modeChange')">
        <el-radio-button label="weekly">周总结</el-radio-button>
        <el-radio-button label="mood">情绪趋势</el-radio-button>
        <el-radio-button label="themes">反复在意</el-radio-button>
        <el-radio-button label="daily">当天</el-radio-button>
        <el-radio-button label="monthly">月度</el-radio-button>
        <el-radio-button label="custom">自定义</el-radio-button>
      </el-radio-group>
    </el-form-item>
    <el-form-item v-if="form.mode === 'daily'" label="日期" prop="date">
      <el-date-picker
        v-model="form.date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
      />
    </el-form-item>
    <el-form-item v-else label="日期范围" prop="range">
      <el-date-picker
        v-model="form.range"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
      />
    </el-form-item>
    <el-button
      class="report-generate-btn"
      type="primary"
      :icon="MagicStick"
      :loading="loading"
      @click="emit('generate')"
    >
      让小记陪我看一看
    </el-button>
  </el-form>
</template>
