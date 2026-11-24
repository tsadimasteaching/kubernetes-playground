<script setup>
import { login } from '../auth'
</script>

<template>
  <section class="card login">
    <h2>Log in</h2>
    <p>Users and passwords are managed by <strong>Keycloak</strong>. You will be redirected to its login page and back.</p>
    <button @click="login">Log in with Keycloak</button>
    <p class="hint">Demo users: <code>alice</code>, <code>bob</code> (USER) and <code>agent</code> (AGENT) — password <code>password</code></p>
  </section>
</template>
