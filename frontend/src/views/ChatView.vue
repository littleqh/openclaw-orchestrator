<template>
  <div class="chat-view">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <n-select
          v-model:value="selectedWorkerId"
          :options="workerOptions"
          placeholder="选择 Agent"
          filterable
          @update:value="handleWorkerChange"
        />
      </div>
      <n-tabs v-model:value="sessionTab" type="line" size="small" class="session-tabs">
        <n-tab-pane name="history" tab="历史" />
        <n-tab-pane name="archived" tab="归档" />
      </n-tabs>
      <div class="session-list">
        <div class="session-item" v-if="!selectedWorkerId">
          <n-empty description="请选择左侧 Agent" size="small" />
        </div>
        <div v-else-if="sessions.length === 0" class="session-empty">
          <n-empty :description="sessionTab === 'history' ? '暂无会话' : '暂无归档会话'" size="small" />
        </div>
        <div
          v-else
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ selected: selectedSessionId === session.id }"
          @click="selectSession(session)"
        >
          <div class="session-info">
            <div class="session-title">{{ session.title }}</div>
            <div class="session-time">{{ formatTime(session.updatedAt) }}</div>
          </div>
          <div class="session-actions" @click.stop>
            <n-button text size="tiny" @click.stop="openRenameModal(session)" title="重命名">
              ✏️
            </n-button>
            <n-button text size="tiny" @click.stop="toggleArchive(session)" :title="sessionTab === 'history' ? '归档' : '取消归档'">
              {{ sessionTab === 'history' ? '📁' : '📂' }}
            </n-button>
            <n-popconfirm @positive-click.stop="deleteSession(session)">
              <template #trigger>
                <n-button text size="tiny" title="删除">🗑️</n-button>
              </template>
              确定要删除会话「{{ session.title }}」吗？删除后无法恢复。
            </n-popconfirm>
          </div>
        </div>
      </div>
      <div class="sidebar-footer" v-if="selectedWorkerId && sessionTab === 'history'">
        <n-button block @click="createNewSession">➕ 新对话</n-button>
      </div>
    </div>

    <div class="chat-main">
      <div v-if="!selectedSessionId" class="no-session">
        <n-empty description="选择一个会话或开始新对话" />
      </div>
      <div v-else class="messages-container">
        <div class="messages-list" ref="messagesListRef">
          <div
            v-for="msg in currentMessages"
            :key="msg.id"
            class="message"
            :class="msg.role"
          >
            <div class="message-avatar">
              <span v-if="msg.role === 'user'">👤</span>
              <span v-else>🤖</span>
            </div>
            <div class="message-content-wrapper">
              <div
                v-if="msg.role === 'assistant' && msg.thinking"
                class="thinking-wrapper"
                :class="{ 'collapsed': !msg._expanded }"
                @click="msg._expanded = !msg._expanded"
              >
                <div class="thinking-toggle">
                  {{ msg._expanded ? '▲ 点击收起思考过程' : '▼ 点击展开思考过程' }}
                </div>
                <div class="message-content thinking-content" v-html="renderMarkdown(msg.thinking)"></div>
              </div>
              <div class="message-content" v-html="renderMarkdown(msg.content)"></div>
            </div>
          </div>
          <div v-if="streaming" class="message assistant">
            <div class="message-avatar">🤖</div>
            <div class="message-content-wrapper">
              <div
                v-if="streamingThinking"
                class="thinking-wrapper"
                :class="{ 'collapsed': !streamingThinkingExpanded }"
                @click="streamingThinkingExpanded = !streamingThinkingExpanded"
              >
                <div class="thinking-toggle">
                  {{ streamingThinkingExpanded ? '▲ 点击收起思考过程' : '▼ 点击展开思考过程' }}
                </div>
                <div class="message-content thinking-content" v-html="renderMarkdown(streamingThinking)"></div>
              </div>
              <div class="message-content streaming" v-html="renderMarkdown(streamingContent)"></div>
              <span v-if="streaming" class="cursor">▊</span>
            </div>
          </div>
        </div>
        <div class="message-input">
          <n-input
            v-model:value="inputMessage"
            type="textarea"
            placeholder="输入消息..."
            :rows="2"
            @keydown="handleKeydown"
          />
          <n-button type="primary" @click="handleSend" :loading="sending" :disabled="!inputMessage.trim()">
            发送
          </n-button>
        </div>
      </div>
    </div>

    <!-- Rename Modal -->
    <n-modal v-model:show="showRenameModal" preset="card" title="重命名会话" style="width: 400px;">
      <n-input v-model:value="renameTitle" placeholder="请输入会话名称" />
      <template #footer>
        <n-button @click="showRenameModal = false">取消</n-button>
        <n-button type="primary" @click="confirmRename">确定</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { NSelect, NButton, NInput, NEmpty, NTabs, NTabPane, NModal, NPopconfirm, useMessage } from 'naive-ui'
import { workerApi } from '../api/workerApi.js'
import { chatApi } from '../api/index.js'
import { marked } from 'marked'

const message = useMessage()
const workers = ref([])
const selectedWorkerId = ref(null)
const selectedSessionId = ref(null)
const sessions = ref([])
const currentMessages = ref([])
const inputMessage = ref('')
const streaming = ref(false)
const streamingContent = ref('')
const streamingThinking = ref('')
const streamingThinkingExpanded = ref(false)
const sending = ref(false)
const messagesListRef = ref(null)
let ws = null

// New state for tabs and session operations
const sessionTab = ref('history')
const showRenameModal = ref(false)
const renameTitle = ref('')
const renamingSession = ref(null)

function renderMarkdown(text) {
  if (!text) return ''
  const trimmed = text.trimEnd()
  let html = marked.parse(trimmed)
  // Remove trailing whitespace/newlines from rendered HTML to prevent extra empty lines
  html = html.replace(/\s+$/, '')
  return html
}

const workerOptions = computed(() => {
  return workers.value
    .filter(w => w.localRuntime)
    .map(w => ({
      label: `${w.name} (${w.nickname || '本地 Agent'})`,
      value: w.id
    }))
})

// Watch session tab changes to reload sessions
watch(sessionTab, async () => {
  if (selectedWorkerId.value) {
    await loadSessions()
  }
})

async function loadWorkers() {
  try {
    workers.value = await workerApi.list()
  } catch (e) {
    console.error('Load workers error:', e)
  }
}

async function handleWorkerChange(workerId) {
  selectedWorkerId.value = workerId
  selectedSessionId.value = null
  sessions.value = []
  currentMessages.value = []
  closeWs()
  if (workerId) {
    await loadSessions()
  }
}

async function loadSessions() {
  if (!selectedWorkerId.value) return
  try {
    const archived = sessionTab.value === 'archived'
    sessions.value = await chatApi.listSessions(selectedWorkerId.value, archived)
  } catch (e) {
    console.error('Load sessions error:', e)
  }
}

async function selectSession(session) {
  console.log('[Chat] selectSession called:', session)
  try {
    selectedSessionId.value = session.id
    currentMessages.value = []
    await loadMessages()
    connectWs()
  } catch (e) {
    console.error('[Chat] selectSession error:', e)
    message.error('选择会话失败')
  }
}

async function createNewSession() {
  if (!selectedWorkerId.value) return
  try {
    const session = await chatApi.createSession(selectedWorkerId.value, '新对话')
    sessions.value.unshift(session)
    selectedSessionId.value = session.id
    currentMessages.value = []
    connectWs()
  } catch (e) {
    message.error('创建会话失败')
  }
}

function openRenameModal(session) {
  renamingSession.value = session
  renameTitle.value = session.title
  showRenameModal.value = true
}

async function confirmRename() {
  if (!renamingSession.value || !renameTitle.value.trim()) return
  try {
    await chatApi.renameSession(selectedWorkerId.value, renamingSession.value.id, renameTitle.value.trim())
    renamingSession.value.title = renameTitle.value.trim()
    showRenameModal.value = false
    message.success('会话已重命名')
  } catch (e) {
    message.error('重命名失败')
  }
}

async function toggleArchive(session) {
  try {
    if (sessionTab.value === 'history') {
      await chatApi.archiveSession(selectedWorkerId.value, session.id)
      message.success('会话已归档')
      sessions.value = sessions.value.filter(s => s.id !== session.id)
    } else {
      await chatApi.unarchiveSession(selectedWorkerId.value, session.id)
      message.success('会话已取消归档')
      sessions.value = sessions.value.filter(s => s.id !== session.id)
    }
    if (selectedSessionId.value === session.id) {
      selectedSessionId.value = null
      closeWs()
      currentMessages.value = []
    }
  } catch (e) {
    message.error('操作失败')
  }
}

async function deleteSession(session) {
  try {
    await chatApi.deleteSession(selectedWorkerId.value, session.id)
    sessions.value = sessions.value.filter(s => s.id !== session.id)
    if (selectedSessionId.value === session.id) {
      selectedSessionId.value = null
      closeWs()
      currentMessages.value = []
    }
    message.success('会话已删除')
  } catch (e) {
    message.error('删除失败')
  }
}

async function loadMessages() {
  if (!selectedSessionId.value) return
  try {
    const data = await chatApi.getMessages(selectedWorkerId.value, selectedSessionId.value)
    console.log('[Chat] Messages loaded:', data)
    // Merge server messages with local pending messages (those with temp IDs)
    const serverMessages = Array.isArray(data) ? data : []
    const localPending = currentMessages.value.filter(msg => String(msg.id).startsWith('temp_'))

    // Deduplicate: if server already has a message with same role+content, skip local duplicate
    const serverKeySet = new Set(serverMessages.map(m => `${m.role}:${m.content}`))
    const uniqueLocal = localPending.filter(m => !serverKeySet.has(`${m.role}:${m.content}`))

    const merged = [...serverMessages, ...uniqueLocal]
    console.log('[Chat] Merged messages:', merged.length, 'server:', serverMessages.length, 'local pending:', localPending.length, 'unique local:', uniqueLocal.length)
    currentMessages.value = merged
  } catch (e) {
    console.error('Load messages error:', e)
    currentMessages.value = []
  }
}

function connectWs() {
  if (!selectedWorkerId.value || !selectedSessionId.value) return

  closeWs()

  const token = localStorage.getItem('token')
  console.log('[Chat] Token exists:', !!token, token ? token.substring(0, 10) + '...' : null)
  const wsUrl = `ws://localhost:8080/ws/chat/${selectedWorkerId.value}?token=${token}`
  console.log('[Chat] Connecting to:', wsUrl)

  ws = new WebSocket(wsUrl)
  console.log('[Chat] WebSocket created, readyState:', ws.readyState)

  ws.onopen = () => {
    console.log('[Chat] WebSocket connected, readyState:', ws.readyState)
  }

  ws.onmessage = (event) => {
    console.log('[Chat] WS message received:', event.data)
    try {
      const data = JSON.parse(event.data)
      console.log('[Chat] Parsed message type:', data.type, 'content:', data.content ? data.content.substring(0, 50) : null)
      if (data.type === 'chunk') {
        streaming.value = true
        streamingContent.value += data.content
        scrollToBottom()
      } else if (data.type === 'thinking') {
        streamingThinking.value += data.content
        scrollToBottom()
      } else if (data.type === 'done') {
        console.log('[Chat] Done received')
        streaming.value = false
        streamingContent.value = ''
        streamingThinking.value = ''
        streamingThinkingExpanded.value = false
        loadMessages()
      } else if (data.type === 'error') {
        console.error('[Chat] Error from server:', data.content)
        message.error(data.content)
        streaming.value = false
        streamingContent.value = ''
      } else if (data.type === 'system') {
        console.log('[Chat] System:', data.content)
      }
    } catch (e) {
      console.error('[Chat] Parse error:', e, 'raw data:', event.data)
    }
  }

  ws.onerror = (error) => {
    console.error('[Chat] WebSocket error:', error)
    message.error('WebSocket 连接失败，请检查后端服务')
  }

  ws.onclose = (event) => {
    console.log('[Chat] WebSocket closed, code:', event.code, 'reason:', event.reason)
    ws = null
  }
}

function closeWs() {
  if (ws) {
    ws.onclose = null  // Prevent the old onclose from firing
    ws.close()
    ws = null
  }
}

function handleKeydown(e) {
  // Only handle plain Enter without modifiers (Shift+Enter should insert newline)
  if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.metaKey) {
    e.preventDefault()
    handleSend()
  }
}

async function handleSend() {
  const content = inputMessage.value.replace(/\n/g, ' ').trim()
  if (!content || !selectedSessionId.value || sending.value) return

  inputMessage.value = ''
  sending.value = true

  // Add user message to UI immediately with temp ID
  currentMessages.value.push({
    id: 'temp_' + Date.now(),
    role: 'user',
    content: content
  })
  scrollToBottom()

  try {
    // Send via WebSocket
    if (ws && ws.readyState === WebSocket.OPEN) {
      console.log('[Chat] Sending message, readyState:', ws.readyState)
      ws.send(JSON.stringify({
        type: 'message',
        content: content,
        sessionId: selectedSessionId.value
      }))
    } else {
      console.warn('[Chat] Cannot send, ws is', ws, 'readyState:', ws ? ws.readyState : 'null')
      message.error('连接已断开，请重新选择会话')
    }
  } catch (e) {
    message.error('发送失败')
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesListRef.value) {
      messagesListRef.value.scrollTop = messagesListRef.value.scrollHeight
    }
  })
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  return d.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  loadWorkers()
})

onUnmounted(() => {
  closeWs()
})
</script>

<style scoped>
.chat-view {
  display: flex;
  height: calc(100vh - 120px);
  background: #f3f4f6;
  gap: 16px;
  padding: 16px;
}

.chat-sidebar {
  width: 260px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.session-tabs {
  padding: 0 12px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100px;
}

.session-item {
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
}

.session-item:hover {
  background: #f5f5f5;
}

.session-item.selected {
  background: #eef2ff;
  border: 2px solid #6366f1;
}

.session-info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.session-title {
  font-weight: 500;
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 11px;
  color: #888;
  margin-top: 4px;
}

.session-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .session-actions {
  opacity: 1;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #f0f0f0;
}

.chat-main {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.no-session {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.messages-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.messages-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: #eef2ff;
}

.message-content {
  padding: 14px 18px;
  border-radius: 12px;
  background: #f5f5f5;
  color: #333;
  line-height: 1.6;
  white-space: pre-line;
  word-break: break-word;
}

/* Markdown rendered content styles */
.message-content h1 { font-size: 1.5em; margin: 0.5em 0; font-weight: bold; }
.message-content h2 { font-size: 1.3em; margin: 0.5em 0; font-weight: bold; }
.message-content h3 { font-size: 1.1em; margin: 0.5em 0; font-weight: bold; }
.message-content p { margin: 0.5em 0; }
.message-content ul,
.message-content ol { margin: 0.5em 0; padding-left: 5em; list-style-position: inside; }
.message-content li { margin: 0.25em 0; }
.message-content code { background: #e5e5e5; padding: 0.1em 0.3em; border-radius: 3px; font-family: monospace; }
.message-content pre { background: #e5e5e5; padding: 0.5em; border-radius: 6px; overflow-x: auto; margin: 0.5em 0; }
.message-content pre code { background: none; padding: 0; }
.message-content table { border-collapse: collapse; margin: 0.5em 0; }
.message-content th, .message-content td { border: 1px solid #ddd; padding: 0.3em 0.6em; }
.message-content th { background: #e5e5e5; font-weight: bold; }
.message-content strong { font-weight: bold; }
.message-content em { font-style: italic; }
.message-content blockquote { border-left: 3px solid #ccc; padding-left: 0.5em; margin: 0.5em 0; color: #666; }
.message-content hr { border: none; border-top: 1px solid #ddd; margin: 0.5em 0; }

.message.user .message-content {
  background: #6366f1;
  color: #fff;
  padding: 14px 18px;
}

.message.assistant .message-content {
  background: #dbeafe;
  color: #1e40af;
}

.message-content.streaming {
  background: #fff3cd;
  padding: 14px 18px;
}

.message-content-wrapper {
  max-width: 70%;
  display: flex;
  flex-direction: column;
  padding: 14px 18px;
  box-sizing: border-box;
}

.thinking-wrapper {
  margin-bottom: 8px;
  cursor: pointer;
  box-sizing: border-box;
}

.thinking-wrapper.collapsed .thinking-content {
  max-height: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  opacity: 0.7;
}

.thinking-toggle {
  font-size: 12px;
  color: #9ca3af;
  cursor: pointer;
  margin-bottom: 4px;
  user-select: none;
  font-weight: 500;
}

.thinking-toggle:hover {
  color: #6b7280;
}

.message-content.thinking-content {
  background: #f3f4f6;
  color: #6b7280;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 14px 18px;
}

.message-content.thinking-content h1,
.message-content.thinking-content h2,
.message-content.thinking-content h3 { font-size: 1em; margin: 0.3em 0; }
.message-content.thinking-content p { margin: 0.3em 0; }
.message-content.thinking-content ul,
.message-content.thinking-content ol { margin: 0.3em 0; padding-left: 1.2em; }
.message-content.thinking-content li { margin: 0.15em 0; }

.cursor {
  animation: blink 1s infinite;
  display: inline-block;
  margin-left: 2px;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.message-input {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.message-input .n-input {
  flex: 1;
}
</style>
