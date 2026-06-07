<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/authStore'
import { persistPendingTrial } from '../api/trial'
import type { RegisterRequest } from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<RegisterRequest>({
  email: '',
  username: '',
  password: '',
})

const rules: FormRules<RegisterRequest> = {
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
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await authStore.register(form)
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

        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="50" placeholder="设置你的用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 个字符" />
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
