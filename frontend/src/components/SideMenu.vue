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
