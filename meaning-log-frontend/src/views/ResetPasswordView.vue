<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/authStore'
import type { ResetPasswordRequest } from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<ResetPasswordRequest>({
  email: '',
  newPassword: '',
})

const rules: FormRules<ResetPasswordRequest> = {
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码至少 6 个字符', trigger: 'blur' },
  ],
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
        title="当前版本使用注册邮箱直接重置密码，后续可以接入邮箱验证码。"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="注册邮箱" prop="email">
          <el-input v-model="form.email" placeholder="you@example.com" />
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
