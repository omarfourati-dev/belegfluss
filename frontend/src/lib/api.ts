import { auth } from './auth'
import type { Invoice, InvoiceEvent, InvoiceFields, Me, Role, User } from './types'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly title: string,
    detail: string,
  ) {
    super(detail || title)
  }
}

let onUnauthorized: () => void = () => {}

/** Called on 401 for an authenticated request (expired token), e.g. to redirect to the login. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (auth.token) headers.set('Authorization', `Bearer ${auth.token}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')

  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    let title = response.statusText
    let detail = ''
    try {
      const problem = await response.json()
      title = problem.title ?? title
      detail = problem.detail ?? ''
    } catch {
      /* not a problem+json body */
    }
    if (response.status === 401 && auth.token) {
      auth.clear()
      onUnauthorized()
    }
    throw new ApiError(response.status, title, detail)
  }
  const type = response.headers.get('Content-Type') ?? ''
  if (response.status === 204) return undefined as T
  if (type.includes('application/json')) return (await response.json()) as T
  return (await response.blob()) as T
}

export const api = {
  async login(email: string, password: string) {
    const result = await request<{ accessToken: string; expiresAt: string; user: Me }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    auth.set({ token: result.accessToken, expiresAt: result.expiresAt, user: result.user })
    return result.user
  },

  invoices: () => request<Invoice[]>('/api/invoices'),
  invoice: (id: string) => request<Invoice>(`/api/invoices/${id}`),
  history: (id: string) => request<InvoiceEvent[]>(`/api/invoices/${id}/history`),
  document: (id: string) => request<Blob>(`/api/invoices/${id}/document`),

  upload(file: File) {
    const form = new FormData()
    form.append('file', file)
    return request<Invoice>('/api/invoices', { method: 'POST', body: form })
  },

  approve: (id: string, comment?: string) =>
    request<Invoice>(`/api/invoices/${id}/approve`, { method: 'POST', body: JSON.stringify({ comment: comment || null }) }),
  reject: (id: string, reason: string) =>
    request<Invoice>(`/api/invoices/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
  book: (id: string) => request<Invoice>(`/api/invoices/${id}/book`, { method: 'POST' }),
  correct: (id: string, fields: InvoiceFields) =>
    request<Invoice>(`/api/invoices/${id}`, { method: 'PATCH', body: JSON.stringify(fields) }),

  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/api/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) }),

  exportCsv: () => request<Blob>('/api/invoices/export.csv'),

  users: () => request<User[]>('/api/users'),
  createUser: (user: { email: string; password: string; displayName: string; role: Role }) =>
    request<User>('/api/users', { method: 'POST', body: JSON.stringify(user) }),
  setUserEnabled: (id: string, enabled: boolean) =>
    request<User>(`/api/users/${id}`, { method: 'PATCH', body: JSON.stringify({ enabled }) }),
}
