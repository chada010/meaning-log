<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/authStore'
import { persistPendingTrial } from '../api/trial'
import type { LoginRequest } from '../api/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<LoginRequest>({
  email: '',
  password: '',
})

const rules: FormRules<LoginRequest> = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await authStore.login(form)
    const newLogId = await persistPendingTrial()
    ElMessage.success(newLogId ? '已经替你把那条小记收好了' : '登录成功')
    if (newLogId) {
      router.push({ name: 'log-detail', params: { id: newLogId } })
    } else {
      router.push((route.query.redirect as string) || '/')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-layout">
    <div class="auth-intro">
      <p class="eyebrow">Welcome Back</p>
      <h1>回到你的温柔日志角落。</h1>
      <p>登录后继续记录今天的小小微光，也可以让小记帮你整理成更清晰的回顾。</p>
    </div>

    <div class="auth-card">
      <h2>登录</h2>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="you@example.com" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>

        <el-button class="auth-submit" type="primary" :loading="submitting" @click="submit">登录</el-button>
      </el-form>

      <div class="auth-links">
        <RouterLink to="/register">注册账号</RouterLink>
        <RouterLink to="/reset-password">忘记密码</RouterLink>
        <RouterLink to="/trial">先随便写写，体验一下</RouterLink>
      </div>
    </div>
  </section>
</template>
