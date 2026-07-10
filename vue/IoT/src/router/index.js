import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', layout: 'blank' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册', layout: 'blank' },
  },
  {
    path: '/oauth-callback',
    name: 'OAuthCallback',
    component: () => import('../views/OAuthCallback.vue'),
    meta: { title: '登录中...', layout: 'blank' },
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '仪表盘' },
  },
  {
    path: '/monitor',
    name: 'Monitor',
    component: () => import('../views/Monitor.vue'),
    meta: { title: '实时监控' },
  },
  {
    path: '/devices',
    name: 'DeviceList',
    component: () => import('../views/DeviceList.vue'),
    meta: { title: '设备管理' },
  },
  {
    path: '/history',
    name: 'History',
    component: () => import('../views/History.vue'),
    meta: { title: '历史数据' },
  },
  {
    path: '/alarm',
    name: 'Alarm',
    component: () => import('../views/Alarm.vue'),
    meta: { title: '报警管理' },
  },
  {
    path: '/automation',
    name: 'Automation',
    component: () => import('../views/Automation.vue'),
    meta: { title: '自动化规则' },
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('../views/Chat.vue'),
    meta: { title: 'AI 助手' },
  },
  {
    path: '/pay',
    name: 'Pay',
    component: () => import('../views/Pay.vue'),
    meta: { title: '支付测试' },
  },
  {
    path: '/screen',
    name: 'Screen',
    component: () => import('../views/Screen.vue'),
    meta: { title: '数据大屏', layout: 'blank' },
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// 路由守卫：使用 Pinia authStore 检查登录态
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  authStore.restore() // 从 localStorage 恢复登录态
  const isAuthPage = to.path === '/login' || to.path === '/register' || to.path === '/oauth-callback'

  if (!authStore.isLoggedIn && !isAuthPage) {
    next('/login')
  } else if (authStore.isLoggedIn && isAuthPage) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
