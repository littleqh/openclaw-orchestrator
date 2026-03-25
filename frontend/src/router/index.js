import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import InstanceManager from '../views/InstanceManager.vue'
import MonitorView from '../views/MonitorView.vue'
import WorkerView from '../views/WorkerView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard },
  { path: '/instances', name: 'Instances', component: InstanceManager },
  { path: '/monitor', name: 'Monitor', component: MonitorView },
  { path: '/workers', name: 'Workers', component: WorkerView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router