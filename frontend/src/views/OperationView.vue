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
    <OperationFormModal v-model:modelValue="showModal" :operation="selectedOperation" @saved="loadOperations" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { NButton, NEmpty, useMessage } from 'naive-ui'
import { operationApi } from '../api/index.js'
import OperationFormModal from '../components/OperationFormModal.vue'

const operations = ref([])
const showModal = ref(false)
const selectedOperation = ref(null)
const loading = ref(false)
const message = useMessage()

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
  selectedOperation.value = null
  showModal.value = true
}

function openEdit(op) {
  selectedOperation.value = op
  showModal.value = true
}

async function handleDelete(id) {
  await operationApi.delete(id)
  message.success('删除成功')
  await loadOperations()
}

onMounted(() => {
  loadOperations()
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
