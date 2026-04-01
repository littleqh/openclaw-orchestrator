<template>
  <div class="panel status-agent-panel">
    <div class="panel-title">Agent 列表</div>
    <div class="panel-content" v-if="agents.length > 0">
      <div
        v-for="agent in agents"
        :key="agent.agentId"
        class="agent-item"
      >
        <div class="agent-info">
          <span class="agent-name">{{ agent.name || agent.agentId }}</span>
          <div class="agent-meta">
            <n-tag :type="getAgentStatusType(agent)" size="tiny">
              {{ agent.status || 'unknown' }}
            </n-tag>
            <span class="session-count">{{ agent.sessionCount || 0 }} sessions</span>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty">暂无 Agent</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = defineProps({ data: Object })

const agents = computed(() => {
  // Gateway 返回格式: {"type":"res","ok":true,"payload":{agents:[...]}}
  const payload = props.data?.agents?.payload
  if (!payload) return []

  // agents.list 返回的结构在 payload 中
  if (Array.isArray(payload.agents)) return payload.agents
  if (Array.isArray(payload.details?.agents)) return payload.details.agents
  if (Array.isArray(payload.details)) return payload.details

  return []
})

function getAgentStatusType(agent) {
  const status = agent.status?.toLowerCase()
  if (status === 'active' || status === 'running') return 'success'
  if (status === 'error') return 'error'
  if (status === 'idle') return 'default'
  return 'info'
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

.agent-item {
  padding: 8px;
  border-bottom: 1px solid #f3f4f6;
}

.agent-item:last-child {
  border-bottom: none;
}

.agent-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.agent-name {
  font-size: 12px;
  color: #333;
  font-weight: 500;
}

.agent-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-count {
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
