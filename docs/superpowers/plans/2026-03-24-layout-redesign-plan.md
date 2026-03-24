# 前端布局重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将前端页面布局改为左侧可收起菜单 + 顶部 Navbar + 中间内容区，Dashboard/实例管理/实时监控作为静态菜单路由。

**Tech Stack:** Vue 3 + Naive UI + Vue Router

---

## 文件结构变更

### 需要创建
| 文件 | 职责 |
|-----|------|
| `frontend/src/router/index.js` | Vue Router 配置 |
| `frontend/src/layouts/MainLayout.vue` | 主布局组件（左侧菜单 + 顶部 Navbar + 内容区） |
| `frontend/src/components/SideMenu.vue` | 可收起的侧边菜单组件 |

### 需要修改
| 文件 | 修改内容 |
|-----|---------|
| `frontend/src/App.vue` | 使用 MainLayout 包裹路由内容 |
| `frontend/src/main.js` | 安装 Vue Router |
| `frontend/src/views/Dashboard.vue` | 迁移为路由视图（已存在） |
| `frontend/src/views/InstanceManager.vue` | 迁移为路由视图（已存在） |
| `frontend/src/views/MonitorView.vue` | 迁移为路由视图（已存在） |

### 路由配置
```javascript
[
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard },
  { path: '/instances', name: 'Instances', component: InstanceManager },
  { path: '/monitor', name: 'Monitor', component: MonitorView }
]
```

---

## Task 1: 安装 Vue Router

**Files:**
- Modify: `frontend/src/main.js`
- Create: `frontend/src/router/index.js`

- [ ] **Step 1: 安装 vue-router**

```bash
cd frontend
npm install vue-router@4
```

- [ ] **Step 2: 创建 router/index.js**

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import InstanceManager from '../views/InstanceManager.vue'
import MonitorView from '../views/MonitorView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: Dashboard },
  { path: '/instances', name: 'Instances', component: InstanceManager },
  { path: '/monitor', name: 'Monitor', component: MonitorView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
```

- [ ] **Step 3: 修改 main.js 安装 Router**

```javascript
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(router)
app.mount('#app')
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/main.js frontend/src/router/index.js
git commit -m "feat(frontend): add Vue Router configuration"
```

---

## Task 2: 创建 MainLayout 组件

**Files:**
- Create: `frontend/src/layouts/MainLayout.vue`

- [ ] **Step 1: 创建 MainLayout.vue**

```vue
<template>
  <n-layout has-sider class="main-layout">
    <!-- 左侧菜单 -->
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="200"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
      class="side-menu"
    >
      <div class="logo-area" :class="{ collapsed }">
        <span class="logo-icon">🦞</span>
        <span v-if="!collapsed" class="logo-text">OpenClaw</span>
      </div>
      <SideMenu :collapsed="collapsed" />
    </n-layout-sider>

    <!-- 右侧主内容 -->
    <n-layout>
      <!-- 顶部 Navbar -->
      <n-layout-header class="top-navbar" bordered>
        <div class="navbar-left">
          <n-button text @click="toggleCollapse" class="collapse-btn">
            {{ collapsed ? '→' : '←' }}
          </n-button>
        </div>
        <div class="navbar-right">
          <n-button size="small" @click="refresh">🔄 刷新</n-button>
        </div>
      </n-layout-header>

      <!-- 内容区 -->
      <n-layout-content class="main-content">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup>
import { ref } from 'vue'
import SideMenu from '../components/SideMenu.vue'

const collapsed = ref(false)

function toggleCollapse() {
  collapsed.value = !collapsed.value
}

function refresh() {
  window.location.reload()
}
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

.side-menu {
  background: #fff;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  font-weight: 600;
  font-size: 16px;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s;
}

.logo-area.collapsed {
  justify-content: center;
  padding: 16px 8px;
}

.logo-icon {
  font-size: 24px;
}

.top-navbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
}

.navbar-left {
  display: flex;
  align-items: center;
}

.collapse-btn {
  font-size: 18px;
  padding: 4px 8px;
}

.main-content {
  padding: 0;
  background: #f3f4f6;
  height: calc(100vh - 56px);
  overflow-y: auto;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/layouts/MainLayout.vue
git commit -m "feat(frontend): add MainLayout with sidebar and navbar"
```

---

## Task 3: 创建 SideMenu 组件

**Files:**
- Create: `frontend/src/components/SideMenu.vue`

- [ ] **Step 1: 创建 SideMenu.vue**

```vue
<template>
  <n-menu
    :collapsed="collapsed"
    :collapsed-width="64"
    :collapsed-icon-size="22"
    :value="currentPath"
    :options="menuOptions"
    @update:value="handleMenuSelect"
  />
</template>

<script setup>
import { computed, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon } from 'naive-ui'

const props = defineProps({
  collapsed: Boolean
})

const router = useRouter()
const route = useRoute()

const currentPath = computed(() => route.path)

const menuOptions = [
  {
    label: 'Dashboard',
    key: '/dashboard',
    icon: () => h('span', '📊')
  },
  {
    label: '实例管理',
    key: '/instances',
    icon: () => h('span', '🖥️')
  },
  {
    label: '实时监控',
    key: '/monitor',
    icon: () => h('span', '📡')
  }
]

function handleMenuSelect(key) {
  router.push(key)
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/SideMenu.vue
git commit -m "feat(frontend): add SideMenu component with navigation"
```

---

## Task 4: 修改 App.vue 使用 MainLayout

**Files:**
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: 修改 App.vue**

```vue
<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <MainLayout />
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup>
import MainLayout from './layouts/MainLayout.vue'

const themeOverrides = {
  common: {
    primaryColor: '#6366f1',
    primaryColorHover: '#818cf8',
    primaryColorPressed: '#4f46e5',
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/App.vue
git commit -m "feat(frontend): use MainLayout as root component"
```

---

## Task 5: 验证构建

- [ ] **Step 1: 编译检查**

```bash
cd frontend
npm run build
```

- [ ] **Step 2: 检查路由是否正常工作**

访问各路径确认显示正确内容。

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "feat: complete layout redesign with sidebar navigation"
```

---

## 实现顺序总结

1. **Task 1**: 安装 Vue Router + 创建 router 配置
2. **Task 2**: 创建 MainLayout 组件
3. **Task 3**: 创建 SideMenu 组件
4. **Task 4**: 修改 App.vue 使用 MainLayout
5. **Task 5**: 验证构建
