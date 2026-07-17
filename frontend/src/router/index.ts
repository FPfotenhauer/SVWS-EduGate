import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/auth/authStore'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'schultraeger-liste',
      component: () => import('@/views/SchultraegerListView.vue'),
    },
    {
      path: '/schultraeger/neu',
      name: 'schultraeger-neu',
      component: () => import('@/views/SchultraegerFormView.vue'),
    },
    {
      path: '/schultraeger/:id/bearbeiten',
      name: 'schultraeger-bearbeiten',
      component: () => import('@/views/SchultraegerFormView.vue'),
      props: true,
    },
    {
      path: '/auth/callback',
      name: 'auth-callback',
      component: () => import('@/views/LoginCallbackView.vue'),
      meta: { public: true },
    },
  ],
})

// Ohne gültige Anmeldung ist keine Ansicht erreichbar (Abnahmekriterium 2, FIRSTPROMPT.md):
// Der Guard leitet unauthentifizierte Aufrufe direkt zu Keycloak um.
router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true
  }

  const authStore = useAuthStore()
  if (!authStore.user) {
    await authStore.restore()
  }

  if (!authStore.isAuthenticated) {
    await authStore.login()
    return false
  }

  return true
})

export default router
