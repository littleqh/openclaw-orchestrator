<template>
  <div class="worker-view">
    <div class="sidebar">
      <WorkerListPanel
        :workers="workers"
        :selected-id="selectedId"
        @select="handleSelect"
        @add="handleAdd"
      />
    </div>
    <div class="main-content">
      <WorkerDetailPanel
        :worker="selectedWorker"
        :loading="detailLoading"
        :is-adding="selectedId === null"
        @saved="handleSaved"
        @deleted="handleDeleted"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import WorkerListPanel from '../components/WorkerListPanel.vue'
import WorkerDetailPanel from '../components/WorkerDetailPanel.vue'
import { workerApi } from '../api/workerApi.js'

const workers = ref([])
const selectedId = ref(null)
const detailLoading = ref(false)

const selectedWorker = computed(() => {
  if (!selectedId.value) return null
  return workers.value.find(w => w.id === selectedId.value) || null
})

async function loadWorkers() {
  try {
    workers.value = await workerApi.list()
  } catch (e) {
    console.error('Load workers error:', e)
  }
}

function handleSelect(id) {
  selectedId.value = id
}

function handleAdd() {
  selectedId.value = null
}

async function handleSaved() {
  await loadWorkers()
  // Keep selection if still exists
  if (selectedId.value) {
    const w = workers.value.find(w => w.id === selectedId.value)
    if (!w) {
      selectedId.value = null
    }
  }
}

async function handleDeleted() {
  await loadWorkers()
  selectedId.value = null
}

import { onMounted } from 'vue'
onMounted(() => {
  loadWorkers()
})
</script>

<style scoped>
.worker-view {
  display: flex;
  height: calc(100vh - 120px);
  background: #f3f4f6;
  gap: 16px;
  padding: 16px;
}

.sidebar {
  width: 280px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
  flex-shrink: 0;
}

.main-content {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
  min-width: 0;
}
</style>
