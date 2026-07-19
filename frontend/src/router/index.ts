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
      path: '/schultraeger/:schultraegerId/schulen/:schuleId',
      name: 'schule-detail',
      component: () => import('@/views/SchuleDetailView.vue'),
      props: true,
    },
    {
      path: '/schuldatenbanken',
      name: 'schuldatenbank-uebersicht',
      component: () => import('@/views/SchuldatenbankUebersichtView.vue'),
    },
    {
      path: '/svws-instanzen',
      name: 'svws-instanz-liste',
      component: () => import('@/views/SvwsInstanzListView.vue'),
    },
    {
      path: '/svws-instanzen/neu',
      name: 'svws-instanz-neu',
      component: () => import('@/views/SvwsInstanzFormView.vue'),
    },
    {
      path: '/svws-instanzen/:id/bearbeiten',
      name: 'svws-instanz-bearbeiten',
      component: () => import('@/views/SvwsInstanzFormView.vue'),
      props: true,
    },
    {
      path: '/einstellungen',
      name: 'einstellungen',
      component: () => import('@/views/EinstellungenView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/einstellungen/umgebungen',
      name: 'schema-umgebungen',
      component: () => import('@/views/SchemaUmgebungenView.vue'),
      meta: { requiresAdmin: true },
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

  // Frontend-seitige UX-Absicherung für die Betreiber-Einstellungen (Zahnrad-Bereich): Die
  // eigentliche Durchsetzung bleibt wie überall der Backend-Endpunkt (@RolesAllowed
  // "dienstleister-admin"). Aktuell gibt es nur diese eine Rolle, ein Redirect verhindert aber
  // zumindest eine verwirrende 403-Seite für Nutzer ohne die Rolle.
  if (to.meta.requiresAdmin && !authStore.roles.includes('dienstleister-admin')) {
    return { name: 'schultraeger-liste' }
  }

  return true
})

export default router
