<template>
  <div class="panel status-session-log-panel">
    <div class="panel-header">
      <div class="tabs">
        <button
          class="tab"
          :class="{ active: activeTab === 'sessions' }"
          @click="activeTab = 'sessions'"
        >
          Session 列表
        </button>
        <button
          class="tab"
          :class="{ active: activeTab === 'logs' }"
          @click="activeTab = 'logs'"
        >
          实时日志
        </button>
      </div>
      <div class="panel-actions" v-if="activeTab === 'sessions'">
        <n-button size="tiny" @click="handleRefresh">刷新</n-button>
      </div>
    </div>

    <!-- Session 列表 -->
    <div class="tab-content sessions-tab" v-show="activeTab === 'sessions'">
      <!-- 分组 tabs -->
      <div class="session-group-tabs" v-if="sessionGroups.length > 1">
        <button
          v-for="group in sessionGroups"
          :key="group.key"
          class="group-tab"
          :class="{ active: activeGroup === group.key }"
          @click="activeGroup = group.key"
        >
          {{ group.label }}
          <span class="group-count">{{ group.sessions.length }}</span>
        </button>
      </div>

      <!-- 当前分组的 sessions -->
      <div :class="selectedSession ? 'sessions-scroll-wrapper-half' : 'sessions-scroll-wrapper'">
        <div class="sessions-list-wrapper">
          <div v-if="currentGroupSessions.length > 0" class="sessions-list">
            <div
              v-for="session in currentGroupSessions"
              :key="session.key || session.id"
              class="session-item"
              :class="{ selected: selectedSession?.key === session.key }"
              @click="selectSession(session)"
            >
              <div class="session-info">
                <span class="session-title">{{ session.title || session.key || '未命名' }}</span>
                <span class="session-meta">
                  {{ formatTime(session.updatedAt) }}
                </span>
              </div>
            </div>
          </div>
          <div v-else-if="loadingSessions" class="empty">加载中...</div>
          <div v-else class="empty">暂无 Session</div>
        </div>
      </div>

      <!-- 选中 Session 的消息 -->
      <div v-if="selectedSession" class="session-messages">
        <div class="messages-header">
          <span class="messages-title">{{ selectedSession.title || selectedSession.key }}</span>
          <n-button size="tiny" @click="selectedSession = null">关闭</n-button>
        </div>
        <div class="messages-content" ref="messageContainer">
          <div v-if="messagesLoading" class="empty">加载消息中...</div>
          <div v-else-if="messages.length === 0" class="empty">暂无消息</div>
          <div v-else class="message-list">
            <div
              v-for="(msg, idx) in messages"
              :key="idx"
              class="message-item"
              :class="'role-' + msg.role"
            >
              <span class="msg-role">{{ msg.role }}</span>
              <span class="msg-content">{{ msg.content }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 日志 -->
    <div class="tab-content log-content" ref="logContainer" v-show="activeTab === 'logs'">
      <div v-if="logs.length === 0" class="empty">等待日志...</div>
      <div
        v-for="(log, idx) in logs"
        :key="idx"
        class="log-line"
        :class="getLogClass(log)"
      >
        {{ log }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { NButton } from 'naive-ui'
import { connectGatewayLogStream, createGatewayApi } from '../api/gatewayApi.js'

const props = defineProps({
  workerId: { type: Number, required: true }
})

const activeTab = ref('sessions')
const sessions = ref([])
const logs = ref([])
const loadingSessions = ref(false)
const selectedSession = ref(null)
const messages = ref([])
const messagesLoading = ref(false)
const messageContainer = ref(null)
const logContainer = ref(null)
const activeGroup = ref('all')
let eventSource = null

// 根据 session key 前缀分组
const sessionGroups = computed(() => {
  const groups = []
  const groupMap = new Map()

  for (const session of sessions.value) {
    const key = session.key || ''
    // 从 key 提取分组
    // agent:main:main -> main
    // agent:main:paperclip -> paperclip
    // agent:main:paperclip:issue:xxx -> paperclip
    // agent:main:feishu:direct:xxx -> feishu
    // agent:main:subagent:xxx -> subagent
    let groupKey = 'other'
    let groupLabel = 'Other'

    const parts = key.split(':')
    // parts[0] = 'agent', parts[1] = 'main', parts[2] = ...
    if (parts.length >= 3) {
      const type = parts[2]
      if (type === 'main') {
        groupKey = 'main'
        groupLabel = 'Main'
      } else if (type === 'paperclip') {
        groupKey = 'paperclip'
        groupLabel = 'Paperclip'
      } else if (type === 'feishu') {
        groupKey = 'feishu'
        groupLabel = 'Feishu'
      } else if (type === 'subagent') {
        groupKey = 'subagent'
        groupLabel = 'Subagent'
      }
    }

    if (!groupMap.has(groupKey)) {
      groupMap.set(groupKey, {
        key: groupKey,
        label: groupLabel,
        sessions: []
      })
      groups.push(groupMap.get(groupKey))
    }
    groupMap.get(groupKey).sessions.push(session)
  }

  // 按优先级排序
  const order = ['main', 'paperclip', 'feishu', 'subagent', 'other']
  groups.sort((a, b) => {
    const aIdx = order.indexOf(a.key)
    const bIdx = order.indexOf(b.key)
    if (aIdx === -1 && bIdx === -1) return 0
    if (aIdx === -1) return 1
    if (bIdx === -1) return -1
    return aIdx - bIdx
  })

  // 如果只有一个分组，添加"全部"选项
  if (groups.length > 1) {
    groups.unshift({
      key: 'all',
      label: '全部',
      sessions: sessions.value
    })
  }

  return groups
})

// 当前分组下的 sessions
const currentGroupSessions = computed(() => {
  if (activeGroup.value === 'all') {
    return sessions.value
  }
  const group = sessionGroups.value.find(g => g.key === activeGroup.value)
  return group?.sessions || []
})

async function loadSessions() {
  if (!props.workerId) return
  loadingSessions.value = true
  try {
    const gateway = createGatewayApi(props.workerId)
    const data = await gateway.sessionsList()
    const payload = data?.payload
    if (payload) {
      sessions.value = payload.sessions || payload.details?.sessions || []
    }
  } catch (e) {
    console.error('[SessionLog] Load sessions error:', e)
  } finally {
    loadingSessions.value = false
  }
}

async function selectSession(session) {
  console.log('[DEBUG] selectSession called, session.key:', session.key)
  if (selectedSession.value?.key === session.key) {
    selectedSession.value = null
    messages.value = []
    return
  }
  selectedSession.value = session
  console.log('[DEBUG] selectedSession.value:', selectedSession.value)
  console.log('[DEBUG] messages.value before:', messages.value)
  messagesLoading.value = true
  messages.value = []
  try {
    const gateway = createGatewayApi(props.workerId)
    const data = await gateway.sessionsHistory(session.key)
    console.log('[DEBUG] API response data:', JSON.stringify(data, null, 2).substring(0, 500))
    const payload = data?.payload
    console.log('[DEBUG] payload:', payload)

    if (payload?.previews && payload.previews.length > 0) {
      const sessionData = payload.previews.find(s => s.key === session.key)
      console.log('[DEBUG] sessionData:', sessionData)
      if (sessionData?.items) {
        messages.value = sessionData.items.map(item => ({
          role: item.role,
          content: item.text
        }))
        console.log('[DEBUG] messages.value after:', messages.value)
      }
    }
  } catch (e) {
    console.error('[SessionLog] Load messages error:', e)
  } finally {
    messagesLoading.value = false
  }
}

function handleRefresh() {
  loadSessions()
}

// 监听 sessions 变化，自动选择第一个分组
watch(sessions, (newSessions) => {
  if (newSessions.length > 0 && sessionGroups.value.length > 0) {
    // 确保 activeGroup 有效
    const validGroup = sessionGroups.value.find(g => g.key === activeGroup.value)
    if (!validGroup) {
      activeGroup.value = sessionGroups.value[0].key
    }
  }
})

function getLogClass(log) {
  if (log.includes('[ERROR]') || log.includes('error')) return 'log-error'
  if (log.includes('[WARN]') || log.includes('warn')) return 'log-warn'
  if (log.includes('[INFO]')) return 'log-info'
  if (log.includes('[DEBUG]')) return 'log-debug'
  return ''
}

function formatTime(timestamp) {
  if (!timestamp) return ''
  const d = new Date(timestamp)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return d.toLocaleDateString('zh-CN')
}

function connectLogStream() {
  if (!props.workerId) return
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (activeTab.value !== 'logs') return

  eventSource = connectGatewayLogStream(
    props.workerId,
    (content) => {
      if (content) {
        const lines = content.split('\n').filter(line => line.trim())
        if (lines.length === 0) return
        logs.value.push(...lines)
        if (logs.value.length > 500) {
          logs.value = logs.value.slice(-500)
        }
        nextTick(() => {
          if (logContainer.value) {
            const { scrollTop, scrollHeight, clientHeight } = logContainer.value
            const distanceFromBottom = scrollHeight - scrollTop - clientHeight
            if (distanceFromBottom < 50) {
              logContainer.value.scrollTop = scrollHeight
            }
          }
        })
      }
    },
    (error) => {
      console.error('[StatusLogPanel] SSE error:', error)
    }
  )
}

// 监听 tab 切换
watch(activeTab, (newTab) => {
  if (newTab === 'logs') {
    nextTick(() => connectLogStream())
  } else if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})

onMounted(() => {
  loadSessions()
  if (activeTab.value === 'logs') {
    connectLogStream()
  }
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})
</script>

<style scoped>
.panel {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.tabs {
  display: flex;
  gap: 4px;
}

.tab {
  padding: 8px 12px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  color: #666;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab:hover {
  color: #333;
}

.tab.active {
  color: #6366f1;
  border-bottom-color: #6366f1;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.tab-content {
  flex: 1;
  overflow: hidden;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
}

.sessions-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.session-group-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 0;
  flex-shrink: 0;
  overflow-x: auto;
}

.group-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 12px;
  cursor: pointer;
  font-size: 11px;
  color: #666;
  white-space: nowrap;
  transition: all 0.2s;
}

.group-tab:hover {
  background: #f3f4f6;
  color: #333;
}

.group-tab.active {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}

.group-tab.active .group-count {
  background: rgba(255, 255, 255, 0.3);
  color: #fff;
}

.group-count {
  background: #e5e7eb;
  color: #666;
  padding: 0 5px;
  border-radius: 8px;
  font-size: 10px;
}

.sessions-scroll-wrapper-half {
  flex: 0.5;
  overflow: hidden;
  min-height: 100px;
}

.sessions-scroll-wrapper {
  flex: 1;
  overflow: hidden;
  min-height: 100px;
}

.sessions-list-wrapper {
  height: 100%;
  overflow-y: auto;
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-item {
  padding: 8px;
  background: #f9fafb;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.session-item:hover {
  background: #f3f4f6;
}

.session-item.selected {
  background: #e0e7ff;
}

.session-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-title {
  font-size: 12px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  font-size: 11px;
  color: #9ca3af;
}

.session-messages {
  flex-shrink: 0;
  height: 200px;
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fafafa;
  overflow: hidden;
  margin-top: 8px;
}

.messages-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.messages-title {
  font-size: 12px;
  font-weight: 600;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.messages-content {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  border-radius: 6px;
  padding: 8px;
  min-height: 100px;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.message-item {
  display: flex;
  gap: 8px;
  font-size: 11px;
  padding: 4px 0;
}

.msg-role {
  flex-shrink: 0;
  width: 60px;
  font-weight: 600;
  color: #60a5fa;
}

.msg-content {
  color: #333;
  white-space: pre-wrap;
  word-break: break-all;
}

.role-user .msg-role { color: #34d399; }
.role-assistant .msg-role { color: #60a5fa; }
.role-tool .msg-role { color: #f472b6; }

.log-content {
  flex: 1;
  background: #fff;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 11px;
  line-height: 1.5;
  overflow-y: auto;
}

.log-line {
  color: #111;
  white-space: pre-wrap;
  word-break: break-all;
  padding: 1px 0;
}

.log-error { color: #dc2626; }
.log-warn { color: #d97706; }
.log-info { color: #2563eb; }
.log-debug { color: #6b7280; }

.empty {
  color: #6b7280;
  text-align: center;
  padding: 20px;
  font-size: 12px;
}
</style>
