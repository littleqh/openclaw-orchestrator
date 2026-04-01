<template>
  <div class="panel status-health-panel">
    <div class="panel-title">已安装技能</div>
    <div class="panel-content" v-if="skillsData">
      <div class="skills-list">
        <div v-for="(skill, index) in skillsList" :key="index" class="skill-item">
          <div class="skill-info">
            <span class="skill-name">{{ skill.name || skill.id || index }}</span>
            <span class="skill-version" v-if="skill.version">v{{ skill.version }}</span>
          </div>
          <n-tag :type="skill.enabled ? 'success' : 'default'" size="tiny">
            {{ skill.enabled ? '启用' : '禁用' }}
          </n-tag>
        </div>
      </div>
    </div>
    <div v-else class="empty">暂无技能数据</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { NTag } from 'naive-ui'

const props = defineProps({ data: Object })

// skillsStatus 返回格式: {"type":"res","ok":true,"payload":{skills:[...]}}
const skillsData = computed(() => {
  return props.data?.skills?.payload || null
})

const skillsList = computed(() => {
  if (!skillsData.value) return []
  // payload.skills 是数组
  const skills = skillsData.value.skills
  if (Array.isArray(skills)) return skills
  // 可能是对象格式
  return Object.values(skills)
})
</script>

<style scoped>
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 10px;
  color: #1e1e2e;
  flex-shrink: 0;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
}

.skills-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skill-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  background: #f9fafb;
  border-radius: 6px;
  font-size: 12px;
}

.skill-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skill-name {
  color: #333;
  font-weight: 500;
}

.skill-version {
  color: #9ca3af;
  font-size: 11px;
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 12px;
}
</style>
