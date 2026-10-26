export type Session = { user: string; roles: string[]; csrfToken: string }
export class ApiError extends Error {
  constructor(public code: string, message: string, public traceId = '', public currentStatus = '', public allowedActions: string[] = []) {
    super(message + (traceId ? '（追踪号：' + traceId + '）' : ''))
  }
}
let csrf = ''
let user = ''
const pending = new Map<string, Promise<unknown>>()
export async function session(): Promise<Session> {
  const response = await fetch('/session', { credentials: 'include' })
  if (!response.ok) throw new Error('无法读取登录状态')
  const result = await response.json() as Session
  csrf = result.csrfToken
  user = result.user
  return result
}
export async function login(username: string, password: string): Promise<Session> {
  await session()
  await fetch('/login', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': csrf }, body: new URLSearchParams({ username, password }) })
  const result = await session()
  if (result.user !== username) throw new Error('账号或密码错误')
  return result
}
export async function logout(): Promise<void> {
  await fetch('/session/logout', { method: 'POST', credentials: 'include', headers: { 'X-XSRF-TOKEN': csrf } })
  await session()
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
      storageKey = 'tradetrail:pending:' + Array.from(new Uint8Array(hash), v => v.toString(16).padStart(2, '0')).join('')
      key = sessionStorage.getItem(storageKey) || crypto.randomUUID()
      sessionStorage.setItem(storageKey, key)
    }
    const response = await fetch(path, { method, credentials: 'include', headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(method === 'GET' ? {} : { 'X-XSRF-TOKEN': csrf, 'Idempotency-Key': key })
    }, body: body === undefined ? undefined : payload })
    if (!response.ok) {
      if (storageKey && response.status < 500) sessionStorage.removeItem(storageKey)
      const error = await response.json().catch(() => ({ code: 'HTTP_ERROR', message: '请求失败：' + response.status }))
      throw new ApiError(error.code, error.message, error.traceId, error.currentStatus, error.allowedActions)
    }
    const result = response.status === 204 ? undefined : await response.json()
    if (storageKey) sessionStorage.removeItem(storageKey)
    return result as T
  }
  const promise = execute()
  if (method !== 'GET') pending.set(identity, promise)
  try { return await promise } finally { pending.delete(identity) }
}
