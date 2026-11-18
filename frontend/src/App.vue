<script setup>
import { onMounted, ref } from 'vue'
import { api, clearCredentials, hasCredentials } from './api'
import LoginForm from './components/LoginForm.vue'
import TicketBoard from './components/TicketBoard.vue'

const me = ref(null)

async function loadMe() {
  try {
    me.value = await api.me()
  } catch {
    clearCredentials()
    me.value = null
  }
}

function logout() {
  clearCredentials()
  me.value = null
}

onMounted(() => {
  if (hasCredentials()) loadMe()
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
    <LoginForm v-if="!me" @logged-in="me = $event" />
    <TicketBoard v-else :me="me" />
  </main>
</template>
