// Tiny wrapper around fetch() for the Spring Boot API (HTTP Basic auth).
// All URLs are relative (/api/...): Vite (dev) or nginx (Docker) forwards them to the backend.

const KEY = 'tickets-auth'
let auth = sessionStorage.getItem(KEY)

export function setCredentials(username, password) {
  auth = 'Basic ' + btoa(`${username}:${password}`)
  sessionStorage.setItem(KEY, auth)
}

export function clearCredentials() {
  auth = null
  sessionStorage.removeItem(KEY)
}

export function hasCredentials() {
  return auth !== null
}

async function request(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: {
      ...(auth ? { Authorization: auth } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      // Tells Spring Security not to send "WWW-Authenticate", which would pop up the browser login dialog.
      'X-Requested-With': 'XMLHttpRequest',
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
