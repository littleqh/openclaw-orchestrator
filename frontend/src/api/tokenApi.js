import { get, post, put, del } from './index'

export const tokenApi = {
  getAll: () => get('/tokens'),
  create: (workerId) => post('/tokens', workerId != null ? { workerId } : {}),
  getById: (id) => get(`/tokens/${id}`),
  reset: (id) => put(`/tokens/${id}/reset`),
  delete: (id) => del(`/tokens/${id}`),
}