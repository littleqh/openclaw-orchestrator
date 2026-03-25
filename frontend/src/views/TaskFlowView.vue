<template>
  <div class="task-flow-view">
    <div class="sidebar">
      <WorkerDragPanel :workers="workers" />
    </div>
    <div class="canvas-container">
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="workflow-name" v-if="currentWorkflow">{{ currentWorkflow.name }}</span>
          <span class="workflow-name placeholder" v-else>未保存的设计</span>
        </div>
        <div class="toolbar-right">
          <n-button size="small" @click="saveWorkflow" :disabled="!currentWorkflow">保存</n-button>
          <n-button size="small" @click="showSaveAsModal = true">另存为</n-button>
          <n-button size="small" @click="createNewWorkflow">新建</n-button>
          <n-button size="small" @click="openListModal">打开</n-button>
        </div>
      </div>
      <div id="lf-canvas" class="lf-canvas"></div>
    </div>

    <!-- 设计列表弹窗 -->
    <n-modal v-model:show="showList" preset="card" title="选择设计" style="width: 500px">
      <n-list hoverable clickable>
        <n-list-item v-for="wf in workflows" :key="wf.id" @click="loadWorkflow(wf)">
          <n-thing :title="wf.name" :description="wf.description || '无描述'" />
        </n-list-item>
      </n-list>
      <n-empty v-if="workflows.length === 0" description="暂无设计" />
    </n-modal>

    <!-- 另存为弹窗 -->
    <n-modal v-model:show="showSaveAsModal" preset="card" title="另存为" style="width: 400px">
      <n-form-item label="设计名称">
        <n-input v-model:value="newWorkflowName" placeholder="输入设计名称" />
      </n-form-item>
      <n-form-item label="描述">
        <n-input v-model:value="newWorkflowDesc" type="textarea" placeholder="输入描述" />
      </n-form-item>
      <template #footer>
        <n-button @click="showSaveAsModal = false">取消</n-button>
        <n-button type="primary" @click="saveAsNewWorkflow" :disabled="!newWorkflowName.trim()">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/es/style/index.css'
import WorkerDragPanel from '../components/WorkerDragPanel.vue'
import { workerApi } from '../api/workerApi.js'
import { workflowApi } from '../api/workflowApi.js'

const route = useRoute()
const router = useRouter()

const workers = ref([])
const workflows = ref([])
const currentWorkflow = ref(null)
const showList = ref(false)
const showSaveAsModal = ref(false)
const newWorkflowName = ref('')
const newWorkflowDesc = ref('')

let lf = null
let selectedEdgeId = null

// 显示删除按钮 - 使用 absolute 定位的 DOM 元素
let deleteBtnEl = null
let deleteBtnEdgeId = null

function showDeleteButton(edge) {
  hideDeleteButton()

  // 计算连线中点坐标
  const startX = edge.properties?.startPoint?.x || edge.startPoint?.x || 0
  const startY = edge.properties?.startPoint?.y || edge.startPoint?.y || 0
  const endX = edge.properties?.endPoint?.x || edge.endPoint?.x || 0
  const endY = edge.properties?.endPoint?.y || edge.endPoint?.y || 0
  const centerX = (startX + endX) / 2
  const centerY = (startY + endY) / 2

  // 转换为页面坐标
  const point = lf.graphModel.getPointByClient({ x: centerX, y: centerY })
  const pageX = point.domOverlayPosition?.x
  const pageY = point.domOverlayPosition?.y

  if (pageX === undefined || pageY === undefined) {
    console.log('[DeleteBtn] Failed to get position')
    return
  }

  deleteBtnEl = document.createElement('div')
  deleteBtnEl.textContent = '×'
  deleteBtnEl.style.cssText = `
    position: fixed;
    left: ${pageX - 15}px;
    top: ${pageY - 35}px;
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
    z-index: 10000;
  `
  deleteBtnEl.onclick = () => {
    if (deleteBtnEdgeId) {
      lf.deleteEdge(deleteBtnEdgeId)
      hideDeleteButton()
      selectedEdgeId = null
    }
  }
  document.body.appendChild(deleteBtnEl)
  deleteBtnEdgeId = edge.id
}

function hideDeleteButton() {
  if (deleteBtnEl) {
    deleteBtnEl.remove()
    deleteBtnEl = null
    deleteBtnEdgeId = null
  }
}

// 获取画布数据
function getCanvasData() {
  const graphData = lf.getGraphData()
  return {
    nodes: graphData.nodes.map(node => ({
      tempId: node.id,
      workerId: node.properties?.workerId,
      x: node.x,
      y: node.y
    })),
    edges: graphData.edges.map(edge => ({
      sourceTempId: edge.sourceNodeId,
      targetTempId: edge.targetNodeId
    }))
  }
}

// 保存当前设计
async function saveWorkflow() {
  if (!currentWorkflow.value) return
  const data = getCanvasData()
  try {
    const updated = await workflowApi.update(currentWorkflow.value.id, {
      name: currentWorkflow.value.name,
      description: currentWorkflow.value.description,
      version: currentWorkflow.value.version,
      ...data
    })
    currentWorkflow.value = updated
    window.$message?.success('保存成功')
  } catch (err) {
    console.error('Save failed:', err)
    window.$message?.error('保存失败')
  }
}

// 另存为新设计
async function saveAsNewWorkflow() {
  if (!newWorkflowName.value.trim()) return
  const data = getCanvasData()
  try {
    const created = await workflowApi.create({
      name: newWorkflowName.value.trim(),
      description: newWorkflowDesc.value,
      ...data
    })
    currentWorkflow.value = created
    showSaveAsModal.value = false
    newWorkflowName.value = ''
    newWorkflowDesc.value = ''
    window.$message?.success('保存成功')
  } catch (err) {
    console.error('Save failed:', err)
    window.$message?.error('保存失败')
  }
}

// 创建新设计
function createNewWorkflow() {
  lf.clearData()
  currentWorkflow.value = null
  hideDeleteButton()
}

// 打开设计列表
async function openListModal() {
  workflows.value = await workflowApi.list()
  showList.value = true
}

// 加载设计到画布
async function loadWorkflow(wf) {
  try {
    const detail = await workflowApi.get(wf.id)
    console.log('[Load] Received workflow detail:', detail)
    currentWorkflow.value = detail
    showList.value = false

    // 清除现有数据
    lf.clearData()

    // 重建节点和边
    const nodeIdMap = {}
    const nodes = detail.nodes || []
    const edges = detail.edges || []

    console.log('[Load] Nodes count:', nodes.length)
    console.log('[Load] Edges count:', edges.length)

    // 先创建所有节点
    for (const node of nodes) {
      console.log('[Load] Creating node:', node)
      const lfNode = {
        type: 'rect',
        id: node.tempId,
        x: node.x,
        y: node.y,
        width: 160,
        height: 60,
        text: '🦞 ' + (node.workerName || '员工'),
        properties: {
          workerId: node.workerId,
          nickname: node.workerNickname,
          avatar: node.workerAvatar
        }
      }
      lf.addNode(lfNode)
      nodeIdMap[node.tempId] = node.tempId
    }

    // 再创建所有边
    for (const edge of edges) {
      console.log('[Load] Creating edge:', edge)
      // 找到对应的tempId - edge中使用的是数据库ID，需要映射到tempId
      const sourceNode = nodes.find(n => n.id === edge.sourceNodeId)
      const targetNode = nodes.find(n => n.id === edge.targetNodeId)
      console.log('[Load] Source node:', sourceNode, 'Target node:', targetNode)
      if (sourceNode && targetNode) {
        lf.addEdge({
          sourceNodeId: sourceNode.tempId,
          targetNodeId: targetNode.tempId
        })
      } else {
        console.warn('[Load] Could not find source or target node for edge:', edge)
      }
    }

    window.$message?.success('加载成功')
  } catch (err) {
    console.error('Load failed:', err)
    window.$message?.error('加载失败')
  }
}

// 删除设计
async function deleteWorkflow(wf) {
  if (!confirm(`确定删除设计"${wf.name}"吗？`)) return
  try {
    await workflowApi.delete(wf.id)
    if (currentWorkflow.value?.id === wf.id) {
      createNewWorkflow()
    }
    workflows.value = await workflowApi.list()
    window.$message?.success('删除成功')
  } catch (err) {
    console.error('Delete failed:', err)
    window.$message?.error('删除失败')
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

  // 检查 URL 是否有 workflow ID
  if (route.params.id) {
    const wf = { id: route.params.id, name: '加载中...' }
    loadWorkflow(wf)
  }

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
  lf.on('edge:click', (ev) => {
    const edge = ev.data || ev
    if (!edge || !edge.id) return

    selectedEdgeId = edge.id

    // 显示删除按钮
    showDeleteButton(edge)
  })

  // 处理节点点击选中
  lf.on('node:click', (ev) => {
    const node = ev.data || ev
    if (!node || !node.id) return

    selectedEdgeId = 'node_' + node.id
  })

  // 点击空白处清除选中
  lf.on('blank:click', () => {
    selectedEdgeId = null
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
  display: flex;
  flex-direction: column;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.workflow-name {
  font-weight: 500;
  color: #333;
}
.workflow-name.placeholder {
  color: #999;
  font-style: italic;
}
.lf-canvas {
  flex: 1;
  position: relative;
  min-height: 0;
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
