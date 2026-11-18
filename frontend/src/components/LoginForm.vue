<script setup>
import { ref } from 'vue'
import { api, clearCredentials, setCredentials } from '../api'

const emit = defineEmits(['logged-in'])
const username = ref('alice')
const password = ref('password')
const error = ref('')

async function login() {
  error.value = ''
  setCredentials(username.value, password.value)
  try {
    emit('logged-in', await api.me())
  } catch (e) {
    clearCredentials()
    error.value = e.status === 401 ? 'Wrong username or password' : `Backend not reachable (${e.message})`
  }
}
</script>

<template>
  <form class="card login" @submit.prevent="login">
    <h2>Log in</h2>
    <label>Username <input v-model="username" autocomplete="username" required /></label>
    <label>Password <input v-model="password" type="password" autocomplete="current-password" required /></label>
    <button type="submit">Log in</button>
    <p v-if="error" class="error">{{ error }}</p>
    <p class="hint">Demo users: <code>alice</code>, <code>bob</code> (USER) and <code>agent</code> (AGENT) — password <code>password</code></p>
  </form>
</template>
