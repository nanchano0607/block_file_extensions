import { request } from './client'

export const policyApi = {
  getFixed() {
    return request('/api/policy/fixed-extensions')
  },
  updateFixed(extension, blocked) {
    return request(`/api/policy/fixed-extensions/${extension}`, {
      method: 'PATCH',
      body: JSON.stringify({ blocked }),
    })
  },
  getCustom() {
    return request('/api/policy/custom-extensions')
  },
  addCustom(extension) {
    return request('/api/policy/custom-extensions', {
      method: 'POST',
      body: JSON.stringify({ extension }),
    })
  },
  deleteCustom(id) {
    return request(`/api/policy/custom-extensions/${id}`, { method: 'DELETE' })
  },
}
