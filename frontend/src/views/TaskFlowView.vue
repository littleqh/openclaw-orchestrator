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

// 注册自定义员工节点 - 显示图标+名字
function registerEmployeeNode() {
  lf.register('employee-node', ({ model }) => {
    const data = model.getData()
    const name = data.text?.value || data.name || '?'
    const nickname = data.nickname || ''

    // 节点尺寸
    const width = 160
    const height = 70

    // 头像圆形背景
    const avatarBg = new Circle({
      x: -50,
      y: 0,
      r: 22,
      fill: '#6366f1',
      stroke: 'none'
    })

    // 头像文字（姓名的第一个字）
    const avatarText = new Text({
      x: -50,
      y: 5,
      text: name.charAt(0),
      fill: '#fff',
      fontSize: 14,
      textAnchor: 'middle'
    })

    // 名字文字
    const nameText = new Text({
      x: -15,
      y: -5,
      text: name,
      fill: '#333',
      fontSize: 14,
      fontWeight: 'bold'
    })

    // 昵称文字
    const nicknameText = new Text({
      x: -15,
      y: 15,
      text: nickname,
      fill: '#888',
      fontSize: 12
    })

    // 背景矩形
    const bg = new Rect({
      x: -width / 2,
      y: -height / 2,
      width: width,
      height: height,
      fill: '#fff',
      stroke: '#6366f1',
      strokeWidth: 2,
      radius: 10
    })

    return h('g', { class: 'employee-node' }, [
      bg,
      avatarBg,
      avatarText,
      nameText,
      nicknameText
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
  try {
    lf.deleteElement('edge-delete-btn')
  } catch (e) {
    // ignore if not exists
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

  // 注册自定义员工节点
  registerEmployeeNode()

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
      type: 'employee-node',
      id: `node_${worker.id}_${Date.now()}`,
      x: x,
      y: y,
      text: worker.name,
      name: worker.name,
      nickname: worker.nickname,
      avatar: worker.avatar
    })
  }

  // 监听 container 拖拽事件
  container.addEventListener('dragover', handleDragOver)
  container.addEventListener('drop', handleDrop)
  container.addEventListener('dragenter', (e) => e.preventDefault())
  container.addEventListener('dragleave', (e) => e.preventDefault())

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
</style>
