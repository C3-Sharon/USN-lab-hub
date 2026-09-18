<template>
  <el-container class="app-layout">
    <el-aside class="sidebar desktop-sidebar" :width="sidebarWidth">
      <div class="sidebar-brand">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" width="22" height="22">
            <circle cx="16" cy="16" r="13" fill="none" stroke="currentColor" stroke-width="2" />
            <path d="M6 16 Q 12 9 16 16 T 26 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            <circle cx="16" cy="16" r="2" fill="currentColor" />
          </svg>
        </span>
        <div class="brand-text">
          <strong>USN lab-hub</strong>
          <span class="text-help muted">个人工作台</span>
        </div>
      </div>

      <el-menu :default-active="activePath" router class="sidebar-menu" background-color="transparent" text-color="rgba(255,255,255,0.78)" active-text-color="#ffffff">
        <template v-for="item in menuItems" :key="item.index">
          <el-sub-menu v-if="item.children" :index="item.index">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.index" :index="child.index">
              <el-icon><component :is="child.icon" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.index">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <el-button
          class="mobile-menu-button"
          :icon="Menu"
          circle
          aria-label="打开导航菜单"
          @click="mobileNavOpen = true"
        />
        <div class="topbar-info">
          <h1 class="topbar-title">{{ route.meta.title || 'USN-lab-hub' }}</h1>
          <p class="text-help muted">{{ pageSubtitle }}</p>
        </div>
        <div class="user-actions">
          <el-tag :type="roleMeta.tagType" effect="dark" class="role-tag" size="small">
            {{ roleMeta.name }}
          </el-tag>
          <el-avatar :size="32" class="user-avatar">{{ usernameInitial }}</el-avatar>
          <span class="user-name">{{ userStore.userInfo?.username || '未命名用户' }}</span>
          <el-button :icon="SwitchButton" size="small" plain @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>

    <el-drawer v-model="mobileNavOpen" direction="ltr" size="280px" :with-header="false" class="mobile-nav-drawer">
      <nav class="mobile-sidebar" aria-label="移动端主导航">
        <div class="sidebar-brand">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 32 32" width="22" height="22">
              <circle cx="16" cy="16" r="13" fill="none" stroke="currentColor" stroke-width="2" />
              <path d="M6 16 Q 12 9 16 16 T 26 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
              <circle cx="16" cy="16" r="2" fill="currentColor" />
            </svg>
          </span>
          <div class="brand-text">
            <strong>USN lab-hub</strong>
            <span class="text-help muted">个人工作台</span>
          </div>
        </div>
        <el-menu
          :default-active="activePath"
          router
          class="sidebar-menu"
          background-color="transparent"
          text-color="rgba(255,255,255,0.78)"
          active-text-color="#ffffff"
          @select="mobileNavOpen = false"
        >
          <template v-for="item in menuItems" :key="item.index">
            <el-sub-menu v-if="item.children" :index="item.index">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="child in item.children" :key="child.index" :index="child.index">
                <el-icon><component :is="child.icon" /></el-icon>
                <span>{{ child.title }}</span>
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.index">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </nav>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { computed, markRaw, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, DocumentChecked, SwitchButton, UserFilled, Cpu, Monitor, Bell, SetUp, FolderOpened, Document, Menu } from '@element-plus/icons-vue'
import { userStore, hasAnyRole } from '@/store/user'
import { ROLE } from '@/utils/permission'

const route = useRoute()
const router = useRouter()
const mobileNavOpen = ref(false)

const sidebarWidth = '224px'

const activePath = computed(() => route.path)
const usernameInitial = computed(() => {
  const user = userStore.userInfo
  return (user?.username || user?.memberId || 'U').slice(0, 1)
})
const primaryRole = computed(() => userStore.userInfo?.primaryRoleKey || 'MEMBER')
const roleMeta = computed(() => {
  const map = {
    SYSTEM_ADMIN: { name: '系统管理员', tagType: 'danger' },
    TEACHER: { name: '老师', tagType: 'primary' },
    STOCK_KEEPER: { name: '库存管理员', tagType: 'warning' },
    MEMBER: { name: '普通成员', tagType: 'info' }
  }
  return map[primaryRole.value] || map.MEMBER
})

const pageSubtitle = computed(() => {
  if (route.path.startsWith('/dashboard')) return '今日考勤、进行中项目、本周任务、学习、设备提醒'
  if (route.path === '/members' || route.path === '/attendance') return '实验室成员与考勤检查'
  if (route.path.startsWith('/iot')) return 'IoT 设备、告警、建议、指令与日志'
  return ''
})

const ICON = markRaw({
  dashboard: DataBoard,
  member: UserFilled,
  attendance: DocumentChecked,
  iot: Cpu,
  overview: Monitor,
  folder: FolderOpened,
  device: Cpu,
  bell: Bell,
  command: SetUp,
  document: Document
})

const menuItems = computed(() => {
  const all = [
    {
      index: '/dashboard',
      title: '个人工作台',
      icon: ICON.dashboard,
      roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER]
    },
    {
      index: '/laboratory',
      title: '实验室管理',
      icon: ICON.member,
      roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER],
      children: [
        { index: '/members', title: '成员管理', icon: ICON.member, roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER] },
        { index: '/attendance', title: '考勤检查', icon: ICON.attendance, roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER] }
      ]
    },
    {
      index: '/iot',
      title: 'IoT 管理',
      icon: ICON.iot,
      roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER],
      children: [
        { index: '/iot/overview', title: 'IoT 总览', icon: ICON.overview, roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] },
        { index: '/iot/projects', title: '项目列表', icon: ICON.folder, roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] },
        { index: '/iot/devices', title: '设备列表', icon: ICON.device, roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] },
        { index: '/iot/alerts', title: '告警中心', icon: ICON.bell, roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER] },
        { index: '/iot/commands', title: '指令控制台', icon: ICON.command, roles: [ROLE.SYSTEM_ADMIN] },
        { index: '/iot/logs', title: '操作日志', icon: ICON.document, roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER] }
      ]
    }
  ]

  return all
    .filter((item) => hasAnyRole(item.roles))
    .map((item) => {
      if (!item.children) return item
      const children = item.children.filter((c) => hasAnyRole(c.roles))
      if (children.length === 0) return null
      return { ...item, children }
    })
    .filter(Boolean)
})

function handleLogout() {
  userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: var(--usn-canvas);
}

.sidebar {
  background: var(--usn-ink-900);
  color: #fff;
  display: flex;
  flex-direction: column;
}

.sidebar-brand {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--usn-radius-sm);
  background: var(--usn-blue-700);
  color: #ffffff;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-text strong {
  font-size: 15px;
  color: #ffffff;
  font-weight: 700;
}

.brand-text .text-help {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
}

.sidebar-menu {
  border-right: none;
  background: transparent;
  flex: 1;
  padding-top: 8px;
}

.sidebar-menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.78);
  height: 40px;
  line-height: 40px;
}

.sidebar-menu :deep(.el-menu-item.is-active),
.sidebar-menu :deep(.el-menu-item:hover) {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.sidebar-menu :deep(.el-sub-menu__title) {
  color: rgba(255, 255, 255, 0.78);
}

.topbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--usn-space-4);
  padding: 0 var(--usn-space-6);
  background: var(--usn-surface);
  border-bottom: 1px solid var(--usn-line);
}

.mobile-menu-button {
  display: none;
  flex: 0 0 auto;
}

.mobile-sidebar {
  min-height: 100%;
  background: var(--usn-ink-900);
  color: #ffffff;
  display: flex;
  flex-direction: column;
}

:global(.mobile-nav-drawer .el-drawer__body) {
  padding: 0;
  background: var(--usn-ink-900);
}

.topbar-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--usn-ink-900);
  line-height: 1.2;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: var(--usn-space-3);
  white-space: nowrap;
}

.user-avatar {
  background: var(--usn-blue-100);
  color: var(--usn-blue-700);
  font-weight: 700;
}

.user-name {
  font-size: var(--usn-font-size-body);
  color: var(--usn-ink-700);
}

.role-tag {
  font-weight: 600;
}

.main-content {
  padding: var(--usn-space-6);
}

@media (max-width: 1024px) {
  .app-layout {
    display: block;
  }

  .desktop-sidebar {
    display: none;
  }

  .mobile-menu-button {
    display: inline-flex;
  }

  .topbar {
    height: auto;
    min-height: 56px;
    padding: var(--usn-space-3) var(--usn-space-4);
    align-items: center;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .topbar-info {
    flex: 1 1 180px;
    min-width: 0;
  }

  .user-actions {
    flex: 1 1 100%;
    flex-wrap: wrap;
  }

  .main-content {
    padding: var(--usn-space-4);
  }
}
</style>
