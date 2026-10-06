<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../lib/api'

const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const error = ref(route.query.expired ? 'Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.' : '')
const busy = ref(false)

const DEMO = { email: 'demo@belegfluss.app', password: 'demo-belegfluss' }

async function submit() {
  error.value = ''
  busy.value = true
  try {
    await api.login(email.value, password.value)
    const next = typeof route.query.next === 'string' && route.query.next.startsWith('/') ? route.query.next : '/'
    await router.push(next)
  } catch (e) {
    error.value = e instanceof ApiError && e.status === 401 ? 'E-Mail oder Passwort ist falsch.' : 'Anmeldung fehlgeschlagen.'
  } finally {
    busy.value = false
  }
}

function useDemo() {
  email.value = DEMO.email
  password.value = DEMO.password
  void submit()
}
</script>

<template>
  <div class="mx-auto mt-10 grid max-w-4xl gap-8 md:grid-cols-2 md:items-center">
    <section>
      <div class="mb-4 flex items-center gap-3">
        <img src="/favicon.svg" alt="" class="h-10 w-10" />
        <h1 class="text-3xl font-bold text-slate-900">Belegfluss</h1>
      </div>
      <p class="text-lg text-slate-700">Rechnungseingang mit KI – vom PDF bis zur Buchung.</p>
      <ul class="mt-4 space-y-2 text-sm text-slate-600">
        <li>✓ KI liest Lieferant, Rechnungsnummer, Beträge, USt. und IBAN aus</li>
        <li>✓ Automatische Prüfung auf Dubletten, Rechenfehler und geänderte Bankdaten</li>
        <li>✓ Freigabe nach dem Vier-Augen-Prinzip mit lückenloser Historie</li>
        <li>✓ Export für die Buchhaltung im DATEV-Stil</li>
      </ul>
    </section>

    <section class="card p-6">
      <h2 class="mb-4 text-lg font-semibold">Anmelden</h2>
      <form class="space-y-4" @submit.prevent="submit">
        <div>
          <label class="label" for="email">E-Mail</label>
          <input id="email" v-model="email" type="email" class="input" autocomplete="username" required />
        </div>
        <div>
          <label class="label" for="password">Passwort</label>
          <input id="password" v-model="password" type="password" class="input" autocomplete="current-password" required />
        </div>
        <p v-if="error" class="text-sm text-rose-600" role="alert">{{ error }}</p>
        <button type="submit" class="btn btn-primary w-full" :disabled="busy">{{ busy ? 'Anmelden …' : 'Anmelden' }}</button>
      </form>
      <div class="mt-5 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
        <p class="font-medium text-slate-700">Live-Demo ansehen</p>
        <p class="mt-1">Lesezugriff ohne Upload oder Freigabe.</p>
        <button type="button" class="btn btn-secondary mt-2 w-full" :disabled="busy" @click="useDemo">Als Demo anmelden</button>
      </div>
    </section>
  </div>
</template>
