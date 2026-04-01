import api from './index'

/**
 * Gateway 查询 API
 *
 * 通过 Worker 关联的 Gateway 进行查询
 */

/**
 * 创建 Gateway API 实例
 * 注意: api 的 baseURL 是 /api，所以路径不要带 /api 前缀
 */
function createGatewayApi(workerId) {
  const baseUrl = `/workers/${workerId}/gateway`

  return {
    // ==================== 状态类 ====================

    /** Gateway 运行时状态 */
    status: () => api.get(`${baseUrl}/status`).then(r => r.data),

    /** 健康检查 */
    health: () => api.get(`${baseUrl}/health`).then(r => r.data),

    /** 用量统计 */
    usage: () => api.get(`${baseUrl}/usage`).then(r => r.data),

    // ==================== 日志 ====================

    /** 日志查询 (一次性) */
    logsTail: (params = {}) => {
      const { cursor, limit, maxBytes } = params
      const queryParams = {}
      if (cursor !== undefined) queryParams.cursor = cursor
      if (limit !== undefined) queryParams.limit = limit
      if (maxBytes !== undefined) queryParams.maxBytes = maxBytes
      return api.get(`${baseUrl}/logs.tail`, { params: queryParams }).then(r => r.data)
    },

    // ==================== Agent / Session ====================

    /** Agent 列表 */
    agentsList: () => api.get(`${baseUrl}/agents.list`).then(r => r.data),

    /** Agent 文件列表 */
    agentsFilesList: (agentId) =>
      api.get(`${baseUrl}/agents.files.list`, { params: { agentId } }).then(r => r.data),

    /** 获取 Agent 文件内容 */
    agentsFilesGet: (agentId, name) =>
      api.get(`${baseUrl}/agents.files.get`, { params: { agentId, name } }).then(r => r.data),

    /** Session 列表 */
    sessionsList: (params = {}) => api.post(`${baseUrl}/sessions.list`, params).then(r => r.data),

    /** Session 预览 */
    sessionsPreview: (params = {}) => api.post(`${baseUrl}/sessions.preview`, params).then(r => r.data),

    /** Session 历史消息 - 实际调用 sessions.preview */
    sessionsHistory: (sessionKey, limit = 100) =>
      api.post(`${baseUrl}/sessions.preview`, { keys: [sessionKey], limit }).then(r => r.data),

    /** 发送消息到 Session */
    sessionsSend: (sessionKey, message) =>
      api.post(`${baseUrl}/sessions.send`, { key: sessionKey, message }).then(r => r.data),

    // ==================== Skills ====================

    /** Skills 状态 */
    skillsStatus: (agentId) => {
      const params = agentId ? { agentId } : {}
      return api.get(`${baseUrl}/skills.status`, { params }).then(r => r.data)
    },

    /** Skills 可执行文件列表 */
    skillsBins: () => api.get(`${baseUrl}/skills.bins`).then(r => r.data),

    // ==================== Config ====================

    /** 获取当前配置 */
    configGet: () => api.get(`${baseUrl}/config.get`).then(r => r.data),

    /** 合并补丁到配置 */
    configPatch: (patch) => api.patch(`${baseUrl}/config.patch`, patch).then(r => r.data)
  }
}

/**
 * Gateway 日志流 SSE 连接
 * 注意: EventSource 不支持自定义 headers，所以通过 cookie 传递认证
 *
 * @param {number} workerId - Worker ID
 * @param {function} onLog - 收到日志时的回调 (content: string) => void
 * @param {function} onError - 错误时的回调 (error: Error) => void
 * @returns {EventSource} - EventSource 实例，用于主动关闭
 */
function connectGatewayLogStream(workerId, onLog, onError) {
  const url = `/api/workers/${workerId}/gateway/logs.tail/stream`
  console.log('[GatewayLogStream] Connecting to:', url)
  console.log('[GatewayLogStream] Full URL:', window.location.origin + url)

  const eventSource = new EventSource(url, { withCredentials: true })

  eventSource.onopen = (e) => {
    console.log('[GatewayLogStream] Connection opened', e)
  }

  eventSource.onmessage = (event) => {
    console.log('[GatewayLogStream] Message received:', event.data)
    try {
      const data = JSON.parse(event.data)
      console.log('[GatewayLogStream] Parsed data:', data)
      if (data.type === 'log' && onLog) {
        onLog(data.content)
      }
    } catch (e) {
      console.error('[GatewayLogStream] Parse error:', e, 'Raw data:', event.data)
    }
  }

  eventSource.onerror = (e) => {
    console.error('[GatewayLogStream] Error:', e)
    console.error('[GatewayLogStream] Error readyState:', eventSource.readyState)
    console.error('[GatewayLogStream] Error URL:', url)
    onError && onError(e)
  }

  return eventSource
}

/**
 * 停止 Gateway 日志流
 */
function stopGatewayLogStream(workerId) {
  return api.delete(`/workers/${workerId}/gateway/logs.tail/stream`)
}

export { createGatewayApi, connectGatewayLogStream, stopGatewayLogStream }
export default { createGatewayApi, connectGatewayLogStream, stopGatewayLogStream }
