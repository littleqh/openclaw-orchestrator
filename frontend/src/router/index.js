import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import InstanceManager from '../views/InstanceManager.vue'
import MonitorView from '../views/MonitorView.vue'
import WorkerView from '../views/WorkerView.vue'
import TaskFlowView from '../views/TaskFlowView.vue'
import SkillView from '../views/SkillView.vue'
import OperationView from '../views/OperationView.vue'
import LoginView from '../views/LoginView.vue'
import TokenManagement from '../views/TokenManagement.vue'
import WorkflowManagement from '../views/WorkflowManagement.vue'
import WorkflowInstanceView from '../views/WorkflowInstanceView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard, meta: { requiresAuth: true } },
  { path: '/instances', name: 'Instances', component: InstanceManager, meta: { requiresAuth: true } },
  { path: '/monitor', name: 'Monitor', component: MonitorView, meta: { requiresAuth: true } },
  { path: '/workers', name: 'Workers', component: WorkerView, meta: { requiresAuth: true } },
  { path: '/skills', name: 'Skills', component: SkillView, meta: { requiresAuth: true } },
  { path: '/operations', name: 'Operations', component: OperationView, meta: { requiresAuth: true } },
  { path: '/login', name: 'Login', component: LoginView },
  { path: '/token-management', name: 'TokenManagement', component: TokenManagement, meta: { requiresAuth: true } },
  { path: '/workflows', name: 'WorkflowManagement', component: WorkflowManagement, meta: { requiresAuth: true } },
  { path: '/workflows/:id/edit', name: 'TemplateEditor', component: TaskFlowView, meta: { requiresAuth: true } },
  { path: '/workflow-instances', name: 'WorkflowInstances', component: WorkflowInstanceView, meta: { requiresAuth: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard for auth
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router