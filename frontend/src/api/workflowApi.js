import api from './index'

export const workflowApi = {
  list: () => api.get('/workflows').then(r => r.data),
  get: (id) => api.get(`/workflows/${id}`).then(r => r.data),
  create: (data) => api.post('/workflows', data).then(r => r.data),
  update: (id, data) => api.put(`/workflows/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/workflows/${id}`)
}
