<template>
  <view class="page research-page">
    <PageHeader title="研究工作台" subtitle="实验记录与内部知识库证据查询" :badge="session.token ? '已接入' : '需登录'" />

    <view v-if="!session.token" class="card">
      <EmptyState text="请先登录账号查看研究数据" />
      <button class="btn-primary action-button" @tap="goLogin">前往登录</button>
    </view>

    <template v-else>
      <view class="notice-card">
        <text class="notice-text">仅供研究验证：数据可能来自合成仿真或内部小样本，不代表田间实测或生产安全认证。</text>
      </view>

      <view class="card pipeline-card">
        <text class="section-title">研究链路状态</text>
        <view class="pipeline-row">
          <view v-for="(step, index) in pipelineSteps" :key="step.label" class="pipeline-step">
            <view class="step-dot" :class="index < pipelineStep ? 'step-done' : ''">{{ index + 1 }}</view>
            <text class="step-label">{{ step.label }}</text>
            <text class="hint">{{ step.status }}</text>
          </view>
        </view>
        <text v-if="overviewError" class="error-text">{{ overviewError }}</text>
        <button class="btn-secondary small-button" :disabled="loading" @tap="loadResearchData">{{ loading ? '刷新中…' : '刷新研究数据' }}</button>
      </view>

      <view class="section-heading"><text class="section-title">最新实验结论</text></view>
      <view class="run-card-list">
        <view v-for="item in latestRuns" :key="item.key" class="card run-card">
          <view class="run-heading">
            <text class="run-name">{{ item.label }}</text>
            <text class="status-tag" :class="statusClass(item.run?.status)">{{ item.run?.status || '未上传' }}</text>
          </view>
          <template v-if="item.run?.runId">
            <text class="run-title">{{ item.run.title || item.run.runKey || '实验运行' }}</text>
            <text class="hint">运行编号：{{ item.run.runKey || item.run.runId }}</text>
            <text class="summary-text">{{ normalizeSummary(item.run.summary) || '后端未提供摘要' }}</text>
            <view v-if="item.metrics.length" class="key-metrics">
              <view v-for="metric in item.metrics" :key="metric.methodName + metric.metricName" class="metric-line">
                <text>{{ metric.methodName }} · {{ metric.metricName }}</text>
                <text class="metric-value">{{ formatMetricValue(metric.metricValue) }}{{ metric.unit ? ' ' + metric.unit : '' }}</text>
              </view>
            </view>
            <text v-else class="hint">暂无指标记录</text>
          </template>
          <text v-else class="hint empty-run">暂无该类型实验记录</text>
        </view>
      </view>

      <view class="card">
        <view class="section-heading inner-heading">
          <text class="section-title">实验指标明细</text>
          <button class="btn-secondary small-button" :disabled="runsLoading" @tap="loadExperimentRuns">刷新列表</button>
        </view>
        <picker :range="experimentLabels" :value="selectedRunIndex" @change="selectExperiment">
          <view class="picker-field">{{ experimentLabels[selectedRunIndex] || '选择实验运行' }}　›</view>
        </picker>
        <text v-if="runsError" class="error-text">{{ runsError }}</text>
        <view v-if="selectedMetrics.length" class="metric-table">
          <view v-for="(metric, index) in selectedMetrics" :key="index" class="metric-row">
            <view class="metric-row-title">
              <text class="run-name">{{ metric.methodName || '—' }} · {{ metric.metricName || '—' }}</text>
              <text class="metric-value">{{ formatMetricValue(metric.metricValue) }}{{ metric.unit ? ' ' + metric.unit : '' }}</text>
            </view>
            <text class="hint">{{ metric.higherIsBetter === null || metric.higherIsBetter === undefined ? '方向未标注' : metric.higherIsBetter ? '数值越高越好' : '数值越低越好' }}{{ metric.notes ? ' · ' + metric.notes : '' }}</text>
          </view>
        </view>
        <text v-else-if="selectedRunId && !detailLoading" class="hint empty-run">该实验没有可显示的指标</text>
        <text v-if="detailLoading" class="hint">正在读取指标…</text>
      </view>

      <view class="card rag-card">
        <text class="section-title">RAG 证据问答</text>
        <text class="hint rag-description">仅解释，不下发设备命令。基于内部 BM25 知识库检索，不调用外部大语言模型。</text>
        <textarea class="question-field" v-model="ragQuestion" maxlength="1000" placeholder="输入农业灌溉相关问题（1–1000字）" />
        <text class="character-count">{{ ragQuestion.length }}/1000</text>
        <button class="btn-primary action-button" :disabled="ragLoading || !ragQuestion.trim()" @tap="submitRagQuery">{{ ragLoading ? '检索中…' : '查询证据' }}</button>
        <text v-if="ragError" class="error-text">{{ ragError }}</text>
        <view v-if="ragResult" class="answer-block">
          <text class="answer-title">{{ ragResult.abstained ? '系统拒绝回答' : '检索结果' }}</text>
          <text v-if="ragResult.abstainReason" class="hint">拒答原因：{{ ragResult.abstainReason }}</text>
          <text class="answer-text">{{ ragResult.answer || '后端未返回回答' }}</text>
          <view v-if="!ragResult.abstained" class="score-row">
            <text>检索器：{{ ragResult.retriever || '—' }}</text>
            <text>最高得分：{{ formatScore(ragResult.topScore) }}</text>
            <text>拒答阈值：{{ formatScore(ragResult.threshold) }}</text>
          </view>
          <view v-if="ragResult.citations?.length" class="evidence-section">
            <text class="evidence-heading">引用</text>
            <view v-for="(citation, index) in ragResult.citations" :key="index" class="citation-row">
              <text>{{ citation.source_id }}#{{ citation.chunk_id }}</text>
              <text class="hint">得分 {{ formatScore(citation.score) }}</text>
            </view>
          </view>
          <view v-if="ragResult.evidence?.length" class="evidence-section">
            <text class="evidence-heading">证据片段</text>
            <view v-for="(evidence, index) in ragResult.evidence" :key="index" class="evidence-card">
              <text class="run-name">{{ evidence.source_id }}#{{ evidence.chunk_id }} · {{ evidence.title }}</text>
              <text class="evidence-text">{{ evidence.text }}</text>
              <text v-if="evidence.limitations" class="limitation-text">适用限制：{{ evidence.limitations }}</text>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
import { session } from '../../utils/session.js'
import { getResearchOverview, listExperiments, getExperimentDetail, queryResearchEvidence } from '../../utils/research-store.js'

const overview = reactive({ latestMpc: null, latestRl: null, latestRag: null })
const overviewError = ref('')
const loading = ref(false)
const runs = ref([])
const runsLoading = ref(false)
const runsError = ref('')
const selectedRunId = ref('')
const selectedMetrics = ref([])
const detailLoading = ref(false)
const ragQuestion = ref('')
const ragResult = ref(null)
const ragError = ref('')
const ragLoading = ref(false)
const pipelineDesc = reactive({ auth: '等待中', db: '等待中' })
let pageGeneration = 0
let detailRequestId = 0

const pipelineStep = computed(() => {
  if (pipelineDesc.db === '数据已写入') return 3
  if (pipelineDesc.auth === '鉴权通过') return 2
  return 1
})
const pipelineSteps = computed(() => [
  { label: '实验产出', status: 'MATLAB / 外部运行' },
  { label: '鉴权校验', status: pipelineDesc.auth },
  { label: '数据持久化', status: pipelineDesc.db },
  { label: '小程序展示', status: '当前页面' }
])
const latestRuns = computed(() => [
  { key: 'mpc', label: 'MPC 基线实验', run: overview.latestMpc, metrics: overview.latestMpc?.metrics || [] },
  { key: 'rl', label: '残差 SAC 实验', run: overview.latestRl, metrics: overview.latestRl?.metrics || [] },
  { key: 'rag', label: 'RAG 检索实验', run: overview.latestRag, metrics: overview.latestRag?.metrics || [] }
])
const experimentLabels = computed(() => runs.value.map((run) => '[' + (run.experimentType || '未知类型') + '] ' + (run.title || '未命名') + ' · ' + (run.runKey || run.id)))
const selectedRunIndex = computed(() => Math.max(0, runs.value.findIndex((run) => String(run.id) === String(selectedRunId.value))))

// 页面显示时加载当前账号的研究概览和实验运行列表。
onShow(loadResearchData)

// 切换账号时清空研究结果并重新读取，避免旧账号数据残留。
watch(() => session.generation, () => {
  pageGeneration += 1
  detailRequestId += 1
  Object.assign(overview, { latestMpc: null, latestRl: null, latestRag: null })
  runs.value = []; selectedRunId.value = ''; selectedMetrics.value = []; detailLoading.value = false
  ragResult.value = null; ragError.value = ''; overviewError.value = ''; runsError.value = ''
  if (session.token) loadResearchData()
})

// 并行读取研究概览和运行目录，并拉取三类最新运行的指标。
async function loadResearchData() {
  if (!session.token) return
  const generation = ++pageGeneration
  const accountGeneration = session.generation
  loading.value = true; overviewError.value = ''
  const results = await Promise.allSettled([getResearchOverview(), listExperiments(50)])
  if (generation !== pageGeneration || accountGeneration !== session.generation) return
  const [overviewResult, runsResult] = results
  if (overviewResult.status === 'fulfilled') {
    Object.assign(overview, {
      latestMpc: overviewResult.value?.latestMpc || null,
      latestRl: overviewResult.value?.latestRl || null,
      latestRag: overviewResult.value?.latestRag || null
    })
    pipelineDesc.auth = '鉴权通过'
    pipelineDesc.db = ['latestMpc', 'latestRl', 'latestRag'].some((key) => overview[key]?.runId) ? '数据已写入' : '无数据'
    const detailTargets = ['latestMpc', 'latestRl', 'latestRag'].filter((key) => overview[key]?.runId)
    await Promise.all(detailTargets.map(async (key) => {
      try {
        const detail = await getExperimentDetail(overview[key].runId)
        if (generation === pageGeneration && accountGeneration === session.generation) overview[key] = { ...overview[key], metrics: detail?.metrics || [] }
      } catch (error) {
        if (!error?.stale && generation === pageGeneration && accountGeneration === session.generation) overview[key] = { ...overview[key], metrics: [] }
      }
    }))
  } else if (!overviewResult.reason?.stale) {
    overviewError.value = overviewResult.reason?.message || '研究总览读取失败'
    pipelineDesc.auth = '鉴权或请求失败'
    pipelineDesc.db = '状态未知'
  }
  if (runsResult.status === 'fulfilled') runs.value = Array.isArray(runsResult.value) ? runsResult.value : []
  else if (!runsResult.reason?.stale) runsError.value = runsResult.reason?.message || '实验列表读取失败'
  if (generation === pageGeneration && accountGeneration === session.generation) loading.value = false
}

// 单独刷新研究实验列表。
async function loadExperimentRuns() {
  if (!session.token) return
  runsLoading.value = true; runsError.value = ''
  try {
    const result = await listExperiments(50)
    runs.value = Array.isArray(result) ? result : []
  } catch (error) {
    if (!error?.stale) runsError.value = error.message || '实验列表读取失败'
  } finally { runsLoading.value = false }
}

// 选择实验运行并加载完整指标明细。
async function selectExperiment(event) {
  const index = Number(event.detail.value)
  const run = runs.value[index]
  const requestId = ++detailRequestId
  selectedRunId.value = run ? String(run.id) : ''
  selectedMetrics.value = []
  if (!run) return
  const generation = pageGeneration
  detailLoading.value = true
  try {
    const detail = await getExperimentDetail(run.id)
    if (generation === pageGeneration && requestId === detailRequestId && String(run.id) === selectedRunId.value) {
      selectedMetrics.value = Array.isArray(detail?.metrics) ? detail.metrics : []
    }
  } catch (error) {
    if (generation === pageGeneration && requestId === detailRequestId && !error?.stale) uni.showToast({ title: error.message || '实验指标读取失败', icon: 'none' })
  } finally {
    if (generation === pageGeneration && requestId === detailRequestId) detailLoading.value = false
  }
}

// 校验问题长度并提交至内部知识库，保留后端回答或拒答结果。
async function submitRagQuery() {
  const question = ragQuestion.value.trim()
  if (!question) return uni.showToast({ title: '请输入问题', icon: 'none' })
  if (question.length > 1000) return uni.showToast({ title: '问题不能超过1000字', icon: 'none' })
  ragLoading.value = true; ragError.value = ''; ragResult.value = null
  const generation = session.generation
  try {
    const result = await queryResearchEvidence(question, 5)
    if (generation === session.generation) ragResult.value = result
  } catch (error) {
    if (!error?.stale && generation === session.generation) ragError.value = error.message || '证据查询失败'
  } finally { if (generation === session.generation) ragLoading.value = false }
}

// 显示实验运行状态对应的视觉样式。
function statusClass(status) { return ({ PASSED: 'status-success', FAILED: 'status-failed', MIXED: 'status-warning' })[status] || 'status-neutral' }

// 格式化数字指标，保留适合阅读的有效精度。
function formatMetricValue(value) {
  if (value === null || value === undefined) return '—'
  if (typeof value !== 'number') return String(value)
  return Math.abs(value) < 1 && value !== 0 ? value.toFixed(6) : value.toFixed(4)
}

// 显示后端可能以双百分号保存的摘要文本。
function normalizeSummary(value) { return value ? String(value).replace(/%%/g, '%') : '' }

// 格式化检索得分，缺失时显示占位符。
function formatScore(value) { return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(4) : '—' }

// 跳转到小程序账号登录页。
function goLogin() { uni.navigateTo({ url: '/pages/login/login' }) }
</script>

<style scoped>
.research-page { padding-bottom: 48rpx; }
.notice-card { margin: 18rpx 0; padding: 22rpx 24rpx; border: 1rpx solid #f0d9a8; border-radius: 16rpx; background: #fffaf0; }
.notice-text { color: #805b16; font-size: 25rpx; line-height: 1.6; }
.pipeline-card { margin-bottom: 20rpx; }
.pipeline-row { display: flex; justify-content: space-between; gap: 8rpx; margin: 28rpx 0 20rpx; }
.pipeline-step { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; text-align: center; gap: 10rpx; }
.step-dot { width: 38rpx; height: 38rpx; border-radius: 50%; border: 2rpx solid #cbd5e1; color: #64748b; line-height: 34rpx; font-size: 22rpx; }
.step-done { color: #fff; background: #1f6b4f; border-color: #1f6b4f; }
.step-label { font-size: 23rpx; color: #334155; }
.small-button { margin: 12rpx 0 0; font-size: 24rpx; }
.section-heading { margin: 24rpx 4rpx 12rpx; }
.inner-heading { display: flex; justify-content: space-between; align-items: center; margin: 0 0 18rpx; }
.run-card-list { display: flex; flex-direction: column; gap: 16rpx; }
.run-card { padding: 24rpx; }
.empty-run { display: block; padding: 24rpx 0 4rpx; text-align: center; }
.run-heading, .metric-row-title { display: flex; justify-content: space-between; align-items: center; gap: 12rpx; }
.run-name { font-size: 28rpx; font-weight: 600; color: #1f2937; }
.status-tag { flex: none; padding: 5rpx 12rpx; border-radius: 18rpx; font-size: 20rpx; }
.status-success { color: #17633e; background: #e8f5ed; }
.status-failed { color: #a33434; background: #fff0f0; }
.status-warning { color: #8b5b11; background: #fff5df; }
.status-neutral { color: #64748b; background: #f1f5f9; }
.run-title { display: block; margin-top: 18rpx; color: #334155; font-size: 26rpx; }
.summary-text { display: block; margin-top: 14rpx; color: #475569; line-height: 1.6; font-size: 25rpx; }
.key-metrics, .metric-table { margin-top: 16rpx; border-top: 1rpx solid #eef1f4; }
.metric-line, .metric-row { padding: 16rpx 0; border-bottom: 1rpx solid #eef1f4; font-size: 24rpx; color: #475569; }
.metric-value { flex: none; color: #1f6b4f; font-weight: 600; }
.picker-field { margin-top: 18rpx; padding: 22rpx; border: 1rpx solid #d8dee6; border-radius: 12rpx; color: #334155; font-size: 25rpx; }
.metric-row-title { margin-bottom: 8rpx; }
.rag-card { margin-top: 20rpx; }
.rag-description { display: block; margin: 14rpx 0; line-height: 1.5; }
.question-field { width: auto; min-height: 150rpx; padding: 18rpx; border: 1rpx solid #d8dee6; border-radius: 12rpx; font-size: 26rpx; }
.character-count { display: block; text-align: right; color: #94a3b8; font-size: 21rpx; }
.answer-block { margin-top: 24rpx; padding-top: 20rpx; border-top: 1rpx solid #e5e7eb; }
.answer-title, .evidence-heading { display: block; margin-bottom: 12rpx; font-weight: 600; color: #1f2937; }
.answer-text, .evidence-text { display: block; margin: 12rpx 0; color: #334155; line-height: 1.7; font-size: 25rpx; white-space: pre-wrap; }
.score-row { display: flex; flex-direction: column; gap: 8rpx; padding: 14rpx; background: #f7f9fb; border-radius: 10rpx; color: #64748b; font-size: 22rpx; }
.evidence-section { margin-top: 22rpx; }
.citation-row { display: flex; justify-content: space-between; padding: 10rpx 0; color: #475569; font-size: 23rpx; }
.evidence-card { display: flex; flex-direction: column; gap: 8rpx; margin-top: 12rpx; padding: 16rpx; border: 1rpx solid #e5e7eb; border-radius: 10rpx; }
.limitation-text { color: #9a641c; font-size: 22rpx; line-height: 1.5; }
</style>
