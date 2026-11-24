import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// In development, Vite forwards /api calls to the Spring Boot backend,
// so the browser only ever talks to one origin (no CORS needed).
// Inside Docker set BACKEND_URL, e.g. BACKEND_URL=http://tickets-backend:8080
const backend = process.env.BACKEND_URL || 'http://localhost:8080'

// /config.json tells the browser where Keycloak is (src/auth.js). In the Docker image nginx serves it
// (nginx/default.conf.template); in `npm run dev` / `npm run preview` this small plugin does.
// Default = Keycloak started locally with Docker on port 8180 (see Keycloak.md).
const runtimeConfig = {
  keycloakUrl: process.env.KEYCLOAK_URL || 'http://localhost:8180',
  realm: process.env.KEYCLOAK_REALM || 'tickets',
  clientId: process.env.KEYCLOAK_CLIENT_ID || 'tickets-frontend',
}

function serveRuntimeConfig() {
  const handler = (req, res, next) => {
    if (req.url.split('?')[0] !== '/config.json') return next()
    res.setHeader('Content-Type', 'application/json')
    res.setHeader('Cache-Control', 'no-store')
    res.end(JSON.stringify(runtimeConfig))
  }
  return {
    name: 'runtime-config',
    // Block bodies on purpose: a function returned from these hooks would be run as a "post" hook.
    configureServer(server) { server.middlewares.use(handler) },
    configurePreviewServer(server) { server.middlewares.use(handler) },
  }
}

export default defineConfig({
  plugins: [vue(), serveRuntimeConfig()],
  server: {
    host: true, // listen on 0.0.0.0 so it is reachable from outside a container
    port: 5173,
    proxy: {
      '/api': backend,
    },
  },
  // Vitest: simulate a browser (DOM, sessionStorage) with jsdom
  test: {
    environment: 'jsdom',
  },
})
