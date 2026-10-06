import { describe, expect, it } from 'vitest'
import { formatDate, formatIban, formatMoney, STATUS, WARNING_LABEL } from './format'

// Intl uses a narrow no-break space in some environments
const normalize = (s: string) => s.replace(/ | /g, ' ')

describe('format', () => {
  it('formats money the German way', () => {
    expect(normalize(formatMoney(1234.5, 'EUR'))).toBe('1.234,50 €')
    expect(formatMoney(null)).toBe('–')
  })

  it('falls back for unknown currency codes', () => {
    expect(normalize(formatMoney(10, 'XYZ1'))).toContain('10,00')
  })

  it('formats ISO dates without timezone shift', () => {
    expect(formatDate('2026-10-01')).toBe('01.10.2026')
    expect(formatDate(null)).toBe('–')
  })

  it('groups IBANs in blocks of four', () => {
    expect(formatIban('DE89370400440532013000')).toBe('DE89 3704 0044 0532 0130 00')
  })

  it('has a label for every status and every backend warning code', () => {
    expect(Object.keys(STATUS)).toHaveLength(6)
    expect(Object.keys(WARNING_LABEL).sort()).toEqual(
      ['DUPLICATE', 'IBAN_CHANGED', 'INVALID_IBAN', 'MISSING_FIELDS', 'UNUSUAL_VAT_RATE', 'VAT_MISMATCH'],
    )
  })
})
