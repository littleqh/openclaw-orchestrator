import api from './index'

export const workerApi = {
  list: () => api.get('/workers').then(r => r.data),
  get: (id) => api.get(`/workers/${id}`).then(r => r.data),
  create: (data) => api.post('/workers', data).then(r => r.data),
  update: (id, data) => api.put(`/workers/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/workers/${id}`),
  connect: (id) => api.post(`/workers/${id}/connect`).then(r => r.data)
}
