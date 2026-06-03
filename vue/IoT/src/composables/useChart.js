import { ref, onUnmounted } from 'vue'
import * as echarts from 'echarts'

export function useChart() {
  const chartRef = ref(null)
  let instance = null

  function init(options) {
    if (!chartRef.value) return
    instance = echarts.init(chartRef.value)
    if (options) instance.setOption(options)
    window.addEventListener('resize', resize)
  }

  function setOption(options) {
    instance?.setOption(options)
  }

  function resize() {
    instance?.resize()
  }

  function dispose() {
    window.removeEventListener('resize', resize)
    instance?.dispose()
    instance = null
  }

  onUnmounted(dispose)

  return { chartRef, init, setOption, resize, dispose }
}
