<template>
  <div class="operation-drag-panel">
    <div class="panel-title">操作列表</div>
    <div class="operation-list">
      <div
        v-for="operation in operations"
        :key="operation.id"
        class="operation-card"
        draggable="true"
        @dragstart="handleDragStart($event, operation)"
      >
        <div class="icon">⚙️</div>
        <div class="info">
          <div class="name">{{ operation.name }}</div>
          <div class="desc">{{ operation.description || '-' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  operations: { type: Array, default: () => [] }
})

function handleDragStart(e, operation) {
  e.dataTransfer.setData('application/json', JSON.stringify({
    type: 'operation',
    id: operation.id,
    name: operation.name,
    description: operation.description
  }))
  e.dataTransfer.effectAllowed = 'copy'
}
</script>

<style scoped>
.operation-drag-panel {
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
.operation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.operation-card {
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
.operation-card:hover {
  background: #f5f5f5;
  border-color: #e5e7eb;
}
.operation-card:active {
  cursor: grabbing;
}
.icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #6366f1;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
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
}
.desc {
  font-size: 11px;
  color: #888;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>