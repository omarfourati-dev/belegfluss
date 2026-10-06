import { beforeEach, describe, expect, it } from 'vitest'
import { auth, hasRole } from './auth'

describe('hasRole (mirrors the backend role hierarchy)', () => {
  it('higher roles include lower ones', () => {
    expect(hasRole(['ADMIN'], 'APPROVER')).toBe(true)
    expect(hasRole(['ACCOUNTANT'], 'EMPLOYEE')).toBe(true)
  })

  it('lower roles do not include higher ones', () => {
    expect(hasRole(['VIEWER'], 'EMPLOYEE')).toBe(false)
    expect(hasRole(['APPROVER'], 'ACCOUNTANT')).toBe(false)
  })

  it('no roles means no access', () => {
    expect(hasRole(undefined, 'VIEWER')).toBe(false)
  })
})

describe('auth session', () => {
  beforeEach(() => auth.clear())

  it('stores and clears the token', () => {
    auth.set({
      token: 'abc',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { email: 'a@b.de', displayName: 'A', roles: ['EMPLOYEE'] },
    })
    expect(auth.token).toBe('abc')
    expect(auth.loggedIn.value).toBe(true)
    expect(sessionStorage.getItem('belegfluss.session')).toContain('abc')

    auth.clear()
    expect(auth.token).toBeNull()
    expect(sessionStorage.getItem('belegfluss.session')).toBeNull()
  })
})
