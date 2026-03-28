import { get, post, put, delete } from './index'

export const tokenApi = {
  getAll: () => get('/api/tokens'),
  create: (workerId) => post('/api/tokens', workerId != null ? { workerId } : {}),
  getById: (id) => get(`/api/tokens/${id}`),
  reset: (id) => put(`/api/tokens/${id}/reset`),
  delete: (id) => delete(`/api/tokens/${id}`),
}