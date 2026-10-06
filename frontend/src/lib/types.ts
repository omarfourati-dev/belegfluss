export type Role = 'VIEWER' | 'EMPLOYEE' | 'APPROVER' | 'ACCOUNTANT' | 'ADMIN'

export type InvoiceStatus = 'RECEIVED' | 'EXTRACTED' | 'FAILED' | 'APPROVED' | 'REJECTED' | 'BOOKED'

export interface Warning {
  code: string
  message: string
}

export interface Invoice {
  id: string
  originalFilename: string
  status: InvoiceStatus
  supplierName: string | null
  invoiceNumber: string | null
  invoiceDate: string | null
  dueDate: string | null
  netAmount: number | null
  vatAmount: number | null
  grossAmount: number | null
  currency: string | null
  iban: string | null
  errorMessage: string | null
  decisionComment: string | null
  warnings: Warning[]
  createdAt: string
  updatedAt: string
}

export interface InvoiceEvent {
  type: string
  actor: string
  comment: string | null
  at: string
}

export interface Me {
  email: string
  displayName: string
  roles: Role[]
}

export interface User {
  id: string
  email: string
  displayName: string
  role: Role
  enabled: boolean
  createdAt: string
}
