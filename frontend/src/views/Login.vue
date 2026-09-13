<template>
  <main class="login-page">
    <aside class="login-visual" aria-hidden="true">
      <div class="visual-grid">
        <div class="emblem">
          <svg viewBox="0 0 64 64" width="56" height="56">
            <circle cx="32" cy="32" r="28" fill="none" stroke="currentColor" stroke-width="2.5" />
            <path d="M10 32 Q 20 18 32 32 T 54 32" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
            <circle cx="32" cy="32" r="4" fill="currentColor" />
          </svg>
        </div>
        <div class="visual-meta">
          <strong class="visual-mark">USN</strong>
          <span class="visual-name">lab-hub</span>
          <span class="text-help visual-sub">实验室考勤与人员管理系统</span>
        </div>
        <ul class="visual-points">
          <li>考勤、项目、实验、库存、设备在同一台工作台</li>
          <li>多角色登录，按权限看到对应模块</li>
          <li>数据驱动状态，避免营销式干扰</li>
        </ul>
      </div>
    </aside>

    <section class="login-panel">
      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form" @keyup.enter="handleLogin">
        <header class="login-form__head">
          <h2>登录</h2>
          <p class="muted">使用学号或工号进入工作台</p>
        </header>

        <el-form-item prop="memberId">
          <el-input v-model.trim="loginForm.memberId" size="large" placeholder="账号 / 学工号" :prefix-icon="User" />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            size="large"
            placeholder="密码"
            type="password"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="rememberMe">保持登录</el-checkbox>
        </el-form-item>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="login-error" />

        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="handleLogin">
          登录系统
        </el-button>

        <p class="text-help muted login-hint">本系统为实验室内部使用，登录即视为同意内部数据规范；账号异常请联系系统管理员。</p>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { userStore, currentRoles } from '@/store/user'

const router = useRouter()
const route = useRoute()
const loginFormRef = ref()
const loading = ref(false)
const rememberMe = ref(true)
const errorMessage = ref('')

const loginForm = reactive({
  memberId: '',
  password: ''
})

const loginRules = {
  memberId: [{ required: true, message: '请输入账号 / 学工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  errorMessage.value = ''
  await loginFormRef.value?.validate()
  loading.value = true
  try {
    const loginData = await login(loginForm)
    userStore.setLoginData(loginData, rememberMe.value)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    if (redirect && redirect !== '/login') {
      router.replace(String(redirect))
      return
    }
    const roles = currentRoles()
    if (roles.includes('SYSTEM_ADMIN') || roles.includes('TEACHER')) {
      router.replace('/admin/members')
    } else {
      router.replace('/dashboard')
    }
  } catch (err) {
    const code = err?.code || ''
    if (code === 'ACCOUNT_DISABLED') {
      errorMessage.value = '账号已被禁用，请联系管理员'
    } else if (code === 'TOKEN_EXPIRED' || code === 'TOKEN_INVALID' || code === 'TOKEN_MISSING') {
      errorMessage.value = '登录状态已失效，请重新登录'
    } else {
      errorMessage.value = err?.msg || '登录失败，请检查账号密码'
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
  background: var(--usn-canvas);
}

.login-visual {
  background: var(--usn-blue-700);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px;
}

.visual-grid {
  max-width: 520px;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-5);
}

.emblem {
  width: 64px;
  height: 64px;
  border-radius: var(--usn-radius-md);
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.visual-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.visual-mark {
  font-size: 36px;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1;
}

.visual-name {
  font-size: 18px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}

.visual-sub {
  color: rgba(255, 255, 255, 0.7);
  margin-top: 4px;
}

.visual-points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-2);
  color: rgba(255, 255, 255, 0.86);
  font-size: var(--usn-font-size-body);
}

.visual-points li::before {
  content: '·';
  color: var(--usn-blue-100);
  margin-right: 8px;
  font-weight: 800;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.login-form {
  width: min(100%, 420px);
  padding: var(--usn-space-6);
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  box-shadow: var(--usn-shadow-panel);
  display: flex;
  flex-direction: column;
  gap: var(--usn-space-4);
}

.login-form__head h2 {
  margin: 0 0 4px;
  font-size: 22px;
  color: var(--usn-ink-900);
}

.login-form__head p {
  margin: 0;
  color: var(--usn-ink-500);
}

.login-button {
  width: 100%;
  height: 44px;
}

.login-error {
  margin-bottom: 0;
}

.login-hint {
  text-align: center;
  margin: 0;
}

@media (max-width: 1024px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-visual {
    min-height: 240px;
    padding: 40px;
  }

  .login-panel {
    padding: 24px;
  }
}

@media (max-width: 480px) {
  .login-visual {
    padding: 32px;
  }

  .visual-mark {
    font-size: 28px;
  }
}
</style>
