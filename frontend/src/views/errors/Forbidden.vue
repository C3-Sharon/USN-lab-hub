<template>
  <main class="error-page">
    <div class="error-card">
      <div class="badge badge--warn">403</div>
      <h1>无权访问</h1>
      <p class="muted">
        {{ reason || '当前账号角色没有访问该页面的权限' }}
      </p>
      <p class="text-help muted" v-if="from">来源：{{ from }}</p>
      <div class="error-card__actions">
        <el-button @click="$router.replace('/dashboard')">返回工作台</el-button>
        <el-button type="primary" @click="handleLogout">切换账号</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userStore } from '@/store/user'

const route = useRoute()
const router = useRouter()

const from = computed(() => (route.query.from ? String(route.query.from) : ''))
const reason = computed(() => {
  if (route.query.reason === 'ACCOUNT_DISABLED') return '账号已被禁用'
  if (route.query.reason === 'TOKEN_EXPIRED') return '登录已过期'
  if (route.query.reason === 'TOKEN_INVALID') return '登录状态无效'
  return ''
})

function handleLogout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--usn-canvas);
  padding: var(--usn-space-6);
}

.error-card {
  width: 100%;
  max-width: 480px;
  background: var(--usn-surface);
  border: 1px solid var(--usn-line);
  border-radius: var(--usn-radius-md);
  padding: var(--usn-space-6);
  text-align: center;
  box-shadow: var(--usn-shadow-panel);
}

.badge {
  display: inline-block;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--usn-blue-100);
  color: var(--usn-blue-700);
  margin-bottom: var(--usn-space-3);
}

.badge--warn {
  background: rgba(166, 97, 0, 0.12);
  color: var(--usn-warning);
}

.error-card h1 {
  margin: 0 0 var(--usn-space-2);
  font-size: 22px;
  color: var(--usn-ink-900);
}

.error-card__actions {
  display: flex;
  justify-content: center;
  gap: var(--usn-space-3);
  margin-top: var(--usn-space-5);
}
</style>
