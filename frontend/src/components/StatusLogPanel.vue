<template>
  <div class="panel status-log-panel">
    <div class="panel-header">
      <div class="panel-title">实时日志</div>
      <div class="panel-actions">
        <n-button size="tiny" @click="handleClear">清屏</n-button>
      </div>
    </div>
    <div class="log-content" ref="logContainer">
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
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { NButton } from 'naive-ui'
import { connectGatewayLogStream } from '../api/gatewayApi.js'

const props = defineProps({
  workerId: { type: Number, required: true }
})

const logs = ref([])
const logContainer = ref(null)
let eventSource = null

function connectLogStream() {
  if (!props.workerId) return

  // 关闭已有连接
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  eventSource = connectGatewayLogStream(
    props.workerId,
    (content) => {
      // 收到日志内容，追加到显示
      if (content) {
        // 分割多行日志
        const lines = content.split('\n').filter(line => line.trim())
        if (lines.length === 0) return  // 没有新内容不处理

        logs.value.push(...lines)
        // 限制日志数量
        if (logs.value.length > 500) {
          logs.value = logs.value.slice(-500)
        }

        // 只有当滚动条已经在底部附近时才自动滚动
        nextTick(() => {
          if (logContainer.value) {
            const { scrollTop, scrollHeight, clientHeight } = logContainer.value
            const distanceFromBottom = scrollHeight - scrollTop - clientHeight
            // 如果距离底部小于50px，认为在底部，自动滚动
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

function handleClear() {
  logs.value = []
  userScrolledUp = false
}

function getLogClass(log) {
  if (log.includes('[ERROR]') || log.includes('error')) return 'log-error'
  if (log.includes('[WARN]') || log.includes('warn')) return 'log-warn'
  if (log.includes('[INFO]')) return 'log-info'
  if (log.includes('[DEBUG]')) return 'log-debug'
  return ''
}

onMounted(() => {
  connectLogStream()
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
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}

.panel-title {
  font-weight: 600;
  font-size: 13px;
  color: #1e1e2e;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.log-content {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  background: #1e1e2e;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 11px;
  line-height: 1.5;
}

.log-line {
  color: #e5e5e5;
  white-space: pre-wrap;
  word-break: break-all;
  padding: 1px 0;
}

.log-error {
  color: #f87171;
}

.log-warn {
  color: #fbbf24;
}

.log-info {
  color: #60a5fa;
}

.log-debug {
  color: #9ca3af;
}

.empty {
  color: #6b7280;
  text-align: center;
  padding: 20px;
}
</style>
