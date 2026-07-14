<script setup>
import SideMenu from './SideMenu.vue'
import TopBar from './TopBar.vue'
</script>

<!-- 主布局壳：左侧 SideMenu + 右侧 (TopBar + router-view 中间内容区) -->
<template>
  <div class="app-layout">
    <aside class="layout-sidebar">
      <SideMenu />
    </aside>
    <div class="layout-right">
      <header class="layout-header">
        <TopBar />
      </header>
      <main class="layout-main">
        <!-- 路由视图，fade 过渡动画 -->
        <router-view v-slot="{ Component, route }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
}

.layout-sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.layout-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.layout-header {
  height: var(--header-height);
  background: var(--bg-header);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-xl);
  position: sticky;
  top: 0;
  z-index: 10;
}

.layout-main {
  flex: 1;
  padding: var(--spacing-xl);
  overflow-y: auto;
}
</style>
