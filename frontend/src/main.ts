import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { setUnauthorizedHandler } from './lib/api'
import { pwa } from './lib/pwa'
import './style.css'

setUnauthorizedHandler(() => {
  void router.push({ name: 'login', query: { expired: '1' } })
})

createApp(App).use(router).mount('#app')

// installable app; the dev server has no stamped service worker, and the app works without one
if (import.meta.env.PROD) void pwa.register().catch(() => undefined)
