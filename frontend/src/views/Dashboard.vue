<template>
  <div class="dashboard">
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="instances.length === 0" class="empty">
      <n-empty description="还没有配置任何 Gateway 实例">
        <template #extra>
          <n-button type="primary" @click="goToInstances">去添加</n-button>
        </template>
      </n-empty>
    </div>

    <div v-else class="instance-grid">
      <InstanceCard
        v-for="inst in instances"
        :key="inst.id"
        :instance="inst"
        @deleted="loadInstances"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { NEmpty, NButton } from 'naive-ui'
import InstanceCard from '../components/InstanceCard.vue'
import { instanceApi } from '../api/index.js'
import { useRouter } from 'vue-router'

const router = useRouter()
const instances = ref([])
const loading = ref(false)

async function loadInstances() {
  loading.value = true
  try {
    const data = await instanceApi.list()
    instances.value = Array.isArray(data) ? data : (data.result?.details || [])
  } catch (e) {
    console.error('Load instances error:', e)
  } finally {
    loading.value = false
  }
}

function goToInstances() {
  router.push('/instances')
}

onMounted(() => {
  loadInstances()
})
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
.loading {
  padding: 80px;
  text-align: center;
  color: #666;
}
</style>
