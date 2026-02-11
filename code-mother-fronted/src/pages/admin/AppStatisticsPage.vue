<template>
  <div id="appStatisticsPage">
    <!-- 标签页 -->
    <a-tabs v-model:activeKey="activeTab" type="card">
      <!-- 排行榜标签页 -->
      <a-tab-pane key="ranking" tab="APP排行榜">
        <div class="ranking-tab">
          <div class="ranking-header">
            <a-space>
              <a-select
                v-model:value="rankingType"
                style="width: 150px"
                @change="loadRanking"
              >
                <a-select-option
                  v-for="option in RANKING_TYPE_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </a-select-option>
              </a-select>
              <a-select
                v-model:value="rankingLimit"
                style="width: 120px"
                @change="loadRanking"
              >
                <a-select-option value="10">Top 10</a-select-option>
                <a-select-option value="20">Top 20</a-select-option>
                <a-select-option value="50">Top 50</a-select-option>
              </a-select>
              <a-button type="primary" :loading="loadingRanking" @click="loadRanking">
                刷新
              </a-button>
            </a-space>
          </div>

          <!-- 图表展示 -->
          <div class="charts-grid">
            <ChartContainer 
              :options="rankingBarChartOptions" 
              :loading="loadingRanking"
              class="chart-item"
              height="400px"
            />
            <ChartContainer 
              :options="rankingPieChartOptions" 
              :loading="loadingRanking"
              class="chart-item"
              height="400px"
            />
          </div>

          <!-- 排行榜表格 -->
          <a-table
            :columns="rankingColumns"
            :data-source="rankingData"
            :loading="loadingRanking"
            :pagination="false"
            class="ranking-table"
          >
             <template #bodyCell="{ column, record, index }">
               <template v-if="column.dataIndex === 'rank'">
                 <span :class="{
                   'rank-badge': true,
                   'rank-first': index === 0,
                   'rank-second': index === 1,
                   'rank-third': index === 2
                 }">
                   {{ index + 1 }}
                 </span>
               </template>
               <template v-else-if="column.dataIndex === 'appName'">
                 {{ record.app?.appName || record.appName || '未命名应用' }}
               </template>
               <template v-else-if="column.dataIndex === 'user'">
                 <UserInfo :user="record.app?.user" size="small" />
               </template>
               <template v-else-if="column.dataIndex === 'value'">
                 <span class="token-value">{{ formatNumber(record.value) }}</span>
               </template>
               <template v-else-if="column.dataIndex === 'inputTokens'">
                 <span class="token-value">{{ formatNumber(record.value) }}</span>
               </template>
               <template v-else-if="column.dataIndex === 'outputTokens'">
                 <span class="token-value">{{ formatNumber(record.value) }}</span>
               </template>
             </template>
          </a-table>
        </div>
      </a-tab-pane>

      <!-- 详细统计标签页 -->
      <a-tab-pane key="statistics" tab="详细统计">
        <div class="statistics-tab">
           <!-- 图表展示 -->
           <div class="charts-grid">
             <ChartContainer 
               :options="scatterChartOptions" 
               :loading="loadingStatistics"
               class="chart-item"
               height="400px"
             />
             <ChartContainer 
               :options="trendChartOptions" 
               :loading="loadingStatistics"
               class="chart-item"
               height="400px"
             />
           </div>

          <a-form layout="inline" :model="searchParams" @finish="doSearch" class="search-form">
            <a-form-item label="应用名称">
              <a-input
                v-model:value="searchParams.appName"
                placeholder="输入应用名称"
              />
            </a-form-item>
            <a-form-item label="创建者">
              <a-input
                v-model:value="searchParams.userId"
                placeholder="输入用户ID或名称"
              />
            </a-form-item>
            <a-form-item label="生成类型">
              <a-select
                v-model:value="searchParams.codeGenType"
                placeholder="选择生成类型"
                style="width: 150px"
              >
                <a-select-option value="">全部</a-select-option>
                <a-select-option
                  v-for="option in CODE_GEN_TYPE_OPTIONS"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item>
              <a-button type="primary" html-type="submit">搜索</a-button>
            </a-form-item>
          </a-form>
          <a-divider />

          <!-- 统计表格 -->
          <a-table
            :columns="statisticsColumns"
            :data-source="statisticsData"
            :pagination="statisticsPagination"
            @change="doTableChange"
            :loading="loadingStatistics"
            :scroll="{ x: 1400 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'appName'">
                {{ record.appName || '未命名应用' }}
              </template>
              <template v-else-if="column.dataIndex === 'user'">
                <UserInfo :user="record.user" size="small" />
              </template>
              <template v-else-if="column.dataIndex === 'createTime'">
                {{ formatTime(record.createTime) }}
              </template>
              <template v-else-if="column.dataIndex === 'totalInputTokens'">
                {{ formatNumber(record.totalInputTokens) }}
              </template>
              <template v-else-if="column.dataIndex === 'totalOutputTokens'">
                {{ formatNumber(record.totalOutputTokens) }}
              </template>
              <template v-else-if="column.dataIndex === 'totalTokens'">
                <span class="token-total">
                  {{ formatNumber(Number(record.totalInputTokens || 0) + Number(record.totalOutputTokens || 0)) }}
                </span>
              </template>
              <template v-else-if="column.dataIndex === 'totalConsumeTime'">
                {{ formatDuration(record.totalConsumeTime) }}
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="showAppDetail(record)">
                    查看详情
                  </a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="selectedApp"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import * as echarts from 'echarts'
import { useLoginUserStore } from '@/stores/loginUser'
import { getRanking } from '@/api/observableRecordController'
import { listAppVoByPageByAdmin } from '@/api/appController'
import { CODE_GEN_TYPE_OPTIONS, formatCodeGenType } from '@/utils/codeGenTypes'
import { formatTime } from '@/utils/time'
import { RANKING_TYPE_OPTIONS, RankingTypeEnum } from '@/constants/observableTypes'
import UserInfo from '@/components/UserInfo.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import ChartContainer from '@/components/ChartContainer.vue'

const loginUserStore = useLoginUserStore()

// 标签页控制
const activeTab = ref('ranking')

// 排行榜数据
const rankingData = ref<API.AppRankingVO[]>([])
const loadingRanking = ref(false)
const rankingType = ref(RankingTypeEnum.TOKENS)
const rankingLimit = ref('10')

const rankingColumns = computed(() => {
  const baseColumns: any[] = [
    {
      title: '排名',
      dataIndex: 'rank',
      key: 'rank',
      width: 80,
      align: 'center' as const,
    },
    {
      title: '应用名称',
      dataIndex: 'appName',
      key: 'appName',
      width: 200,
    },
    {
      title: '创建者',
      dataIndex: 'user',
      key: 'user',
      width: 150,
    },
  ]

  // 根据排行类型添加对应列
  const type = rankingType.value as string
  const { title: columnTitle } = getRankingLabel()
  
  baseColumns.push({
    title: columnTitle,
    dataIndex: 'value',
    key: 'value',
    width: 150,
    align: 'right',
  })

  return baseColumns
})

// 统计数据
const statisticsData = ref<API.AppVO[]>([])
const loadingStatistics = ref(false)
const statisticsPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const searchParams = reactive({
  appName: '',
  userId: '',
  codeGenType: '',
})

const statisticsColumns = [
  {
    title: '应用名称',
    dataIndex: 'appName',
    key: 'appName',
    width: 150,
  },
  {
    title: '创建者',
    dataIndex: 'user',
    key: 'user',
    width: 120,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 160,
  },
  {
    title: '输入Token',
    dataIndex: 'totalInputTokens',
    key: 'totalInputTokens',
    width: 120,
    align: 'right' as const,
  },
  {
    title: '输出Token',
    dataIndex: 'totalOutputTokens',
    key: 'totalOutputTokens',
    width: 120,
    align: 'right' as const,
  },
  {
    title: '总消耗Token',
    dataIndex: 'totalTokens',
    key: 'totalTokens',
    width: 130,
    align: 'right' as const,
  },
  {
    title: '总消耗时间',
    dataIndex: 'totalConsumeTime',
    key: 'totalConsumeTime',
    width: 120,
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
    align: 'center' as const,
  },
]

// 应用详情弹窗
const appDetailVisible = ref(false)
const selectedApp = ref<API.AppVO>()

// 格式化数字 - 添加千位分隔符
const formatNumber = (num?: number): string => {
  if (num === undefined || num === null) return '0'
  return num.toLocaleString('en-US')
}

// 格式化时间长度
const formatDuration = (ms?: number): string => {
  if (ms === undefined || ms === null) return '0ms'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`
  return `${(ms / 60000).toFixed(2)}min`
}

// 加载排行榜
const loadRanking = async () => {
  loadingRanking.value = true
  try {
    const res = await getRanking({
      type: rankingType.value,
      limit: parseInt(rankingLimit.value),
    })
    if (res.data.code === 0 && res.data.data) {
      rankingData.value = res.data.data
    } else {
      message.error('加载排行榜失败')
    }
  } catch (error) {
    console.error('加载排行榜失败：', error)
    message.error('加载排行榜失败')
  } finally {
    loadingRanking.value = false
  }
}

// 加载详细统计
const loadStatistics = async (page: number = 1, pageSize: number = 10) => {
  loadingStatistics.value = true
  try {
    const params: any = {
      current: page,
      pageSize: pageSize,
    }
    if (searchParams.appName) {
      params.appName = searchParams.appName
    }
    if (searchParams.codeGenType) {
      params.codeGenType = searchParams.codeGenType
    }

    const res = await listAppVoByPageByAdmin(params)
    if (res.data.code === 0 && res.data.data) {
      statisticsData.value = res.data.data.records || []
      statisticsPagination.total = res.data.data.total || 0
      statisticsPagination.current = page
      statisticsPagination.pageSize = pageSize
    } else {
      message.error('加载统计数据失败')
    }
  } catch (error) {
    console.error('加载统计数据失败：', error)
    message.error('加载统计数据失败')
  } finally {
    loadingStatistics.value = false
  }
}

// 搜索
const doSearch = () => {
  statisticsPagination.current = 1
  loadStatistics(1, statisticsPagination.pageSize)
}

// 表格变化处理
const doTableChange = (pag: any) => {
  statisticsPagination.current = pag.current
  statisticsPagination.pageSize = pag.pageSize
  loadStatistics(pag.current, pag.pageSize)
}

// 显示应用详情
const showAppDetail = (app: API.AppVO) => {
  selectedApp.value = app
  appDetailVisible.value = true
}

// ========== 图表配置 ==========

// 获取排行榜标题和单位
const getRankingLabel = (): { title: string; unit: string } => {
  const type = rankingType.value as string
  switch (type) {
    case RankingTypeEnum.TOKENS:
    case 'tokens':
      return { title: 'Token 消耗量', unit: 'Tokens' }
    case RankingTypeEnum.INPUT_TOKENS:
    case 'inputTokens':
      return { title: '输入 Token 消耗量', unit: 'Tokens' }
    case RankingTypeEnum.OUTPUT_TOKENS:
    case 'outputTokens':
      return { title: '输出 Token 消耗量', unit: 'Tokens' }
    case RankingTypeEnum.TOOL_CALLS:
    case 'toolCalls':
      return { title: '工具调用次数', unit: '次' }
    case RankingTypeEnum.DURATION:
    case 'duration':
      return { title: '平均耗时', unit: 'ms' }
    default:
      return { title: '数据', unit: '' }
  }
}

// 排行榜柱状图
const rankingBarChartOptions = computed((): echarts.EChartsOption => {
  const top10 = rankingData.value.slice(0, 10)
  const { title, unit } = getRankingLabel()
  
  // 根据排行类型获取对应数据（后端返回的 value 字段根据 type 参数表示不同含义）
  const getChartData = () => {
    return top10.map(d => d.value || 0)
  }
  
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (Array.isArray(params) && params.length > 0) {
          const param = params[0]
          const value = param.value.toLocaleString()
          return `${param.name}<br/>${value} ${unit}`
        }
        return ''
      },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: top10.map(d => d.app?.appName || d.appName || '未命名'),
      axisLabel: {
        interval: 0,
        rotate: 45,
      },
    },
    yAxis: {
      type: 'value',
      name: title,
    },
    series: [
      {
        data: getChartData(),
        type: 'bar',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#83bff6' },
            { offset: 0.5, color: '#188df0' },
            { offset: 1, color: '#188df0' },
          ]),
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}',
        },
      },
    ],
  }
})

// 排行榜饼图
const rankingPieChartOptions = computed((): echarts.EChartsOption => {
  const top5 = rankingData.value.slice(0, 5)
  const { title, unit } = getRankingLabel()

  // 后端返回的 value 字段根据 type 参数表示不同含义
  const total = rankingData.value.reduce((sum, item) => sum + (item.value || 0), 0)
  const topTotal = top5.reduce((sum, item) => sum + (item.value || 0), 0)
  const others = Math.max(0, total - topTotal)

  const data = [
    ...top5.map(d => ({
      value: d.value || 0,
      name: d.app?.appName || d.appName || '未命名',
    })),
  ]

  if (others > 0) {
    data.push({
      value: others,
      name: '其他应用',
    })
  }

  return {
    tooltip: {
      trigger: 'item',
      formatter: `{{b}}: {{c}} ${unit} ({{d}}%)`,
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      top: 'center',
    },
    series: [
      {
        name: title + '占比',
        type: 'pie',
        radius: '50%',
        data: data,
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)',
          },
        },
      },
    ],
  }
})

// 双柱图 - 输入/输出 Token 对比
const statisticsBarChartOptions = computed((): echarts.EChartsOption => {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow',
      },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true,
    },
    legend: {
      data: ['输入 Token', '输出 Token'],
      top: 10,
    },
    xAxis: {
      type: 'category',
      data: statisticsData.value.map(d => d.appName || '未命名'),
      axisLabel: {
        interval: 0,
        rotate: 45,
      },
    },
    yAxis: {
      type: 'value',
      name: 'Token 数量',
    },
    series: [
      {
        name: '输入 Token',
        data: statisticsData.value.map(d => d.totalInputTokens || 0),
        type: 'bar',
        itemStyle: { color: '#91d1f7' },
      },
      {
        name: '输出 Token',
        data: statisticsData.value.map(d => d.totalOutputTokens || 0),
        type: 'bar',
        itemStyle: { color: '#ffa940' },
      },
    ],
  }
})

// 散点图 - Token 消耗 vs 耗时分析
const scatterChartOptions = computed((): echarts.EChartsOption => {
  const data = statisticsData.value.map((d, idx) => ({
    value: [
      (d.totalInputTokens || 0) + (d.totalOutputTokens || 0),
      d.totalConsumeTime || 0,
    ],
    appName: d.appName || '未命名',
    idx,
  }))

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const item = data.find(d => d.idx === params.dataIndex)
        return `${item?.appName}<br/>总Token: ${params.value[0].toLocaleString()}<br/>耗时: ${params.value[1]}ms`
      },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true,
    },
    xAxis: {
      type: 'value',
      name: '总消耗 Token',
      nameTextStyle: {
        color: '#333',
      },
    },
    yAxis: {
      type: 'value',
      name: '总消耗时间 (ms)',
      nameTextStyle: {
        color: '#333',
      },
    },
    series: [
      {
        name: '应用',
        data: data.map(d => d.value),
        type: 'scatter',
        symbolSize: 8,
        itemStyle: {
          color: '#5470c6',
          opacity: 0.7,
        },
      },
    ],
  }
})

// 线性图 - Token 消耗趋势（按创建时间）
const trendChartOptions = computed((): echarts.EChartsOption => {
  const sorted = [...statisticsData.value].sort(
    (a, b) =>
      new Date(a.createTime || 0).getTime() - new Date(b.createTime || 0).getTime(),
  )

  return {
    tooltip: {
      trigger: 'axis',
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      name: '应用创建时间',
      data: sorted.map(d => formatTime(d.createTime || '')),
      axisLabel: {
        interval: 0,
        rotate: 45,
      },
    },
    yAxis: {
      type: 'value',
      name: 'Token 消耗量',
    },
    series: [
      {
        name: '累计 Token 消耗',
        data: sorted.map(d => (d.totalInputTokens || 0) + (d.totalOutputTokens || 0)),
        type: 'line',
        smooth: true,
        itemStyle: {
          color: '#ee6666',
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(238, 102, 102, 0.3)' },
            { offset: 1, color: 'rgba(238, 102, 102, 0)' },
          ]),
        },
        label: {
          show: false,
        },
      },
    ],
  }
})

// 初始化
onMounted(() => {
  loadRanking()
  loadStatistics()
})
</script>

<style scoped>
#appStatisticsPage {
  padding: 0;
}

.ranking-tab {
  padding: 16px;
}

.ranking-header {
  margin-bottom: 16px;
}

.ranking-table {
  margin-top: 16px;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: #f0f0f0;
  font-weight: 600;
  font-size: 14px;
}

.rank-first {
  background-color: #ffd666;
  color: #fff;
}

.rank-second {
  background-color: #d4d4d8;
  color: #fff;
}

.rank-third {
  background-color: #d4a373;
  color: #fff;
}

.token-value {
  font-weight: 600;
  color: #1890ff;
}

.token-total {
  font-weight: 600;
  color: #ff4d4f;
}

.statistics-tab {
  padding: 16px;
}

.search-form {
  margin-bottom: 16px;
}

/* 图表样式 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.chart-item {
  background: #fff;
  padding: 16px;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.chart-item.full-width {
  grid-column: 1 / -1;
}

@media (max-width: 1200px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
