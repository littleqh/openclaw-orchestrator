import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

export const skillApi = {
  list: () => api.get('/skills').then(r => r.data),
  create: (data) => api.post('/skills', data).then(r => r.data)
}
