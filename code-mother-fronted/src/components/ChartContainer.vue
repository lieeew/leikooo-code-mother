<template>
  <div class="chart-container" ref="chartRef"></div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

interface Props {
  options: echarts.EChartsOption
  loading?: boolean
  autoResize?: boolean
  height?: string | number
}

const props = withDefaults(defineProps<Props>(), {
  autoResize: true,
  height: 400,
})

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    chart.setOption(props.options)

    if (props.autoResize) {
      const handleResize = () => {
        chart?.resize()
      }

      // Use ResizeObserver for better responsiveness
      try {
        resizeObserver = new ResizeObserver(handleResize)
        resizeObserver.observe(chartRef.value)
      } catch {
        // Fallback to window resize event
        window.addEventListener('resize', handleResize)
      }
    }
  }
})

watch(
  () => props.options,
  (newOptions) => {
    if (chart) {
      chart.setOption(newOptions)
    }
  },
  { deep: true },
)

watch(
  () => props.loading,
  (loading) => {
    if (chart) {
      if (loading) {
        chart.showLoading()
      } else {
        chart.hideLoading()
      }
    }
  },
)

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
  if (chart) {
    chart.dispose()
  }
})
</script>

<style scoped>
.chart-container {
  width: 100%;
  height: v-bind(height);
}
</style>
