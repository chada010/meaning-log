<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/authStore'
import { persistPendingTrial } from '../api/trial'
import { sendVerificationCode } from '../api/auth'
import type { RegisterRequest } from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)

const form = reactive<RegisterRequest & { confirmPassword: string }>({
  email: '',
  username: '',
  password: '',
  verificationCode: '',
  confirmPassword: '',
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度为 2 到 50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码至少 6 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (e?: Error) => void) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  verificationCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const startCountdown = () => {
  countdown.value = 60
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

const handleSendCode = async () => {
  // 仅校验邮箱字段
  await formRef.value?.validateField('email')
  sendingCode.value = true
  try {
    await sendVerificationCode(form.email)
    ElMessage.success('验证码已发送，请查收邮件')
    startCountdown()
  } finally {
    sendingCode.value = false
  }
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const { confirmPassword: _confirm, ...registerData } = form
    await authStore.register(registerData)
    const newLogId = await persistPendingTrial()
    ElMessage.success(newLogId ? '已经替你把第一条小记收好了' : '注册成功')
    router.push(newLogId ? { name: 'log-detail', params: { id: newLogId } } : { name: 'home' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="auth-intro">
      <p class="eyebrow">Create Account</p>
      <h1>给自己的生活开一个小小相册。</h1>
      <p>用邮箱注册后，你的日志、AI 总结和标签都会保存在数据库里，只属于你的账号。</p>
    </div>

    <div class="auth-card">
      <h2>注册</h2>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="you@example.com" />
        </el-form-item>

        <el-form-item label="验证码" prop="verificationCode">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="form.verificationCode" placeholder="6 位验证码" maxlength="6" />
            <el-button
              :disabled="countdown > 0"
              :loading="sendingCode"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="50" placeholder="设置你的用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 个字符" />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>

        <el-button class="auth-submit" type="primary" :loading="submitting" @click="submit">注册并登录</el-button>
      </el-form>

      <div class="auth-links">
        <RouterLink to="/login">已有账号，去登录</RouterLink>
        <RouterLink to="/trial">先随便写写，体验一下</RouterLink>
      </div>
    </div>
  </section>
</template>
