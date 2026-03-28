import api from './index'

export const workflowApi = {
  // List all workflows
  list: () => api.get('/v1/admin/workflows').then(r => r.data),
  // Get single workflow
  get: (id) => api.get(`/v1/admin/workflows/${id}`).then(r => r.data),
  // Create new workflow
  create: (data) => api.post('/v1/admin/workflows', data).then(r => r.data),
  // Update workflow
  update: (id, data) => api.put(`/v1/admin/workflows/${id}`, data).then(r => r.data),
  // Delete workflow
  delete: (id) => api.delete(`/v1/admin/workflows/${id}`),
  // List output schemas
  listSchemas: () => api.get('/v1/admin/workflows/schemas').then(r => r.data)
}
