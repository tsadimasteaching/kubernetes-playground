// Login with Keycloak (OpenID Connect, Authorization Code flow + PKCE) using the official keycloak-js adapter.
//
// The browser is redirected to Keycloak's login page. After the login Keycloak redirects back to this app
// with a one-time code, which keycloak-js exchanges for an access token. The password never reaches this
// app or the backend. The token is kept in memory only (not in localStorage).

import Keycloak from 'keycloak-js'

let keycloak = null

// Where Keycloak is. The browser cannot read environment variables and the URL must not be baked into the
// bundle at build time (one image for every environment), so it is fetched at runtime:
//   - Docker / Kubernetes: nginx renders /config.json from KEYCLOAK_URL, ... (nginx/default.conf.template)
//   - npm run dev:         the Vite dev server serves it (vite.config.js)
async function loadConfig() {
  const res = await fetch('/config.json', { cache: 'no-store' })
  if (!res.ok) throw new Error(`Cannot load /config.json (${res.status})`)
  return res.json()
}

/** Resolves to true if the user already has a session at Keycloak (then a token is available). */
export async function initAuth() {
  const config = await loadConfig()
  keycloak = new Keycloak({ url: config.keycloakUrl, realm: config.realm, clientId: config.clientId })
  return keycloak.init({
    // Ask Keycloak (with a quick redirect) whether the user is already logged in, without showing the
    // login page. Keeps the user logged in across page reloads.
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    // The session-check iframe needs third-party cookies, which modern browsers block.
    checkLoginIframe: false,
  })
}

export function login() {
  return keycloak.login()
}

export function logout() {
  // Ends the session at Keycloak too (single sign-out), then comes back to the start page.
  return keycloak.logout({ redirectUri: window.location.origin + '/' })
}

/** A valid access token for the API. Refreshes it (with the refresh token) if it expires within 30 s. */
export async function getToken() {
  try {
    await keycloak.updateToken(30)
  } catch {
    // The Keycloak session has expired too: log in again.
    await keycloak.login()
  }
  return keycloak.token
}

/** Decoded claims of the access token, for display. */
export function tokenClaims() {
  return keycloak?.tokenParsed ?? null
}
