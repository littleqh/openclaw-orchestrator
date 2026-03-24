<template>
  <div class="panel activity-panel">
    <div class="panel-title">最近活动</div>
    <div class="panel-content" v-if="data && data.activities.length > 0">
      <div
        v-for="(activity, index) in data.activities"
        :key="index"
        class="activity-item"
        :class="getActivityClass(activity.type)"
      >
        <div class="activity-icon">{{ getActivityIcon(activity.type) }}</div>
        <div class="activity-content">
          <div class="activity-detail">{{ activity.detail }}</div>
          <div class="activity-time">{{ formatTime(activity.time) }}</div>
        </div>
      </div>
    </div>
    <div v-else-if="data" class="empty">暂无活动</div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
defineProps({ data: Object })

const TYPE_CONFIG = {
  session_created: { icon: '🆕', class: 'created' },
  session_ended: { icon: '👋', class: 'ended' },
  session_error: { icon: '❌', class: 'error' },
  agent_registered: { icon: '➕', class: 'registered' },
  agent_unregistered: { icon: '➖', class: 'unregistered' },
}

function getActivityIcon(type) {
  return TYPE_CONFIG[type]?.icon || '📌'
}

function getActivityClass(type) {
  return TYPE_CONFIG[type]?.class || ''
}

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
  overflow: hidden;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
}
.activity-item {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}
.activity-item:last-child {
  border-bottom: none;
}
.activity-icon {
  font-size: 14px;
  flex-shrink: 0;
}
.activity-content {
  flex: 1;
  min-width: 0;
}
.activity-detail {
  font-size: 12px;
  color: #374151;
  word-break: break-word;
}
.activity-time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}
.activity-item.error .activity-detail {
  color: #ef4444;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 20px;
}
</style>
