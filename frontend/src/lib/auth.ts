import { computed, reactive } from 'vue'
import type { Me, Role } from './types'

const STORAGE_KEY = 'belegfluss.session'

interface Session {
  token: string
  expiresAt: string
  user: Me
}

/** Same order as the backend role hierarchy: each role includes the ones before it. */
const HIERARCHY: Role[] = ['VIEWER', 'EMPLOYEE', 'APPROVER', 'ACCOUNTANT', 'ADMIN']

function load(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    return new Date(session.expiresAt) > new Date() ? session : null
  } catch {
    return null
  }
}

const state = reactive<{ session: Session | null }>({ session: load() })

export const auth = {
  get token(): string | null {
    return state.session?.token ?? null
  },
  user: computed(() => state.session?.user ?? null),
  loggedIn: computed(() => state.session !== null),

  set(session: Session) {
    state.session = session
    try {
      // sessionStorage: the token is gone when the tab is closed
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    } catch {
      /* private mode: keep the session in memory only */
    }
  },

  clear() {
    state.session = null
    try {
      sessionStorage.removeItem(STORAGE_KEY)
    } catch {
      /* ignore */
    }
  },
}

export function hasRole(userRoles: Role[] | undefined, required: Role): boolean {
  if (!userRoles) return false
  const needed = HIERARCHY.indexOf(required)
  return userRoles.some((role) => HIERARCHY.indexOf(role) >= needed)
}

export function can(required: Role): boolean {
  return hasRole(auth.user.value?.roles, required)
}
