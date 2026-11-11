<template>
  <div class="sso-callback">
    <el-card class="box" shadow="never">
      <div v-if="status === 'loading'" class="state">
        <el-icon class="spin" :size="28"><Loading /></el-icon>
        <p class="title">正在完成企业登录…</p>
        <p class="hint">正在校验企业身份并建立会话，请稍候</p>
      </div>
      <div v-else class="state">
        <el-icon :size="30" color="#f56c6c"><CircleClose /></el-icon>
        <p class="title">企业登录未完成</p>
        <p class="hint">{{ errorMessage }}</p>
        <el-button type="primary" @click="backToLogin">返回登录页</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleClose, Loading } from '@element-plus/icons-vue'
import { exchangeSsoTicket } from '@/api/sso'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const status = ref<'loading' | 'error'>('loading')
const errorMessage = ref('')

function backToLogin() {
  router.replace({ name: 'login' })
}

onMounted(async () => {
  const error = route.query.error as string | undefined
  const message = route.query.message as string | undefined
  if (error) {
    status.value = 'error'
    errorMessage.value = message || '企业身份校验失败，请重新登录'
    return
  }
  const ticket = route.query.ticket as string | undefined
  if (!ticket) {
    status.value = 'error'
    errorMessage.value = '登录链接缺少必要参数，请返回登录页重新发起'
    return
  }
  try {
    const result = await exchangeSsoTicket(ticket)
    auth.applyLogin(result)
    ElMessage.success(`欢迎回来，${result.user?.displayName || result.username}`)
    const target = (route.query.redirect as string) || '/dashboard'
    router.replace(target.startsWith('/') && !target.startsWith('//') ? target : '/dashboard')
  } catch (e) {
    status.value = 'error'
    errorMessage.value = e instanceof Error ? e.message : '登录凭证已失效，请重新登录'
  }
})
</script>

<style scoped>
.sso-callback {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--vben-content-bg);
  padding: 24px;
}
.box {
  width: 420px;
  max-width: 100%;
  border-radius: 14px;
}
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 28px 12px;
  text-align: center;
}
.title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.hint {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
