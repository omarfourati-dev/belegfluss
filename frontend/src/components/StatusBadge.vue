<script setup lang="ts">
import { computed } from 'vue'
import { STATUS } from '../lib/format'
import type { InvoiceStatus } from '../lib/types'

const props = defineProps<{ status: InvoiceStatus }>()
const info = computed(() => STATUS[props.status])
</script>

<template>
  <span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap" :class="info.tone">
    <span v-if="status === 'RECEIVED'" class="h-2 w-2 animate-pulse rounded-full bg-sky-500" />
    {{ info.label }}
  </span>
</template>
