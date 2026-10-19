// "Datenschutzeinstellungen" in the landing page footer: shows Google's consent message again, so visitors can
// change or withdraw their ad consent at any time (AdSense on the landing page, see omarfourati.de/de/datenschutz).
type GoogleFc = { showRevocationMessage?: () => void; callbackQueue?: Array<() => void> }

const w = window as unknown as { googlefc?: GoogleFc }

document.querySelector('[data-privacy-settings]')?.addEventListener('click', () => {
  w.googlefc ??= {}
  w.googlefc.callbackQueue ??= []
  w.googlefc.callbackQueue.push(() => w.googlefc?.showRevocationMessage?.())
})
