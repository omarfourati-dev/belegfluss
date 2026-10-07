import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import UploadDropzone from './UploadDropzone.vue'
import { api } from '../lib/api'

function fileList(...files: File[]): FileList {
  return Object.assign(files, { item: (i: number) => files[i] }) as unknown as FileList
}

describe('UploadDropzone', () => {
  it('rejects non-PDF files without calling the API', async () => {
    const upload = vi.spyOn(api, 'upload')
    const wrapper = mount(UploadDropzone)

    await wrapper.trigger('drop', { dataTransfer: { files: fileList(new File(['x'], 'bild.png', { type: 'image/png' })) } })

    expect(upload).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('nur PDF-Rechnungen und E-Rechnungen')
  })

  it('uploads PDFs and emits the created invoice', async () => {
    const invoice = { id: '1', status: 'RECEIVED' }
    vi.spyOn(api, 'upload').mockResolvedValue(invoice as never)
    const wrapper = mount(UploadDropzone)

    await wrapper.trigger('drop', { dataTransfer: { files: fileList(new File(['%PDF'], 'r.pdf', { type: 'application/pdf' })) } })
    await vi.waitFor(() => expect(wrapper.emitted('uploaded')).toBeTruthy())

    expect(wrapper.emitted('uploaded')![0]).toEqual([invoice])
  })
})
