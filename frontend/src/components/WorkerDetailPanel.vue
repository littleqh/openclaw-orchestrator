<template>
  <div class="worker-detail-panel">
    <div v-if="!isAdding && !worker && !loading" class="no-selection">
      <n-empty description="请选择一名员工或新增员工" />
    </div>

    <div v-else-if="loading" class="loading">
      <n-spin size="large" />
    </div>

    <div v-else-if="isAdding || worker" class="detail-content">
      <n-tabs type="line">
        <n-tab-pane name="basic" tab="基本信息">
          <n-form label-placement="top" class="form">
            <n-form-item label="姓名" required>
              <n-input v-model:value="form.name" placeholder="请输入姓名" />
            </n-form-item>
            <n-form-item label="昵称">
              <n-input v-model:value="form.nickname" placeholder="请输入昵称" />
            </n-form-item>
            <n-form-item label="角色">
              <n-input v-model:value="form.role" placeholder="如：开发助手、客服" />
            </n-form-item>
            <n-form-item label="运行模式">
              <n-switch v-model:value="form.localRuntime">
                <template #checked>本地 Agent</template>
                <template #unchecked>OpenClaw Worker</template>
              </n-switch>
              <div style="font-size: 12px; color: #888; margin-top: 4px">
                {{ form.localRuntime ? '在进程内运行，可聊天' : '连接远程 Gateway' }}
              </div>
            </n-form-item>
            <n-form-item v-if="form.localRuntime" label="选择模型" required>
              <n-select
                v-model:value="form.modelId"
                :options="modelOptions"
                placeholder="请选择模型"
                filterable
              />
            </n-form-item>
            <n-form-item v-if="!form.localRuntime" label="Gateway 地址">
              <n-input v-model:value="form.gatewayUrl" placeholder="http://127.0.0.1:18789" />
            </n-form-item>
            <n-form-item v-if="!form.localRuntime" label="密钥">
              <n-input v-model:value="form.gatewayToken" type="password" placeholder="Token" />
            </n-form-item>
            <n-form-item label="人格描述">
              <n-input v-model:value="form.personality" type="textarea" placeholder="描述员工的人格特点..." :rows="4" />
            </n-form-item>
            <n-form-item v-if="form.localRuntime" label="系统提示">
              <n-input v-model:value="form.systemPrompt" type="textarea" placeholder="自定义系统提示词..." :rows="3" />
            </n-form-item>
            <n-form-item label="头像 URL">
              <n-input v-model:value="form.avatar" placeholder="https://example.com/avatar.png" />
            </n-form-item>
            <n-form-item label="状态">
              <n-select v-model:value="form.status" :options="statusOptions" />
            </n-form-item>
          </n-form>
        </n-tab-pane>

        <n-tab-pane v-if="!form.localRuntime" name="connection" tab="连接管理">
          <div class="connection-panel">
            <div class="connection-status">
              <n-tag :type="connectStatusType" size="medium">
                {{ connectStatusText }}
              </n-tag>
            </div>

            <div class="connection-logs" v-if="connectLogs.length > 0">
              <div class="logs-title">连接日志:</div>
              <div class="logs-list">
                <div v-for="(log, index) in connectLogs" :key="index" class="log-item">
                  {{ log }}
                </div>
              </div>
            </div>

            <div class="connection-actions">
              <n-button
                v-if="connectStatus !== 'connecting'"
                type="primary"
                @click="handleConnect"
                :loading="connecting"
                :disabled="!form.gatewayUrl"
              >
                {{ connectStatus === 'connected' ? '重新连接' : '连接' }}
              </n-button>
              <n-button
                v-if="connectStatus === 'connecting'"
                type="warning"
                @click="handleCancelConnect"
              >
                取消
              </n-button>
              <n-button
                v-if="connectStatus === 'connected'"
                type="default"
                @click="handleDisconnect"
              >
                断开
              </n-button>
            </div>

            <div v-if="connectError" class="connection-error">
              <n-alert type="error" :title="connectError" />
            </div>

            <div v-if="connectStatus === 'pairing_required'" class="pairing-hint">
              <n-alert type="warning" title="需要配对">
                <template #icon>
                  <span>⚠️</span>
                </template>
                <div>
                  设备未配对，请到 OpenClaw Gateway 管理界面手动配对该设备。
                </div>
              </n-alert>
            </div>
          </div>
        </n-tab-pane>

        <n-tab-pane name="skills" tab="常用技能">
          <div class="skills-grid">
            <div
              v-for="skill in allSkills"
              :key="skill.id"
              class="skill-item"
              :class="{ selected: isSkillSelected(skill.id) }"
              @click="toggleSkill(skill.id)"
            >
              <span class="skill-name">{{ skill.name }}</span>
              <span class="skill-desc">{{ skill.description }}</span>
              <span v-if="isSkillSelected(skill.id)" class="skill-check">✓</span>
            </div>
          </div>
        </n-tab-pane>
      </n-tabs>

      <div class="action-bar">
        <n-button type="error" @click="handleDelete" :loading="saving">删除</n-button>
        <n-button type="primary" @click="handleSave" :loading="saving">保存</n-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted } from 'vue'
import { NTabs, NTabPane, NForm, NFormItem, NInput, NSelect, NSwitch, NButton, NEmpty, NSpin, NTag, NAlert, useMessage } from 'naive-ui'
import { workerApi } from '../api/workerApi.js'
import { skillApi } from '../api/skillApi.js'
import { llmModelApi } from '../api/index.js'

const props = defineProps({
  worker: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  isAdding: { type: Boolean, default: false }
})

const emit = defineEmits(['saved', 'deleted'])

const message = useMessage()
const saving = ref(false)
const allSkills = ref([])
const selectedSkillIds = ref([])
const allModels = ref([])
const modelOptions = ref([])

// Connection state
const connecting = ref(false)
const connectStatus = ref('disconnected')  // disconnected, connecting, connected, pairing_required, error
const connectLogs = ref([])
const connectError = ref('')
let connectCancelToken = null

const form = ref({
  name: '',
  nickname: '',
  role: '',
  gatewayUrl: '',
  gatewayToken: '',
  personality: '',
  avatar: '',
  status: 'OFFLINE',
  localRuntime: false,
  modelId: null,
  systemPrompt: ''
})

const statusOptions = [
  { label: '在线', value: 'ONLINE' },
  { label: '离线', value: 'OFFLINE' },
  { label: '忙碌', value: 'BUSY' }
]

watch(() => props.worker, (w) => {
  if (w) {
    form.value = {
      name: w.name || '',
      nickname: w.nickname || '',
      role: w.role || '',
      gatewayUrl: w.gatewayUrl || '',
      gatewayToken: w.gatewayToken || '',
      personality: w.personality || '',
      avatar: w.avatar || '',
      status: w.status || 'OFFLINE',
      localRuntime: w.localRuntime || false,
      modelId: w.model?.id || null,
      systemPrompt: w.systemPrompt || ''
    }
    selectedSkillIds.value = w.skills?.map(s => s.id) || []
  } else {
    resetForm()
  }
}, { immediate: true })

watch(() => form.value.localRuntime, async (isLocal) => {
  if (isLocal) {
    await loadModels()
  }
})

// Connection handlers
const connectStatusType = computed(() => {
  switch (connectStatus.value) {
    case 'connected': return 'success'
    case 'connecting': return 'warning'
    case 'pairing_required': return 'warning'
    case 'error': return 'error'
    default: return 'default'
  }
})

const connectStatusText = computed(() => {
  switch (connectStatus.value) {
    case 'connected': return '已连接'
    case 'connecting': return '连接中...'
    case 'pairing_required': return '需要配对'
    case 'error': return '连接失败'
    default: return '未连接'
  }
})

async function handleConnect() {
  if (!props.worker?.id) return
  connecting.value = true
  connectStatus.value = 'connecting'
  connectLogs.value = []
  connectError.value = ''

  try {
    const result = await workerApi.connect(props.worker.id)
    connectLogs.value = result.logs || []
    connectStatus.value = result.status
    if (result.status === 'error' || result.status === 'pairing_required') {
      connectError.value = result.message
    }
    if (result.status === 'connected') {
      message.success('连接成功')
    }
  } catch (e) {
    connectStatus.value = 'error'
    connectError.value = e.response?.data?.message || e.message || '连接失败'
    connectLogs.value.push('连接失败: ' + connectError.value)
    message.error('连接失败')
  } finally {
    connecting.value = false
  }
}

function handleDisconnect() {
  connectStatus.value = 'disconnected'
  connectLogs.value = []
  message.info('已断开连接')
}

function handleCancelConnect() {
  // For now, just reset status - actual cancellation would need abort support
  connectStatus.value = 'disconnected'
  connectLogs.value.push('连接已取消')
}

function resetForm() {
  form.value = {
    name: '',
    nickname: '',
    role: '',
    gatewayUrl: '',
    gatewayToken: '',
    personality: '',
    avatar: '',
    status: 'OFFLINE',
    localRuntime: false,
    modelId: null,
    systemPrompt: ''
  }
  selectedSkillIds.value = []
}

function isSkillSelected(skillId) {
  return selectedSkillIds.value.includes(skillId)
}

function toggleSkill(skillId) {
  const idx = selectedSkillIds.value.indexOf(skillId)
  if (idx >= 0) {
    selectedSkillIds.value.splice(idx, 1)
  } else {
    selectedSkillIds.value.push(skillId)
  }
}

async function handleSave() {
  if (!form.value.name) {
    message.warning('请输入姓名')
    return
  }
  if (form.value.localRuntime && !form.value.modelId) {
    message.warning('本地 Agent 必须选择模型')
    return
  }
  saving.value = true
  try {
    const data = {
      name: form.value.name,
      nickname: form.value.nickname,
      role: form.value.role,
      gatewayUrl: form.value.gatewayUrl,
      gatewayToken: form.value.gatewayToken,
      personality: form.value.personality,
      avatar: form.value.avatar,
      status: form.value.status,
      localRuntime: form.value.localRuntime,
      modelId: form.value.localRuntime ? form.value.modelId : null,
      systemPrompt: form.value.systemPrompt,
      skillIds: selectedSkillIds.value
    }
    if (props.worker?.id) {
      await workerApi.update(props.worker.id, data)
    } else {
      await workerApi.create(data)
    }
    message.success('保存成功')
    emit('saved')
  } catch (e) {
    message.error('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  if (!props.worker?.id) return
  if (!confirm('确定删除该员工？')) return
  saving.value = true
  try {
    await workerApi.delete(props.worker.id)
    message.success('删除成功')
    emit('deleted')
  } catch (e) {
    message.error('删除失败')
  } finally {
    saving.value = false
  }
}

async function loadSkills() {
  try {
    allSkills.value = await skillApi.list()
  } catch (e) {
    console.error('Load skills error:', e)
  }
}

async function loadModels() {
  try {
    allModels.value = await llmModelApi.listEnabled()
    modelOptions.value = allModels.value.map(m => ({
      label: `${m.name} (${m.provider}/${m.model})`,
      value: m.id
    }))
  } catch (e) {
    console.error('Load models error:', e)
  }
}

onMounted(() => {
  loadSkills()
  loadModels()
})
</script>

<style scoped>
.worker-detail-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.no-selection, .loading {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.form {
  padding: 16px;
  overflow-y: auto;
  max-height: calc(100vh - 280px);
}
.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  padding: 16px;
  overflow-y: auto;
  max-height: calc(100vh - 280px);
}
.skill-item {
  padding: 12px;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}
.skill-item:hover {
  border-color: #6366f1;
}
.skill-item.selected {
  border-color: #6366f1;
  background: #eef2ff;
}
.skill-name {
  font-weight: 600;
  display: block;
}
.skill-desc {
  font-size: 12px;
  color: #888;
  display: block;
  margin-top: 4px;
}
.skill-check {
  position: absolute;
  top: 8px;
  right: 8px;
  color: #6366f1;
  font-weight: bold;
}
.action-bar {
  padding: 16px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
}
.connection-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.connection-status {
  display: flex;
  align-items: center;
  gap: 12px;
}
.connection-logs {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 12px;
  max-height: 200px;
  overflow-y: auto;
}
.logs-title {
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
  font-weight: 500;
}
.logs-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.log-item {
  font-size: 12px;
  font-family: monospace;
  color: #333;
  padding: 2px 0;
}
.connection-actions {
  display: flex;
  gap: 8px;
}
.connection-error {
  margin-top: 8px;
}
.pairing-hint {
  margin-top: 8px;
}
</style>
