const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
export const useMockApi = import.meta.env.VITE_USE_MOCK_API === 'true'

export async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: options.body instanceof FormData ? undefined : { 'Content-Type': 'application/json' },
    ...options,
  })
  const body = await response.json().catch(() => null)
  if (!response.ok || body?.success === false) {
    const error = new Error(body?.message || '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.')
    error.status = response.status
    throw error
  }
  return body?.data ?? body
}

export const wait = (milliseconds = 350) => new Promise((resolve) => setTimeout(resolve, milliseconds))
