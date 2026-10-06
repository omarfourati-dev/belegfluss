<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api, ApiError } from '../lib/api'
import { can } from '../lib/auth'
import { EVENT_LABEL, formatDate, formatDateTime, formatIban, formatMoney, WARNING_LABEL } from '../lib/format'
import { subscribeToStatusChanges } from '../lib/live'
import type { Invoice, InvoiceEvent } from '../lib/types'

const props = defineProps<{ id: string }>()

const invoice = ref<Invoice | null>(null)
const history = ref<InvoiceEvent[]>([])
const pdfUrl = ref('')
const error = ref('')
const actionError = ref('')
const busy = ref(false)
const comment = ref('')
const mode = ref<'none' | 'approve' | 'reject'>('none')
let unsubscribe: () => void = () => {}

async function load() {
  try {
    const [inv, events] = await Promise.all([api.invoice(props.id), api.history(props.id)])
    invoice.value = inv
    history.value = events
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Rechnung konnte nicht geladen werden'
  }
}

async function loadPdf() {
  try {
    const blob = await api.document(props.id)
    pdfUrl.value = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
  } catch {
    /* preview is optional */
  }
}

onMounted(() => {
  void load()
  void loadPdf()
  unsubscribe = subscribeToStatusChanges((change) => {
    if (change.invoiceId === props.id) void load()
  })
})
onUnmounted(() => {
  unsubscribe()
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
})

const needsComment = computed(() => (invoice.value?.warnings.length ?? 0) > 0)

async function run(action: () => Promise<Invoice>) {
  actionError.value = ''
  busy.value = true
  try {
    invoice.value = await action()
    history.value = await api.history(props.id)
    mode.value = 'none'
    comment.value = ''
  } catch (e) {
    actionError.value = e instanceof ApiError ? e.message : 'Aktion fehlgeschlagen'
  } finally {
    busy.value = false
  }
}

const approve = () => run(() => api.approve(props.id, comment.value.trim()))
const reject = () => run(() => api.reject(props.id, comment.value.trim()))
const book = () => run(() => api.book(props.id))
</script>

<template>
  <div class="space-y-6">
    <RouterLink to="/" class="text-sm text-brand-700 hover:underline">← Zurück zur Übersicht</RouterLink>

    <p v-if="error" class="text-rose-600" role="alert">{{ error }}</p>

    <template v-if="invoice">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 class="text-2xl font-bold text-slate-900">{{ invoice.supplierName ?? invoice.originalFilename }}</h1>
          <p class="text-sm text-slate-500">
            {{ invoice.invoiceNumber ? `Rechnung ${invoice.invoiceNumber}` : invoice.originalFilename }}
          </p>
        </div>
        <StatusBadge :status="invoice.status" />
      </div>

      <div
        v-if="invoice.warnings.length"
        class="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900"
        role="status"
      >
        <p class="font-semibold">Automatische Prüfung: {{ invoice.warnings.length }} Warnung(en)</p>
        <ul class="mt-1 list-disc pl-5">
          <li v-for="w in invoice.warnings" :key="w.code">{{ WARNING_LABEL[w.code] ?? w.message }}</li>
        </ul>
      </div>
      <div v-if="invoice.status === 'FAILED'" class="rounded-xl border border-rose-300 bg-rose-50 p-4 text-sm text-rose-800">
        {{ invoice.errorMessage }}
      </div>

      <div class="grid gap-6 lg:grid-cols-5">
        <div class="space-y-6 lg:col-span-2">
          <section class="card p-5">
            <h2 class="mb-3 font-semibold">Ausgelesene Daten</h2>
            <dl class="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              <dt class="text-slate-500">Rechnungsdatum</dt><dd>{{ formatDate(invoice.invoiceDate) }}</dd>
              <dt class="text-slate-500">Fällig</dt><dd>{{ formatDate(invoice.dueDate) }}</dd>
              <dt class="text-slate-500">Netto</dt><dd class="tabular-nums">{{ formatMoney(invoice.netAmount, invoice.currency) }}</dd>
              <dt class="text-slate-500">USt.</dt><dd class="tabular-nums">{{ formatMoney(invoice.vatAmount, invoice.currency) }}</dd>
              <dt class="text-slate-500">Brutto</dt><dd class="font-semibold tabular-nums">{{ formatMoney(invoice.grossAmount, invoice.currency) }}</dd>
              <dt class="text-slate-500">IBAN</dt><dd class="font-mono text-xs break-all">{{ formatIban(invoice.iban) }}</dd>
            </dl>
            <p v-if="invoice.decisionComment" class="mt-4 rounded-lg bg-slate-50 p-3 text-sm">
              <span class="font-medium">Kommentar:</span> {{ invoice.decisionComment }}
            </p>
          </section>

          <section v-if="invoice.status === 'EXTRACTED' && can('APPROVER')" class="card space-y-3 p-5">
            <h2 class="font-semibold">Freigabe</h2>
            <p class="text-xs text-slate-500">Vier-Augen-Prinzip: Wer die Rechnung hochgeladen hat, kann sie nicht selbst freigeben.</p>
            <div v-if="mode === 'none'" class="flex gap-2">
              <button class="btn btn-primary flex-1" @click="mode = 'approve'">Freigeben</button>
              <button class="btn btn-secondary flex-1" @click="mode = 'reject'">Ablehnen</button>
            </div>
            <form v-else class="space-y-2" @submit.prevent="mode === 'approve' ? approve() : reject()">
              <label class="label" for="comment">
                {{ mode === 'reject' ? 'Grund der Ablehnung' : needsComment ? 'Begründung (wegen Warnungen erforderlich)' : 'Kommentar (optional)' }}
              </label>
              <textarea id="comment" v-model="comment" class="input" rows="3" maxlength="500"
                        :required="mode === 'reject' || needsComment" />
              <div class="flex gap-2">
                <button type="submit" class="flex-1" :class="mode === 'approve' ? 'btn btn-primary' : 'btn btn-danger'" :disabled="busy">
                  {{ mode === 'approve' ? 'Jetzt freigeben' : 'Jetzt ablehnen' }}
                </button>
                <button type="button" class="btn btn-secondary" @click="mode = 'none'; actionError = ''">Abbrechen</button>
              </div>
            </form>
            <p v-if="actionError" class="text-sm text-rose-600" role="alert">{{ actionError }}</p>
          </section>

          <section v-if="invoice.status === 'APPROVED' && can('ACCOUNTANT')" class="card space-y-3 p-5">
            <h2 class="font-semibold">Buchhaltung</h2>
            <button class="btn btn-primary w-full" :disabled="busy" @click="book">Als verbucht markieren</button>
            <p v-if="actionError" class="text-sm text-rose-600" role="alert">{{ actionError }}</p>
          </section>

          <section class="card p-5">
            <h2 class="mb-3 font-semibold">Historie</h2>
            <ol class="space-y-3 border-l border-slate-200 pl-4 text-sm">
              <li v-for="(event, index) in history" :key="index" class="relative">
                <span class="absolute top-1.5 -left-[21px] h-2.5 w-2.5 rounded-full bg-brand-600" />
                <p class="font-medium">{{ EVENT_LABEL[event.type] ?? event.type }}</p>
                <p class="text-xs text-slate-500">{{ event.actor }} · {{ formatDateTime(event.at) }}</p>
                <p v-if="event.comment" class="text-xs text-slate-600">{{ event.comment }}</p>
              </li>
            </ol>
          </section>
        </div>

        <section class="card overflow-hidden lg:col-span-3">
          <iframe v-if="pdfUrl" :src="pdfUrl" title="Original-PDF" class="h-[75vh] w-full" />
          <p v-else class="p-6 text-sm text-slate-500">PDF-Vorschau wird geladen …</p>
        </section>
      </div>
    </template>
  </div>
</template>
