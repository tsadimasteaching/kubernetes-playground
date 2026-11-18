import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, clearCredentials, hasCredentials, setCredentials } from '../api'

function mockFetch(status, body) {
  const fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    statusText: 'x',
    json: async () => body,
  })
  vi.stubGlobal('fetch', fetch)
  return fetch
}

describe('api', () => {
  beforeEach(() => clearCredentials())
  afterEach(() => vi.unstubAllGlobals())

  it('sends HTTP Basic credentials', async () => {
    const fetch = mockFetch(200, { username: 'alice', roles: ['USER'] })
    setCredentials('alice', 'password')

    const me = await api.me()

    expect(me.username).toBe('alice')
    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/me')
    expect(options.headers.Authorization).toBe('Basic ' + btoa('alice:password'))
    expect(options.headers['X-Requested-With']).toBe('XMLHttpRequest')
  })

  it('posts JSON when creating a ticket', async () => {
    const fetch = mockFetch(201, { id: 1 })
    await api.create({ title: 'Broken laptop' })

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/tickets')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ title: 'Broken laptop' })
  })

  it('throws an error carrying the HTTP status', async () => {
    mockFetch(403, { detail: 'Forbidden' })
    await expect(api.remove(1)).rejects.toMatchObject({ status: 403, message: 'Forbidden' })
  })

  it('remembers and forgets credentials', () => {
    expect(hasCredentials()).toBe(false)
    setCredentials('bob', 'password')
    expect(hasCredentials()).toBe(true)
    clearCredentials()
    expect(hasCredentials()).toBe(false)
  })
})
