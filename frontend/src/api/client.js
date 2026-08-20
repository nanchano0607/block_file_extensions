const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const DEFAULT_ERROR_MESSAGE = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'

export async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers: options.body instanceof FormData ? undefined : { 'Content-Type': 'application/json' },
      ...options,
    })
  } catch {
    const error = new Error(DEFAULT_ERROR_MESSAGE)
    error.status = 0
    throw error
  }

  const body = await response.json().catch(() => null)
  if (!response.ok || body?.success === false) {
    const error = new Error(body?.message || DEFAULT_ERROR_MESSAGE)
    error.status = response.status
    throw error
  }
  return body?.data ?? body
}
