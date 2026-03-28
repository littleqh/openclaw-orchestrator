import axios from 'axios'
import router from '../router'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Request interceptor: add Authorization header
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `User ${token}`
  }
  return config
})

// Response interceptor: handle 401
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

// Helper methods for other api modules
export const post = (url, data) => api.post(url, data).then(r => r.data)
export const get = (url, params) => api.get(url, { params }).then(r => r.data)
export const put = (url, data) => api.put(url, data).then(r => r.data)
export const del = (url) => api.delete(url)

// 实例管理
export const instanceApi = {
  list: () => api.get('/instances').then(r => r.data),
  create: (data) => api.post('/instances', data).then(r => r.data),
  delete: (id) => api.delete(`/instances/${id}`),
  getStatus: (id) => api.get(`/instances/${id}/status`).then(r => r.data),
  getSessions: (id) => api.get(`/instances/${id}/sessions`).then(r => r.data),
  getSubagents: (id) => api.get(`/instances/${id}/subagents`).then(r => r.data),
  searchMemory: (id, query, maxResults) =>
    api.get(`/instances/${id}/memory`, { params: { query, maxResults } }).then(r => r.data),
  invoke: (id, tool, args) =>
    api.post(`/instances/${id}/invoke`, { tool, args }).then(r => r.data)
}

// 技能管理
export const skillApi = {
  list: () => api.get('/skills').then(r => r.data),
  get: (id) => api.get(`/skills/${id}`).then(r => r.data),
  create: (data) => api.post('/skills', data).then(r => r.data),
  update: (id, data) => api.put(`/skills/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/skills/${id}`),
  checkDelete: (id) => api.get(`/skills/${id}/check-delete`).then(r => r.data),
  forceDelete: (id) => api.delete(`/skills/${id}/force`)
}

// 操作管理
export const operationApi = {
  list: () => api.get('/operations').then(r => r.data),
  get: (id) => api.get(`/operations/${id}`).then(r => r.data),
  create: (data) => api.post('/operations', data).then(r => r.data),
  update: (id, data) => api.put(`/operations/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/operations/${id}`)
}

// SSE 连接
export function connectSse(instanceId, onMessage, onError) {
  const url = `/api/sse/status/${instanceId}`
  const eventSource = new EventSource(url)

  eventSource.addEventListener('status', (event) => {
    try {
      const data = JSON.parse(event.data)
      onMessage(data)
    } catch (e) {
      console.error('SSE parse error:', e)
    }
  })

  eventSource.onerror = (e) => {
    console.error('SSE error:', e)
    onError && onError(e)
  }

  return eventSource
}

export default api
