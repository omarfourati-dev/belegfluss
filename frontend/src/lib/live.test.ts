import { describe, expect, it } from 'vitest'
import { parseSse } from './live'

describe('parseSse', () => {
  it('parses named events and keeps the unfinished rest', () => {
    const { messages, rest } = parseSse(
      'event:invoice\ndata:{"invoiceId":"1","status":"EXTRACTED"}\n\nevent:inv',
    )
    expect(messages).toEqual([{ event: 'invoice', data: '{"invoiceId":"1","status":"EXTRACTED"}' }])
    expect(rest).toBe('event:inv')
  })

  it('ignores heartbeat comments', () => {
    expect(parseSse(':ping\n\n').messages).toEqual([])
  })

  it('handles CRLF line endings and multi-line data', () => {
    const { messages } = parseSse('event: ready\r\ndata: a\r\ndata: b\r\n\r\n')
    expect(messages).toEqual([{ event: 'ready', data: 'a\nb' }])
  })

  it('defaults to the message event name', () => {
    expect(parseSse('data:x\n\n').messages[0].event).toBe('message')
  })
})
