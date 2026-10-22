export type Product = { id: number; name: string; description: string; priceCents: number; available: number; published: boolean }
export type Order = { id: number; customer: string; productId: number; quantity: number; totalCents: number; status: string; trackingNo: string | null }

let csrf = ''
export async function session(): Promise<string> {
  const response = await fetch('/session', { credentials: 'include' })
  const result = await response.json()
  csrf = result.csrfToken
  return result.user
}
export async function login(username: string, password: string): Promise<boolean> {
  await session()
  await fetch('/login', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': csrf }, body: new URLSearchParams({ username, password }) })
  return (await session()) === username
}
export async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await fetch(path, { method, credentials: 'include', headers: {
    ...(body ? { 'Content-Type': 'application/json' } : {}), ...(method !== 'GET' ? { 'X-XSRF-TOKEN': csrf } : {})
  }, body: body ? JSON.stringify(body) : undefined })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: `HTTP ${response.status}` }))
    throw new Error(error.message || `HTTP ${response.status}`)
  }
  return response.json() as Promise<T>
}
