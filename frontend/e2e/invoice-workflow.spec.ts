import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { fileURLToPath } from 'node:url'

/**
 * The whole business process in a real browser against the real stack:
 * an employee uploads an XRechnung, an approver releases it, accounting books it.
 * An e-invoice needs no AI, so this test runs without any API key.
 */

const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@belegfluss.test'
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'local-admin-password-123'
const PASSWORD = 'e2e-test-password-123'
const XRECHNUNG = fileURLToPath(new URL('../../src/test/resources/einvoices/xrechnung-ubl.xml', import.meta.url))
const run = Date.now().toString(36)

const users = {
  employee: { email: `emma.${run}@e2e.test`, displayName: 'Emma Einkauf', role: 'EMPLOYEE' },
  approver: { email: `anton.${run}@e2e.test`, displayName: 'Anton Freigabe', role: 'APPROVER' },
  accountant: { email: `berta.${run}@e2e.test`, displayName: 'Berta Buchhaltung', role: 'ACCOUNTANT' },
}

async function createUsers(request: APIRequestContext) {
  const login = await request.post('/api/auth/login', { data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } })
  expect(login.ok(), 'admin login').toBeTruthy()
  const token = (await login.json()).accessToken as string
  for (const user of Object.values(users)) {
    const created = await request.post('/api/users', {
      headers: { Authorization: `Bearer ${token}` },
      data: { ...user, password: PASSWORD },
    })
    expect(created.status(), `create ${user.role}`).toBe(201)
  }
}

async function loginAs(page: Page, email: string) {
  await page.goto('/#/login')
  await page.getByLabel('E-Mail').fill(email)
  await page.getByLabel('Passwort').fill(PASSWORD)
  await page.getByRole('button', { name: 'Anmelden', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Rechnungseingang' })).toBeVisible()
}

/** Uploads the XRechnung and returns the id of the new invoice from the API response. */
async function uploadXRechnung(page: Page): Promise<string> {
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().endsWith('/api/invoices') && r.request().method() === 'POST'),
    page.locator('input[type="file"]').setInputFiles(XRECHNUNG),
  ])
  expect(response.status()).toBe(202)
  return (await response.json()).id as string
}

async function logout(page: Page) {
  await page.getByRole('button', { name: 'Abmelden' }).click()
  await expect(page.getByRole('heading', { name: 'Anmelden' })).toBeVisible()
}

test('upload, approve and book an e-invoice with three different people', async ({ page, request }) => {
  await createUsers(request)

  // 1. Employee uploads an XRechnung; the list updates live via Server-Sent Events
  await loginAs(page, users.employee.email)
  const id = await uploadXRechnung(page)
  const row = page.getByRole('row').filter({ hasText: 'Rheinland IT-Service GmbH' }).first()
  await expect(row).toContainText('Zur Freigabe')
  await expect(row).toContainText('E-Rechnung')
  await expect(row).toContainText('1.190,00')
  await logout(page)

  // 2. Approver opens it and approves (a comment covers a possible duplicate warning on reruns)
  await loginAs(page, users.approver.email)
  await page.goto(`/#/invoices/${id}`)
  await expect(page.getByText('E-Rechnung · XRechnung (UBL)')).toBeVisible()
  await expect(page.getByText('E-Rechnung – exakt gelesen, ohne KI')).toBeVisible()
  await page.getByRole('button', { name: 'Freigeben', exact: true }).click()
  await page.getByLabel(/Kommentar|Begründung/).fill('E2E: Bestellung geprüft')
  await page.getByRole('button', { name: 'Jetzt freigeben' }).click()
  await expect(page.getByText('Freigegeben', { exact: true }).first()).toBeVisible()
  await logout(page)

  // 3. Accounting books it; the audit trail shows all three people
  await loginAs(page, users.accountant.email)
  await page.goto(`/#/invoices/${id}`)
  await page.getByRole('button', { name: 'Als verbucht markieren' }).click()
  await expect(page.getByText('Verbucht', { exact: true }).first()).toBeVisible()
  const history = page.locator('ol')
  await expect(history).toContainText('Emma Einkauf')
  await expect(history).toContainText('Anton Freigabe')
  await expect(history).toContainText('Berta Buchhaltung')
})

test('the uploader cannot approve their own invoice', async ({ page, request }) => {
  const approver = { email: `chef.${run}@e2e.test`, displayName: 'Chef Selbst', role: 'APPROVER' }
  const login = await request.post('/api/auth/login', { data: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } })
  const token = (await login.json()).accessToken as string
  await request.post('/api/users', { headers: { Authorization: `Bearer ${token}` }, data: { ...approver, password: PASSWORD } })

  await loginAs(page, approver.email)
  const id = await uploadXRechnung(page)
  await page.goto(`/#/invoices/${id}`)
  await page.getByRole('button', { name: 'Freigeben', exact: true }).click()
  await page.getByLabel(/Kommentar|Begründung/).fill('Selbst freigeben?')
  await page.getByRole('button', { name: 'Jetzt freigeben' }).click()
  await expect(page.getByRole('alert')).toContainText('Four-eyes principle')
})
