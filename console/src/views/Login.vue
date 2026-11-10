<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">easy-bpm 控制台</h2>
      <el-form :model="form" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="租户编码">
          <el-input v-model="form.tenantCode" placeholder="demo" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="admin123" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="onSubmit">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ tenantCode: 'demo', username: 'admin', password: '' })

async function onSubmit() {
  if (!form.tenantCode || !form.username || !form.password) {
    return
  }
  loading.value = true
  try {
    await auth.login(form.tenantCode, form.username, form.password)
    router.push((route.query.redirect as string) || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { height: 100%; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1d2530 0%, #2b3b52 100%); }
.login-card { width: 380px; }
.title { text-align: center; margin-bottom: 24px; color: #303133; }
</style>
