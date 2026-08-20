import { request } from './client'

export const uploadApi = {
  upload(file) {
    const form = new FormData()
    form.append('file', file)
    return request('/api/upload', { method: 'POST', body: form })
  },
}
