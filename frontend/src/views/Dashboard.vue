<template>
  <div class="dashboard">
    <div v-if="instances.length === 0" class="empty">
      <n-empty description="还没有配置任何 Gateway 实例">
        <template #extra>
          <n-button type="primary" @click="$emit('changeTab', 'instances')">去添加</n-button>
        </template>
      </n-empty>
    </div>

    <div v-else class="instance-grid">
      <InstanceCard
        v-for="inst in instances"
        :key="inst.id"
        :instance="inst"
        @deleted="$emit('refresh')"
      />
    </div>
  </div>
</template>

<script setup>
import { NEmpty, NButton } from 'naive-ui'
import InstanceCard from '../components/InstanceCard.vue'

defineProps({ instances: Array })
defineEmits(['refresh', 'changeTab'])
</script>

<style scoped>
.dashboard {
  padding: 24px;
}
.instance-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(480px, 1fr));
  gap: 20px;
}
.empty {
  padding: 80px;
  text-align: center;
}
</style>
