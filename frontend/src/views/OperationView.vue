<template>
  <div class="manager">
    <div class="toolbar">
      <n-button type="primary" @click="openAdd">➕ 添加操作</n-button>
      <n-button @click="loadOperations" :loading="loading">🔄 刷新</n-button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="operations.length > 0">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>描述</th>
            <th>关联技能</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="op in operations" :key="op.id">
            <td>{{ op.id }}</td>
            <td>{{ op.name }}</td>
            <td>{{ op.description || '-' }}</td>
            <td>{{ op.skills?.map(s => s.name).join(', ') || '-' }}</td>
            <td>{{ new Date(op.createdAt).toLocaleString('zh-CN') }}</td>
            <td>
              <n-button size="tiny" type="info" @click="openEdit(op)" style="margin-right: 4px">编辑</n-button>
              <n-button size="tiny" type="error" @click="handleDelete(op.id)">删除</n-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <n-empty v-else description="暂无操作" />

    <!-- 添加/编辑弹窗 -->
    <n-modal v-model:show="showModal" preset="card" :title="isEdit ? '编辑操作' : '添加操作'" style="width: 500px">
      <n-form :model="form" label-placement="top">
        <n-form-item label="操作名称">
          <n-input v-model:value="form.name" placeholder="例如：代码审查" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" placeholder="操作描述..." />
        </n-form-item>
        <n-form-item label="关联技能">
          <n-select
            v-model:value="form.skillIds"
            :options="skillOptions"
            multiple
            placeholder="选择关联的技能"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <n-button @click="showModal = false">取消</n-button>
          <n-button type="primary" @click="handleSave" :loading="saving">确认保存</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { NButton, NEmpty, NModal, NForm, NFormItem, NInput, NSelect, useMessage } from 'naive-ui'
import { operationApi, skillApi } from '../api/index.js'

const operations = ref([])
const skills = ref([])
const showModal = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const loading = ref(false)
const message = useMessage()

const form = reactive({
  id: null,
  name: '',
  description: '',
  skillIds: []
})

const skillOptions = ref([])

async function loadSkills() {
  try {
    const data = await skillApi.list()
    skills.value = Array.isArray(data) ? data : []
    skillOptions.value = skills.value.map(s => ({ label: s.name, value: s.id }))
  } catch (e) {
    console.error('加载技能失败', e)
  }
}

async function loadOperations() {
  loading.value = true
  try {
    const data = await operationApi.list()
    operations.value = Array.isArray(data) ? data : []
  } catch (e) {
    message.error('加载操作失败')
  } finally {
    loading.value = false
  }
}

function openAdd() {
  isEdit.value = false
  form.id = null
  form.name = ''
  form.description = ''
  form.skillIds = []
  showModal.value = true
}

function openEdit(op) {
  isEdit.value = true
  form.id = op.id
  form.name = op.name
  form.description = op.description || ''
  form.skillIds = op.skills?.map(s => s.id) || []
  showModal.value = true
}

async function handleSave() {
  if (!form.name) {
    message.warning('请填写操作名称')
    return
  }
  saving.value = true
  try {
    const data = {
      name: form.name,
      description: form.description,
      skillIds: form.skillIds
    }
    if (isEdit.value) {
      await operationApi.update(form.id, data)
      message.success('更新成功')
    } else {
      await operationApi.create(data)
      message.success('添加成功')
    }
    showModal.value = false
    await loadOperations()
  } catch (e) {
    message.error((isEdit.value ? '更新' : '添加') + '失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await operationApi.delete(id)
  message.success('删除成功')
  await loadOperations()
}

onMounted(() => {
  loadOperations()
  loadSkills()
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
</style>
