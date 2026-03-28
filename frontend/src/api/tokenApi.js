import { get, post, put, del } from './index'

export const tokenApi = {
  getAll: () => get('/api/tokens'),
  create: (workerId) => post('/api/tokens', workerId != null ? { workerId } : {}),
  getById: (id) => get(`/api/tokens/${id}`),
  reset: (id) => put(`/api/tokens/${id}/reset`),
  delete: (id) => del(`/api/tokens/${id}`),
}