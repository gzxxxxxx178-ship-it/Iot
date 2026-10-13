// main.js
// uni-app Vue3 入口文件：创建应用实例，不引入路由或第三方 UI 库

import App from './App.vue'
import { createSSRApp } from 'vue'

// 创建并返回小程序应用实例（uni-app Vue3 约定的导出方式）
export function createApp() {
  const app = createSSRApp(App)
  return { app }
}
