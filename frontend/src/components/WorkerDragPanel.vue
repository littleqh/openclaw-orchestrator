<template>
  <div class="worker-drag-panel">
    <div class="panel-title">数字员工</div>
    <div class="worker-list">
      <div
        v-for="worker in workers"
        :key="worker.id"
        class="worker-card"
        draggable="true"
        @dragstart="handleDragStart($event, worker)"
      >
        <img v-if="worker.avatar" :src="worker.avatar" class="avatar" />
        <span v-else class="avatar-placeholder">{{ worker.name?.charAt(0) }}</span>
        <div class="info">
          <div class="name">{{ worker.name }}</div>
          <div class="nickname">{{ worker.nickname || '-' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  workers: { type: Array, default: () => [] }
})

function handleDragStart(e, worker) {
  console.log('[DragStart] worker:', worker)
  e.dataTransfer.setData('application/json', JSON.stringify({
    type: 'worker',
    id: worker.id,
    name: worker.name,
    nickname: worker.nickname,
    avatar: worker.avatar
  }))
  e.dataTransfer.effectAllowed = 'copy'
}
</script>

<style scoped>
.worker-drag-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.panel-title {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  color: #1e1e2e;
  border-bottom: 1px solid #f0f0f0;
}
.worker-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.worker-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s;
  border: 2px solid transparent;
  margin-bottom: 6px;
}
.worker-card:hover {
  background: #f5f5f5;
  border-color: #e5e7eb;
}
.worker-card:active {
  cursor: grabbing;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}
.avatar-placeholder {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #6366f1;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}
.info {
  flex: 1;
  min-width: 0;
}
.name {
  font-weight: 500;
  color: #333;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nickname {
  font-size: 11px;
  color: #888;
}
</style>
