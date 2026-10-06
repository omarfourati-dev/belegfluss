import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { setUnauthorizedHandler } from './lib/api'
import './style.css'

setUnauthorizedHandler(() => {
  void router.push({ name: 'login', query: { expired: '1' } })
})

createApp(App).use(router).mount('#app')
