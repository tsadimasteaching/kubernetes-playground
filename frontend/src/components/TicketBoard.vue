<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api'

const props = defineProps({ me: { type: Object, required: true } })

const isAgent = computed(() => props.me.roles.includes('AGENT'))
const tickets = ref([])
const statusFilter = ref('')
const error = ref('')

// form used for both "new ticket" and "edit ticket" (USER only)
const form = reactive({ id: null, title: '', description: '', priority: 'MEDIUM' })
const resolutions = reactive({})

async function run(action) {
  error.value = ''
  try {
    await action()
    tickets.value = await api.list(statusFilter.value)
  } catch (e) {
    error.value = e.message
  }
}

const load = () => run(async () => {})

function resetForm() {
  Object.assign(form, { id: null, title: '', description: '', priority: 'MEDIUM' })
}

function edit(t) {
  Object.assign(form, { id: t.id, title: t.title, description: t.description, priority: t.priority })
}

function save() {
  const body = { title: form.title, description: form.description, priority: form.priority }
  return run(async () => {
    form.id ? await api.update(form.id, body) : await api.create(body)
    resetForm()
  })
}

const remove = (t) => confirm(`Delete ticket #${t.id}?`) && run(() => api.remove(t.id))
const assign = (t) => run(() => api.assign(t.id))
const resolve = (t) => run(async () => {
  await api.resolve(t.id, resolutions[t.id] || '')
  delete resolutions[t.id]
})

onMounted(load)
</script>

<template>
  <section v-if="!isAgent" class="card">
    <h2>{{ form.id ? `Edit ticket #${form.id}` : 'New ticket' }}</h2>
    <form @submit.prevent="save">
      <label>Title <input v-model="form.title" required maxlength="255" /></label>
      <label>Description <textarea v-model="form.description" rows="3" maxlength="2000" /></label>
      <label>Priority
        <select v-model="form.priority">
          <option>LOW</option><option>MEDIUM</option><option>HIGH</option>
        </select>
      </label>
      <div class="actions">
        <button type="submit">{{ form.id ? 'Save' : 'Create' }}</button>
        <button v-if="form.id" type="button" class="secondary" @click="resetForm">Cancel</button>
      </div>
    </form>
  </section>

  <section class="card">
    <div class="toolbar">
      <h2>{{ isAgent ? 'All tickets' : 'My tickets' }}</h2>
      <select v-model="statusFilter" @change="load">
        <option value="">All statuses</option>
        <option>OPEN</option><option>IN_PROGRESS</option><option>RESOLVED</option>
      </select>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!tickets.length">No tickets.</p>

    <article v-for="t in tickets" :key="t.id" class="ticket">
      <div class="ticket-head">
        <strong>#{{ t.id }} {{ t.title }}</strong>
        <span :class="['badge', t.status.toLowerCase()]">{{ t.status }}</span>
        <span class="badge">{{ t.priority }}</span>
      </div>
      <p v-if="t.description">{{ t.description }}</p>
      <p class="meta">
        by {{ t.createdBy }}<template v-if="t.assignedTo"> · assigned to {{ t.assignedTo }}</template>
        · {{ new Date(t.createdAt).toLocaleString() }}
      </p>
      <p v-if="t.resolution" class="resolution">✅ {{ t.resolution }}</p>

      <div v-if="!isAgent && t.status === 'OPEN'" class="actions">
        <button class="secondary" @click="edit(t)">Edit</button>
        <button class="danger" @click="remove(t)">Delete</button>
      </div>
      <div v-if="isAgent && t.status !== 'RESOLVED'" class="actions">
        <button v-if="t.assignedTo !== me.username" class="secondary" @click="assign(t)">Assign to me</button>
        <input v-model="resolutions[t.id]" placeholder="Resolution…" />
        <button :disabled="!resolutions[t.id]" @click="resolve(t)">Resolve</button>
      </div>
    </article>
  </section>
</template>
