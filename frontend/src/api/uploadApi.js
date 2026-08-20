import { request, useMockApi, wait } from './client'

const candidatesOf = (filename) => filename.includes('.')
  ? filename.split('.').map((part) => part.trim().toLowerCase()).filter(Boolean)
  : []

export const uploadApi = {
  async upload(file, blockedExtensions = new Set()) {
    if (!useMockApi) {
      const form = new FormData(); form.append('file', file)
      return request('/api/upload', { method: 'POST', body: form })
    }
    await wait(900)
    const candidates = candidatesOf(file.name)
    if (file.size > 10 * 1024 * 1024) {
      const error = new Error('파일 크기 제한을 초과했습니다.'); error.status = 422; throw error
    }
    if (candidates.length === 0 || candidates.some((value) => blockedExtensions.has(value))) {
      const error = new Error('허용되지 않는 파일 형식입니다.'); error.status = 422; throw error
    }
    return { id: Date.now(), originalFilename: file.name, sizeBytes: file.size }
  },
}
