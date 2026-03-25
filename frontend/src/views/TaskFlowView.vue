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
let selectedEdgeId = null

// 显示删除按钮
function showDeleteButton(edge) {
  hideDeleteButton()

  const centerX = (edge.startPoint.x + edge.endPoint.x) / 2
  const centerY = (edge.startPoint.y + edge.endPoint.y) / 2

  lf.addElement({
    type: 'html',
    id: 'edge-delete-btn',
    x: centerX,
    y: centerY - 20,
    width: 30,
    height: 30
  })

  // 使用定时器等待元素渲染
  setTimeout(() => {
    const btn = document.querySelector('#edge-delete-btn')
    if (btn) {
      btn.innerHTML = '<div class="delete-btn" onclick="window.__deleteEdge()">×</div>'
      btn.style.cssText = 'width:30px;height:30px;background:#ef4444;border-radius:50%;color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer;font-size:18px;font-weight:bold;'
    }
  }, 50)
}

// 隐藏删除按钮
function hideDeleteButton() {
  const existing = lf.graphModel.getElementById('edge-delete-btn')
  if (existing) {
    lf.deleteElement('edge-delete-btn')
  }
}

// 全局删除函数
if (typeof window.__deleteEdge !== 'function') {
  window.__deleteEdge = () => {
    if (selectedEdgeId) {
      lf.deleteEdge(selectedEdgeId)
      selectedEdgeId = null
      hideDeleteButton()
    }
  }
}

onMounted(async () => {
  // 加载员工
  workers.value = await workerApi.list()

  // 获取容器尺寸
  const container = document.getElementById('lf-canvas')
  const rect = container.getBoundingClientRect()

  // 初始化 LogicFlow
  lf = new LogicFlow({
    container: container,
    width: rect.width,
    height: rect.height,
    grid: true,
    background: { color: '#f5f5f5' },
    // 启用多选
    multipleSelect: true,
    // 键盘删除
    keyboard: { delete: true }
  })
  console.log('[LF] initialized, version:', lf.version || 'unknown')

  // 处理外部拖拽到画布 - 同时监听 document 和 container
  const handleDragOver = (e) => {
    console.log('[DragOver] target:', e.target.tagName, e.target.className, 'types:', e.dataTransfer.types)
    if (e.dataTransfer.types.includes('application/json')) {
      e.preventDefault()
    }
  }

  const handleDrop = (e) => {
    console.log('[Drop] target:', e.target.tagName, e.target.className)
    e.preventDefault()
    const data = e.dataTransfer.getData('application/json')
    console.log('[Drop] raw data:', data)
    if (!data) {
      console.log('[Drop] No data found')
      return
    }

    const worker = JSON.parse(data)
    console.log('[Drop] worker:', worker)

    // 计算画布上的坐标 - 转换为 LogicFlow 坐标
    const point = lf.graphModel.getPointByClient({
      x: e.clientX,
      y: e.clientY
    })
    console.log('[Drop] LF point:', point)
    // 使用 canvasOverlayPosition 作为 LogicFlow 内部坐标
    const x = point.canvasOverlayPosition?.x || point.domOverlayPosition?.x
    const y = point.canvasOverlayPosition?.y || point.domOverlayPosition?.y
    console.log('[Drop] final x/y:', x, y)

    lf.addNode({
      type: 'rect',
      id: `node_${worker.id}_${Date.now()}`,
      x: x,
      y: y,
      text: worker.name,
      width: 160,
      height: 60,
      properties: {
        workerId: worker.id,
        nickname: worker.nickname,
        avatar: worker.avatar
      }
    })
    console.log('[Drop] Node added')
    console.log('[Drop] Graph data:', JSON.stringify(lf.getGraphData()))
  }

  // 监听 container 本身
  container.addEventListener('dragover', handleDragOver)
  container.addEventListener('drop', handleDrop)
  container.addEventListener('dragenter', (e) => console.log('[DragEnter] target:', e.target.tagName))
  container.addEventListener('dragleave', (e) => console.log('[DragLeave] target:', e.target.tagName))

  // 同时监听 document 级别
  document.addEventListener('dragover', handleDragOver)
  document.addEventListener('drop', handleDrop)

  // 处理边点击选中
  lf.on('edge:click', ({ edge }) => {
    // 清除之前的选中
    if (selectedEdgeId) {
      lf.setElementStateById(selectedEdgeId, 0)
    }

    selectedEdgeId = edge.id
    lf.setSelected(edge.id)

    // 显示删除按钮
    showDeleteButton(edge)
  })

  // 点击空白处清除选中
  lf.on('blank:click', () => {
    if (selectedEdgeId) {
      lf.setElementStateById(selectedEdgeId, 0)
      selectedEdgeId = null
    }
    hideDeleteButton()
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
  position: relative;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
  min-width: 0;
}
.lf-canvas {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;
}

/* 节点样式 */
:deep(.lf-canvas-node) {
  background: #fff !important;
  border: 2px solid #6366f1 !important;
  border-radius: 8px !important;
}
</style>
