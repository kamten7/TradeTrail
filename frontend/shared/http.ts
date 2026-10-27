export type Session = { user: string; roles: string[]; csrfToken: string }

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public traceId = '',
    public currentStatus = '',
    public allowedActions: string[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

let csrf = ''
let user = ''
const pending = new Map<string, Promise<unknown>>()

async function request(path: string, options: RequestInit = {}): Promise<Response> {
  let response: Response
  try {
    response = await fetch('/api' + path, {
      ...options,
      credentials: 'include',
      signal: AbortSignal.timeout(15000),
    })
  } catch (error) {
    const timeout =
      error instanceof DOMException && ['TimeoutError', 'AbortError'].includes(error.name)
    throw new ApiError(
      timeout ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR',
      timeout
        ? '请求超时，请稍后重试。未确认的操作会使用原请求号重试。'
        : '暂时无法连接商城服务，请确认服务已启动后重试。',
    )
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new ApiError(
      error?.code || (response.status === 401 ? 'UNAUTHENTICATED' : 'SERVICE_UNAVAILABLE'),
      error?.message ||
        (response.status === 401 ? '登录已过期，请重新登录。' : '商城服务暂时不可用，请稍后重试。'),
      error?.traceId,
      error?.currentStatus,
      error?.allowedActions,
    )
  }
  return response
}

async function jsonResponse<T>(response: Response): Promise<T> {
  try {
    return (await response.json()) as T
  } catch {
    throw new ApiError('INVALID_RESPONSE', '服务返回了异常内容，请刷新页面后重试。')
  }
}

export async function session(): Promise<Session> {
  const result = await jsonResponse<Session>(await request('/session'))
  if (typeof result.user !== 'string' || !Array.isArray(result.roles) || !result.csrfToken) {
    throw new ApiError('INVALID_RESPONSE', '登录服务响应不完整，请检查商城服务版本。')
  }
  csrf = result.csrfToken
  user = result.user
  return result
}

export async function login(username: string, password: string): Promise<Session> {
  await session()
  await request('/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': csrf },
    body: new URLSearchParams({ username, password }),
  })
  return session()
}

export async function logout(): Promise<void> {
  await request('/session/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': csrf } })
  csrf = ''
  user = ''
}

export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const payload = body === undefined ? '' : JSON.stringify(body)
  const identity = user + ':' + method + ':' + path + ':' + payload
  if (method !== 'GET' && pending.has(identity)) return pending.get(identity) as Promise<T>
  const execute = async () => {
    let storageKey = ''
    let key = ''
    if (method !== 'GET') {
      const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(identity))
      storageKey =
        'tradetrail:pending:' +
        Array.from(new Uint8Array(hash), (v) => v.toString(16).padStart(2, '0')).join('')
      key = sessionStorage.getItem(storageKey) || crypto.randomUUID()
      sessionStorage.setItem(storageKey, key)
    }
    try {
      const response = await request(path, {
        method,
        headers: {
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...(method === 'GET' ? {} : { 'X-XSRF-TOKEN': csrf, 'Idempotency-Key': key }),
        },
        body: body === undefined ? undefined : payload,
      })
      const result = response.status === 204 ? undefined : await jsonResponse<T>(response)
      if (storageKey) sessionStorage.removeItem(storageKey)
      return result as T
    } catch (error) {
      const retryable = [
        'NETWORK_ERROR',
        'REQUEST_TIMEOUT',
        'SERVICE_UNAVAILABLE',
        'INTERNAL_ERROR',
        'INVALID_RESPONSE',
      ]
      if (storageKey && error instanceof ApiError && !retryable.includes(error.code))
        sessionStorage.removeItem(storageKey)
      if (error instanceof ApiError && error.code === 'UNAUTHENTICATED')
        window.dispatchEvent(new Event('tradetrail:unauthenticated'))
      throw error
    }
  }
  const promise = execute()
  if (method !== 'GET') pending.set(identity, promise)
  try {
    return await promise
  } finally {
    pending.delete(identity)
  }
}
