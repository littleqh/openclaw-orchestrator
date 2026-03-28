<template>
  <div class="token-management">
    <n-tabs type="line" animated>
      <n-tab-pane name="system" tab="系统令牌">
        <div class="token-section">
          <n-card v-if="systemToken" title="系统令牌">
            <n-descriptions :column="2">
              <n-descriptions-item label="令牌预览">{{ systemToken.tokenPreview }}</n-descriptions-item>
              <n-descriptions-item label="创建时间">{{ formatDate(systemToken.createdAt) }}</n-descriptions-item>
              <n-descriptions-item label="最后访问">{{ formatDate(systemToken.lastAccessAt) }}</n-descriptions-item>
            </n-descriptions>
            <template #action>
              <n-space>
                <n-button size="small" @click="copyToken(systemToken)">复制令牌</n-button>
                <n-button size="small" type="warning" @click="handleReset(systemToken)">重置令牌</n-button>
              </n-space>
            </template>
          </n-card>
          <n-card v-else title="系统令牌">
            <n-empty description="暂无系统令牌">
              <template #extra>
                <n-button type="primary" @click="createSystemToken">创建系统令牌</n-button>
              </template>
            </n-empty>
          </n-card>
        </div>
      </n-tab-pane>

      <n-tab-pane name="agent" tab="Agent 令牌">
        <div class="token-section">
          <n-card title="Agent 令牌列表">
            <template #action>
              <n-button type="primary" size="small" @click="showCreateModal = true">创建令牌</n-button>
            </template>
            <n-table :columns="columns" :data="agentTokens" :pagination="false" striped>
              <template #tokenPreview="{ row }">
                {{ row.tokenPreview || '—' }}
              </template>
              <template #actions="{ row }">
                <n-space>
                  <n-button size="tiny" @click="copyToken(row)">复制</n-button>
                  <n-button size="tiny" type="warning" @click="handleReset(row)">重置</n-button>
                  <n-button size="tiny" type="error" @click="handleDelete(row)">删除</n-button>
                </n-space>
              </template>
            </n-table>
            <n-empty v-if="agentTokens.length === 0" description="暂无 Agent 令牌" />
          </n-card>
        </div>
      </n-tab-pane>
    </n-tabs>

    <!-- Create Token Modal -->
    <n-modal v-model:show="showCreateModal" preset="card" title="创建 Agent 令牌" style="width: 400px">
      <n-form>
        <n-form-item label="选择 Worker">
          <n-select v-model:value="selectedWorkerId" :options="workerOptions" placeholder="请选择 Worker" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-space>
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" @click="handleCreateAgentToken" :loading="loading">创建</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { NCard, NTabs, NTabPane, NButton, NSpace, NTable, NEmpty, NModal, NForm, NFormItem, NSelect, NDescriptions, NDescriptionsItem } from 'naive-ui'
import { tokenApi } from '../api/tokenApi'
import { workerApi } from '../api/workerApi'

const message = useMessage()
const dialog = useDialog()

const tokens = ref([])
const workers = ref([])
const showCreateModal = ref(false)
const selectedWorkerId = ref(null)
const loading = ref(false)

const systemToken = computed(() => tokens.value.find(t => t.type === 'SYSTEM'))
const agentTokens = computed(() => tokens.value.filter(t => t.type === 'AGENT'))

const workerOptions = computed(() =>
  workers.value
    .filter(w => !tokens.value.some(t => t.workerId === w.id))
    .map(w => ({ label: w.name, value: w.id }))
)

const columns = [
  { title: 'Worker', key: 'workerName' },
  { title: '令牌预览', key: 'tokenPreview' },
  { title: '创建时间', key: 'createdAt', render: (row) => formatDate(row.createdAt) },
  { title: '操作', key: 'actions', slot: 'actions' }
]

const loadData = async () => {
  try {
    const [tokenData, workerData] = await Promise.all([
      tokenApi.getAll(),
      workerApi.list()
    ])
    tokens.value = tokenData
    workers.value = workerData
  } catch (error) {
    message.error('加载数据失败')
  }
}

const createSystemToken = async () => {
  loading.value = true
  try {
    await tokenApi.create(null)
    message.success('系统令牌创建成功')
    await loadData()
  } catch (error) {
    message.error(error.response?.data?.message || '创建失败')
  } finally {
    loading.value = false
  }
}

const handleCreateAgentToken = async () => {
  if (!selectedWorkerId.value) {
    message.warning('请选择 Worker')
    return
  }
  loading.value = true
  try {
    await tokenApi.create(selectedWorkerId.value)
    message.success('Agent 令牌创建成功')
    showCreateModal.value = false
    selectedWorkerId.value = null
    await loadData()
  } catch (error) {
    message.error(error.response?.data?.message || '创建失败')
  } finally {
    loading.value = false
  }
}

const handleReset = async (token) => {
  dialog.warning({
    title: '确认重置',
    content: '重置后旧令牌将失效，确定要重置吗？',
    positiveText: '确定',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await tokenApi.reset(token.id)
        message.success('令牌已重置')
        await loadData()
      } catch (error) {
        message.error(error.response?.data?.message || '重置失败')
      }
    }
  })
}

const handleDelete = async (token) => {
  dialog.warning({
    title: '确认删除',
    content: '删除后无法恢复，确定要删除吗？',
    positiveText: '确定',
    negativeText: '取消',
    async onPositiveClick() {
      try {
        await tokenApi.delete(token.id)
        message.success('令牌已删除')
        await loadData()
      } catch (error) {
        message.error(error.response?.data?.message || '删除失败')
      }
    }
  })
}

const copyToken = async (token) => {
  try {
    const fullToken = await tokenApi.getById(token.id)
    if (fullToken.token) {
      await navigator.clipboard.writeText(fullToken.token)
      message.success('令牌已复制到剪贴板')
    } else {
      message.warning('无法复制完整令牌，请重置后复制')
    }
  } catch (error) {
    message.error('复制失败')
  }
}

const formatDate = (date) => {
  if (!date) return '—'
  return new Date(date).toLocaleString('zh-CN')
}

onMounted(loadData)
</script>

<style scoped>
.token-management {
  padding: 24px;
}
.token-section {
  margin-top: 16px;
}
</style>