<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StatusBadge from '../components/StatusBadge.vue'
import UploadDropzone from '../components/UploadDropzone.vue'
import { api, ApiError } from '../lib/api'
import { can } from '../lib/auth'
import { formatDate, formatMoney, STATUS } from '../lib/format'
import { subscribeToStatusChanges } from '../lib/live'
import type { Invoice, InvoiceStatus } from '../lib/types'

const router = useRouter()
const invoices = ref<Invoice[]>([])
const loading = ref(true)
const error = ref('')
const filter = ref<InvoiceStatus | 'ALL' | 'WARNINGS'>('ALL')
let unsubscribe: () => void = () => {}

async function load() {
  try {
    invoices.value = await api.invoices()
    error.value = ''
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Rechnungen konnten nicht geladen werden'
  } finally {
    loading.value = false
  }
}

async function refreshOne(id: string) {
  try {
    const updated = await api.invoice(id)
    const index = invoices.value.findIndex((i) => i.id === id)
    if (index >= 0) invoices.value[index] = updated
    else invoices.value.unshift(updated)
  } catch {
    /* ignore single refresh errors */
  }
}

onMounted(() => {
  void load()
  unsubscribe = subscribeToStatusChanges((change) => void refreshOne(change.invoiceId))
})
onUnmounted(() => unsubscribe())

function onUploaded(invoice: Invoice) {
  if (!invoices.value.some((i) => i.id === invoice.id)) invoices.value.unshift(invoice)
}

const stats = computed(() => {
  const count = (s: InvoiceStatus) => invoices.value.filter((i) => i.status === s).length
  const open = invoices.value.filter((i) => i.status === 'EXTRACTED')
  return {
    pending: count('EXTRACTED'),
    approved: count('APPROVED'),
    warnings: invoices.value.filter((i) => i.warnings.length > 0 && ['EXTRACTED', 'APPROVED'].includes(i.status)).length,
    openAmount: open.reduce((sum, i) => sum + (i.grossAmount ?? 0), 0),
  }
})

const visible = computed(() => {
  if (filter.value === 'ALL') return invoices.value
  if (filter.value === 'WARNINGS') return invoices.value.filter((i) => i.warnings.length > 0)
  return invoices.value.filter((i) => i.status === filter.value)
})

const exporting = ref(false)
async function exportCsv() {
  exporting.value = true
  try {
    const blob = await api.exportCsv()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `belegfluss-export-${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Export fehlgeschlagen'
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Rechnungseingang</h1>
        <p class="text-sm text-slate-500">Status aktualisiert sich live, sobald die KI fertig ist.</p>
      </div>
      <button v-if="can('ACCOUNTANT')" class="btn btn-secondary" :disabled="exporting" @click="exportCsv">
        CSV-Export (DATEV-Stil)
      </button>
    </div>

    <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <div class="card p-4">
        <p class="text-xs text-slate-500">Zur Freigabe</p>
        <p class="mt-1 text-2xl font-semibold">{{ stats.pending }}</p>
      </div>
      <div class="card p-4">
        <p class="text-xs text-slate-500">Offener Betrag</p>
        <p class="mt-1 text-2xl font-semibold">{{ formatMoney(stats.openAmount) }}</p>
      </div>
      <div class="card p-4">
        <p class="text-xs text-slate-500">Freigegeben, nicht verbucht</p>
        <p class="mt-1 text-2xl font-semibold">{{ stats.approved }}</p>
      </div>
      <div class="card p-4">
        <p class="text-xs text-slate-500">Mit Warnungen</p>
        <p class="mt-1 text-2xl font-semibold" :class="stats.warnings ? 'text-amber-600' : ''">{{ stats.warnings }}</p>
      </div>
    </div>

    <UploadDropzone v-if="can('EMPLOYEE')" @uploaded="onUploaded" />

    <div class="flex flex-wrap gap-2 text-sm">
      <button
        v-for="option in (['ALL', 'EXTRACTED', 'WARNINGS', 'APPROVED', 'BOOKED', 'REJECTED', 'FAILED'] as const)"
        :key="option"
        class="rounded-full border px-3 py-1"
        :class="filter === option ? 'border-brand-700 bg-brand-700 text-white' : 'border-slate-300 bg-white text-slate-600 hover:border-brand-600'"
        @click="filter = option"
      >
        {{ option === 'ALL' ? 'Alle' : option === 'WARNINGS' ? 'Mit Warnungen' : STATUS[option].label }}
      </button>
    </div>

    <p v-if="error" class="text-sm text-rose-600" role="alert">{{ error }}</p>

    <div class="card overflow-x-auto">
      <table class="w-full min-w-[640px] text-sm">
        <thead class="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500 uppercase">
          <tr>
            <th class="px-4 py-3">Lieferant</th>
            <th class="px-4 py-3">Rechnungs-Nr.</th>
            <th class="px-4 py-3">Datum</th>
            <th class="px-4 py-3 text-right">Brutto</th>
            <th class="px-4 py-3">Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="5" class="px-4 py-8 text-center text-slate-500">Lädt …</td></tr>
          <tr v-else-if="visible.length === 0"><td colspan="5" class="px-4 py-8 text-center text-slate-500">Keine Rechnungen</td></tr>
          <tr
            v-for="invoice in visible"
            :key="invoice.id"
            class="cursor-pointer border-b border-slate-100 last:border-0 hover:bg-slate-50"
            @click="router.push({ name: 'invoice', params: { id: invoice.id } })"
          >
            <td class="px-4 py-3">
              <div class="font-medium text-slate-900">{{ invoice.supplierName ?? invoice.originalFilename }}</div>
              <div class="flex flex-wrap gap-x-2 text-xs">
                <span v-if="invoice.source === 'E_INVOICE'" class="text-indigo-700">E-Rechnung</span>
                <span v-if="invoice.warnings.length" class="text-amber-600">⚠ {{ invoice.warnings.length }} Warnung(en)</span>
              </div>
            </td>
            <td class="px-4 py-3 text-slate-600">{{ invoice.invoiceNumber ?? '–' }}</td>
            <td class="px-4 py-3 text-slate-600">{{ formatDate(invoice.invoiceDate) }}</td>
            <td class="px-4 py-3 text-right font-medium tabular-nums">{{ formatMoney(invoice.grossAmount, invoice.currency) }}</td>
            <td class="px-4 py-3"><StatusBadge :status="invoice.status" /></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
