import api from './index'

export const instanceApi = {
  // List workflow instances
  list: (status) => {
    const params = status ? { status } : {}
    return api.get('/v1/admin/instances', { params }).then(r => r.data)
  },
  // Get single instance
  get: (id) => api.get(`/v1/admin/instances/${id}`).then(r => r.data),
  // Create new instance (CREATE state)
  create: (data) => api.post('/v1/admin/instances', data).then(r => r.data),
  // Complete assignment - CREATE/PLANNED → PLANNED
  assign: (id) => api.post(`/v1/admin/instances/${id}/assign`).then(r => r.data),
  // Start task - PLANNED/READY → RUNNING
  start: (id) => api.post(`/v1/admin/instances/${id}/start`).then(r => r.data),
  // Pause instance
  pause: (id) => api.post(`/v1/admin/instances/${id}/pause`),
  // Resume instance
  resume: (id) => api.post(`/v1/admin/instances/${id}/resume`),
  // Terminate instance
  terminate: (id, reason) => api.post(`/v1/admin/instances/${id}/terminate`, { reason }),
  // Update task worker
  updateTaskWorker: (instanceId, taskId, workerId) =>
    api.put(`/v1/admin/instances/${instanceId}/tasks/${taskId}/worker`, { workerId }).then(r => r.data),
  // Get instance tasks
  getTasks: (id) => api.get(`/v1/admin/instances/${id}/tasks`).then(r => r.data),
  // Get instance logs
  getLogs: (id) => api.get(`/v1/admin/instances/${id}/logs`).then(r => r.data)
}
