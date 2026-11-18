import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// In development, Vite forwards /api calls to the Spring Boot backend,
// so the browser only ever talks to one origin (no CORS needed).
// Inside Docker set BACKEND_URL, e.g. BACKEND_URL=http://tickets-backend:8080
const backend = process.env.BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
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
