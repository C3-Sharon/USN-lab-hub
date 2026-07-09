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
import { hasLogin, isAdmin } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { public: true }
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
        meta: { title: '个人考勤控制台' }
      },
      {
        path: 'admin/members',
        name: 'MemberManage',
        component: MemberManage,
        meta: { title: '实验室成员管理', role: 'admin' }
      },
      {
        path: 'admin/attendance',
        name: 'AttendanceRecord',
        component: AttendanceRecord,
        meta: { title: '考勤检查与导出', role: 'admin' }
      },
      {
        path: 'iot/overview',
        name: 'IotOverview',
        component: IotOverview,
        meta: { title: 'IoT 总览' }
      },
      {
        path: 'iot/devices',
        name: 'IotDeviceList',
        component: IotDeviceList,
        meta: { title: '设备列表' }
      },
      {
        path: 'iot/devices/:id',
        name: 'IotDeviceDetail',
        component: IotDeviceDetail,
        meta: { title: '设备详情' }
      },
      {
        path: 'iot/alerts',
        name: 'IotAlertCenter',
        component: IotAlertCenter,
        meta: { title: '告警中心' }
      },
      {
        path: 'iot/commands',
        name: 'IotCommandConsole',
        component: IotCommandConsole,
        meta: { title: '指令控制台' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return hasLogin() ? '/dashboard' : true
  }
  if (!hasLogin()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.role === 'admin' && !isAdmin()) {
    ElMessage.warning('当前账号无权访问管理员页面')
    return '/dashboard'
  }
  return true
})

export default router
