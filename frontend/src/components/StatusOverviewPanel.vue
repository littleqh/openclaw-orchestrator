<template>
  <div class="panel status-overview-panel">
    <div class="panel-title">实例概览</div>
    <div class="panel-content" v-if="data?.status">
      <!-- 基本信息 -->
      <div class="info-section">
        <div class="info-row">
          <span class="label">Gateway</span>
          <n-tag type="success" size="small">在线</n-tag>
        </div>
      </div>

      <!-- 详细状态信息 -->
      <div class="status-section" v-if="statusLines.length > 0">
        <div class="section-title">运行状态</div>
        <div class="status-lines">
          <div v-for="(line, idx) in statusLines" :key="idx" class="status-line">
            <span class="status-label">{{ line.key }}</span>
            <span class="status-value">{{ line.value }}</span>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = defineProps({ data: Object })

const statusLines = computed(() => {
  // Gateway 返回格式: {"type":"res","ok":true,"payload":{snapshot:{...}}}
  const payload = props.data?.status?.payload
  if (!payload) return []

  const lines = []

  // 尝试解析 snapshot 中的信息
  if (payload.snapshot) {
    const snap = payload.snapshot
    if (snap.agents) {
      lines.push({ key: 'Agent 数', value: snap.agents.length })
    }
    if (snap.sessions) {
      lines.push({ key: 'Session 数', value: snap.sessions.count || (Array.isArray(snap.sessions) ? snap.sessions.length : 0) })
    }
    if (snap.uptimeMs) {
      lines.push({ key: '运行时长', value: formatUptime(snap.uptimeMs) })
    }
    if (snap.health?.ok !== undefined) {
      lines.push({ key: '健康状态', value: snap.health.ok ? '正常' : '异常' })
    }
  }

  return lines
})

function formatUptime(ms) {
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) return `${days}天 ${hours % 24}小时`
  if (hours > 0) return `${hours}小时 ${minutes % 60}分钟`
  if (minutes > 0) return `${minutes}分钟`
  return `${seconds}秒`
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

.info-section {
  margin-bottom: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  font-size: 12px;
}

.label {
  color: #6b7280;
}

.status-section {
  margin-top: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
}

.status-lines {
  background: #f9fafb;
  border-radius: 6px;
  padding: 8px;
}

.status-line {
  display: flex;
  gap: 8px;
  padding: 3px 0;
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', monospace;
}

.status-label {
  color: #6366f1;
  font-weight: 500;
  white-space: nowrap;
}

.status-value {
  color: #333;
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
