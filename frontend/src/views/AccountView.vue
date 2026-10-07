<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, ApiError } from '../lib/api'
import { auth } from '../lib/auth'
import { ROLE_LABEL } from '../lib/format'

const DEMO_EMAIL = 'demo@belegfluss.app'

const current = ref('')
const next = ref('')
const repeat = ref('')
const busy = ref(false)
const error = ref('')
const success = ref('')

const isDemo = computed(() => auth.user.value?.email === DEMO_EMAIL)

async function submit() {
  error.value = ''
  success.value = ''
  if (next.value !== repeat.value) {
    error.value = 'Die beiden neuen Passwörter stimmen nicht überein.'
    return
  }
  busy.value = true
  try {
    await api.changePassword(current.value, next.value)
    success.value = 'Passwort geändert. Es gilt ab der nächsten Anmeldung.'
    current.value = next.value = repeat.value = ''
  } catch (e) {
    error.value = e instanceof ApiError && e.title === 'Wrong password'
      ? 'Das aktuelle Passwort ist falsch.'
      : e instanceof ApiError ? e.message : 'Passwort konnte nicht geändert werden.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="mx-auto grid max-w-3xl gap-6 md:grid-cols-2">
    <section class="card p-5">
      <h1 class="mb-3 text-xl font-bold">Mein Konto</h1>
      <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt class="text-slate-500">Name</dt><dd>{{ auth.user.value?.displayName }}</dd>
        <dt class="text-slate-500">E-Mail</dt><dd class="break-all">{{ auth.user.value?.email }}</dd>
        <dt class="text-slate-500">Rolle</dt><dd>{{ ROLE_LABEL[auth.user.value?.roles[0] ?? 'VIEWER'] }}</dd>
      </dl>
    </section>

    <section class="card p-5">
      <h2 class="mb-3 font-semibold">Passwort ändern</h2>
      <p v-if="isDemo" class="text-sm text-slate-600">
        Das öffentliche Demo-Konto behält sein Passwort, damit jeder die Demo ansehen kann.
      </p>
      <form v-else class="space-y-3" @submit.prevent="submit">
        <div>
          <label class="label" for="pw-current">Aktuelles Passwort</label>
          <input id="pw-current" v-model="current" type="password" class="input" autocomplete="current-password" required />
        </div>
        <div>
          <label class="label" for="pw-new">Neues Passwort</label>
          <input id="pw-new" v-model="next" type="password" class="input" autocomplete="new-password" required minlength="12" />
          <p class="mt-1 text-xs text-slate-500">Mindestens 12 Zeichen</p>
        </div>
        <div>
          <label class="label" for="pw-repeat">Neues Passwort wiederholen</label>
          <input id="pw-repeat" v-model="repeat" type="password" class="input" autocomplete="new-password" required minlength="12" />
        </div>
        <p v-if="error" class="text-sm text-rose-600" role="alert">{{ error }}</p>
        <p v-if="success" class="text-sm text-emerald-700" role="status">{{ success }}</p>
        <button type="submit" class="btn btn-primary w-full" :disabled="busy">Passwort ändern</button>
      </form>
    </section>
  </div>
</template>
