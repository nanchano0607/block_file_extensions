import { request, useMockApi, wait } from './client'

const FIXED_KEY = 'fileguard.fixed'
const CUSTOM_KEY = 'fileguard.custom'
const DEFAULT_FIXED = ['bat', 'cmd', 'com', 'cpl', 'exe', 'scr', 'js']
const readFixed = () => {
  const stored = JSON.parse(localStorage.getItem(FIXED_KEY) || '{}')
  return DEFAULT_FIXED.map((extension) => ({ extension, blocked: Boolean(stored[extension]) }))
}
const readCustom = () => JSON.parse(localStorage.getItem(CUSTOM_KEY) || '[]')

export const policyApi = {
  async getFixed() {
    if (!useMockApi) return request('/api/policy/fixed-extensions')
    await wait(); return readFixed()
  },
  async updateFixed(extension, blocked) {
    if (!useMockApi) return request(`/api/policy/fixed-extensions/${extension}`, { method: 'PATCH', body: JSON.stringify({ blocked }) })
    await wait(220)
    const values = Object.fromEntries(readFixed().map((item) => [item.extension, item.blocked]))
    values[extension] = blocked
    localStorage.setItem(FIXED_KEY, JSON.stringify(values))
  },
  async getCustom() {
    if (!useMockApi) return request('/api/policy/custom-extensions')
    await wait(); const items = readCustom(); return { count: items.length, limit: 200, items }
  },
  async addCustom(extension) {
    if (!useMockApi) return request('/api/policy/custom-extensions', { method: 'POST', body: JSON.stringify({ extension }) })
    await wait(300)
    if (DEFAULT_FIXED.includes(extension)) throw new Error('이미 고정 차단 목록에 있는 확장자입니다.')
    const items = readCustom()
    if (items.some((item) => item.extension === extension)) throw new Error('이미 등록된 확장자입니다.')
    if (items.length >= 200) throw new Error('커스텀 확장자는 최대 200개까지 등록할 수 있습니다.')
    const created = { id: Date.now(), extension }
    localStorage.setItem(CUSTOM_KEY, JSON.stringify([...items, created]))
    return created
  },
  async deleteCustom(id) {
    if (!useMockApi) return request(`/api/policy/custom-extensions/${id}`, { method: 'DELETE' })
    await wait(250); localStorage.setItem(CUSTOM_KEY, JSON.stringify(readCustom().filter((item) => item.id !== id)))
  },
}
