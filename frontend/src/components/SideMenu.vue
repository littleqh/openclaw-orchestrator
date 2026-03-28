<template>
  <n-menu
    :collapsed="collapsed"
    :collapsed-width="64"
    :collapsed-icon-size="22"
    :value="activeKey"
    :options="menuOptions"
    @update:value="handleMenuSelect"
  />
</template>

<script setup>
import { h, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const props = defineProps({
  collapsed: Boolean
})

const router = useRouter()
const route = useRoute()
const activeKey = ref(route.path)

watch(() => route.path, (newPath) => {
  activeKey.value = newPath
})

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
  },
  {
    label: '数字员工',
    key: '/workers',
    icon: () => h('span', '👤')
  },
  {
    label: '技能管理',
    key: '/skills',
    icon: () => h('span', '🛠️')
  },
  {
    label: '操作管理',
    key: '/operations',
    icon: () => h('span', '⚙️')
  },
  {
    label: '任务模板',
    key: '/workflows',
    icon: () => h('span', '📋')
  },
  {
    label: '任务实例',
    key: '/workflow-instances',
    icon: () => h('span', '▶️')
  },
  {
    label: '令牌管理',
    key: '/token-management',
    icon: () => h('span', '🔑')
  }
]

function handleMenuSelect(key) {
  router.push(key)
}
</script>
