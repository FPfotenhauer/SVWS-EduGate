import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { configureUnauthorizedRecoveryHandler } from './api/httpClient'
import { useAuthStore } from './auth/authStore'
import { initTheme } from './composables/useTheme'
import './style.css'

initTheme()

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

configureUnauthorizedRecoveryHandler(async () => {
	const authStore = useAuthStore(pinia)
	const renewed = await authStore.renewToken()
	if (renewed) {
		return true
	}

	await authStore.login()
	return false
})

app.mount('#app')
