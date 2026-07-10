<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor, Odometer, Setting, Bell, Histogram, DataAnalysis, Aim, ChatDotRound, Money } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// 菜单配置：路由路径、标签文字、对应图标
const menuItems = [
  { path: '/dashboard', label: '仪表盘', icon: Odometer },
  { path: '/monitor', label: '实时监控', icon: Monitor },
  { path: '/devices', label: '设备管理', icon: Setting },
  { path: '/history', label: '历史数据', icon: Histogram },
  { path: '/alarm', label: '报警管理', icon: Bell },
  { path: '/automation', label: '自动化规则', icon: DataAnalysis },
  { path: '/chat', label: 'AI 助手', icon: ChatDotRound },
  { path: '/pay', label: '支付测试', icon: Money },
  { path: '/screen', label: '数据大屏', icon: Aim },
]

// 根据当前路由路径高亮对应菜单项
const activePath = computed(() => route.path)

// 点击菜单项跳转到对应路由
function navigate(path) {
  router.push(path)
}
</script>

<template>
  <div class="sidebar">
    <div class="sidebar-logo">
      <span class="logo-icon">&#x1F33E;</span>
      <span class="logo-text">智慧农业</span>
    </div>
    <nav class="sidebar-nav">
      <div
        v-for="item in menuItems"
        :key="item.path"
        class="nav-item"
        :class="{ active: activePath === item.path }"
        @click="navigate(item.path)"
      >
        <el-icon :size="18"><component :is="item.icon" /></el-icon>
        <span class="nav-label">{{ item.label }}</span>
      </div>
    </nav>
  </div>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg) var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
}

.logo-icon {
  font-size: 1.5rem;
}

.logo-text {
  font-size: 1.1rem;
  font-weight: 600;
  letter-spacing: -0.3px;
}

.sidebar-nav {
  flex: 1;
  padding: var(--spacing-sm);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-secondary);
  transition: all 0.2s ease;
  font-size: 0.9rem;
  user-select: none;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-primary);
}

.nav-item.active {
  background: rgba(16, 185, 129, 0.12);
  color: var(--color-green);
}
</style>
