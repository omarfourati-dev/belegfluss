import { auth } from './auth'
import type { InvoiceStatus } from './types'

export interface StatusChange {
  invoiceId: string
  status: InvoiceStatus
}

export interface SseMessage {
  event: string
  data: string
}

/**
 * Parses a chunk of a Server-Sent Events stream. Returns complete messages and the
 * unfinished rest, which must be prepended to the next chunk.
 */
export function parseSse(buffer: string): { messages: SseMessage[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const blocks = normalized.split('\n\n')
  const rest = blocks.pop() ?? ''
  const messages: SseMessage[] = []
  for (const block of blocks) {
    let event = 'message'
    const data: string[] = []
    for (const line of block.split('\n')) {
      if (line.startsWith(':')) continue // comment / heartbeat
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
    }
    if (data.length > 0) messages.push({ event, data: data.join('\n') })
  }
  return { messages, rest }
}

/**
 * Subscribes to live invoice status changes. EventSource cannot send an Authorization
 * header, so this reads the stream with fetch and reconnects with backoff.
 */
export function subscribeToStatusChanges(
  onChange: (change: StatusChange) => void,
  onDeleted: (invoiceId: string) => void = () => {},
): () => void {
  const controller = new AbortController()
  let retryMs = 1000

  async function connect() {
    while (!controller.signal.aborted && auth.token) {
      try {
        const response = await fetch('/api/invoices/events', {
          headers: { Authorization: `Bearer ${auth.token}`, Accept: 'text/event-stream' },
          signal: controller.signal,
        })
        if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`)
        retryMs = 1000
        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
        let buffer = ''
        for (;;) {
          const { value, done } = await reader.read()
          if (done) break
          const parsed = parseSse(buffer + value)
          buffer = parsed.rest
          for (const message of parsed.messages) {
            if (message.event === 'invoice') onChange(JSON.parse(message.data) as StatusChange)
            if (message.event === 'invoice-deleted') onDeleted((JSON.parse(message.data) as { invoiceId: string }).invoiceId)
          }
        }
      } catch {
        if (controller.signal.aborted) return
      }
      await new Promise((resolve) => setTimeout(resolve, retryMs))
      retryMs = Math.min(retryMs * 2, 30_000)
    }
  }

  void connect()
  return () => controller.abort()
}
