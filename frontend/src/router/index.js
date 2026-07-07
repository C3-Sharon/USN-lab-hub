import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/layout/Index.vue'
import Login from '@/views/Login.vue'
import Dashboard from '@/views/Dashboard.vue'
import MemberManage from '@/views/admin/MemberManage.vue'
import AttendanceRecord from '@/views/admin/AttendanceRecord.vue'
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
