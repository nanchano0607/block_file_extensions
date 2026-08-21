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
  // body.data가 정상적으로 null인 응답(예: DELETE)과, 응답 자체가 JSON이 아니었던 경우를
  // `??`로는 구분할 수 없다 — 둘 다 null이 되어 버려 전자가 body 전체를 반환해버린다.
  return body ? body.data : null
}
