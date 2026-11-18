import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TicketBoard from '../components/TicketBoard.vue'
import { api } from '../api'

vi.mock('../api', () => ({
  api: { list: vi.fn(), create: vi.fn(), assign: vi.fn(), resolve: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))

const tickets = [
  { id: 1, title: 'VPN down', status: 'OPEN', priority: 'HIGH', createdBy: 'alice', createdAt: '2026-01-01T10:00:00Z' },
  { id: 2, title: 'New monitor', status: 'RESOLVED', priority: 'LOW', createdBy: 'alice', assignedTo: 'agent',
    resolution: 'Replaced', createdAt: '2026-01-02T10:00:00Z' },
]

describe('TicketBoard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.list.mockResolvedValue(tickets)
  })

  it('shows the "new ticket" form and edit buttons to a USER', async () => {
    const wrapper = mount(TicketBoard, { props: { me: { username: 'alice', roles: ['USER'] } } })
    await flushPromises()

    expect(wrapper.text()).toContain('New ticket')
    expect(wrapper.text()).toContain('My tickets')
    expect(wrapper.findAll('article')).toHaveLength(2)
    // only the OPEN ticket can be edited
    expect(wrapper.findAll('button').filter(b => b.text() === 'Edit')).toHaveLength(1)
  })

  it('lets an AGENT assign a ticket but not create one', async () => {
    api.assign.mockResolvedValue({})
    const wrapper = mount(TicketBoard, { props: { me: { username: 'agent', roles: ['AGENT'] } } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('New ticket')
    expect(wrapper.text()).toContain('All tickets')

    await wrapper.findAll('button').find(b => b.text() === 'Assign to me').trigger('click')
    await flushPromises()

    expect(api.assign).toHaveBeenCalledWith(1)
    expect(api.list).toHaveBeenCalledTimes(2) // initial load + reload after assigning
  })

  it('creates a ticket from the form', async () => {
    api.create.mockResolvedValue({ id: 3 })
    const wrapper = mount(TicketBoard, { props: { me: { username: 'alice', roles: ['USER'] } } })
    await flushPromises()

    await wrapper.find('input').setValue('Keyboard broken')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(api.create).toHaveBeenCalledWith({ title: 'Keyboard broken', description: '', priority: 'MEDIUM' })
  })
})
