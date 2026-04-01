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

        <n-tab-pane v-if="!form.localRuntime" name="monitor" tab="状态监控">
          <StatusMonitorPanel
            :workerId="worker?.id"
            :gatewayUrl="form.gatewayUrl"
          />
        </n-tab-pane>

        <n-tab-pane v-if="!form.localRuntime" name="test" tab="测试">
          <div class="test-panel">
            <n-button type="primary" @click="showTestDialog = true" :disabled="!worker?.id">
              发送测试消息
            </n-button>
            <p class="test-hint">点击按钮向该数字员工发送测试消息</p>
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

      <!-- 测试对话框 -->
      <n-modal v-model:show="showTestDialog" preset="card" title="发送测试消息" style="width: 500px;">
        <n-form-item label="Session">
          <n-select
            v-model:value="testSessionKey"
            :options="sessionOptions"
            placeholder="选择或输入 Session"
            filterable
            allow-input
          />
        </n-form-item>
        <n-form-item label="消息内容">
          <n-input
            v-model:value="testMessage"
            type="textarea"
            placeholder="输入要执行的任务..."
            :rows="4"
          />
        </n-form-item>
        <template #footer>
          <div style="display: flex; justify-content: flex-end; gap: 8px;">
            <n-button @click="showTestDialog = false">取消</n-button>
            <n-button type="primary" @click="handleSendTest" :loading="sendingTest" :disabled="!testMessage.trim()">
              发送
            </n-button>
          </div>
        </template>
      </n-modal>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed, onMounted } from 'vue'
import { NTabs, NTabPane, NForm, NFormItem, NInput, NSelect, NSwitch, NButton, NEmpty, NSpin, NTag, NAlert, NModal, NCard, useMessage, useDialog } from 'naive-ui'
import { workerApi } from '../api/workerApi.js'
import { skillApi } from '../api/skillApi.js'
import { llmModelApi } from '../api/index.js'
import { createGatewayApi } from '../api/gatewayApi.js'
import StatusMonitorPanel from './StatusMonitorPanel.vue'

const props = defineProps({
  worker: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  isAdding: { type: Boolean, default: false }
})

const emit = defineEmits(['saved', 'deleted'])

defineExpose({
  handleSave,
  handleDelete
})

const message = useMessage()
const dialog = useDialog()
const saving = ref(false)  // kept for potential future use
const allSkills = ref([])
const selectedSkillIds = ref([])
const allModels = ref([])
const modelOptions = ref([])

// 测试对话框相关
const showTestDialog = ref(false)
const testMessage = ref('')
const testSessionKey = ref('agent:main:main')
const sendingTest = ref(false)
const sessionOptions = ref([
  { label: 'agent:main:main', value: 'agent:main:main' },
  { label: 'agent:main:feishu:direct:*', value: 'agent:main:feishu:direct:ou_efaa3cd689b87d6f7fcf1940b0a6488e' }
])

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

async function handleSendTest() {
  if (!testMessage.value.trim()) {
    message.warning('请输入消息内容')
    return
  }
  if (!testSessionKey.value) {
    message.warning('请选择 Session')
    return
  }
  sendingTest.value = true
  try {
    const gateway = createGatewayApi(props.worker.id)
    const result = await gateway.sessionsSend(testSessionKey.value, testMessage.value)
    console.log('[Test] Send result:', result)
    if (result.ok) {
      message.success('消息已发送')
      showTestDialog.value = false
      testMessage.value = ''
    } else {
      message.error('发送失败: ' + (result.error?.message || '未知错误'))
    }
  } catch (e) {
    console.error('[Test] Send error:', e)
    message.error('发送失败: ' + e.message)
  } finally {
    sendingTest.value = false
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
.test-panel {
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}
.test-hint {
  font-size: 12px;
  color: #888;
  margin: 0;
}
</style>
