<template>
  <main class="login-page">
    <section class="login-visual">
      <div class="brand-block">
        <span class="brand-mark">USN</span>
        <h1>USN-lab-hub</h1>
        <p>实验室考勤与人员管理系统</p>
      </div>
    </section>

    <section class="login-panel">
      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form" @keyup.enter="handleLogin">
        <h2>登录</h2>
        <p class="muted">使用学号或工号进入工作台</p>

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

        <el-button class="login-button" type="primary" size="large" :loading="loading" @click="handleLogin">
          登录系统
        </el-button>
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
import { userStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const loginFormRef = ref()
const loading = ref(false)
const rememberMe = ref(true)

const loginForm = reactive({
  memberId: '',
  password: ''
})

const loginRules = {
  memberId: [{ required: true, message: '请输入账号 / 学工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
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
    router.replace(loginData.user?.role === 'admin' ? '/admin/members' : '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(420px, 0.9fr);
  background: #f4f7fb;
}

.login-visual {
  position: relative;
  display: flex;
  align-items: center;
  padding: 64px;
  color: #fff;
  background:
    linear-gradient(rgba(16, 38, 64, 0.64), rgba(16, 38, 64, 0.72)),
    url("https://images.unsplash.com/photo-1581093588401-fbb62a02f120?auto=format&fit=crop&w=1600&q=80") center / cover;
}

.brand-block {
  max-width: 620px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 8px;
  background: #ffffff;
  color: #1d4f91;
  font-weight: 800;
  font-size: 20px;
}

.brand-block h1 {
  margin: 28px 0 12px;
  font-size: 52px;
  line-height: 1.08;
  letter-spacing: 0;
}

.brand-block p {
  margin: 0;
  font-size: 20px;
  color: rgba(255, 255, 255, 0.84);
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.login-form {
  width: min(100%, 420px);
  padding: 36px;
  background: #fff;
  border: 1px solid #e7ecf3;
  border-radius: 8px;
  box-shadow: 0 24px 64px rgba(24, 39, 75, 0.08);
}

.login-form h2 {
  margin: 0 0 8px;
  font-size: 28px;
}

.login-button {
  width: 100%;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-visual {
    min-height: 280px;
    padding: 36px;
  }

  .brand-block h1 {
    font-size: 38px;
  }

  .login-panel {
    padding: 24px;
  }
}
</style>
