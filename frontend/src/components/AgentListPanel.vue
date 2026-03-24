<template>
  <div class="panel agent-panel">
    <div class="panel-title">
      Agent 列表
      <span class="count" v-if="data">{{ data.agents.length }}</span>
    </div>
    <div class="panel-content" v-if="data && data.agents.length > 0">
      <div
        v-for="agent in data.agents"
        :key="agent.id"
        class="list-item"
      >
        <div class="item-main">
          <span class="item-name">{{ agent.name }}</span>
          <n-tag :type="agent.status === 'active' ? 'info' : 'default'" size="tiny">
            {{ agent.status }}
          </n-tag>
        </div>
        <div class="item-sub">
          <span class="item-id">{{ agent.id }}</span>
          <span>活跃 Session: {{ agent.sessionCount }}</span>
        </div>
      </div>
    </div>
    <div v-else-if="data" class="empty">暂无 Agent</div>
    <div v-else class="empty">暂无数据</div>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'

defineProps({ data: Object })
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
  border-left: 3px solid #6366f1;
}
.item-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.item-name {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}
.item-id {
  font-size: 11px;
  font-family: monospace;
  color: #9ca3af;
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
