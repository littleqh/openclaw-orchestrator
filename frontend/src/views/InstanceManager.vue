<template>
  <div class="manager">
    <div class="toolbar">
      <n-button type="primary" @click="showAdd = true">➕ 添加实例</n-button>
    </div>

    <n-table v-if="instances.length > 0" :columns="columns" :data="instances" bordered>
      <template #empty>
        <n-empty description="暂无实例" />
      </template>
    </n-table>

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
import { ref, reactive } from 'vue'
import { NButton, NTable, NEmpty, NModal, NForm, NFormItem, NInput, NInputGroup, useMessage } from 'naive-ui'
import { instanceApi } from '../api/index.js'

const props = defineProps({ instances: Array })
const emit = defineEmits(['refresh'])

const showAdd = ref(false)
const saving = ref(false)
const message = useMessage()

const form = reactive({
  name: '',
  url: '',
  token: '',
  description: ''
})

const columns = [
  { title: '名称', key: 'name' },
  { title: 'URL', key: 'url' },
  { title: '描述', key: 'description', ellipsis: true },
  { title: '创建时间', key: 'createdAt', render: (row) => row.createdAt ? new Date(row.createdAt).toLocaleString('zh-CN') : '-' },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row) => {
      return h('div', { style: 'display:flex;gap:4px' }, [
        h(NButton, {
          size: 'tiny', type: 'error',
          onClick: () => handleDelete(row.id)
        }, { default: () => '删除' })
      ])
    }
  }
]

import { h } from 'vue'

async function handleAdd() {
  if (!form.name || !form.url || !form.token) {
    message.warning('请填写完整')
    return
  }
  saving.value = true
  try {
    await instanceApi.create({ ...form })
    message.success('添加成功')
    showAdd.value = false
    Object.assign(form, { name: '', url: '', token: '', description: '' })
    emit('refresh')
  } catch (e) {
    message.error('添加失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await instanceApi.delete(id)
  message.success('删除成功')
  emit('refresh')
}
</script>

<style scoped>
.manager {
  padding: 24px;
}
.toolbar {
  margin-bottom: 16px;
}
</style>
