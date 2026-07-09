<template>
  <el-container class="app-layout">
    <el-aside class="sidebar" width="232px">
      <div class="sidebar-brand">
        <span>USN</span>
        <strong>lab-hub</strong>
      </div>
      <el-menu :default-active="activePath" router class="sidebar-menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>个人考勤</span>
        </el-menu-item>
        <template v-if="isAdminUser">
          <el-menu-item index="/admin/members">
            <el-icon><UserFilled /></el-icon>
            <span>成员管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/attendance">
            <el-icon><DocumentChecked /></el-icon>
            <span>考勤检查</span>
          </el-menu-item>
        </template>
        <el-sub-menu index="/iot">
          <template #title>
            <el-icon><Cpu /></el-icon>
            <span>IoT 管理</span>
          </template>
          <el-menu-item index="/iot/overview">
            <el-icon><Monitor /></el-icon>
            <span>IoT 总览</span>
          </el-menu-item>
          <el-menu-item index="/iot/devices">
            <el-icon><Cpu /></el-icon>
            <span>设备列表</span>
          </el-menu-item>
          <el-menu-item index="/iot/alerts">
            <el-icon><Bell /></el-icon>
            <span>告警中心</span>
          </el-menu-item>
          <el-menu-item index="/iot/commands">
            <el-icon><SetUp /></el-icon>
            <span>指令控制台</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <h1>{{ route.meta.title || 'USN-lab-hub' }}</h1>
          <p>实验室考勤与人员管理系统</p>
        </div>
        <div class="user-actions">
          <el-avatar :size="36">{{ usernameInitial }}</el-avatar>
          <span>{{ userStore.userInfo?.username || '未命名用户' }}</span>
          <el-button :icon="SwitchButton" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, DocumentChecked, SwitchButton, UserFilled, Cpu, Monitor, Bell, SetUp } from '@element-plus/icons-vue'
import { userStore } from '@/store/user'

const route = useRoute()
const router = useRouter()

const activePath = computed(() => route.path)
const isAdminUser = computed(() => userStore.userInfo?.role === 'admin')
const usernameInitial = computed(() => (userStore.userInfo?.username || 'U').slice(0, 1))

function handleLogout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: #f4f7fb;
}

.sidebar {
  background: #172033;
  color: #fff;
}

.sidebar-brand {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar-brand span {
  display: inline-flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #f5c542;
  color: #172033;
  font-weight: 800;
}

.sidebar-brand strong {
  font-size: 18px;
}

.sidebar-menu {
  border-right: none;
  background: transparent;
}

.sidebar-menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.72);
}

.sidebar-menu :deep(.el-menu-item.is-active),
.sidebar-menu :deep(.el-menu-item:hover) {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
}

.topbar {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  background: #fff;
  border-bottom: 1px solid #e7ecf3;
}

.topbar h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.topbar p {
  margin: 4px 0 0;
  color: #7c8798;
  font-size: 13px;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.main-content {
  padding: 24px;
}

@media (max-width: 860px) {
  .app-layout {
    flex-direction: column;
  }

  .sidebar {
    width: 100% !important;
  }

  .sidebar-menu {
    display: flex;
    overflow-x: auto;
  }

  .topbar {
    height: auto;
    padding: 14px 16px;
    align-items: flex-start;
    flex-direction: column;
  }

  .main-content {
    padding: 16px;
  }
}
</style>
