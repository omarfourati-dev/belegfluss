<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api, ApiError } from '../lib/api'
import { can } from '../lib/auth'
import {
  EVENT_LABEL, formatDate, formatDateTime, formatIban, formatMoney, SOURCE_LABEL, translateChangeComment, WARNING_LABEL,
} from '../lib/format'
import { subscribeToStatusChanges } from '../lib/live'
import type { Invoice, InvoiceEvent, InvoiceFields } from '../lib/types'

const props = defineProps<{ id: string }>()

const invoice = ref<Invoice | null>(null)
const history = ref<InvoiceEvent[]>([])
const pdfUrl = ref('')
const xmlText = ref('')
const error = ref('')
const actionError = ref('')
const busy = ref(false)
const comment = ref('')
const mode = ref<'none' | 'approve' | 'reject'>('none')
const editing = ref(false)
const form = ref<InvoiceFields | null>(null)
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

async function loadDocument() {
  try {
    const blob = await api.document(props.id)
    if (blob.type.startsWith('text/')) {
      xmlText.value = await blob.text()
    } else {
      pdfUrl.value = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
    }
  } catch {
    /* preview is optional */
  }
}

onMounted(() => {
  void load()
  void loadDocument()
  unsubscribe = subscribeToStatusChanges((change) => {
    if (change.invoiceId === props.id && !editing.value) void load()
  })
})
onUnmounted(() => {
  unsubscribe()
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
})

const needsComment = computed(() => (invoice.value?.warnings.length ?? 0) > 0)
const canCorrect = computed(() =>
  !!invoice.value && ['EXTRACTED', 'FAILED'].includes(invoice.value.status) && can('EMPLOYEE'))

async function run(action: () => Promise<Invoice>) {
  actionError.value = ''
  busy.value = true
  try {
    invoice.value = await action()
    history.value = await api.history(props.id)
    mode.value = 'none'
    comment.value = ''
    editing.value = false
  } catch (e) {
    actionError.value = e instanceof ApiError ? e.message : 'Aktion fehlgeschlagen'
  } finally {
    busy.value = false
  }
}

const approve = () => run(() => api.approve(props.id, comment.value.trim()))
const reject = () => run(() => api.reject(props.id, comment.value.trim()))
const book = () => run(() => api.book(props.id))

function startEdit() {
  const i = invoice.value!
  form.value = {
    supplierName: i.supplierName ?? '',
    invoiceNumber: i.invoiceNumber ?? '',
    invoiceDate: i.invoiceDate ?? '',
    dueDate: i.dueDate,
    netAmount: i.netAmount,
    vatAmount: i.vatAmount,
    grossAmount: i.grossAmount ?? 0,
    currency: i.currency ?? 'EUR',
    iban: i.iban,
  }
  actionError.value = ''
  editing.value = true
}

function saveCorrection() {
  const f = form.value!
  const toNumber = (v: number | string | null) => (v === '' || v === null ? null : Number(v))
  return run(() => api.correct(props.id, {
    ...f,
    dueDate: f.dueDate || null,
    iban: f.iban?.trim() || null,
    currency: f.currency.trim().toUpperCase(),
    netAmount: toNumber(f.netAmount),
    vatAmount: toNumber(f.vatAmount),
    grossAmount: Number(f.grossAmount),
  }))
}
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
        <div class="flex flex-wrap items-center gap-2">
          <span v-if="invoice.source === 'E_INVOICE'" class="rounded-full bg-indigo-100 px-2.5 py-0.5 text-xs font-medium text-indigo-800">
            E-Rechnung · {{ invoice.eInvoiceFormat }}
          </span>
          <span v-if="invoice.manuallyCorrected" class="rounded-full bg-slate-200 px-2.5 py-0.5 text-xs font-medium text-slate-700">
            Von Hand korrigiert
          </span>
          <StatusBadge :status="invoice.status" />
        </div>
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
        <span v-if="canCorrect"> – Du kannst die Felder unten von Hand erfassen.</span>
      </div>

      <div class="grid gap-6 lg:grid-cols-5">
        <div class="space-y-6 lg:col-span-2">
          <section class="card p-5">
            <div class="mb-3 flex items-center justify-between gap-2">
              <h2 class="font-semibold">{{ editing ? 'Felder korrigieren' : 'Ausgelesene Daten' }}</h2>
              <button v-if="canCorrect && !editing" class="btn btn-secondary py-1 text-xs" @click="startEdit">
                {{ invoice.status === 'FAILED' ? 'Von Hand erfassen' : 'Korrigieren' }}
              </button>
            </div>

            <dl v-if="!editing" class="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              <dt class="text-slate-500">Rechnungsdatum</dt><dd>{{ formatDate(invoice.invoiceDate) }}</dd>
              <dt class="text-slate-500">Fällig</dt><dd>{{ formatDate(invoice.dueDate) }}</dd>
              <dt class="text-slate-500">Netto</dt><dd class="tabular-nums">{{ formatMoney(invoice.netAmount, invoice.currency) }}</dd>
              <dt class="text-slate-500">USt.</dt><dd class="tabular-nums">{{ formatMoney(invoice.vatAmount, invoice.currency) }}</dd>
              <dt class="text-slate-500">Brutto</dt><dd class="font-semibold tabular-nums">{{ formatMoney(invoice.grossAmount, invoice.currency) }}</dd>
              <dt class="text-slate-500">IBAN</dt><dd class="font-mono text-xs break-all">{{ formatIban(invoice.iban) }}</dd>
            </dl>

            <form v-else-if="form" class="grid grid-cols-2 gap-3 text-sm" @submit.prevent="saveCorrection">
              <div class="col-span-2"><label class="label" for="c-supplier">Lieferant</label><input id="c-supplier" v-model="form.supplierName" class="input" required maxlength="255" /></div>
              <div class="col-span-2"><label class="label" for="c-number">Rechnungsnummer</label><input id="c-number" v-model="form.invoiceNumber" class="input" required maxlength="100" /></div>
              <div><label class="label" for="c-date">Rechnungsdatum</label><input id="c-date" v-model="form.invoiceDate" type="date" class="input" required /></div>
              <div><label class="label" for="c-due">Fällig</label><input id="c-due" v-model="form.dueDate" type="date" class="input" /></div>
              <div><label class="label" for="c-net">Netto</label><input id="c-net" v-model="form.netAmount" type="number" step="0.01" min="0" class="input" /></div>
              <div><label class="label" for="c-vat">USt.</label><input id="c-vat" v-model="form.vatAmount" type="number" step="0.01" min="0" class="input" /></div>
              <div><label class="label" for="c-gross">Brutto</label><input id="c-gross" v-model="form.grossAmount" type="number" step="0.01" min="0" class="input" required /></div>
              <div><label class="label" for="c-cur">Währung</label><input id="c-cur" v-model="form.currency" class="input" required pattern="[A-Za-z]{3}" maxlength="3" /></div>
              <div class="col-span-2"><label class="label" for="c-iban">IBAN</label><input id="c-iban" v-model="form.iban" class="input font-mono" maxlength="42" /></div>
              <p class="col-span-2 text-xs text-slate-500">
                Vier-Augen-Prinzip: Wer Felder korrigiert, kann die Rechnung danach nicht selbst freigeben.
              </p>
              <div class="col-span-2 flex gap-2">
                <button type="submit" class="btn btn-primary flex-1" :disabled="busy">Speichern</button>
                <button type="button" class="btn btn-secondary" @click="editing = false; actionError = ''">Abbrechen</button>
              </div>
              <p v-if="actionError" class="col-span-2 text-sm text-rose-600" role="alert">{{ actionError }}</p>
            </form>

            <p v-if="invoice.source && !editing" class="mt-4 text-xs text-slate-500">{{ SOURCE_LABEL[invoice.source] }}</p>
            <p v-if="invoice.decisionComment && !editing" class="mt-3 rounded-lg bg-slate-50 p-3 text-sm">
              <span class="font-medium">Kommentar:</span> {{ invoice.decisionComment }}
            </p>
          </section>

          <section v-if="invoice.status === 'EXTRACTED' && can('APPROVER') && !editing" class="card space-y-3 p-5">
            <h2 class="font-semibold">Freigabe</h2>
            <p class="text-xs text-slate-500">Vier-Augen-Prinzip: Wer die Rechnung hochgeladen oder korrigiert hat, kann sie nicht selbst freigeben.</p>
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
                <p v-if="event.comment" class="text-xs text-slate-600">{{ translateChangeComment(event.comment) }}</p>
              </li>
            </ol>
          </section>
        </div>

        <section class="card overflow-hidden lg:col-span-3">
          <iframe v-if="pdfUrl" :src="pdfUrl" title="Original-PDF" class="h-[75vh] w-full" />
          <div v-else-if="xmlText" class="h-[75vh] overflow-auto bg-slate-900 p-4">
            <p class="mb-2 text-xs font-medium text-slate-400">E-Rechnung (XML-Original)</p>
            <pre class="text-xs leading-relaxed whitespace-pre-wrap text-slate-100">{{ xmlText }}</pre>
          </div>
          <p v-else class="p-6 text-sm text-slate-500">Vorschau wird geladen …</p>
        </section>
      </div>
    </template>
  </div>
</template>
