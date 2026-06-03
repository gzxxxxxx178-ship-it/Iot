<script setup>
import { useChart } from '../../composables/useChart'
import { watch, onMounted } from 'vue'

const props = defineProps({
  timeLabels: { type: Array, default: () => [] },
  tempSeries: { type: Array, default: () => [] },
  humSeries: { type: Array, default: () => [] },
  height: { type: String, default: '100%' },
})

const { chartRef, init, setOption } = useChart()

const chartOption = {
  backgroundColor: 'transparent',
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'cross', label: { backgroundColor: '#6a7985' } },
  },
  legend: {
    data: ['温度', '湿度'],
    top: 0,
    textStyle: { color: '#94a3b8' },
  },
  grid: {
    left: '3%', right: '4%', bottom: '5%', top: '15%', containLabel: true,
  },
  xAxis: [{
    type: 'category',
    boundaryGap: false,
    data: props.timeLabels,
    axisLabel: { color: '#94a3b8', interval: 'auto', hideOverlap: true, rotate: 30 },
    axisLine: { lineStyle: { color: '#475569' } },
  }],
  yAxis: [{
    type: 'value',
    axisLabel: { color: '#94a3b8' },
    splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
  }],
  series: [
    {
      name: '温度', type: 'line', smooth: true, showSymbol: false,
      lineStyle: { width: 3, shadowColor: 'rgba(16,185,129,0.5)', shadowBlur: 10 },
      itemStyle: { color: '#10b981' },
      areaStyle: {
        opacity: 0.1,
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(16,185,129,0.5)' }, { offset: 1, color: 'rgba(16,185,129,0.01)' }] },
      },
      data: props.tempSeries,
    },
    {
      name: '湿度', type: 'line', smooth: true, showSymbol: false,
      lineStyle: { width: 3, shadowColor: 'rgba(59,130,246,0.5)', shadowBlur: 10 },
      itemStyle: { color: '#3b82f6' },
      areaStyle: {
        opacity: 0.1,
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59,130,246,0.5)' }, { offset: 1, color: 'rgba(59,130,246,0.01)' }] },
      },
      data: props.humSeries,
    },
  ],
}

onMounted(() => {
  init(chartOption)
})

watch([() => props.timeLabels, () => props.tempSeries, () => props.humSeries], () => {
  setOption({
    xAxis: { data: props.timeLabels },
    series: [{ data: props.tempSeries }, { data: props.humSeries }],
  })
}, { deep: true })
</script>

<template>
  <div class="chart-wrapper" ref="chartRef" :style="{ height }"></div>
</template>

<style scoped>
.chart-wrapper {
  width: 100%;
  min-height: 300px;
}
</style>
