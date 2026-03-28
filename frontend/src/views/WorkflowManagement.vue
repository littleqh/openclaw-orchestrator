<template>
  <div class="workflow-management">
    <div class="toolbar">
      <n-button type="primary" @click="openCreateModal">➕ 新建模板</n-button>
      <n-button @click="loadWorkflows" :loading="loading">🔄 刷新</n-button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="workflows.length > 0">
      <n-data-table :columns="columns" :data="workflows" :bordered="false" />
    </div>

    <n-empty v-else description="暂无任务模板" />

    <!-- 创建/编辑弹窗 -->
    <n-modal v-model:show="showCreate" preset="card" :title="editingWorkflow ? '编辑模板' : '新建模板'" style="width: 600px">
      <n-form :model="form" label-placement="top">
        <n-form-item label="模板名称" required>
          <n-input v-model:value="form.name" placeholder="请输入模板名称" :disabled="!!editingWorkflow?.isSystem" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" placeholder="请输入描述" :disabled="!!editingWorkflow?.isSystem" />
        </n-form-item>
        <n-form-item label="环节列表" v-if="editingWorkflow?.stages?.length">
          <div class="stage-list">
            <div v-for="(stage, idx) in editingWorkflow.stages" :key="stage.id" class="stage-item">
              <span class="stage-order">{{ idx + 1 }}</span>
              <span class="stage-name">{{ stage.name }}</span>
              <span class="stage-desc">{{ stage.description || '-' }}</span>
            </div>
          </div>
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <n-button @click="closeForm">取消</n-button>
          <n-button v-if="!editingWorkflow?.isSystem" type="primary" @click="handleSave" :loading="saving">保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NEmpty, NModal, NForm, NFormItem, NInput, NSelect, NDataTable, useMessage, NTag, NPopconfirm } from 'naive-ui'
import { workflowApi } from '../api/workflowApi.js'

const router = useRouter()

const workflows = ref([])
const showCreate = ref(false)
const editingWorkflow = ref(null)
const saving = ref(false)
const loading = ref(false)
const message = useMessage()

const form = reactive({
  name: '',
  description: ''
})

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '名称', key: 'name' },
  { title: '类型', key: 'isSystem', width: 100, render: (row) => row.isSystem ? h(NTag, { type: 'warning', size: 'small' }, { default: () => '系统' }) : h(NTag, { type: 'info', size: 'small' }, { default: () => '用户' }) },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '环节数', key: 'stageCount', width: 80, render: (row) => row.stages?.length || 0 },
  { title: '创建时间', key: 'createdAt', width: 180, render: (row) => new Date(row.createdAt).toLocaleString('zh-CN') },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: (row) => [
      !row.isSystem ? h(NButton, { size: 'tiny', onClick: () => openEditor(row) }, { default: () => '编排' }) : null,
      h(NButton, { size: 'tiny', onClick: () => viewWorkflow(row) }, { default: () => '查看' }),
      !row.isSystem ? h(NPopconfirm, {
        onPositiveClick: () => handleDelete(row.id)
      }, {
        trigger: h(NButton, { size: 'tiny', type: 'error' }, { default: () => '删除' }),
        default: () => '确认删除?'
      }) : null
    ]
  }
]

async function loadWorkflows() {
  loading.value = true
  try {
    workflows.value = await workflowApi.list()
  } catch (e) {
    message.error('加载模板失败')
  } finally {
    loading.value = false
  }
}

function openCreateModal() {
  editingWorkflow.value = null
  form.name = ''
  form.description = ''
  showCreate.value = true
}

function openEditor(wf) {
  router.push(`/workflows/${wf.id}/edit`)
}

function viewWorkflow(wf) {
  editingWorkflow.value = wf
  form.name = wf.name
  form.description = wf.description || ''
  showCreate.value = true
}

function closeForm() {
  showCreate.value = false
  editingWorkflow.value = null
  Object.assign(form, { name: '', description: '' })
}

async function handleSave() {
  if (!form.name) {
    message.warning('请填写名称')
    return
  }
  saving.value = true
  try {
    // Check if editing an existing workflow (has valid id)
    if (editingWorkflow.value && editingWorkflow.value.id) {
      await workflowApi.update(editingWorkflow.value.id, form)
      message.success('更新成功')
    } else {
      // Create new workflow
      await workflowApi.create(form)
      message.success('创建成功')
    }
    closeForm()
    await loadWorkflows()
  } catch (e) {
    const isUpdate = editingWorkflow.value && editingWorkflow.value.id
    message.error((isUpdate ? '更新失败' : '创建失败') + ': ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  try {
    await workflowApi.delete(id)
    message.success('删除成功')
    await loadWorkflows()
  } catch (e) {
    message.error('删除失败: ' + (e.response?.data?.message || e.message))
  }
}

onMounted(() => {
  loadWorkflows()
})
</script>

<style scoped>
.workflow-management {
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
.stage-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  background: #f5f5f5;
  border-radius: 8px;
}
.stage-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stage-order {
  width: 24px;
  height: 24px;
  background: #6366f1;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.stage-name {
  font-weight: 500;
  color: #333;
  min-width: 100px;
}
.stage-desc {
  color: #666;
  font-size: 12px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
