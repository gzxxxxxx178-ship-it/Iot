import { ref, onUnmounted } from 'vue'
import * as echarts from 'echarts'

// ECharts 实例生命周期管理 composable
// 返回 chartRef（模板 ref）、init、setOption、resize、dispose
export function useChart() {
  const chartRef = ref(null)
  let instance = null

  // 初始化 ECharts 实例并绑定 DOM 元素，可选传入初始 options
  function init(options) {
    if (!chartRef.value) return
    instance = echarts.init(chartRef.value)
    if (options) instance.setOption(options)
    // 窗口大小变化时自动重绘
    window.addEventListener('resize', resize)
  }

  // 更新图表配置
  function setOption(options) {
    instance?.setOption(options)
  }

  // 触发表格重绘以响应容器大小变化
  function resize() {
    instance?.resize()
  }

  // 销毁实例、移除 resize 监听，释放资源
  function dispose() {
    window.removeEventListener('resize', resize)
    instance?.dispose()
    instance = null
  }

  // 组件卸载时自动清理
  onUnmounted(dispose)

  return { chartRef, init, setOption, resize, dispose }
}
