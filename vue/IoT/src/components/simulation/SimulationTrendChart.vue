<script setup>
import { useChart } from '../../composables/useChart'
import { watch, onMounted } from 'vue'

// 仿真遥测多指标趋势折线图：5条指标线共享自动量程，支持按图例筛选查看
const props = defineProps({
  timeLabels: { type: Array, default: () => [] },
  series: { type: Array, default: () => [] },
  height: { type: String, default: '320px' },
})

const { chartRef, init, setOption } = useChart()

// 构建初始 ECharts 配置
function buildOption(labels, s) {
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#334155' } },
    },
    legend: {
      data: s.map((item) => item.name),
      top: 0,
      textStyle: { color: '#94a3b8', fontSize: 11 },
    },
    grid: {
      left: '3%', right: '4%', bottom: '5%', top: '15%', containLabel: true,
    },
    xAxis: [{
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisLabel: { color: '#94a3b8', interval: 'auto', hideOverlap: true, rotate: 30 },
      axisLine: { lineStyle: { color: '#475569' } },
    }],
    yAxis: [{
      type: 'value',
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
    }],
    series: s.map((item) => ({
      name: item.name,
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2 },
      itemStyle: { color: item.color },
      areaStyle: {
        opacity: 0.08,
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: item.color }, { offset: 1, color: 'rgba(255,255,255,0)' }] },
      },
      data: item.data,
    })),
  }
}

// 挂载时初始化图表
onMounted(() => {
  init(buildOption(props.timeLabels, props.series))
})

// 监听数据变化，仅更新数据部分
watch([() => props.timeLabels, () => props.series], () => {
  const labels = props.timeLabels
  const s = props.series
  setOption({
    xAxis: { data: labels },
    legend: { data: s.map((item) => item.name) },
    series: s.map((item) => ({ name: item.name, data: item.data })),
  })
}, { deep: true })
</script>

<template>
  <div ref="chartRef" class="trend-chart" :style="{ height: props.height }"></div>
</template>

<style scoped>
.trend-chart {
  width: 100%;
  min-height: 300px;
}
</style>
