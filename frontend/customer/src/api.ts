export type Product = { id: number; name: string; description: string; priceCents: number; available: number }
export type Order = { id: number; productId: number; quantity: number; totalCents: number; status: string; trackingNo: string | null }
export type Tracking = { eventId: string; status: string; location: string; occurredAt: string }
export type CartItem = { id: number; productId: number; quantity: number; selected: boolean }

let csrf = ''
export async function session(): Promise<string> {
  const response = await fetch('/session', { credentials: 'include' })
  const result = await response.json()
  csrf = result.csrfToken
  return result.user
}
export async function login(username: string, password: string): Promise<boolean> {
  await session()
  const body = new URLSearchParams({ username, password })
  await fetch('/login', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': csrf }, body })
  return (await session()) === username
}
export async function api<T>(path: string, method = 'GET', body?: unknown, key?: string): Promise<T> {
  const response = await fetch(path, { method, credentials: 'include', headers: {
    ...(body ? { 'Content-Type': 'application/json' } : {}),
    ...(method !== 'GET' ? { 'X-XSRF-TOKEN': csrf } : {}),
    ...(key ? { 'Idempotency-Key': key } : {})
  }, body: body ? JSON.stringify(body) : undefined })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: `HTTP ${response.status}` }))
    throw new Error(error.message || `HTTP ${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
