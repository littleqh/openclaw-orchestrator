import api from './index'

export const notificationApi = {
  // Get all notifications for current user
  list: () => api.get('/v1/admin/notifications').then(r => r.data),
  // Get unread notifications
  getUnread: () => api.get('/v1/admin/notifications/unread').then(r => r.data),
  // Get unread count
  getUnreadCount: () => api.get('/v1/admin/notifications/unread/count').then(r => r.data.count),
  // Mark single notification as read
  markAsRead: (id) => api.put(`/v1/admin/notifications/${id}/read`),
  // Mark all as read
  markAllAsRead: () => api.put('/v1/admin/notifications/read-all')
}
