import { createRouter, createWebHistory } from 'vue-router'
import { AUTH_TOKEN_KEY, LOGIN_REDIRECT_QUERY_KEY } from '../constants/app'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('../views/ResetPasswordView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/trial',
      name: 'trial',
      component: () => import('../views/TrialView.vue'),
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { requiresAuth: true },
    },
    {
      path: '/logs/new',
      name: 'log-create',
      component: () => import('../views/LogCreateView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/ai-reports',
      name: 'ai-reports',
      component: () => import('../views/AIReportView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/xiaoji',
      name: 'xiaoji',
      component: () => import('../views/XiaojiChatView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/logs/:id',
      name: 'log-detail',
      component: () => import('../views/LogDetailView.vue'),
      props: (route) => ({ id: Number(route.params.id) }),
      meta: { requiresAuth: true },
    },
    {
      path: '/logs/:id/edit',
      name: 'log-edit',
      component: () => import('../views/LogEditView.vue'),
      props: (route) => ({ id: Number(route.params.id) }),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach((to) => {
  const hasToken = Boolean(localStorage.getItem(AUTH_TOKEN_KEY))

  if (to.meta.requiresAuth && !hasToken) {
    return { name: 'login', query: { [LOGIN_REDIRECT_QUERY_KEY]: to.fullPath } }
  }

  if (to.meta.guestOnly && hasToken) {
    return { name: 'home' }
  }
})

export default router
