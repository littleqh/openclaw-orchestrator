<template>
  <div class="monitor-view">
    <div class="sidebar">
      <div class="sidebar-title">Gateway 实例</div>
      <div v-if="instances.length === 0" class="empty">
        <n-empty description="暂无实例" size="small" />
      </div>
      <div
        v-for="inst in instances"
        :key="inst.id"
        class="instance-item"
        :class="{ selected: selectedId === inst.id }"
        @click="selectInstance(inst.id)"
      >
        <div class="instance-name">{{ inst.name }}</div>
        <div class="instance-url">{{ inst.url }}</div>
      </div>
    </div>

    <div class="main-content">
      <div v-if="!selectedId" class="no-selection">
        <n-empty description="请选择一个实例开始监控" />
      </div>

      <div v-else class="panels-grid">
        <div class="panel-cell top-left">
          <OverviewPanel :data="statusData" />
        </div>
        <div class="panel-cell top-right">
          <SessionListPanel :data="statusData" />
        </div>
        <div class="panel-cell bottom-left">
          <AgentListPanel :data="statusData" />
        </div>
        <div class="panel-cell bottom-right">
          <ActivityPanel :data="statusData" />
        </div>
      </div>

      <!-- 连接状态提示 -->
      <div v-if="error" class="connection-error">
        <span>{{ error }}</span>
        <n-button size="tiny" @click="handleReconnect">重连</n-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { NEmpty, NButton } from 'naive-ui'
import OverviewPanel from '../components/OverviewPanel.vue'
import SessionListPanel from '../components/SessionListPanel.vue'
import AgentListPanel from '../components/AgentListPanel.vue'
import ActivityPanel from '../components/ActivityPanel.vue'
import { instanceApi, connectSse as createSse } from '../api/index.js'

const instances = ref([])
const selectedId = ref(null)
const statusData = ref(null)
const error = ref(null)
const loading = ref(false)

let eventSource = null

async function loadInstances() {
  loading.value = true
  try {
    const data = await instanceApi.list()
    instances.value = Array.isArray(data) ? data : (data.result?.details || [])
  } catch (e) {
    console.error('Load instances error:', e)
  } finally {
    loading.value = false
  }
}

function selectInstance(id) {
  if (selectedId.value === id) return
  selectedId.value = id
  statusData.value = null
  openSse(id)
}

function openSse(instanceId) {
  if (!instanceId) return

  // Close existing connection
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  eventSource = createSse(
    instanceId,
    (data) => {
      statusData.value = data
      error.value = null
    },
    (e) => {
      error.value = '连接中断'
    }
  )
}

function handleReconnect() {
  error.value = null
  if (selectedId.value) {
    openSse(selectedId.value)
  }
}

onMounted(() => {
  loadInstances()
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})
</script>

<style scoped>
.monitor-view {
  display: flex;
  height: calc(100vh - 120px);
  background: #f3f4f6;
  gap: 16px;
  padding: 16px;
}

.sidebar {
  width: 240px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  color: #1e1e2e;
}

.instance-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
  border: 2px solid transparent;
  transition: all 0.2s;
}

.instance-item:hover {
  background: #f9fafb;
}

.instance-item.selected {
  background: #eef2ff;
  border-color: #6366f1;
}

.instance-name {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 2px;
}

.instance-url {
  font-size: 11px;
  color: #9ca3af;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  flex: 1;
  position: relative;
  min-width: 0;
}

.no-selection {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.panels-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 16px;
  height: 100%;
}

.panel-cell {
  min-height: 0;
  overflow: hidden;
}

.panel-cell > * {
  height: 100%;
}

.connection-error {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: #fef2f2;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #dc2626;
  font-size: 13px;
}

.empty {
  padding: 20px 0;
}
</style>
