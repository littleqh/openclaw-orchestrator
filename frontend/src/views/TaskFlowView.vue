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

// 显示删除按钮 - 使用 absolute 定位的 DOM 元素
let deleteBtnEl = null
let deleteBtnEdgeId = null

function showDeleteButton(edge) {
  hideDeleteButton()

  const startX = edge.startPoint?.x || edge.sourceAnchor?.x || 0
  const startY = edge.startPoint?.y || edge.sourceAnchor?.y || 0
  const endX = edge.endPoint?.x || edge.targetAnchor?.x || 0
  const endY = edge.endPoint?.y || edge.targetAnchor?.y || 0

  const centerX = (startX + endX) / 2
  const centerY = (startY + endY) / 2

  deleteBtnEl = document.createElement('div')
  deleteBtnEl.textContent = '×'
  deleteBtnEl.style.cssText = `
    position: absolute;
    left: ${centerX - 15}px;
    top: ${centerY - 35}px;
    width: 30px;
    height: 30px;
    background: #ef4444;
    border-radius: 50%;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    font-size: 18px;
    font-weight: bold;
    z-index: 1000;
  `
  deleteBtnEl.onclick = () => {
    if (deleteBtnEdgeId) {
      lf.deleteEdge(deleteBtnEdgeId)
      hideDeleteButton()
      selectedEdgeId = null
    }
  }
  container.appendChild(deleteBtnEl)
  deleteBtnEdgeId = edge.id
}

function hideDeleteButton() {
  if (deleteBtnEl) {
    deleteBtnEl.remove()
    deleteBtnEl = null
    deleteBtnEdgeId = null
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
    width: rect.width || 800,
    height: rect.height || 600,
    grid: true,
    background: { color: '#f5f5f5' },
    // 启用多选
    multipleSelect: true,
    // 键盘删除
    keyboard: { delete: true },
    // 主题配置
    style: {
      rect: {
        fill: '#fff',
        stroke: '#6366f1',
        strokeWidth: 2,
        radius: 10
      },
      text: {
        color: '#333',
        fontSize: 14
      },
      nodeText: {
        color: '#333',
        fontSize: 14
      }
    }
  })

  // LogicFlow 2.x 需要显式 render
  lf.render()

  // 处理外部拖拽到画布
  const handleDragOver = (e) => {
    if (e.dataTransfer.types.includes('application/json')) {
      e.preventDefault()
    }
  }

  const handleDrop = (e) => {
    e.preventDefault()
    const data = e.dataTransfer.getData('application/json')
    if (!data) return

    const worker = JSON.parse(data)

    // 计算画布上的坐标
    const point = lf.graphModel.getPointByClient({
      x: e.clientX,
      y: e.clientY
    })
    const x = point.canvasOverlayPosition?.x || point.domOverlayPosition?.x
    const y = point.canvasOverlayPosition?.y || point.domOverlayPosition?.y

    lf.addNode({
      type: 'rect',
      id: `node_${worker.id}_${Date.now()}`,
      x: x,
      y: y,
      width: 160,
      height: 60,
      text: '🦞 ' + worker.name,
      properties: {
        workerId: worker.id,
        nickname: worker.nickname,
        avatar: worker.avatar
      }
    })
  }

  // 监听 container 拖拽事件
  container.addEventListener('dragover', handleDragOver)
  container.addEventListener('drop', handleDrop)
  container.addEventListener('dragenter', (e) => e.preventDefault())
  container.addEventListener('dragleave', (e) => e.preventDefault())

  // 处理边点击选中
  lf.on('edge:click', (data) => {
    const edge = data.edge || data
    if (!edge || !edge.id) return

    // 清除之前的选中
    if (selectedEdgeId) {
      lf.setElementStateById(selectedEdgeId, 0)
    }

    selectedEdgeId = edge.id
    lf.setSelected(edge.id)

    // 显示删除按钮
    showDeleteButton(edge)
  })

  // 处理节点点击选中
  lf.on('node:click', (data) => {
    const node = data.node || data
    if (!node || !node.id) return

    // 清除之前的选中
    if (selectedEdgeId) {
      lf.setElementStateById(selectedEdgeId, 0)
      selectedEdgeId = null
    }

    selectedEdgeId = 'node_' + node.id
    lf.setSelected(node.id)
  })

  // 点击空白处清除选中
  lf.on('blank:click', () => {
    if (selectedEdgeId) {
      if (selectedEdgeId.startsWith('node_')) {
        const nodeId = selectedEdgeId.replace('node_', '')
        lf.setElementStateById(selectedEdgeId, 0)
      } else {
        lf.setElementStateById(selectedEdgeId, 0)
      }
      selectedEdgeId = null
    }
    hideDeleteButton()
  })

  // 键盘删除
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Delete' && selectedEdgeId) {
      if (selectedEdgeId.startsWith('node_')) {
        const nodeId = selectedEdgeId.replace('node_', '')
        lf.deleteNode(nodeId)
      } else {
        lf.deleteEdge(selectedEdgeId)
      }
      selectedEdgeId = null
      hideDeleteButton()
    }
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

/* 节点样式 - 美化矩形节点 */
:deep(.lf-node rect) {
  fill: #fff !important;
  stroke: #6366f1 !important;
  stroke-width: 2 !important;
  rx: 10 !important;
  ry: 10 !important;
}
:deep(.lf-node text) {
  fill: #333 !important;
  font-size: 14px !important;
  font-weight: 500 !important;
  text-anchor: middle !important;
}

/* 画布容器需要 relative 定位以容纳删除按钮 */
.canvas-container {
  position: relative;
}
</style>
