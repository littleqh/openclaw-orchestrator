<template>
  <div class="task-flow-view">
    <div class="sidebar">
      <WorkerDragPanel :workers="workers" />
    </div>
    <div class="canvas-container">
      <div id="lf-canvas" class="lf-canvas"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/es/style/index.css'
import WorkerDragPanel from '../components/WorkerDragPanel.vue'
import { workerApi } from '../api/workerApi.js'

const workers = ref([])

let lf = null

onMounted(async () => {
  // 加载员工
  workers.value = await workerApi.list()

  // 初始化 LogicFlow
  lf = new LogicFlow({
    container: document.getElementById('lf-canvas'),
    width: 800,
    height: 600,
    grid: true,
    background: { color: '#f5f5f5' }
  })
})
</script>

<style scoped>
.task-flow-view {
  display: flex;
  height: calc(100vh - 120px);
  background: #f3f4f6;
  gap: 16px;
  padding: 16px;
}
.sidebar {
  width: 260px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
  flex-shrink: 0;
}
.canvas-container {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
}
.lf-canvas {
  width: 100%;
  height: 100%;
}
</style>
