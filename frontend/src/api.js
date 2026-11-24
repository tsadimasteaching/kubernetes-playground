// Tiny wrapper around fetch() for the Spring Boot API (Bearer token from Keycloak).
// All URLs are relative (/api/...): Vite (dev) or nginx (Docker) forwards them to the backend.

// Returns the current access token. Set by App.vue to auth.getToken; kept separate from auth.js
// so this module does not depend on Keycloak and is easy to test.
let tokenProvider = async () => null

export function setTokenProvider(provider) {
  tokenProvider = provider
}

async function request(method, path, body) {
  const token = await tokenProvider()
  const res = await fetch(path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const data = await res.json()
      message = data.detail || data.message || data.error || message
    } catch { /* no JSON body */ }
    const err = new Error(message)
    err.status = res.status
    throw err
  }
  return res.status === 204 ? null : res.json()
}

export const api = {
  me: () => request('GET', '/api/me'),
  list: (status) => request('GET', '/api/tickets' + (status ? `?status=${status}` : '')),
  create: (ticket) => request('POST', '/api/tickets', ticket),
  update: (id, ticket) => request('PUT', `/api/tickets/${id}`, ticket),
  remove: (id) => request('DELETE', `/api/tickets/${id}`),
  assign: (id) => request('POST', `/api/tickets/${id}/assign`),
  resolve: (id, resolution) => request('POST', `/api/tickets/${id}/resolve`, { resolution }),
}
