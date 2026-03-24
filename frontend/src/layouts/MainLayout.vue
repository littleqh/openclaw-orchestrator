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
