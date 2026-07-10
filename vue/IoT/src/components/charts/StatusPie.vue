<script setup>
import { useChart } from '../../composables/useChart'
import { onMounted, watch } from 'vue'

// 设备状态环形饼图：内径 55%，外径 80%，无标签，hover 显示详情
const props = defineProps({
  data: { type: Array, default: () => [] },
})

const { chartRef, init, setOption } = useChart()

const chartOption = {
  backgroundColor: 'transparent',
  tooltip: { trigger: 'item' },
  legend: {
    bottom: 0,
    textStyle: { color: '#94a3b8' },
  },
  series: [{
    name: '设备状态',
    type: 'pie',
    radius: ['55%', '80%'],
    center: ['50%', '45%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 6, borderColor: '#0f172a', borderWidth: 3 },
    label: { show: false },
    emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
    data: props.data,
  }],
}

// 挂载时初始化图表
onMounted(() => {
  init(chartOption)
})

// 数据变化时仅更新 series 数据
watch(() => props.data, (val) => {
  setOption({ series: [{ data: val }] })
}, { deep: true })
</script>

<template>
  <div class="pie-wrapper" ref="chartRef" style="height: 260px; width: 100%;"></div>
</template>
