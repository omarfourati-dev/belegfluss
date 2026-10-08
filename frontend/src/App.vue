<script setup lang="ts">
import { useRouter } from 'vue-router'
import { auth, can } from './lib/auth'
import { ROLE_LABEL } from './lib/format'
import { pwa } from './lib/pwa'

const router = useRouter()

function logout() {
  auth.clear()
  void router.push({ name: 'login' })
}
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <p v-if="pwa.offline.value" role="status" class="bg-amber-100 px-4 py-2 text-center text-sm text-amber-900">
      Keine Verbindung – Rechnungen werden geladen, sobald du wieder online bist.
    </p>
    <p v-if="pwa.updateReady.value" role="status" class="flex items-center justify-center gap-3 bg-brand-700 px-4 py-2 text-sm text-white">
      Neue Version von Belegfluss verfügbar.
      <button type="button" class="rounded bg-white px-3 py-1 font-semibold text-brand-700" @click="pwa.applyUpdate()">Neu laden</button>
    </p>
    <header v-if="auth.loggedIn.value" class="border-b border-slate-200 bg-white">
      <div class="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
        <RouterLink to="/" class="flex items-center gap-2 font-semibold text-slate-900">
          <img src="/favicon.svg" alt="" class="h-7 w-7" />
          Belegfluss
        </RouterLink>
        <nav class="flex gap-4 text-sm">
          <RouterLink to="/" class="text-slate-600 hover:text-brand-700" exact-active-class="font-semibold text-brand-700">
            Rechnungen
          </RouterLink>
          <RouterLink v-if="can('ADMIN')" to="/users" class="text-slate-600 hover:text-brand-700" active-class="font-semibold text-brand-700">
            Benutzer
          </RouterLink>
          <RouterLink to="/account" class="text-slate-600 hover:text-brand-700 sm:hidden" active-class="font-semibold text-brand-700">
            Konto
          </RouterLink>
          <a href="/swagger-ui.html" target="_blank" rel="noopener" class="text-slate-600 hover:text-brand-700">API</a>
        </nav>
        <div class="ml-auto flex items-center gap-3 text-sm">
          <RouterLink to="/account" class="hidden text-slate-600 hover:text-brand-700 sm:inline" title="Mein Konto">
            {{ auth.user.value?.displayName }}
            <span class="ml-1 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
              {{ ROLE_LABEL[auth.user.value?.roles[0] ?? 'VIEWER'] }}
            </span>
          </RouterLink>
          <button class="btn btn-secondary py-1.5" @click="logout">Abmelden</button>
        </div>
      </div>
    </header>

    <main class="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
      <RouterView />
    </main>

    <footer class="py-6 text-center text-xs text-slate-400">
      Belegfluss · Java 21 · Spring Boot · Spring AI · Vue 3 ·
      <a href="https://github.com/omarfourati-dev/belegfluss" class="underline hover:text-slate-600" target="_blank" rel="noopener">Code auf GitHub</a>
    </footer>
  </div>
</template>
