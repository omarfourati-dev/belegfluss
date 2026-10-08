import { expect, test } from '@playwright/test'

/** Belegfluss as an installable app: manifest, service worker, no invoice data in the cache, offline start. */

const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@belegfluss.test'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'local-admin-password-123'

test('the app is installable and never caches API responses', async ({ page, request }) => {
  const manifest = await (await request.get('/app/manifest.webmanifest')).json()
  expect(manifest).toMatchObject({ short_name: 'Belegfluss', start_url: '/app/', scope: '/app/', display: 'standalone' })
  for (const icon of manifest.icons as { src: string }[]) expect((await request.get(icon.src)).ok()).toBeTruthy()
  const sw = await request.get('/app/sw.js')
  expect(sw.headers()['cache-control']).toBe('no-cache')
  expect(await sw.text()).not.toContain('__BUILD__') // stamped by the build: every deploy is an update

  await page.goto('/app/#/login')
  const scope = await page.evaluate(async () => (await navigator.serviceWorker.ready).scope)
  expect(new URL(scope).pathname).toBe('/app/')
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null)

  // log in and load the invoice list: invoices and the live stream go through /api and must not be cached
  await page.getByLabel('E-Mail').fill(ADMIN_EMAIL)
  await page.getByLabel('Passwort').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'Anmelden', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Rechnungseingang' })).toBeVisible()
  const cached = await page.evaluate(async () => {
    const paths: string[] = []
    for (const name of await caches.keys()) for (const req of await (await caches.open(name)).keys()) paths.push(new URL(req.url).pathname)
    return paths
  })
  expect(cached).toContain('/app/')
  expect(cached.some((p) => p.startsWith('/assets/'))).toBeTruthy()
  expect(cached.filter((p) => !p.startsWith('/app/') && !p.startsWith('/assets/'))).toEqual([]) // no /api, no landing page
})

test('offline the installed app still opens and says it has no connection', async ({ page, context }) => {
  await page.goto('/app/#/login')
  await page.evaluate(async () => navigator.serviceWorker.ready)
  await page.waitForFunction(() => navigator.serviceWorker.controller !== null)
  await context.setOffline(true)
  await page.reload()
  await expect(page.getByRole('button', { name: 'Anmelden', exact: true })).toBeVisible()
  await expect(page.getByText('Keine Verbindung')).toBeVisible()
  await context.setOffline(false)
})
