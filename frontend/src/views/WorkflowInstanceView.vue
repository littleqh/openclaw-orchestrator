<template>
  <div class="instance-view">
    <div class="toolbar">
      <n-button type="primary" @click="showCreate = true">➕ 创建任务</n-button>
      <n-button @click="loadInstances" :loading="loading">🔄 刷新</n-button>
      <n-select v-model:value="filterStatus" :options="statusOptions" placeholder="筛选状态" clearable style="width: 150px; margin-left: 8px" />
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="instances.length > 0">
      <n-data-table :columns="columns" :data="filteredInstances" :bordered="false" />
    </div>

    <n-empty v-else description="暂无任务实例" />

    <!-- 创建任务弹窗 -->
    <n-modal v-model:show="showCreate" preset="card" title="创建任务" style="width: 500px">
      <n-form :model="createForm" label-placement="top">
        <n-form-item label="选择模板" required>
          <n-select v-model:value="createForm.workflowId" :options="workflowOptions" placeholder="选择任务模板" />
        </n-form-item>
        <n-form-item label="任务描述" required>
          <n-input v-model:value="createForm.description" type="textarea" placeholder="请输入自然语言任务描述..." :rows="4" />
        </n-form-item>
        <n-form-item label="变量 (JSON格式)">
          <n-input v-model:value="createForm.variables" type="textarea" placeholder='{"key": "value"}' />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <n-button @click="showCreate = false">取消</n-button>
          <n-button type="primary" @click="handleCreate" :loading="saving">创建</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 任务详情弹窗 -->
    <n-modal v-model:show="showDetail" preset="card" :title="'任务详情 - ' + (selectedInstance?.workflowName || '')" style="width: 800px">
      <n-descriptions v-if="selectedInstance" :column="2" bordered>
        <n-descriptions-item label="ID">{{ selectedInstance.id }}</n-descriptions-item>
        <n-descriptions-item label="状态">
          <n-tag :type="statusType(selectedInstance.status)">{{ statusLabel(selectedInstance.status) }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="任务描述" :span="2">{{ selectedInstance.description || '-' }}</n-descriptions-item>
        <n-descriptions-item label="当前阶段">{{ selectedInstance.currentStageName || '-' }}</n-descriptions-item>
        <n-descriptions-item label="启动人">{{ selectedInstance.startedByName || selectedInstance.startedBy || '-' }}</n-descriptions-item>
        <n-descriptions-item label="启动时间">{{ formatDate(selectedInstance.startedAt) }}</n-descriptions-item>
      </n-descriptions>

      <n-tabs type="line" style="margin-top: 16px">
        <n-tab-pane name="tasks" tab="任务环节">
          <div v-if="instanceTasks.length > 0">
            <div class="task-list">
              <div v-for="task in instanceTasks" :key="task.id" class="task-item">
                <div class="task-info">
                  <div class="task-name">{{ task.stageName }}</div>
                  <div class="task-desc">{{ task.stageDescription || '-' }}</div>
                </div>
                <div class="task-meta">
                  <n-tag :type="taskStatusType(task.status)" size="small">{{ taskStatusLabel(task.status) }}</n-tag>
                  <div class="task-worker">
                    <span v-if="editingTaskId === task.id" class="worker-edit">
                      <n-select v-model:value="task.workerId" :options="workerOptions" size="small" placeholder="选择处理人" style="width: 120px" />
                      <n-button size="tiny" @click="saveTaskWorker(task)">保存</n-button>
                      <n-button size="tiny" @click="cancelEditWorker">取消</n-button>
                    </span>
                    <span v-else>
                      {{ task.workerName || '未指派' }}
                      <n-button v-if="canEditWorker(selectedInstance)" size="tiny" @click="editWorker(task)" style="margin-left: 4px">指派</n-button>
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="task-actions" v-if="selectedInstance">
              <n-button v-if="selectedInstance.status === 'CREATE'" type="primary" @click="handleCompleteAssign">完成指派</n-button>
              <n-button v-if="selectedInstance.status === 'PLANNED' || selectedInstance.status === 'READY'" type="primary" @click="handleStartTask">启动任务</n-button>
            </div>
          </div>
          <n-empty v-else description="暂无任务环节" />
        </n-tab-pane>
        <n-tab-pane name="logs" tab="日志">
          <div v-if="instanceLogs.length > 0" style="max-height: 300px; overflow-y: auto">
            <n-log :logs="instanceLogs.map(l => formatLog(l)).join('\n')" />
          </div>
          <n-empty v-else description="暂无日志" />
        </n-tab-pane>
      </n-tabs>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, h } from 'vue'
import { NButton, NEmpty, NModal, NForm, NFormItem, NInput, NSelect, NDataTable, useMessage, NTag, NDescriptions, NDescriptionsItem, NTabs, NTabPane, NLog } from 'naive-ui'
import { instanceApi } from '../api/instanceApi.js'
import { workflowApi } from '../api/workflowApi.js'
import { workerApi } from '../api/workerApi.js'

const instances = ref([])
const workflows = ref([])
const workers = ref([])
const showCreate = ref(false)
const showDetail = ref(false)
const selectedInstance = ref(null)
const instanceTasks = ref([])
const instanceLogs = ref([])
const saving = ref(false)
const loading = ref(false)
const filterStatus = ref(null)
const editingTaskId = ref(null)
const message = useMessage()

const createForm = reactive({
  workflowId: null,
  description: '',
  variables: ''
})

const statusOptions = [
  { label: '创建中', value: 'CREATE' },
  { label: '已指派', value: 'PLANNED' },
  { label: '就绪', value: 'READY' },
  { label: '执行中', value: 'RUNNING' },
  { label: '暂停', value: 'PAUSED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' },
  { label: '已终止', value: 'TERMINATED' }
]

const workflowOptions = computed(() => workflows.value
  .filter(w => w.isSystem === false || w.isSystem === undefined)
  .map(w => ({ label: w.name + (w.isSystem ? ' [系统]' : ''), value: w.id })))

const workerOptions = computed(() => workers.value.map(w => ({
  label: w.name + (w.nickname ? ` (${w.nickname})` : ''),
  value: w.id
})))

const filteredInstances = computed(() => {
  if (!filterStatus.value) return instances.value
  return instances.value.filter(i => i.status === filterStatus.value)
})

const statusType = (status) => {
  const map = {
    'CREATE': 'default',
    'PLANNED': 'info',
    'READY': 'info',
    'RUNNING': 'primary',
    'PENDING': 'default',
    'PROCESSING': 'warning',
    'PAUSED': 'warning',
    'COMPLETED': 'success',
    'FAILED': 'error',
    'TERMINATED': 'error'
  }
  return map[status] || 'default'
}

const statusLabel = (status) => {
  const map = {
    'CREATE': '创建中',
    'PLANNED': '已指派',
    'READY': '就绪',
    'RUNNING': '执行中',
    'PENDING': '等待',
    'PROCESSING': '处理中',
    'PAUSED': '暂停',
    'COMPLETED': '已完成',
    'FAILED': '失败',
    'TERMINATED': '已终止'
  }
  return map[status] || status
}

const taskStatusType = (status) => statusType(status)

const taskStatusLabel = (status) => statusLabel(status)

const formatDate = (date) => date ? new Date(date).toLocaleString('zh-CN') : '-'

const formatVariables = (vars) => {
  if (!vars) return '-'
  if (typeof vars === 'string') return vars
  return JSON.stringify(vars)
}

const formatLog = (log) => {
  return `[${formatDate(log.createdAt)}] ${log.action} ${log.oldStatus || ''} → ${log.newStatus} ${log.comment || ''}`
}

const canEditWorker = (instance) => {
  return instance && ['CREATE', 'PLANNED'].includes(instance.status)
}

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '任务描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '模板', key: 'workflowName' },
  { title: '状态', key: 'status', width: 100, render: (row) => h(NTag, { type: statusType(row.status) }, { default: () => statusLabel(row.status) }) },
  { title: '当前阶段', key: 'currentStageName', render: (row) => row.currentStageName || '-' },
  { title: '启动人', key: 'startedByName', render: (row) => row.startedByName || row.startedBy || '-' },
  { title: '启动时间', key: 'startedAt', width: 180, render: (row) => formatDate(row.startedAt) },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: (row) => [
      h(NButton, { size: 'tiny', onClick: () => viewDetail(row) }, { default: () => '详情' }),
      h(NButton, { size: 'tiny', onClick: () => handleAction(row.id, 'pause'), disabled: row.status !== 'RUNNING' }, { default: () => '暂停' }),
      h(NButton, { size: 'tiny', onClick: () => handleAction(row.id, 'resume'), disabled: row.status !== 'PAUSED' }, { default: () => '恢复' }),
      h(NButton, { size: 'tiny', type: 'error', onClick: () => handleTerminate(row.id), disabled: ['COMPLETED', 'FAILED', 'TERMINATED'].includes(row.status) }, { default: () => '终止' })
    ]
  }
]

async function loadInstances() {
  loading.value = true
  try {
    instances.value = await instanceApi.list()
  } catch (e) {
    message.error('加载任务失败')
  } finally {
    loading.value = false
  }
}

async function loadWorkflows() {
  try {
    workflows.value = await workflowApi.list()
  } catch (e) {
    console.error('加载模板失败', e)
  }
}

async function loadWorkers() {
  try {
    workers.value = await workerApi.list()
  } catch (e) {
    console.error('加载员工失败', e)
  }
}

async function viewDetail(inst) {
  selectedInstance.value = inst
  showDetail.value = true
  editingTaskId.value = null
  // Tasks should be included in the instance response, but fallback to API call
  instanceTasks.value = inst.tasks || []
  if (!inst.tasks || inst.tasks.length === 0) {
    try {
      instanceTasks.value = await instanceApi.getTasks(inst.id)
    } catch (e) {
      console.error('加载任务环节失败', e)
    }
  }
  try {
    instanceLogs.value = await instanceApi.getLogs(inst.id)
  } catch (e) {
    console.error('加载日志失败', e)
  }
}

async function handleCreate() {
  if (!createForm.workflowId || !createForm.description) {
    message.warning('请填写模板和任务描述')
    return
  }
  saving.value = true
  try {
    let variables = null
    if (createForm.variables) {
      try {
        variables = JSON.parse(createForm.variables)
      } catch {
        message.warning('变量必须是有效的JSON格式')
        saving.value = false
        return
      }
    }
    await instanceApi.create({
      workflowId: createForm.workflowId,
      description: createForm.description,
      variables
    })
    message.success('任务创建成功')
    showCreate.value = false
    createForm.workflowId = null
    createForm.description = ''
    createForm.variables = ''
    await loadInstances()
  } catch (e) {
    message.error('创建失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleCompleteAssign() {
  if (!selectedInstance.value) return
  try {
    const updated = await instanceApi.assign(selectedInstance.value.id)
    message.success('已完成指派')
    selectedInstance.value = updated
    instanceTasks.value = updated.tasks || instanceTasks.value
    await loadInstances()
  } catch (e) {
    message.error('完成指派失败')
  }
}

async function handleStartTask() {
  if (!selectedInstance.value) return
  try {
    const updated = await instanceApi.start(selectedInstance.value.id)
    message.success('任务已启动')
    selectedInstance.value = updated
    instanceTasks.value = updated.tasks || instanceTasks.value
    await loadInstances()
  } catch (e) {
    message.error('启动任务失败')
  }
}

function editWorker(task) {
  editingTaskId.value = task.id
}

function cancelEditWorker() {
  editingTaskId.value = null
}

async function saveTaskWorker(task) {
  if (!selectedInstance.value) return
  try {
    await instanceApi.updateTaskWorker(selectedInstance.value.id, task.id, task.workerId)
    message.success('已更新处理人')
    editingTaskId.value = null
    // Refresh tasks
    const updated = await instanceApi.get(selectedInstance.value.id)
    selectedInstance.value = updated
    instanceTasks.value = updated.tasks || []
  } catch (e) {
    message.error('更新处理人失败')
  }
}

async function handleAction(id, action) {
  try {
    if (action === 'pause') {
      await instanceApi.pause(id)
      message.success('已暂停')
    } else if (action === 'resume') {
      await instanceApi.resume(id)
      message.success('已恢复')
    }
    await loadInstances()
  } catch (e) {
    message.error('操作失败')
  }
}

async function handleTerminate(id) {
  try {
    await instanceApi.terminate(id, 'Admin terminated')
    message.success('已终止')
    await loadInstances()
  } catch (e) {
    message.error('终止失败')
  }
}

onMounted(() => {
  loadInstances()
  loadWorkflows()
  loadWorkers()
})
</script>

<style scoped>
.instance-view {
  padding: 24px;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  align-items: center;
}
.loading {
  padding: 20px;
  text-align: center;
  color: #666;
}
.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.task-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 8px;
}
.task-info {
  flex: 1;
}
.task-name {
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}
.task-desc {
  font-size: 12px;
  color: #666;
}
.task-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.task-worker {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #666;
}
.worker-edit {
  display: flex;
  align-items: center;
  gap: 4px;
}
.task-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
