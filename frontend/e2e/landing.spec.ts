import { expect, test } from '@playwright/test'

/** The public landing page: findable content, working links into the app, crawler files. */

test('landing page explains the product and leads to the demo', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveTitle(/Belegfluss/)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('KI nur, wo sie nötig ist')
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', 'https://belegfluss.omarfourati.de/')

  // structured data must be valid JSON and contain the FAQ shown on the page
  const ldJson = await page.locator('script[type="application/ld+json"]').textContent()
  const graph = JSON.parse(ldJson ?? '{}')['@graph'] as { '@type': string; mainEntity?: { name: string }[] }[]
  const faq = graph.find((node) => node['@type'] === 'FAQPage')
  expect(faq?.mainEntity?.length).toBeGreaterThan(3)
  for (const question of faq!.mainEntity!) {
    await expect(page.locator('summary', { hasText: question.name })).toHaveCount(1)
  }

  await page.getByRole('link', { name: 'Live-Demo ansehen' }).click()
  await expect(page).toHaveURL(/\/app\/#\/login/)
  await expect(page.getByRole('button', { name: 'Als Demo anmelden' })).toBeVisible()
})

test('old links to the app under "/#/" still work', async ({ page }) => {
  await page.goto('/#/login')
  await expect(page).toHaveURL(/\/app\/#\/login/)
})

test('crawler files are public', async ({ request }) => {
  const robots = await request.get('/robots.txt')
  expect(robots.ok()).toBeTruthy()
  expect(await robots.text()).toContain('Sitemap: https://belegfluss.omarfourati.de/sitemap.xml')
  expect((await request.get('/sitemap.xml')).ok()).toBeTruthy()
  expect(await (await request.get('/llms.txt')).text()).toContain('# Belegfluss')
  expect((await request.get('/og-image.png')).headers()['content-type']).toBe('image/png')
})
