import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, setTokenProvider } from '../api'

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
  beforeEach(() => setTokenProvider(async () => 'test-token'))
  afterEach(() => vi.unstubAllGlobals())

  it('sends the Keycloak access token as a Bearer token', async () => {
    const fetch = mockFetch(200, { username: 'alice', roles: ['USER'] })

    const me = await api.me()

    expect(me.username).toBe('alice')
    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/me')
    expect(options.headers.Authorization).toBe('Bearer test-token')
  })

  it('asks for a (possibly refreshed) token on every request', async () => {
    const fetch = mockFetch(200, [])
    const provider = vi.fn().mockResolvedValueOnce('token-1').mockResolvedValueOnce('token-2')
    setTokenProvider(provider)

    await api.list()
    await api.list('OPEN')

    expect(provider).toHaveBeenCalledTimes(2)
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer token-1')
    expect(fetch.mock.calls[1][1].headers.Authorization).toBe('Bearer token-2')
    expect(fetch.mock.calls[1][0]).toBe('/api/tickets?status=OPEN')
  })

  it('sends no Authorization header without a token', async () => {
    const fetch = mockFetch(200, [])
    setTokenProvider(async () => null)

    await api.list()

    expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('posts JSON when creating a ticket', async () => {
    const fetch = mockFetch(201, { id: 1 })
    await api.create({ title: 'Broken laptop' })

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/tickets')
    expect(options.method).toBe('POST')
    expect(options.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(options.body)).toEqual({ title: 'Broken laptop' })
  })

  it('throws an error carrying the HTTP status', async () => {
    mockFetch(403, { detail: 'Forbidden' })
    await expect(api.remove(1)).rejects.toMatchObject({ status: 403, message: 'Forbidden' })
  })
})
