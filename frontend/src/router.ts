import { createRouter, createWebHashHistory } from 'vue-router'
import { auth, can } from './lib/auth'
import type { Role } from './lib/types'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    role?: Role
  }
}

// Hash history: the backend only has to serve index.html at "/", no fallback routing needed.
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
    { path: '/', name: 'invoices', component: () => import('./views/InvoicesView.vue') },
    { path: '/invoices/:id', name: 'invoice', component: () => import('./views/InvoiceDetailView.vue'), props: true },
    { path: '/users', name: 'users', component: () => import('./views/UsersView.vue'), meta: { role: 'ADMIN' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.public && !auth.loggedIn.value) {
    return { name: 'login', query: to.fullPath !== '/' ? { next: to.fullPath } : {} }
  }
  if (to.meta.role && !can(to.meta.role)) {
    return { name: 'invoices' }
  }
  return true
})
