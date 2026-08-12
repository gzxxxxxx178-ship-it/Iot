<template>
  <div class="research-workbench">
    <!-- 顶部固定安全警示 -->
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      class="safety-banner"
    >
      <template #title>
        <strong>⚠ 合成仿真/内部小样本，不代表田间实测或生产安全认证</strong>
      </template>
    </el-alert>

    <!-- 链路状态 -->
    <el-card class="pipeline-card">
      <template #header>
        <span>🔗 研究链路状态</span>
      </template>
      <el-steps :active="pipelineStep" finish-status="success" align-center simple>
        <el-step title="MATLAB冻结实验" description="已冻结" />
        <el-step title="Java鉴权校验" :description="pipelineDesc.auth" />
        <el-step title="MySQL持久化" :description="pipelineDesc.db" />
        <el-step title="Vue展示" description="当前页面" />
      </el-steps>
    </el-card>

    <!-- 实验结论区域 -->
    <el-card class="conclusions-card">
      <template #header>
        <span>📊 实验结论</span>
      </template>
      <el-row :gutter="20">
        <!-- MPC 卡片 -->
        <el-col :xs="24" :sm="8">
          <el-card shadow="hover" class="result-card mpc-card">
            <template #header>
              <div class="card-header">
                <span>MPC 基线实验</span>
                <el-tag :type="statusTagType(overview.latestMpc?.status)" size="small">
                  {{ overview.latestMpc?.status || '未上传' }}
                </el-tag>
              </div>
            </template>
            <div v-if="overview.latestMpc?.runId" class="card-body">
              <p><strong>结论：</strong>MPC水位控制优于规则，但用水量增加</p>
              <p class="limitation-text">{{ normalizeSummary(overview.latestMpc?.summary) || 'MPC使用完美预报假设，不可声称节水' }}</p>
              <ul class="key-findings" v-if="mpcDetailMetrics.length">
                <li v-for="(def, idx) in mpcKeyMetrics" :key="idx">
                  {{ formatKeyMetricBullet(def, mpcDetailMetrics) }}
                </li>
              </ul>
            </div>
            <div v-else class="empty-card">等待MATLAB发布</div>
          </el-card>
        </el-col>

        <!-- RL/SAC 卡片 -->
        <el-col :xs="24" :sm="8">
          <el-card shadow="hover" class="result-card rl-card">
            <template #header>
              <div class="card-header">
                <span>残差SAC 实验</span>
                <el-tag :type="statusTagType(overview.latestRl?.status)" size="small">
                  {{ overview.latestRl?.status || '未上传' }}
                </el-tag>
              </div>
            </template>
            <div v-if="overview.latestRl?.runId" class="card-body">
              <p><strong>结论：</strong>❌ 当前 SAC 全指标劣于 MPC</p>
              <p class="limitation-text">{{ normalizeSummary(overview.latestRl?.summary) || 'SAC不足说明方法无效，仅平台验证通过' }}</p>
              <ul class="key-findings" v-if="rlDetailMetrics.length">
                <li v-for="(def, idx) in rlKeyMetrics" :key="idx">
                  {{ formatKeyMetricBullet(def, rlDetailMetrics) }}
                </li>
              </ul>
            </div>
            <div v-else class="empty-card">等待MATLAB发布</div>
          </el-card>
        </el-col>

        <!-- RAG 卡片 -->
        <el-col :xs="24" :sm="8">
          <el-card shadow="hover" class="result-card rag-card">
            <template #header>
              <div class="card-header">
                <span>RAG 检索实验</span>
                <el-tag :type="statusTagType(overview.latestRag?.status)" size="small">
                  {{ overview.latestRag?.status || '未上传' }}
                </el-tag>
              </div>
            </template>
            <div v-if="overview.latestRag?.runId" class="card-body">
              <p><strong>结论：</strong>BM25 优于 hybrid，默认仅使用 BM25</p>
              <p class="limitation-text">{{ normalizeSummary(overview.latestRag?.summary) || '内部小样本评估，非严格盲测' }}</p>
              <ul class="key-findings" v-if="ragDetailMetrics.length">
                <li v-for="(def, idx) in ragKeyMetrics" :key="idx">
                  {{ formatKeyMetricBullet(def, ragDetailMetrics) }}
                </li>
              </ul>
            </div>
            <div v-else class="empty-card">等待MATLAB发布</div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 指标对比表 -->
    <el-card class="metrics-card">
      <template #header>
        <div class="card-header-row">
          <span>📈 指标对比</span>
          <el-select
            v-model="selectedRunId"
            placeholder="选择实验运行"
            clearable
            style="width: 280px"
            @change="loadMetricsTable"
          >
            <el-option
              v-for="run in experimentRuns"
              :key="run.id"
              :label="`[${run.experimentType}] ${run.title} (${run.runKey})`"
              :value="run.id"
            />
          </el-select>
        </div>
      </template>
      <el-table
        v-if="metricsTable.length > 0"
        :data="metricsTable"
        border
        stripe
        size="small"
        style="width: 100%"
      >
        <el-table-column prop="methodName" label="方法" width="140" />
        <el-table-column prop="metricName" label="指标" width="180" />
        <el-table-column prop="metricValue" label="数值" width="140">
          <template #default="scope">
            {{ formatMetricValue(scope.row.metricValue) }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="higherIsBetter" label="越高越好" width="100">
          <template #default="scope">
            <span v-if="scope.row.higherIsBetter === null">—</span>
            <el-tag v-else :type="scope.row.higherIsBetter ? 'success' : 'danger'" size="small">
              {{ scope.row.higherIsBetter ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="notes" label="备注" min-width="200" />
      </el-table>
      <el-empty v-else description="选择实验运行以查看指标" :image-size="60" />
    </el-card>

    <!-- RAG 证据问答 -->
    <el-card class="rag-query-card">
      <template #header>
        <span>🔍 RAG 证据问答</span>
      </template>
      <el-alert type="info" :closable="false" class="rag-hint">
        <strong>仅解释，不下发设备命令。</strong> 本功能基于内部BM25知识库检索，不调用外部LLM。
      </el-alert>

      <div class="rag-input-row">
        <el-input
          v-model="ragQuestion"
          placeholder="输入农业灌溉相关问题（1-1000字符）"
          maxlength="1000"
          show-word-limit
          @keyup.enter="submitRagQuery"
          style="flex: 1; margin-right: 12px"
        />
        <el-button type="primary" @click="submitRagQuery" :loading="ragLoading">
          查询
        </el-button>
      </div>

      <!-- 拒答状态 -->
      <div v-if="ragResult && ragResult.abstained" class="rag-abstained">
        <el-result icon="warning" title="系统拒绝回答">
          <template #sub-title>
            <p><strong>拒答原因：</strong>{{ ragResult.abstainReason }}</p>
          </template>
        </el-result>
        <div class="rag-answer-text">{{ ragResult.answer }}</div>
      </div>

      <!-- 正常回答 -->
      <div v-if="ragResult && !ragResult.abstained" class="rag-answer-section">
        <div class="rag-score-info">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="检索器">{{ ragResult.retriever }}</el-descriptions-item>
            <el-descriptions-item label="最高得分">
              {{ ragResult.topScore?.toFixed(4) }}
            </el-descriptions-item>
            <el-descriptions-item label="拒答阈值">
              {{ ragResult.threshold?.toFixed(4) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="rag-answer-text">{{ ragResult.answer }}</div>

        <!-- 引用 -->
        <div v-if="ragResult.citations?.length" class="rag-citations">
          <h4>📎 引用</h4>
          <el-tag
            v-for="(cit, idx) in ragResult.citations"
            :key="idx"
            type="info"
            size="small"
            class="citation-tag"
          >
            {{ cit.source_id }}#{{ cit.chunk_id }}
            ({{ cit.score?.toFixed(2) }})
          </el-tag>
        </div>

        <!-- 证据卡片 -->
        <div v-if="ragResult.evidence?.length" class="rag-evidence">
          <h4>📄 证据</h4>
          <el-card
            v-for="(ev, idx) in ragResult.evidence"
            :key="idx"
            shadow="hover"
            class="evidence-card"
          >
            <template #header>
              <span>{{ ev.source_id }}#{{ ev.chunk_id }} — {{ ev.title }}</span>
            </template>
            <p>{{ ev.text }}</p>
            <p v-if="ev.limitations" class="evidence-limitation">
              ⚠️ {{ ev.limitations }}
            </p>
          </el-card>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import {
  listExperiments,
  getExperimentDetail,
  getResearchOverview,
  queryRag,
} from '../api/research'

/** 研究总览数据（最新MPC/RL/RAG） */
const overview = reactive({
  latestMpc: {},
  latestRl: {},
  latestRag: {},
})

/** 实验运行列表 */
const experimentRuns = ref([])

/** 当前选中运行的指标数据 */
const metricsTable = ref([])

/** 选中的运行ID */
const selectedRunId = ref(null)

/** RAG问答状态 */
const ragQuestion = ref('')
const ragResult = ref(null)
const ragLoading = ref(false)

/** 链路状态 */
const pipelineStep = ref(1)
const pipelineDesc = reactive({ auth: '等待中', db: '等待中' })

/** MPC 实验卡片关键指标定义（仅含方法名/指标名键，不含冻结数值） */
const mpcKeyMetrics = [
  { methodName: 'MPC', metricName: '水位MAE', icon: '✅', desc: 'MPC水位MAE' },
  { methodName: '规则控制', metricName: '水位MAE', icon: '', desc: '规则水位MAE' },
  { methodName: 'MPC-规则', metricName: '灌溉水量差', icon: '⚠️', desc: '灌溉水量差' },
  { methodName: 'MPC', metricName: '安全越界次数', icon: '✅', desc: '安全越界' },
]

/** RL/SAC 实验卡片关键指标定义 */
const rlKeyMetrics = [
  { methodName: 'SAC+安全屏蔽', metricName: '水位MAE', icon: '❌', desc: 'SAC水位MAE' },
  { methodName: 'MPC', metricName: '水位MAE', icon: '', desc: 'MPC水位MAE' },
  { methodName: 'SAC', metricName: '更差回合率', icon: '❌', desc: '回合更差率' },
  { methodName: 'SAC', metricName: '安全屏蔽干预', icon: '⚠️', desc: '安全屏蔽干预' },
]

/** RAG 实验卡片关键指标定义 */
const ragKeyMetrics = [
  { methodName: 'BM25', metricName: '拒答准确率', icon: '✅', desc: 'BM25拒答准确率' },
  { methodName: 'BM25', metricName: '误答率', icon: '✅', desc: 'BM25误答率' },
  { methodName: 'BM25', metricName: '引用精确率', icon: '✅', desc: 'BM25引用精确率' },
  { methodName: 'hybrid', metricName: '误答率', icon: '⚠️', desc: 'Hybrid误答率' },
]

/** 各实验类型详情指标缓存（用于动态渲染关键指标bullet） */
const mpcDetailMetrics = ref([])
const rlDetailMetrics = ref([])
const ragDetailMetrics = ref([])

/**
 * 页面初始化：加载总览和实验列表
 */
onMounted(async () => {
  await loadOverview()
  await loadExperimentRuns()
  updatePipeline()
})

/**
 * 加载研究总览数据，并拉取各实验详情指标供卡片动态渲染
 */
async function loadOverview() {
  try {
    const data = await getResearchOverview()
    if (data) {
      overview.latestMpc = data.latestMpc || {}
      overview.latestRl = data.latestRl || {}
      overview.latestRag = data.latestRag || {}

      // 并行加载各实验详情指标（用于关键指标bullet的动态渲染）
      const detailPromises = []
      if (data.latestMpc?.runId) {
        detailPromises.push(
          getExperimentDetail(data.latestMpc.runId).then(d => { mpcDetailMetrics.value = d?.metrics || [] }).catch(() => {})
        )
      }
      if (data.latestRl?.runId) {
        detailPromises.push(
          getExperimentDetail(data.latestRl.runId).then(d => { rlDetailMetrics.value = d?.metrics || [] }).catch(() => {})
        )
      }
      if (data.latestRag?.runId) {
        detailPromises.push(
          getExperimentDetail(data.latestRag.runId).then(d => { ragDetailMetrics.value = d?.metrics || [] }).catch(() => {})
        )
      }
      await Promise.all(detailPromises)
    }
    pipelineDesc.auth = '鉴权通过'
    pipelineDesc.db = overview.latestMpc?.runId ? '数据已写入' : '无数据'
    updatePipeline()
  } catch (e) {
    pipelineDesc.auth = '鉴权失败'
    updatePipeline()
  }
}

/**
 * 加载实验运行列表
 */
async function loadExperimentRuns() {
  try {
    experimentRuns.value = await listExperiments({ limit: 50 }) || []
  } catch (e) {
    // 静默处理
  }
}

/**
 * 选择实验运行，加载其指标数据到对比表
 */
async function loadMetricsTable() {
  if (!selectedRunId.value) {
    metricsTable.value = []
    return
  }
  try {
    const detail = await getExperimentDetail(selectedRunId.value)
    metricsTable.value = detail?.metrics || []
  } catch (e) {
    metricsTable.value = []
  }
}

/**
 * 提交RAG查询
 */
async function submitRagQuery() {
  const q = ragQuestion.value.trim()
  if (!q) return
  ragLoading.value = true
  try {
    ragResult.value = await queryRag({ question: q, topK: 5 })
  } catch (e) {
    ragResult.value = { abstained: true, abstainReason: '请求失败', answer: e.message }
  } finally {
    ragLoading.value = false
  }
}

/**
 * 根据状态返回el-tag类型
 */
function statusTagType(status) {
  if (!status) return 'info'
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'MIXED') return 'warning'
  return 'info'
}

/**
 * 格式化指标数值
 */
function formatMetricValue(val) {
  if (val === null || val === undefined) return '—'
  if (typeof val === 'number') {
    return Math.abs(val) < 1 && val !== 0 ? val.toFixed(6) : val.toFixed(4)
  }
  return String(val)
}

/**
 * 从详情指标列表中查找指定指标并格式化为展示bullet
 * @param {Object} def - 指标定义 {methodName, metricName, icon, desc}
 * @param {Array} metrics - 详情指标数组
 * @returns {string} 格式化后的展示文本
 */
function formatKeyMetricBullet(def, metrics) {
  const m = metrics.find(x => x.methodName === def.methodName && x.metricName === def.metricName)
  if (!m) return `${def.icon} ${def.desc}: —`
  const val = formatMetricValue(m.metricValue)
  const unit = m.unit ? ` ${m.unit}` : ''
  return `${def.icon} ${def.desc}: ${val}${unit}`
}

/**
 * 规范化摘要文本：将连续%%替换为单个%，修复MATLAB sprintf双百分号转义
 * @param {string} text - 原始摘要文本
 * @returns {string} 规范化后的文本
 */
function normalizeSummary(text) {
  if (!text) return ''
  return String(text).replace(/%%/g, '%')
}

/**
 * 更新链路步骤进度
 */
function updatePipeline() {
  if (overview.latestMpc?.runId || overview.latestRl?.runId || overview.latestRag?.runId) {
    pipelineStep.value = 3
  } else if (pipelineDesc.auth === '鉴权通过') {
    pipelineStep.value = 2
  } else {
    pipelineStep.value = 1
  }
}
</script>

<style scoped>
.research-workbench {
  padding: 16px;
  max-width: 1200px;
  margin: 0 auto;
}

.safety-banner {
  margin-bottom: 16px;
}

.pipeline-card {
  margin-bottom: 16px;
}

.conclusions-card {
  margin-bottom: 16px;
}

.result-card {
  height: 100%;
  min-height: 240px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-body {
  font-size: 14px;
}

.card-body p {
  margin: 8px 0;
}

.limitation-text {
  color: #909399;
  font-size: 12px;
}

.key-findings {
  padding-left: 18px;
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.8;
  list-style: none;
}

.empty-card {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}

.mpc-card {
  border-top: 3px solid #409eff;
}

.rl-card {
  border-top: 3px solid #f56c6c;
}

.rag-card {
  border-top: 3px solid #67c23a;
}

.metrics-card {
  margin-bottom: 16px;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rag-query-card {
  margin-bottom: 16px;
}

.rag-hint {
  margin-bottom: 12px;
}

.rag-input-row {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.rag-abstained {
  margin-top: 16px;
}

.rag-answer-section {
  margin-top: 16px;
}

.rag-score-info {
  margin-bottom: 16px;
}

.rag-answer-text {
  white-space: pre-wrap;
  font-size: 14px;
  line-height: 1.8;
  background: var(--el-fill-color-light);
  padding: 16px;
  border-radius: 8px;
  word-break: break-word;
  overflow-wrap: break-word;
}

.rag-citations {
  margin-top: 16px;
}

.rag-citations h4,
.rag-evidence h4 {
  margin: 12px 0 8px;
}

.citation-tag {
  margin-right: 8px;
  margin-bottom: 4px;
}

.rag-evidence {
  margin-top: 16px;
}

.evidence-card {
  margin-bottom: 12px;
}

.evidence-card p {
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
  overflow-wrap: break-word;
}

.evidence-limitation {
  color: #e6a23c;
  font-size: 12px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .rag-input-row {
    flex-direction: column;
  }
  .rag-input-row .el-input {
    margin-right: 0;
    margin-bottom: 8px;
  }
  .card-header-row {
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }
}
</style>
