<template>
  <n-card class="instance-card" :class="{ loading: loading }">
    <template #header>
      <div class="card-header">
        <div>
          <div class="inst-name">{{ instance.name }}</div>
          <div class="inst-url">{{ instance.url }}</div>
        </div>
        <div class="card-actions">
          <n-tag :type="statusOk ? 'success' : 'warning'" size="small">
            {{ statusOk ? '在线' : '离线' }}
          </n-tag>
          <n-button size="tiny" @click="loadStatus" :loading="loading">🔄</n-button>
          <n-popconfirm @positive-click="deleteInstance">
            <template #trigger>
              <n-button size="tiny" type="error">🗑</n-button>
            </template>
            确定删除 "{{ instance.name }}" ？
          </n-popconfirm>
        </div>
      </div>
    </template>

    <div v-if="loading && !statusText" class="loading-text">加载中...</div>

    <div v-else-if="statusText" class="status-content">
      <div class="status-lines">
        <div v-for="line in statusLines" :key="line.key" class="status-line">
          <span class="status-label">{{ line.key }}</span>
          <span class="status-value">{{ line.value }}</span>
        </div>
      </div>

      <n-divider />

      <div class="info-row">
        <span class="label">Sessions:</span>
        <span class="value">{{ sessions.length }}</span>
      </div>
      <div class="info-row" v-if="subagents.length > 0">
        <span class="label">活跃子Agent:</span>
        <span class="value">{{ subagents.length }}</span>
      </div>
    </div>

    <div v-else-if="error" class="error-text">
      ❌ {{ error }}
    </div>
  </n-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { NCard, NTag, NButton, NPopconfirm, NDivider, useMessage } from 'naive-ui'
import { instanceApi } from '../api/index.js'

const props = defineProps({ instance: Object })
const emit = defineEmits(['deleted'])

const loading = ref(false)
const statusText = ref('')
const sessions = ref([])
const subagents = ref([])
const error = ref('')
const message = useMessage()

const statusOk = computed(() => !!statusText.value && !error.value)

const statusLines = computed(() => {
  if (!statusText.value) return []
  return statusText.value
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

async function loadStatus() {
  loading.value = true
  error.value = ''
  try {
    const [statusData, sessionsData, subagentsData] = await Promise.all([
      instanceApi.getStatus(props.instance.id),
      instanceApi.getSessions(props.instance.id),
      instanceApi.getSubagents(props.instance.id)
    ])

    // 解析 statusText
    if (statusData.ok) {
      const details = statusData.result?.details
      if (details?.statusText) {
        statusText.value = details.statusText
      }
    } else {
      error.value = statusData.error?.message || '获取状态失败'
    }

    // 解析 sessions
    if (sessionsData.ok) {
      const details = sessionsData.result?.details
      sessions.value = details?.sessions || []
    }

    // 解析 subagents
    if (subagentsData.ok) {
      const details = subagentsData.result?.details
      subagents.value = details?.active || []
    }
  } catch (e) {
    error.value = e.message || '网络错误'
  } finally {
    loading.value = false
  }
}

async function deleteInstance() {
  await instanceApi.delete(props.instance.id)
  message.success('删除成功')
  emit('deleted')
}

onMounted(() => {
  loadStatus()
})
</script>

<style scoped>
.instance-card {
  border-radius: 12px;
  transition: all 0.2s;
}
.instance-card.loading {
  opacity: 0.7;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.inst-name {
  font-size: 16px;
  font-weight: 600;
  color: #1e1e2e;
}
.inst-url {
  font-size: 12px;
  color: #888;
  margin-top: 2px;
}
.card-actions {
  display: flex;
  gap: 6px;
  align-items: center;
}
.status-content {
  font-size: 13px;
}
.status-line {
  display: flex;
  gap: 8px;
  padding: 3px 0;
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
.info-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  padding: 2px 0;
}
.label {
  color: #888;
}
.value {
  color: #333;
  font-weight: 500;
}
.loading-text, .error-text {
  padding: 20px;
  text-align: center;
  color: #888;
}
</style>
