<script setup lang="ts">
import { ref } from 'vue'
import { api, ApiError } from '../lib/api'
import type { Invoice } from '../lib/types'

const emit = defineEmits<{ uploaded: [invoice: Invoice] }>()

const dragging = ref(false)
const busy = ref(false)
const error = ref('')
const input = ref<HTMLInputElement | null>(null)

const MAX_BYTES = 10 * 1024 * 1024

async function handle(files: FileList | null) {
  error.value = ''
  if (!files || files.length === 0) return
  busy.value = true
  try {
    for (const file of Array.from(files)) {
      if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
        error.value = `${file.name}: nur PDF-Dateien werden unterstützt`
        continue
      }
      if (file.size > MAX_BYTES) {
        error.value = `${file.name}: maximal 10 MB`
        continue
      }
      emit('uploaded', await api.upload(file))
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Upload fehlgeschlagen'
  } finally {
    busy.value = false
    dragging.value = false
    if (input.value) input.value.value = ''
  }
}
</script>

<template>
  <div
    class="card flex cursor-pointer flex-col items-center justify-center gap-2 border-2 border-dashed px-6 py-8 text-center transition"
    :class="dragging ? 'border-brand-600 bg-brand-50' : 'border-slate-300 hover:border-brand-600'"
    role="button"
    tabindex="0"
    aria-label="PDF-Rechnungen hochladen"
    @click="input?.click()"
    @keydown.enter="input?.click()"
    @dragover.prevent="dragging = true"
    @dragleave.prevent="dragging = false"
    @drop.prevent="handle($event.dataTransfer?.files ?? null)"
  >
    <svg class="h-8 w-8 text-brand-700" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6" aria-hidden="true">
      <path stroke-linecap="round" stroke-linejoin="round" d="M12 16V4m0 0l-4 4m4-4l4 4M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2" />
    </svg>
    <p class="text-sm font-medium text-slate-700">
      {{ busy ? 'Wird hochgeladen …' : 'PDF-Rechnungen hierher ziehen oder klicken' }}
    </p>
    <p class="text-xs text-slate-500">Die KI liest Lieferant, Beträge, USt. und IBAN automatisch aus · max. 10 MB</p>
    <p v-if="error" class="text-sm text-rose-600">{{ error }}</p>
    <input ref="input" type="file" accept="application/pdf,.pdf" multiple class="hidden" @change="handle(($event.target as HTMLInputElement).files)" />
  </div>
</template>
