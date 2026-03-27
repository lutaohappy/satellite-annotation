import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/gis/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/gis',
    name: 'Map',
    component: () => import('@/views/MapView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  // 测试模式：如果没有 token 且访问地图页面，直接跳转到登录
  const token = localStorage.getItem('token')
  if (!to.path.startsWith('/gis/login') && !token) {
    next('/gis/login')
  } else {
    next()
  }
})

export default router
