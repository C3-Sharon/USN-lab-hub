<template>
  <main class="error-page">
    <div class="error-card">
      <div class="badge">404</div>
      <h1>页面未找到</h1>
      <p class="muted">{{ path || '当前路径不存在或已被移动' }}</p>
      <div class="error-card__actions">
        <el-button type="primary" @click="$router.replace('/dashboard')">返回工作台</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
const route = useRoute()
const path = computed(() => String(route.params.pathMatch || ''))
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
