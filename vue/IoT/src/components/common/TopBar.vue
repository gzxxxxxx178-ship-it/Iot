<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, User, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getUsername, removeToken, removeUsername } from '../../utils/auth'

const route = useRoute()

const breadcrumbs = computed(() => {
  const matched = route.matched.filter((r) => r.meta?.title)
  return matched.map((r) => r.meta.title)
})

const displayName = ref('')

onMounted(() => {
  displayName.value = getUsername() || ''
})

function logout() {
  ElMessage.success('已退出登录')
  removeToken()
  removeUsername()
  window.location.hash = '#/login'
}
</script>

<template>
  <div class="topbar">
    <div class="breadcrumb">
      <template v-for="(item, index) in breadcrumbs" :key="index">
        <span v-if="index > 0" class="breadcrumb-sep">
          <el-icon :size="12"><ArrowRight /></el-icon>
        </span>
        <span class="breadcrumb-item" :class="{ current: index === breadcrumbs.length - 1 }">
          {{ item }}
        </span>
      </template>
    </div>
    <div class="topbar-right">
      <div class="user-info" v-if="displayName">
        <el-icon :size="14"><User /></el-icon>
        <span class="username">{{ displayName }}</span>
        <el-button text size="small" :icon="SwitchButton" @click="logout" title="退出登录" />
      </div>
      <span class="time">{{ new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }) }}</span>
    </div>
  </div>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.breadcrumb-item {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.breadcrumb-item.current {
  color: var(--text-primary);
  font-weight: 600;
}

.breadcrumb-sep {
  color: var(--text-muted);
  display: flex;
  align-items: center;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.time {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(16, 185, 129, 0.08);
  padding: 4px 8px 4px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid rgba(16, 185, 129, 0.15);
}

.username {
  color: var(--color-green);
  font-size: 0.85rem;
  font-weight: 500;
}
</style>
