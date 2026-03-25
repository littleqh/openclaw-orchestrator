<template>
  <div class="worker-list-panel">
    <div class="search-bar">
      <n-input v-model:value="searchText" placeholder="搜索姓名/昵称" clearable />
    </div>
    <div class="add-btn">
      <n-button type="primary" block @click="$emit('add')">➕ 新增员工</n-button>
    </div>
    <div class="worker-list">
      <div
        v-for="worker in filteredWorkers"
        :key="worker.id"
        class="worker-item"
        :class="{ selected: selectedId === worker.id }"
        @click="$emit('select', worker.id)"
      >
        <div class="worker-avatar">
          <img v-if="worker.avatar" :src="worker.avatar" alt="avatar" />
          <span v-else class="avatar-placeholder">{{ worker.name?.charAt(0) || '?' }}</span>
        </div>
        <div class="worker-info">
          <div class="worker-name">{{ worker.name }}</div>
          <div class="worker-nickname">{{ worker.nickname || '-' }}</div>
        </div>
        <n-tag :type="statusType(worker.status)" size="small" class="status-tag">
          {{ statusText(worker.status) }}
        </n-tag>
      </div>
      <n-empty v-if="filteredWorkers.length === 0 && !loading" description="暂无员工" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { NInput, NButton, NTag, NEmpty } from 'naive-ui'

const props = defineProps({
  workers: { type: Array, default: () => [] },
  selectedId: { type: Number, default: null },
  loading: { type: Boolean, default: false }
})

defineEmits(['select', 'add'])

const searchText = ref('')

const filteredWorkers = computed(() => {
  if (!searchText.value) return props.workers
  const lower = searchText.value.toLowerCase()
  return props.workers.filter(w =>
    w.name?.toLowerCase().includes(lower) ||
    w.nickname?.toLowerCase().includes(lower)
  )
})

function statusType(status) {
  switch (status) {
    case 'ONLINE': return 'success'
    case 'BUSY': return 'warning'
    default: return 'default'
  }
}

function statusText(status) {
  switch (status) {
    case 'ONLINE': return '在线'
    case 'BUSY': return '忙碌'
    default: return '离线'
  }
}
</script>

<style scoped>
.worker-list-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.search-bar {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.add-btn {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.worker-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.worker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;
}
.worker-item:hover {
  background: #f5f5f5;
}
.worker-item.selected {
  background: #eef2ff;
  border-color: #6366f1;
}
.worker-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}
.worker-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #6366f1;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}
.worker-info {
  flex: 1;
  min-width: 0;
}
.worker-name {
  font-weight: 500;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.worker-nickname {
  font-size: 12px;
  color: #888;
}
.status-tag {
  flex-shrink: 0;
}
</style>
