<template>
  <div class="login-wrap">
    <div class="login-panel">
      <!-- 左侧品牌区 -->
      <div class="brand-side">
        <div class="brand-inner">
          <div class="brand-logo">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none">
              <path d="M4 6a2 2 0 1 1 4 0 2 2 0 0 1-4 0Z" fill="#fff" />
              <path d="M4 18a2 2 0 1 1 4 0 2 2 0 0 1-4 0Z" fill="#fff" />
              <path d="M16 12a2 2 0 1 1 4 0 2 2 0 0 1-4 0Z" fill="#fff" />
              <path d="M7 6h5a3 3 0 0 1 3 3v1a2 2 0 0 0 2 2M7 18h5a3 3 0 0 0 3-3v-1a2 2 0 0 1 2-2" stroke="#fff" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </div>
          <h1 class="brand-title">easy-bpm</h1>
          <p class="brand-sub">轻量级审批流平台 · 快速接入 ERP / SaaS</p>
          <div class="brand-features">
            <div class="feature-item"><span class="dot" />业务表单零设计，数据归业务系统，变更零同步</div>
            <div class="feature-item"><span class="dot" />多租户隔离，数据互不可见</div>
            <div class="feature-item"><span class="dot" />OpenAPI 全链路，一行代码发起审批</div>
            <div class="feature-item"><span class="dot" />Flowable 引擎驱动，条件分支 / 或签会签</div>
          </div>
        </div>
        <div class="brand-decoration" aria-hidden="true">
          <span class="ring r1" />
          <span class="ring r2" />
          <span class="ring r3" />
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-side">
        <div class="form-box">
          <h2 class="form-title">欢迎登录</h2>
          <p class="form-sub">easy-bpm 控制台</p>
          <el-form :model="form" size="large" @keyup.enter="onSubmit">
            <el-form-item>
              <el-input v-model="form.tenantCode" placeholder="租户编码（demo）" clearable>
                <template #prefix><el-icon><OfficeBuilding /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-input v-model="form.username" placeholder="账号（admin）" clearable>
                <template #prefix><el-icon><User /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item>
              <el-input v-model="form.password" type="password" show-password placeholder="密码">
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="onSubmit">
              登 录
            </el-button>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, OfficeBuilding, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ tenantCode: 'demo', username: 'admin', password: '' })

async function onSubmit() {
  if (!form.tenantCode || !form.username || !form.password) {
    ElMessage.warning('请完整填写租户编码、账号和密码')
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
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--vben-content-bg);
  padding: 24px;
}
.login-panel {
  display: flex;
  width: 860px;
  max-width: 100%;
  min-height: 520px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(15, 23, 42, 0.12);
}

/* 左侧品牌区 */
.brand-side {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  background: linear-gradient(145deg, #0f172a 0%, #0960bd 130%);
  overflow: hidden;
}
.brand-inner {
  position: relative;
  z-index: 1;
  padding: 48px;
  color: #fff;
}
.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(4px);
  margin-bottom: 20px;
}
.brand-title { font-size: 30px; font-weight: 800; letter-spacing: 1px; }
.brand-sub { margin-top: 10px; font-size: 14px; color: rgba(255, 255, 255, 0.75); }
.brand-features { margin-top: 40px; display: flex; flex-direction: column; gap: 14px; }
.feature-item { display: flex; align-items: center; gap: 10px; font-size: 13px; color: rgba(255, 255, 255, 0.85); }
.feature-item .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #3aa0ff;
  box-shadow: 0 0 6px #3aa0ff;
  flex-shrink: 0;
}

/* 装饰圆环 */
.brand-decoration { position: absolute; inset: 0; }
.ring {
  position: absolute;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 50%;
}
.r1 { width: 260px; height: 260px; right: -80px; bottom: -80px; }
.r2 { width: 180px; height: 180px; right: -40px; bottom: -40px; }
.r3 { width: 120px; height: 120px; right: 20px; top: -50px; }

/* 右侧表单区 */
.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  padding: 48px 40px;
}
.form-box { width: 320px; }
.form-title { font-size: 22px; font-weight: 700; color: #1f2937; }
.form-sub { margin: 8px 0 28px; font-size: 13px; color: #909399; }
.login-btn { width: 100%; margin-top: 4px; letter-spacing: 4px; }

@media (max-width: 768px) {
  .brand-side { display: none; }
  .login-panel { min-height: auto; }
}
</style>
