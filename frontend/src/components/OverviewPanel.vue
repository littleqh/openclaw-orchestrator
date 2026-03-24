<template>
  <div class="panel overview-panel">
    <div class="panel-title">实例概览</div>
    <div class="panel-content" v-if="data">
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
      <div class="info-row">
        <span class="label">Token</span>
        <span class="value">{{ data.instance.tokenMasked }}</span>
      </div>
      <div class="info-row">
        <span class="label">最后更新</span>
        <span class="value">{{ formatTime(data.timestamp) }}</span>
      </div>
    </div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'

defineProps({ data: Object })

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
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
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
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 20px;
}
</style>
