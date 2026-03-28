import api from './index'

export const skillApi = {
  list: () => api.get('/skills').then(r => r.data),
  create: (data) => api.post('/skills', data).then(r => r.data)
}
