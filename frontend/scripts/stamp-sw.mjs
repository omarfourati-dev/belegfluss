// Runs after `vite build`: gives the service worker a build id and the list of all built JS/CSS files,
// so lazy-loaded views are cached too and every deploy counts as an update ("Neu laden").
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'

const dist = process.argv[2] ?? 'dist'
const assets = readdirSync(`${dist}/assets`).filter((f) => /\.(js|css)$/.test(f)).sort()
const file = `${dist}/app/sw.js`
const src = readFileSync(file, 'utf8')
if (!src.includes('__BUILD__') || !src.includes('[/*__ASSETS__*/]')) throw new Error('sw.js placeholders missing')
writeFileSync(file, src.replace('__BUILD__', String(Date.now())).replace('[/*__ASSETS__*/]', JSON.stringify(assets)))
console.log(`sw.js stamped with ${assets.length} assets`)
