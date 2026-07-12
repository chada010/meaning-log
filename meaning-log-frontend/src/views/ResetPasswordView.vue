<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/authStore'
import { sendVerificationCode } from '../api/auth'
import type { ResetPasswordRequest } from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)

const form = reactive<ResetPasswordRequest>({
  email: '',
  verificationCode: '',
  newPassword: '',
})

const rules: FormRules<ResetPasswordRequest> = {
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位验证码', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码至少 6 个字符', trigger: 'blur' },
  ],
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
    await authStore.resetPassword(form)
    ElMessage.success('密码已重置，请重新登录')
    router.push({ name: 'login' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="auth-intro">
      <p class="eyebrow">Reset Password</p>
      <h1>没关系，我们重新开始。</h1>
      <p>输入注册邮箱和新密码，重置后再登录，就可以继续回到你的日志空间。</p>
    </div>

    <div class="auth-card">
      <h2>重置密码</h2>

      <el-alert
        class="auth-tip"
        type="info"
        show-icon
        :closable="false"
        title="请输入发送到注册邮箱的验证码后重置密码。"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="注册邮箱" prop="email">
          <el-input v-model="form.email" placeholder="you@example.com" />
        </el-form-item>

        <el-form-item label="验证码" prop="verificationCode">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="form.verificationCode" placeholder="6 位验证码" maxlength="6" />
            <el-button :disabled="countdown > 0" :loading="sendingCode" @click="handleSendCode">
              {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 个字符" />
        </el-form-item>

        <el-button class="auth-submit" type="primary" :loading="submitting" @click="submit">重置密码</el-button>
      </el-form>

      <div class="auth-links">
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </div>
  </section>
</template>
