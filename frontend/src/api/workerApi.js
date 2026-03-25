import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const workerApi = {
  list: () => api.get('/workers').then(r => r.data),
  get: (id) => api.get(`/workers/${id}`).then(r => r.data),
  create: (data) => api.post('/workers', data).then(r => r.data),
  update: (id, data) => api.put(`/workers/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/workers/${id}`)
}
