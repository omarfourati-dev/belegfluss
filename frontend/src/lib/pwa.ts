import { ref } from 'vue'

/**
 * Installable app: registers public/app/sw.js and reports when a new version is waiting. Every build stamps the
 * service worker (scripts/stamp-sw.mjs), so each deploy is an update and App.vue offers "Neu laden".
 */
export class Pwa {
  readonly updateReady = ref(false)
  readonly offline = ref(globalThis.navigator?.onLine === false)
  private registration: ServiceWorkerRegistration | null = null
  private requested = false
  private reloading = false

  constructor(
    private readonly container: ServiceWorkerContainer | undefined,
    private readonly reload: () => void,
  ) {
    globalThis.addEventListener?.('online', () => (this.offline.value = false))
    globalThis.addEventListener?.('offline', () => (this.offline.value = true))
  }

  async register(): Promise<void> {
    if (!this.container) return
    const reg = await this.container.register('/app/sw.js', { scope: '/app/' })
    this.registration = reg
    // no controller yet = first install: nothing to update, the page already runs the newest code
    const hasController = () => this.container?.controller != null
    if (reg.waiting && hasController()) this.updateReady.value = true
    reg.addEventListener('updatefound', () => {
      const worker = reg.installing
      worker?.addEventListener('statechange', () => {
        if (worker.state === 'installed' && hasController()) this.updateReady.value = true
      })
    })
    this.container.addEventListener('controllerchange', () => {
      // the first install also changes the controller (clients.claim) – only reload when the user asked
      if (!this.requested || this.reloading) return
      this.reloading = true
      this.reload()
    })
  }

  applyUpdate(): void {
    this.requested = true
    this.registration?.waiting?.postMessage('skip-waiting')
  }
}

export const pwa = new Pwa(globalThis.navigator?.serviceWorker, () => location.reload())
