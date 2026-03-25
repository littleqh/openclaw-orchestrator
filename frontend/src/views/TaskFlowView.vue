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
import { h, Rect, Text, Circle } from '@logicflow/core'
import '@logicflow/core/es/style/index.css'
import WorkerDragPanel from '../components/WorkerDragPanel.vue'
import { workerApi } from '../api/workerApi.js'

const workers = ref([])

let lf = null
let selectedEdgeId = null

// 注册自定义员工节点
function registerEmployeeNode() {
  lf.register('employee-node', ({ model, graphModel }) => {
    const { x, y } = model
    const data = model.getData()

    const nodeData = {
      id: data.id,
      name: data.name || '',
      nickname: data.nickname || '',
      avatar: data.avatar || ''
    }

    // 头像背景
    const avatarBg = new Circle({
      x: -55,
      y: 0,
      r: 18,
      fill: '#6366f1',
      stroke: 'none'
    })

    // 头像文字
    const avatarText = new Text({
      x: -55,
      y: 4,
      text: nodeData.name?.charAt(0) || '?',
      fill: '#fff',
      fontSize: 12,
      textAnchor: 'middle'
    })

    // 姓名
    const nameText = new Text({
      x: -25,
      y: -8,
      text: nodeData.name,
      fill: '#333',
      fontSize: 14,
      fontWeight: 'bold'
    })

    // 昵称
    const nicknameText = new Text({
      x: -25,
      y: 10,
      text: nodeData.nickname || '',
      fill: '#888',
      fontSize: 12
    })

    const group = new Rect({
      x: x - 80,
      y: y - 30,
      width: 160,
      height: 60,
      fill: '#fff',
      stroke: '#6366f1',
      strokeWidth: 2,
      radius: 8
    })

    return h('g', { class: 'employee-node' }, [
      group, avatarBg, avatarText, nameText, nicknameText
    ])
  })
}

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

  // 注册自定义员工节点
  registerEmployeeNode()

  // 处理 drop 事件
  container.addEventListener('drop', (e) => {
    e.preventDefault()
    const data = e.dataTransfer.getData('application/json')
    if (!data) return

    const worker = JSON.parse(data)
    const { x, y } = lf.graphModel.getPointByClient({
      x: e.clientX,
      y: e.clientY
    })

    lf.addNode({
      type: 'employee-node',
      id: `node_${worker.id}_${Date.now()}`,
      x: x,
      y: y,
      text: worker.name,
      name: worker.name,
      nickname: worker.nickname,
      avatar: worker.avatar
    })
  })

  container.addEventListener('dragover', (e) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'copy'
  })

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
</style>
