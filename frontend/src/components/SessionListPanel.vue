<template>
  <div class="panel session-panel">
    <div class="panel-title">
      Session 列表
      <span class="count" v-if="data">{{ data.sessions.length }}</span>
    </div>
    <div class="panel-content" v-if="data && data.sessions.length > 0">
      <div
        v-for="session in data.sessions"
        :key="session.id"
        class="list-item"
        :class="{ abnormal: session.abnormal }"
      >
        <div class="item-main">
          <span class="item-id">{{ session.id }}</span>
          <n-tag :type="session.abnormal ? 'error' : 'success'" size="tiny">
            {{ session.status }}
          </n-tag>
        </div>
        <div class="item-sub">
          <span>Agent: {{ session.agentId }}</span>
          <span>{{ formatTime(session.createdAt) }}</span>
        </div>
      </div>
    </div>
    <div v-else-if="data" class="empty">暂无 Session</div>
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
  overflow: hidden;
}
.panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
  display: flex;
  align-items: center;
  gap: 8px;
}
.count {
  background: #6366f1;
  color: white;
  border-radius: 10px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: normal;
}
.list-item {
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 6px;
  background: #f9fafb;
  border-left: 3px solid #10b981;
}
.list-item.abnormal {
  background: #fef2f2;
  border-left-color: #ef4444;
}
.item-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.item-id {
  font-size: 12px;
  font-family: monospace;
  color: #374151;
}
.item-sub {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #9ca3af;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 20px;
}
</style>
