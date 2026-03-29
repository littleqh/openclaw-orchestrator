<template>
  <div class="panel overview-panel">
    <div class="panel-title">实例概览</div>
    <div class="panel-content" v-if="data">
      <!-- 基本信息 -->
      <div class="info-section" v-if="data.instance">
        <div class="info-row">
          <span class="label">名称</span>
          <span class="value">{{ data.instance.name }}</span>
        </div>
        <div class="info-row">
          <span class="label">URL</span>
          <span class="value url">{{ data.instance.url }}</span>
        </div>
        <div class="info-row">
          <span class="label">状态</span>
          <n-tag :type="data.instance.status === 'online' ? 'success' : 'error'" size="small">
            {{ data.instance.status === 'online' ? '在线' : '离线' }}
          </n-tag>
        </div>
      </div>

      <!-- 详细状态信息 (来自statusText) -->
      <div class="status-section" v-if="statusLines.length > 0">
        <div class="section-title">运行状态</div>
        <div class="status-lines">
          <div v-for="line in statusLines" :key="line.key" class="status-line">
            <span class="status-label">{{ line.key }}</span>
            <span class="status-value">{{ line.value }}</span>
          </div>
        </div>
      </div>

      <!-- 最后更新时间 -->
      <div class="info-row update-time" v-if="data.timestamp">
        <span class="label">最后更新</span>
        <span class="value">{{ formatTime(data.timestamp) }}</span>
      </div>
    </div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = defineProps({ data: Object })

// 从 statusText 解析状态行
const statusLines = computed(() => {
  console.log('[OverviewPanel] data:', JSON.stringify(props.data, null, 2))

  // 尝试从多个可能的路径获取 statusText
  const statusText = props.data?.statusText
    || props.data?.result?.details?.statusText
    || props.data?.details?.statusText

  console.log('[OverviewPanel] statusText found:', statusText)

  if (!statusText) return []

  return statusText
    .split('\n')
    .map(line => line.trim())
    .filter(line => line && (line.includes(':') || line.startsWith('🦞') || line.startsWith('⏱')))
    .map(line => {
      const idx = line.indexOf(':')
      if (idx === -1) return { key: '', value: line }
      return { key: line.slice(0, idx + 1), value: line.slice(idx + 1).trim() }
    })
    .filter(l => l.key || l.value)
})

function formatTime(timestamp) {
  if (!timestamp) return '-'
  const d = new Date(timestamp)
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleTimeString('zh-CN')
}
</script>

<style scoped>
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  overflow-y: auto;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
}
.info-section {
  margin-bottom: 12px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid #f3f4f6;
}
.info-row:last-child {
  border-bottom: none;
}
.label {
  color: #6b7280;
}
.value {
  color: #1e1e2e;
  font-weight: 500;
}
.url {
  font-family: monospace;
  font-size: 12px;
}
.status-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
}
.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 8px;
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
.update-time {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px dashed #e5e7eb;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 20px;
}
</style>
