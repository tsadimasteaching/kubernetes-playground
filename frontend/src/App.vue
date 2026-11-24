<script setup>
import { onMounted, ref } from 'vue'
import { api, setTokenProvider } from './api'
import { getToken, initAuth, logout } from './auth'
import LoginForm from './components/LoginForm.vue'
import TicketBoard from './components/TicketBoard.vue'

const me = ref(null)
const ready = ref(false)
const error = ref('')

onMounted(async () => {
  setTokenProvider(getToken)
  try {
    if (await initAuth()) {
      try {
        me.value = await api.me()
      } catch (e) {
        error.value = e.status === 401
          ? 'Logged in at Keycloak, but the API rejected the token (401). Check KEYCLOAK_ISSUER_URI of the backend.'
          : `Backend not reachable (${e.message})`
      }
    }
  } catch (e) {
    // keycloak-js rejects with an Error or with a plain object
    error.value = `Keycloak not reachable (${e?.message || e?.error || 'unknown error'})`
  } finally {
    ready.value = true
  }
})
</script>

<template>
  <header>
    <h1>🎫 Tickets</h1>
    <div v-if="me" class="whoami">
      {{ me.username }} <span class="badge">{{ me.roles.join(', ') }}</span>
      <button class="secondary" @click="logout">Log out</button>
    </div>
  </header>
  <main>
    <p v-if="!ready" class="hint">Loading…</p>
    <section v-else-if="error" class="card login">
      <p class="error">{{ error }}</p>
      <button class="secondary" @click="logout">Log out</button>
    </section>
    <LoginForm v-else-if="!me" />
    <TicketBoard v-else :me="me" />
  </main>
</template>
