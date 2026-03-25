<template>
  <div class="worker-drag-panel">
    <div class="panel-header">员工列表</div>
    <div class="worker-list">
      <div v-for="worker in workers" :key="worker.id" class="worker-item">
        {{ worker.name }}
      </div>
      <div v-if="workers.length === 0" class="empty-state">
        暂无员工
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  workers: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.worker-drag-panel {
  padding: 12px;
}
.panel-header {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}
.worker-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.worker-item {
  padding: 8px 12px;
  background: #f3f4f6;
  border-radius: 6px;
  font-size: 13px;
  color: #4b5563;
  cursor: move;
}
.empty-state {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  padding: 20px 0;
}
</style>
