<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError } from '../lib/api'
import { formatDate, ROLE_LABEL } from '../lib/format'
import type { Role, User } from '../lib/types'

const users = ref<User[]>([])
const error = ref('')
const success = ref('')
const busy = ref(false)
const form = ref({ email: '', displayName: '', password: '', role: 'EMPLOYEE' as Role })
const roles: Role[] = ['VIEWER', 'EMPLOYEE', 'APPROVER', 'ACCOUNTANT', 'ADMIN']

async function load() {
  users.value = await api.users()
}

onMounted(() => void load())

async function create() {
  error.value = ''
  success.value = ''
  busy.value = true
  try {
    const user = await api.createUser({ ...form.value })
    success.value = `${user.displayName} wurde angelegt.`
    form.value = { email: '', displayName: '', password: '', role: 'EMPLOYEE' }
    await load()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Benutzer konnte nicht angelegt werden'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="grid gap-6 lg:grid-cols-3">
    <section class="card overflow-x-auto lg:col-span-2">
      <h1 class="px-5 pt-5 text-xl font-bold">Benutzer</h1>
      <table class="mt-3 w-full min-w-[480px] text-sm">
        <thead class="border-y border-slate-200 bg-slate-50 text-left text-xs text-slate-500 uppercase">
          <tr><th class="px-5 py-2">Name</th><th class="px-5 py-2">E-Mail</th><th class="px-5 py-2">Rolle</th><th class="px-5 py-2">Seit</th></tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id" class="border-b border-slate-100 last:border-0">
            <td class="px-5 py-2 font-medium">{{ user.displayName }}</td>
            <td class="px-5 py-2 text-slate-600">{{ user.email }}</td>
            <td class="px-5 py-2">{{ ROLE_LABEL[user.role] }}</td>
            <td class="px-5 py-2 text-slate-500">{{ formatDate(user.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="card p-5">
      <h2 class="mb-3 font-semibold">Neuer Benutzer</h2>
      <form class="space-y-3" @submit.prevent="create">
        <div><label class="label" for="name">Name</label><input id="name" v-model="form.displayName" class="input" required maxlength="100" /></div>
        <div><label class="label" for="mail">E-Mail</label><input id="mail" v-model="form.email" type="email" class="input" required /></div>
        <div>
          <label class="label" for="pw">Startpasswort</label>
          <input id="pw" v-model="form.password" type="password" class="input" required minlength="12" autocomplete="new-password" />
          <p class="mt-1 text-xs text-slate-500">Mindestens 12 Zeichen</p>
        </div>
        <div>
          <label class="label" for="role">Rolle</label>
          <select id="role" v-model="form.role" class="input">
            <option v-for="role in roles" :key="role" :value="role">{{ ROLE_LABEL[role] }}</option>
          </select>
        </div>
        <p v-if="error" class="text-sm text-rose-600" role="alert">{{ error }}</p>
        <p v-if="success" class="text-sm text-emerald-700">{{ success }}</p>
        <button type="submit" class="btn btn-primary w-full" :disabled="busy">Anlegen</button>
      </form>
    </section>
  </div>
</template>
