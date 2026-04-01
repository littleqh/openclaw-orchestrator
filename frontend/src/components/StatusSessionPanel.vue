<template>
  <div class="panel status-session-panel">
    <div class="panel-title">Session 列表</div>
    <div class="panel-content" v-if="sessions.length > 0">
      <div
        v-for="session in sessions"
        :key="session.key || session.id"
        class="session-item"
      >
        <div class="session-info">
          <span class="session-title">{{ session.title || session.key || '未命名' }}</span>
          <span class="session-meta">
            {{ formatTime(session.updatedAt) }}
            <n-tag :type="getSessionStatusType(session)" size="tiny">
              {{ session.status || 'unknown' }}
            </n-tag>
          </span>
        </div>
      </div>
    </div>
    <div v-else class="empty">暂无 Session</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = defineProps({ data: Object })

const sessions = computed(() => {
  // Gateway 返回格式: {"type":"res","ok":true,"payload":{sessions:[...]}}
  const payload = props.data?.sessions?.payload
  if (!payload) return []

  if (Array.isArray(payload.sessions)) return payload.sessions
  if (Array.isArray(payload.details?.sessions)) return payload.details.sessions

  return []
})

function getSessionStatusType(session) {
  const status = session.status?.toLowerCase()
  if (status === 'active' || status === 'running') return 'success'
  if (status === 'error' || status === 'failed') return 'error'
  if (status === 'waiting') return 'warning'
  return 'default'
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
</script>

<style scoped>
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 10px;
  color: #1e1e2e;
  flex-shrink: 0;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  padding: 8px;
  border-bottom: 1px solid #f3f4f6;
}

.session-item:last-child {
  border-bottom: none;
}

.session-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-title {
  font-size: 12px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #9ca3af;
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 12px;
}
</style>
