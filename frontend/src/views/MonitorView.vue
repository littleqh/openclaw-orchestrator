<template>
  <div class="monitor-view">
    <div class="sidebar">
      <div class="sidebar-title">
        Gateway 实例
        <n-button size="tiny" @click="showAddModal = true">➕</n-button>
      </div>
      <div v-if="instances.length === 0" class="empty">
        <n-empty description="暂无实例" size="small" />
      </div>
      <div
        v-for="inst in instancesWithStatus"
        :key="inst.id"
        class="instance-item"
        :class="{ selected: selectedId === inst.id }"
        @click="selectInstance(inst)"
      >
        <div class="instance-info">
          <div class="instance-name">{{ inst.name }}</div>
          <div class="instance-url">{{ inst.url }}</div>
        </div>
        <div class="instance-actions">
          <n-tag :type="inst.online ? 'success' : 'warning'" size="small">
            {{ inst.online ? '在线' : '离线' }}
          </n-tag>
          <n-popconfirm @positive-click="deleteInstance(inst.id)">
            <template #trigger>
              <n-button size="tiny" type="error">🗑</n-button>
            </template>
            确定删除 "{{ inst.name }}"？
          </n-popconfirm>
        </div>
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
      <div v-if="connectionError" class="connection-error">
        <span>{{ connectionError }}</span>
        <n-button size="tiny" @click="handleReconnect">重连</n-button>
      </div>
    </div>

    <!-- 添加实例弹窗 -->
    <n-modal v-model:show="showAddModal" preset="card" title="添加实例" style="width: 400px">
      <n-form :model="newInstance" label-placement="top">
        <n-form-item label="名称" required>
          <n-input v-model:value="newInstance.name" placeholder="实例名称" />
        </n-form-item>
        <n-form-item label="URL" required>
          <n-input v-model:value="newInstance.url" placeholder="http://localhost:8080" />
        </n-form-item>
        <n-form-item label="Token">
          <n-input v-model:value="newInstance.token" type="password" placeholder="认证Token" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="newInstance.description" type="textarea" placeholder="描述" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="showAddModal = false">取消</n-button>
        <n-button type="primary" @click="addInstance" :loading="saving">添加</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { NEmpty, NButton, NTag, NPopconfirm, NModal, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import OverviewPanel from '../components/OverviewPanel.vue'
import SessionListPanel from '../components/SessionListPanel.vue'
import AgentListPanel from '../components/AgentListPanel.vue'
import ActivityPanel from '../components/ActivityPanel.vue'
import { instanceApi, connectSse as createSse } from '../api/index.js'

const message = useMessage()
const instances = ref([])
const instanceStatuses = ref({})  // 存储每个实例的在线状态
const selectedId = ref(null)
const selectedInstance = ref(null)
const statusData = ref(null)
const error = ref(null)
const connectionError = ref(null)
const loading = ref(false)
const showAddModal = ref(false)
const saving = ref(false)

const newInstance = ref({
  name: '',
  url: '',
  token: '',
  description: ''
})

let eventSource = null

// 计算每个实例的在线状态
const instancesWithStatus = computed(() => {
  return instances.value.map(inst => ({
    ...inst,
    online: instanceStatuses.value[inst.id] || false
  }))
})

async function loadInstances() {
  loading.value = true
  try {
    const data = await instanceApi.list()
    instances.value = Array.isArray(data) ? data : (data.result?.details || [])
    // 检查每个实例的状态
    instances.value.forEach(inst => checkInstanceStatus(inst.id))
  } catch (e) {
    console.error('Load instances error:', e)
  } finally {
    loading.value = false
  }
}

async function checkInstanceStatus(id) {
  try {
    const status = await instanceApi.getStatus(id)
    instanceStatuses.value[id] = status.ok === true
  } catch (e) {
    instanceStatuses.value[id] = false
  }
}

function selectInstance(inst) {
  if (selectedId.value === inst.id) return
  selectedId.value = inst.id
  selectedInstance.value = inst
  statusData.value = null
  connectionError.value = null
  openSse(inst.id)
}

function openSse(instanceId) {
  if (!instanceId) return

  // Close existing connection
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }

  connectionError.value = null

  eventSource = createSse(
    instanceId,
    (data) => {
      statusData.value = data
      connectionError.value = null
      // 更新在线状态
      instanceStatuses.value[instanceId] = true
      console.log('[MonitorView] SSE data received:', JSON.stringify(data, null, 2))
    },
    (e) => {
      connectionError.value = '连接中断'
      instanceStatuses.value[instanceId] = false
    }
  )
}

function handleReconnect() {
  connectionError.value = null
  if (selectedId.value) {
    openSse(selectedId.value)
  }
}

async function addInstance() {
  if (!newInstance.value.name || !newInstance.value.url) {
    message.warning('请填写名称和URL')
    return
  }
  saving.value = true
  try {
    await instanceApi.create(newInstance.value)
    message.success('添加成功')
    showAddModal.value = false
    newInstance.value = { name: '', url: '', token: '', description: '' }
    await loadInstances()
  } catch (e) {
    message.error('添加失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function deleteInstance(id) {
  try {
    await instanceApi.delete(id)
    message.success('删除成功')
    if (selectedId.value === id) {
      selectedId.value = null
      selectedInstance.value = null
      statusData.value = null
    }
    await loadInstances()
  } catch (e) {
    message.error('删除失败: ' + (e.response?.data?.message || e.message))
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
  width: 280px;
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
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.instance-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
  border: 2px solid transparent;
  transition: all 0.2s;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.instance-item:hover {
  background: #f9fafb;
}

.instance-item.selected {
  background: #eef2ff;
  border-color: #6366f1;
}

.instance-info {
  flex: 1;
  min-width: 0;
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

.instance-actions {
  display: flex;
  gap: 4px;
  align-items: center;
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
