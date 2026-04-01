<template>
  <div class="status-monitor">
    <!-- 连接中状态 -->
    <div v-if="connectStatus === 'connecting'" class="loading-state">
      <n-spin size="large" />
      <div class="loading-text">正在连接 Gateway...</div>
    </div>

    <!-- 需要配对 -->
    <div v-else-if="connectStatus === 'pairing_required'" class="error-state">
      <n-result status="warning" title="需要配对设备">
        <template #-footer>
          <div class="pairing-hint">
            <p>请在 OpenClaw Gateway 服务器上运行以下命令：</p>
            <code class="pairing-command">openclaw devices approve {{ pairingRequestId }}</code>
            <n-button type="primary" @click="handleRetry" :loading="connecting">重试连接</n-button>
          </div>
        </template>
      </n-result>
    </div>

    <!-- 连接失败 -->
    <div v-else-if="connectStatus === 'error'" class="error-state">
      <n-result status="error" title="连接失败" :description="connectError">
        <template #footer>
          <n-button type="primary" @click="handleRetry">重试</n-button>
        </template>
      </n-result>
    </div>

    <!-- 已连接 - 显示状态面板 -->
    <div v-else-if="connectStatus === 'connected'" class="panels-container">
      <div class="panels-grid">
        <div class="panel-cell overview-cell">
          <StatusOverviewPanel :data="statusData" />
        </div>
        <div class="panel-cell skills-cell">
          <StatusHealthPanel :data="statusData" />
        </div>
        <div class="panel-cell">
          <StatusAgentPanel :data="statusData" />
        </div>
      </div>
      <div class="bottom-panel">
        <StatusSessionAndLogPanel :workerId="workerId" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { NSpin, NResult, NButton, useMessage } from 'naive-ui'
import StatusOverviewPanel from './StatusOverviewPanel.vue'
import StatusSessionAndLogPanel from './StatusSessionAndLogPanel.vue'
import StatusAgentPanel from './StatusAgentPanel.vue'
import StatusHealthPanel from './StatusHealthPanel.vue'
import { workerApi } from '../api/workerApi.js'
import { createGatewayApi } from '../api/gatewayApi.js'

const props = defineProps({
  workerId: { type: Number, required: true },
  gatewayUrl: { type: String, default: '' }
})

const message = useMessage()
const connecting = ref(false)
const connectStatus = ref('disconnected')  // disconnected, connecting, connected, pairing_required, error
const connectError = ref('')
const pairingRequestId = ref('')
const statusData = ref(null)
let eventSource = null

async function connectAndLoad() {
  if (!props.workerId) return

  connectStatus.value = 'connecting'
  connectError.value = ''
  statusData.value = null

  try {
    // 连接 Gateway
    const result = await workerApi.connect(props.workerId)

    if (result.status === 'pairing_required') {
      connectStatus.value = 'pairing_required'
      // 从日志中提取 requestId
      const match = result.logs?.find(log => log.includes('requestId'))?.match(/requestId[：:]\s*(\S+)/)
      pairingRequestId.value = match ? match[1] : ''
      return
    }

    if (result.status === 'error') {
      connectStatus.value = 'error'
      connectError.value = result.message || '连接失败'
      return
    }

    if (result.status === 'connected') {
      connectStatus.value = 'connected'
      // 加载状态数据
      await loadStatusData()
    }
  } catch (e) {
    connectStatus.value = 'error'
    connectError.value = e.message || '连接失败'
    message.error('连接失败')
  }
}

async function loadStatusData() {
  if (!props.workerId) return

  try {
    const gateway = createGatewayApi(props.workerId)

    // 并行调用多个接口
    const [status, agents, skills] = await Promise.all([
      gateway.status().catch(() => null),
      gateway.agentsList().catch(() => null),
      gateway.skillsStatus().catch(() => null)
    ])

    statusData.value = {
      status,
      agents,
      skills
    }
  } catch (e) {
    console.error('Load status data error:', e)
  }
}

function handleRetry() {
  connectAndLoad()
}

// 组件挂载时自动连接
onMounted(() => {
  if (props.workerId) {
    connectAndLoad()
  }
})

// 组件卸载时清理
onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
})

// 监听 workerId 变化
watch(() => props.workerId, (newId) => {
  if (newId) {
    connectAndLoad()
  }
})
</script>

<style scoped>
.status-monitor {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}

.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.loading-text {
  color: #666;
  font-size: 14px;
}

.error-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pairing-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.pairing-command {
  background: #f5f5f5;
  padding: 8px 16px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 13px;
}

.panels-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
  min-height: 0;
}

.panels-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 140px 140px;
  grid-template-areas:
    "overview skills"
    "agent skills";
  gap: 12px;
  flex-shrink: 0;
}

.panel-cell {
  overflow: hidden;
}

.panel-cell > * {
  height: 100%;
  overflow-y: auto;
}

.overview-cell {
  grid-area: overview;
}

.skills-cell {
  grid-area: skills;
}

.bottom-panel {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}
</style>
