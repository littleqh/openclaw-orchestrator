<template>
  <div class="manager">
    <div class="toolbar">
      <n-button type="primary" @click="showAdd = true">➕ 添加技能</n-button>
      <n-button @click="loadSkills" :loading="loading">🔄 刷新</n-button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-else-if="skills.length > 0">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>名称</th>
            <th>描述</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="skill in skills" :key="skill.id">
            <td>{{ skill.id }}</td>
            <td>{{ skill.name }}</td>
            <td>{{ skill.description || '-' }}</td>
            <td>{{ new Date(skill.createdAt).toLocaleString('zh-CN') }}</td>
            <td>
              <n-button size="tiny" type="error" @click="handleDelete(skill.id)">删除</n-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <n-empty v-else description="暂无技能" />

    <!-- 添加弹窗 -->
    <n-modal v-model:show="showAdd" preset="card" title="添加技能" style="width: 500px">
      <n-form :model="form" label-placement="top">
        <n-form-item label="技能名称">
          <n-input v-model:value="form.name" placeholder="例如：代码生成" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" type="textarea" placeholder="技能描述..." />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <n-button @click="showAdd = false">取消</n-button>
          <n-button type="primary" @click="handleAdd" :loading="saving">确认添加</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 确认删除弹窗 -->
    <n-modal v-model:show="showConfirm" preset="card" title="确认删除" style="width: 450px">
      <div v-if="deleteCheck.referenced">
        <n-alert type="warning" style="margin-bottom: 16px">
          该技能已被以下操作引用，删除后将从这些操作中移除关联：
        </n-alert>
        <div class="ref-operations">
          <n-tag v-for="op in deleteCheck.referencedBy" :key="op.id" size="small" style="margin: 4px">
            {{ op.name }}
          </n-tag>
        </div>
      </div>
      <div v-else>
        确定要删除该技能吗？
      </div>
      <template #footer>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <n-button @click="showConfirm = false">取消</n-button>
          <n-button type="error" @click="confirmDelete" :loading="deleting">确认删除</n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { NButton, NEmpty, NModal, NForm, NFormItem, NInput, NAlert, NTag, useMessage } from 'naive-ui'
import { skillApi } from '../api/index.js'

const skills = ref([])
const showAdd = ref(false)
const showConfirm = ref(false)
const saving = ref(false)
const deleting = ref(false)
const loading = ref(false)
const message = useMessage()
const pendingDeleteId = ref(null)

const form = reactive({
  name: '',
  description: ''
})

const deleteCheck = reactive({
  referenced: false,
  referencedBy: []
})

async function loadSkills() {
  loading.value = true
  try {
    const data = await skillApi.list()
    skills.value = Array.isArray(data) ? data : []
  } catch (e) {
    message.error('加载技能失败')
  } finally {
    loading.value = false
  }
}

async function handleAdd() {
  if (!form.name) {
    message.warning('请填写技能名称')
    return
  }
  saving.value = true
  try {
    await skillApi.create({ ...form })
    message.success('添加成功')
    showAdd.value = false
    Object.assign(form, { name: '', description: '' })
    await loadSkills()
  } catch (e) {
    message.error('添加失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  pendingDeleteId.value = id
  try {
    const check = await skillApi.checkDelete(id)
    deleteCheck.referenced = check.referenced
    deleteCheck.referencedBy = check.referencedBy || []
    showConfirm.value = true
  } catch (e) {
    message.error('检查删除状态失败')
  }
}

async function confirmDelete() {
  deleting.value = true
  try {
    await skillApi.forceDelete(pendingDeleteId.value)
    message.success('删除成功')
    showConfirm.value = false
    await loadSkills()
  } catch (e) {
    message.error('删除失败: ' + (e.response?.data?.message || e.message))
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
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
.ref-operations {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
