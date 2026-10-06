import type { InvoiceStatus, Role } from './types'

export function formatMoney(value: number | null, currency: string | null = 'EUR'): string {
  if (value === null || value === undefined) return '–'
  try {
    return new Intl.NumberFormat('de-DE', { style: 'currency', currency: currency || 'EUR' }).format(value)
  } catch {
    // unknown currency code from the extraction
    return `${new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2 }).format(value)} ${currency ?? ''}`.trim()
  }
}

export function formatDate(value: string | null): string {
  if (!value) return '–'
  const date = new Date(value.length === 10 ? `${value}T00:00:00` : value)
  return new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium' }).format(date)
}

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export function formatIban(iban: string | null): string {
  if (!iban) return '–'
  return iban.replace(/\s/g, '').replace(/(.{4})/g, '$1 ').trim()
}

export const STATUS: Record<InvoiceStatus, { label: string; tone: string }> = {
  RECEIVED: { label: 'Wird ausgelesen …', tone: 'bg-sky-100 text-sky-800' },
  EXTRACTED: { label: 'Zur Freigabe', tone: 'bg-amber-100 text-amber-800' },
  FAILED: { label: 'Fehlgeschlagen', tone: 'bg-rose-100 text-rose-800' },
  APPROVED: { label: 'Freigegeben', tone: 'bg-emerald-100 text-emerald-800' },
  REJECTED: { label: 'Abgelehnt', tone: 'bg-slate-200 text-slate-700' },
  BOOKED: { label: 'Verbucht', tone: 'bg-brand-100 text-brand-800' },
}

export const ROLE_LABEL: Record<Role, string> = {
  VIEWER: 'Lesen',
  EMPLOYEE: 'Mitarbeiter',
  APPROVER: 'Freigabe',
  ACCOUNTANT: 'Buchhaltung',
  ADMIN: 'Admin',
}

export const EVENT_LABEL: Record<string, string> = {
  UPLOADED: 'Hochgeladen',
  EXTRACTED: 'Von der KI ausgelesen',
  EXTRACTION_FAILED: 'Auslesen fehlgeschlagen',
  APPROVED: 'Freigegeben',
  REJECTED: 'Abgelehnt',
  BOOKED: 'Verbucht',
}

export const WARNING_LABEL: Record<string, string> = {
  MISSING_FIELDS: 'Pflichtfelder fehlen (Lieferant, Rechnungsnummer oder Bruttobetrag)',
  VAT_MISMATCH: 'Netto + USt. ergibt nicht den Bruttobetrag',
  UNUSUAL_VAT_RATE: 'Ungewöhnlicher Steuersatz (nicht 0 %, 7 % oder 19 %)',
  INVALID_IBAN: 'IBAN-Prüfziffer ist ungültig',
  IBAN_CHANGED: 'Lieferant hatte bisher eine andere IBAN – möglicher Zahlungsbetrug',
  DUPLICATE: 'Rechnung mit dieser Nummer gibt es bereits (Doppelzahlung?)',
}
