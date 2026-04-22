import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    name: 'Map',
    component: () => import('@/views/MapView.vue')
  }
]

const router = createRouter({
  history: createWebHistory('/gis/'),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  console.log('[Router Guard] from:', from.path, 'to:', to.path, 'hasToken:', !!token)
  if (to.path !== '/login' && !token) {
    console.log('[Router Guard] Redirecting to login')
    next('/login')
  } else {
    console.log('[Router Guard] Allowing navigation')
    next()
  }
})

export default router
