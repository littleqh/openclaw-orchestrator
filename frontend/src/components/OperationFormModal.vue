<template>
  <n-modal
    v-model:show="show"
    preset="card"
    :title="isEdit ? '编辑操作' : '添加操作'"
    style="width: 500px"
    @after-enter="init"
  >
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
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" @click="handleSave" :loading="saving">确认保存</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { NModal, NForm, NFormItem, NInput, NSelect, NButton, useMessage } from 'naive-ui'
import { operationApi, skillApi } from '../api/index.js'

const props = defineProps({
  modelValue: Boolean,
  operation: Object // 传入操作对象表示编辑，不传表示新增
})

const emit = defineEmits(['update:modelValue', 'saved'])

const message = useMessage()

const show = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const skills = ref([])
const skillOptions = ref([])

const form = reactive({
  id: null,
  name: '',
  description: '',
  skillIds: []
})

watch(() => props.modelValue, (val) => {
  show.value = val
})

watch(show, (val) => {
  emit('update:modelValue', val)
})

watch(() => props.operation, (op) => {
  if (op) {
    isEdit.value = true
    form.id = op.id
    form.name = op.name
    form.description = op.description || ''
    form.skillIds = op.skills?.map(s => s.id) || []
  } else {
    isEdit.value = false
    form.id = null
    form.name = ''
    form.description = ''
    form.skillIds = []
  }
}, { immediate: true })

async function init() {
  if (skills.value.length === 0) {
    try {
      const data = await skillApi.list()
      skills.value = Array.isArray(data) ? data : []
      skillOptions.value = skills.value.map(s => ({ label: s.name, value: s.id }))
    } catch (e) {
      console.error('加载技能失败', e)
    }
  }
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
    show.value = false
    emit('saved')
  } catch (e) {
    message.error((isEdit.value ? '更新' : '添加') + '失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}
</script>
