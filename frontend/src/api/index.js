import axios from 'axios'
import router from '../router'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// Request interceptor: add Authorization header (skip for auth endpoints)
api.interceptors.request.use(config => {
  // Auth endpoints don't need/shouldn't have token
  if (config.url.startsWith('/auth/')) {
    return config
  }
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `User ${token}`
  }
  return config
})

// Response interceptor: handle 401/403 (skip for auth endpoints)
api.interceptors.response.use(
  response => response,
  error => {
    // Skip auth endpoint errors - let them propagate to caller
    if (error.config?.url?.startsWith('/auth/')) {
      return Promise.reject(error)
    }
    if (error.response?.status === 401 || error.response?.status === 403) {
      localStorage.removeItem('token')
      const msg = error.response?.data?.message || '会话已过期，请重新登录'
      alert(msg)
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

// 模型管理
export const llmModelApi = {
  list: () => api.get('/llm-models').then(r => r.data),
  listEnabled: () => api.get('/llm-models/enabled').then(r => r.data),
  get: (id) => api.get(`/llm-models/${id}`).then(r => r.data),
  create: (data) => api.post('/llm-models', data).then(r => r.data),
  update: (id, data) => api.put(`/llm-models/${id}`, data).then(r => r.data),
  delete: (id) => api.delete(`/llm-models/${id}`),
  test: (id) => api.post(`/llm-models/${id}/test`).then(r => r.data)
}

// 聊天会话管理
export const chatApi = {
  listSessions: (workerId, archived) => {
    const params = archived !== undefined ? { archived } : {}
    return api.get(`/chat/${workerId}/sessions`, { params }).then(r => r.data)
  },
  createSession: (workerId, title) => api.post(`/chat/${workerId}/sessions`, { title }).then(r => r.data),
  getMessages: (workerId, sessionId) => api.get(`/chat/${workerId}/sessions/${sessionId}/messages`).then(r => r.data),
  deleteSession: (workerId, sessionId) => api.delete(`/chat/${workerId}/sessions/${sessionId}`),
  renameSession: (workerId, sessionId, title) => api.put(`/chat/${workerId}/sessions/${sessionId}/title`, { title }).then(r => r.data),
  archiveSession: (workerId, sessionId) => api.put(`/chat/${workerId}/sessions/${sessionId}/archive`).then(r => r.data),
  unarchiveSession: (workerId, sessionId) => api.put(`/chat/${workerId}/sessions/${sessionId}/unarchive`).then(r => r.data)
}

// SSE 连接
export function connectSse(instanceId, onMessage, onError) {
  const url = `/api/sse/status/${instanceId}`
  console.log('[SSE] Connecting to:', url)
  console.log('[SSE] ReadyState before:', EventSource.CONNECTING)

  const eventSource = new EventSource(url)
  console.log('[SSE] EventSource created, readyState:', eventSource.readyState)

  eventSource.onopen = (e) => {
    console.log('[SSE] Connection opened! readyState:', eventSource.readyState)
  }

  eventSource.addEventListener('status', (event) => {
    console.log('[SSE] Received status event:', event.data)
    try {
      const data = JSON.parse(event.data)
      onMessage(data)
    } catch (e) {
      console.error('[SSE] Parse error:', e)
    }
  })

  eventSource.addEventListener('error', (event) => {
    console.error('[SSE] Error event, readyState:', eventSource.readyState, event)
  })

  eventSource.onerror = (e) => {
    console.error('[SSE] onerror:', e)
    console.error('[SSE] readyState:', eventSource.readyState)
    console.error('[SSE] URL:', eventSource.url)
    console.error('[SSE] WITH CREDENTIALS:', eventSource.withCredentials)

    // Log response if available (in case of HTTP error)
    if (e.response) {
      console.error('[SSE] Response status:', e.response.status)
      console.error('[SSE] Response body:', e.response.data)
    }

    onError && onError(e)
  }

  return eventSource
}

export default api
