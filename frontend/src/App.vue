<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <div class="app">
            <header class="app-header">
              <div class="header-left">
                <span class="logo">🦞</span>
                <span class="title">OpenClaw Orchestrator</span>
              </div>
              <div class="header-right">
                <n-button size="small" @click="refreshAll">🔄 刷新</n-button>
              </div>
            </header>

            <n-tabs type="line" animated v-model:value="activeTab">
              <n-tab-pane name="dashboard" tab="📊 Dashboard">
                <Dashboard :instances="instances" @refresh="loadInstances" />
              </n-tab-pane>
              <n-tab-pane name="instances" tab="🖥️ 实例管理">
                <InstanceManager :instances="instances" @refresh="loadInstances" />
              </n-tab-pane>
            </n-tabs>
          </div>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { NConfigProvider, NMessageProvider, NDialogProvider, NNotificationProvider, NTabs, NTabPane, NButton } from 'naive-ui'
import Dashboard from './views/Dashboard.vue'
import InstanceManager from './views/InstanceManager.vue'

const activeTab = ref('dashboard')
const instances = ref([])

const themeOverrides = {
  common: {
    primaryColor: '#6366f1',
    primaryColorHover: '#818cf8',
    primaryColorPressed: '#4f46e5',
  }
}

async function loadInstances() {
  try {
    const { instanceApi } = await import('./api/index.js')
    instances.value = await instanceApi.list()
  } catch (e) {
    console.error('加载实例失败', e)
  }
}

function refreshAll() {
  loadInstances()
}

onMounted(() => {
  loadInstances()
})
</script>

<style scoped>
.app {
  min-height: 100vh;
}
.app-header {
  background: #1e1e2e;
  color: white;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo {
  font-size: 24px;
}
.title {
  font-size: 18px;
  font-weight: 600;
}
.header-right {
  display: flex;
  gap: 8px;
}
</style>
