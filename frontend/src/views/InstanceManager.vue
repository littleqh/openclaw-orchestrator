<template>
  <div class="manager">
    <div class="toolbar">
      <n-button type="primary" @click="showAdd = true">➕ 添加实例</n-button>
      <n-button @click="loadInstances" :loading="loading">🔄 刷新</n-button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="instances.length > 0">
      <table class="data-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>URL</th>
            <th>描述</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="inst in instances" :key="inst.id">
            <td>{{ inst.name }}</td>
            <td>{{ inst.url }}</td>
            <td>{{ inst.description || '-' }}</td>
            <td>{{ new Date(inst.createdAt).toLocaleString('zh-CN') }}</td>
            <td>
              <n-button size="tiny" type="error" @click="handleDelete(inst.id)">删除</n-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <n-empty v-else description="暂无实例" />

    <!-- 添加弹窗 -->
    <n-modal v-model:show="showAdd" preset="card" title="添加 Gateway 实例" style="width: 500px">
      <n-form :model="form" label-placement="top">
        <n-form-item label="实例名称">
          <n-input v-model:value="form.name" placeholder="例如：Jack (WSL)" />
        </n-form-item>
        <n-form-item label="Gateway URL">
          <n-input v-model:value="form.url" placeholder="例如：http://127.0.0.1:18789" />
        </n-form-item>
        <n-form-item label="Bearer Token">
          <n-input v-model:value="form.token" type="password" placeholder="Token" />
        </n-form-item>
        <n-form-item label="描述（可选）">
          <n-input v-model:value="form.description" type="textarea" placeholder="备注..." />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <n-button @click="showAdd = false">取消</n-button>
          <n-button type="primary" @click="handleAdd" :loading="saving">确认添加</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { NButton, NEmpty, NModal, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { instanceApi } from '../api/index.js'

const instances = ref([])
const showAdd = ref(false)
const saving = ref(false)
const loading = ref(false)
const message = useMessage()

const form = reactive({
  name: '',
  url: '',
  token: '',
  description: ''
})

async function loadInstances() {
  loading.value = true
  try {
    const data = await instanceApi.list()
    instances.value = Array.isArray(data) ? data : (data.result?.details || [])
  } catch (e) {
    message.error('加载实例失败')
  } finally {
    loading.value = false
  }
}

async function handleAdd() {
  if (!form.name || !form.url || !form.token) {
    message.warning('请填写完整')
    return
  }
  // 检查重复
  if (instances.value.some(i => i.url === form.url)) {
    message.warning('该 URL 已存在')
    return
  }
  saving.value = true
  try {
    await instanceApi.create({ ...form })
    message.success('添加成功')
    showAdd.value = false
    Object.assign(form, { name: '', url: '', token: '', description: '' })
    await loadInstances()
  } catch (e) {
    message.error('添加失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await instanceApi.delete(id)
  message.success('删除成功')
  await loadInstances()
}

onMounted(() => {
  loadInstances()
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
