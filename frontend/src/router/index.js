import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/layout/Index.vue'
import Login from '@/views/Login.vue'
import Dashboard from '@/views/Dashboard.vue'
import MemberManage from '@/views/admin/MemberManage.vue'
import AttendanceRecord from '@/views/admin/AttendanceRecord.vue'
import IotOverview from '@/views/iot/Overview.vue'
import IotDeviceList from '@/views/iot/DeviceList.vue'
import IotDeviceDetail from '@/views/iot/DeviceDetail.vue'
import IotAlertCenter from '@/views/iot/AlertCenter.vue'
import IotCommandConsole from '@/views/iot/CommandConsole.vue'
import IotProjects from '@/views/iot/Projects.vue'
import IotProjectDetail from '@/views/iot/ProjectDetail.vue'
import IotOperationLogs from '@/views/iot/OperationLogs.vue'
import IotPublic from '@/views/iot/PublicDisplay.vue'
import Pm001Live from '@/views/iot/Pm001Live.vue'
import Forbidden from '@/views/errors/Forbidden.vue'
import NotFound from '@/views/errors/NotFound.vue'
import { hasLogin, hasAnyRole, userStore } from '@/store/user'
import { ROLE } from '@/utils/permission'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { public: true, title: '登录' }
  },
  {
    path: '/forbidden',
    name: 'Forbidden',
    component: Forbidden,
    meta: { public: true, title: '无权访问' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: Dashboard,
        meta: { title: '个人工作台', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] }
      },
      {
        path: 'members',
        name: 'MemberManage',
        component: MemberManage,
        meta: { title: '实验室成员管理', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER] }
      },
      {
        path: 'attendance',
        name: 'AttendanceRecord',
        component: AttendanceRecord,
        meta: { title: '考勤检查与导出', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER] }
      },
      {
        path: 'iot/overview',
        name: 'IotOverview',
        component: IotOverview,
        meta: { title: 'IoT 总览', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] }
      },
      {
        path: 'iot/devices',
        name: 'IotDeviceList',
        component: IotDeviceList,
        meta: { title: '设备列表', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] }
      },
      {
        path: 'iot/devices/:id',
        name: 'IotDeviceDetail',
        component: IotDeviceDetail,
        meta: { title: '设备详情', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] }
      },
      {
        path: 'iot/alerts',
        name: 'IotAlertCenter',
        component: IotAlertCenter,
        meta: { title: '告警中心', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER] }
      },
      {
        path: 'iot/commands',
        name: 'IotCommandConsole',
        component: IotCommandConsole,
        meta: { title: '指令控制台', roles: [ROLE.SYSTEM_ADMIN] }
      },
      {
        path: 'iot/projects',
        name: 'IotProjects',
        component: IotProjects,
        meta: { title: '项目列表', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] }
      },
      {
        path: 'iot/projects/:id',
        name: 'IotProjectDetail',
        component: IotProjectDetail,
        meta: { title: '项目详情', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] }
      },
      {
        path: 'iot/logs',
        name: 'IotOperationLogs',
        component: IotOperationLogs,
        meta: { title: '操作日志', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER] }
      },
      {
        path: 'iot/pm001',
        name: 'Pm001Live',
        component: Pm001Live,
        meta: { title: 'PM-001 实时数据', roles: [ROLE.SYSTEM_ADMIN, ROLE.TEACHER, ROLE.STOCK_KEEPER, ROLE.MEMBER] }
      }
    ]
  },
  {
    path: '/iot/public',
    name: 'IotPublic',
    component: IotPublic,
    meta: { public: true, title: '公开项目展示' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: NotFound,
    meta: { public: true, title: '页面未找到' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!hasLogin()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (Array.isArray(to.meta.roles) && to.meta.roles.length) {
    if (!hasAnyRole(to.meta.roles)) {
      ElMessage.warning('当前账号无权访问该页面')
      return { path: '/dashboard', query: { denied: to.fullPath } }
    }
  }
  return true
})

export default router
