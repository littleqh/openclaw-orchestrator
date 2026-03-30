<template>
  <div class="manager">
    <div class="toolbar">
      <n-button type="primary" @click="showAdd = true">➕ 添加模型</n-button>
      <n-button @click="loadModels" :loading="loading">🔄 刷新</n-button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="models.length > 0">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>Provider</th>
            <th>模型</th>
            <th>API格式</th>
            <th>Base URL</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="model in models" :key="model.id">
            <td>{{ model.id }}</td>
            <td>{{ model.name }}</td>
            <td><n-tag size="small">{{ model.provider }}</n-tag></td>
            <td>{{ model.model }}</td>
            <td><n-tag size="small" type="info">{{ model.apiFormat || 'openai' }}</n-tag></td>
            <td class="url-cell">{{ model.baseUrl || '-' }}</td>
            <td>
              <n-tag :type="model.enabled ? 'success' : 'default'" size="small">
                {{ model.enabled ? '启用' : '禁用' }}
              </n-tag>
            </td>
            <td>{{ new Date(model.createdAt).toLocaleString('zh-CN') }}</td>
            <td>
              <div class="action-buttons">
                <n-button size="tiny" type="info" @click="handleTest(model.id)" :loading="testing === model.id">
                  测试
                </n-button>
                <n-button size="tiny" type="warning" @click="handleEdit(model)">编辑</n-button>
                <n-button size="tiny" type="error" @click="handleDelete(model.id)">删除</n-button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <n-empty v-else description="暂无模型" />

    <!-- 添加/编辑弹窗 -->
    <n-modal v-model:show="showAdd" preset="card" :title="editingModel ? '编辑模型' : '添加模型'" style="width: 550px">
      <n-form :model="form" label-placement="top">
        <n-form-item label="模型名称">
          <n-input v-model:value="form.name" placeholder="例如：Ollama-Llama3" />
        </n-form-item>
        <n-form-item label="Provider">
          <n-select v-model:value="form.provider" :options="providerOptions" placeholder="选择 Provider" />
        </n-form-item>
        <n-form-item label="模型">
          <n-input v-model:value="form.model" placeholder="例如：llama3.1" />
        </n-form-item>
        <n-form-item label="Base URL">
          <n-input v-model:value="form.baseUrl" placeholder="例如：http://localhost:11434" />
        </n-form-item>
        <n-form-item label="API Key (可选)">
          <n-input v-model:value="form.apiKey" type="password" placeholder="API Key (如有需要)" />
        </n-form-item>
        <n-form-item label="最大 Tokens">
          <n-input-number v-model:value="form.maxTokens" :min="100" :max="200000" placeholder="最大输出 tokens" style="width: 100%" />
        </n-form-item>
        <n-form-item label="API 格式">
          <n-select v-model:value="form.apiFormat" :options="apiFormatOptions" placeholder="选择 API 格式" />
        </n-form-item>
        <n-form-item label="启用">
          <n-switch v-model:value="form.enabled" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <n-button @click="closeAddModal">取消</n-button>
          <n-button type="primary" @click="handleSave" :loading="saving">{{ editingModel ? '保存' : '确认添加' }}</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 测试结果弹窗 -->
    <n-modal v-model:show="showTestResult" preset="card" title="连接测试结果" style="width: 400px">
      <div v-if="testResult">
        <n-result v-if="testResult.ok" status="success" title="连接成功">
          <template #footer>
            <div>延迟: {{ testResult.latencyMs }}ms</div>
          </template>
        </n-result>
        <n-result v-else status="error" title="连接失败">
          <template #footer>
            <div style="word-break:break-all">{{ testResult.error }}</div>
          </template>
        </n-result>
      </div>
      <div v-else class="loading">测试中...</div>
    </n-modal>

    <!-- 确认删除弹窗 -->
    <n-modal v-model:show="showConfirm" preset="card" title="确认删除" style="width: 450px">
      <div v-if="deleteError">
        <n-alert type="error">{{ deleteError }}</n-alert>
      </div>
      <div v-else>
        确定要删除该模型吗？
      </div>
      <template #footer>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <n-button @click="showConfirm = false">取消</n-button>
          <n-button type="error" @click="confirmDelete" :loading="deleting" :disabled="!!deleteError">确认删除</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { NButton, NEmpty, NModal, NForm, NFormItem, NInput, NTag, NSwitch, NSelect, NInputNumber, NAlert, NResult, useMessage } from 'naive-ui'
import { llmModelApi } from '../api/index.js'

const models = ref([])
const showAdd = ref(false)
const showConfirm = ref(false)
const showTestResult = ref(false)
const saving = ref(false)
const deleting = ref(false)
const loading = ref(false)
const testing = ref(null)
const editingModel = ref(null)
const testResult = ref(null)
const deleteError = ref(null)
const message = useMessage()

const providerOptions = [
  { label: 'Ollama', value: 'ollama' },
  { label: 'vLLM', value: 'vllm' },
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
  { label: 'DeepSeek', value: 'deepseek' },
  { label: 'MiniMax', value: 'minimax' },
  { label: 'Custom (OpenAI兼容)', value: 'custom' }
]

const apiFormatOptions = [
  { label: 'OpenAI 格式', value: 'openai' },
  { label: 'Anthropic 格式 (/v1)', value: 'anthropic' }
]

const form = reactive({
  name: '',
  provider: 'ollama',
  model: '',
  baseUrl: '',
  apiKey: '',
  maxTokens: 4096,
  enabled: true,
  apiFormat: 'openai'
})

async function loadModels() {
  loading.value = true
  try {
    const data = await llmModelApi.list()
    models.value = Array.isArray(data) ? data : []
  } catch (e) {
    message.error('加载模型失败')
  } finally {
    loading.value = false
  }
}

function openAddModal(model = null) {
  if (model) {
    editingModel.value = model
    Object.assign(form, {
      name: model.name,
      provider: model.provider,
      model: model.model,
      baseUrl: model.baseUrl || '',
      apiKey: '',
      maxTokens: model.maxTokens || 4096,
      enabled: model.enabled,
      apiFormat: model.apiFormat || 'openai'
    })
  } else {
    editingModel.value = null
    Object.assign(form, {
      name: '',
      provider: 'ollama',
      model: '',
      baseUrl: '',
      apiKey: '',
      maxTokens: 4096,
      enabled: true,
      apiFormat: 'openai'
    })
  }
  showAdd.value = true
}

function closeAddModal() {
  showAdd.value = false
  editingModel.value = null
}

function handleEdit(model) {
  openAddModal(model)
}

async function handleSave() {
  if (!form.name || !form.model) {
    message.warning('请填写名称和模型')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (!payload.apiKey) {
      delete payload.apiKey
    }
    if (editingModel.value) {
      await llmModelApi.update(editingModel.value.id, payload)
      message.success('更新成功')
    } else {
      await llmModelApi.create(payload)
      message.success('添加成功')
    }
    closeAddModal()
    await loadModels()
  } catch (e) {
    message.error('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleTest(id) {
  testing.value = id
  testResult.value = null
  showTestResult.value = true
  try {
    testResult.value = await llmModelApi.test(id)
  } catch (e) {
    testResult.value = { ok: false, error: e.message }
  } finally {
    testing.value = null
  }
}

async function handleDelete(id) {
  deleteError.value = null
  showConfirm.value = true
  pendingDeleteId.value = id
}

const pendingDeleteId = ref(null)

async function confirmDelete() {
  deleting.value = true
  try {
    await llmModelApi.delete(pendingDeleteId.value)
    message.success('删除成功')
    showConfirm.value = false
    await loadModels()
  } catch (e) {
    deleteError.value = e.response?.data?.message || '删除失败'
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  loadModels()
})
</script>

<style scoped>
.manager {
  padding: 24px;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}
.loading {
  padding: 20px;
  text-align: center;
  color: #666;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.data-table th,
.data-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #eee;
}
.data-table th {
  background: #f5f5f5;
  font-weight: 600;
  color: #333;
}
.data-table tr:hover {
  background: #fafafa;
}
.url-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: monospace;
  font-size: 12px;
}
.action-buttons {
  display: flex;
  gap: 4px;
}
</style>
