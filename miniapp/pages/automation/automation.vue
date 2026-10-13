<template>
  <view class="page">
    <!-- 页面顶部：标题 + 演示标记 -->
    <PageHeader title="自动化规则" :subtitle="'共 ' + store.rules.length + ' 条规则'" />

    <!-- 规则列表 -->
    <view v-if="store.rules.length" class="list">
      <view v-for="rule in store.rules" :key="rule.id" class="rule-card">
        <view class="rule-top">
          <view class="rule-name-wrap">
            <text class="rule-name">{{ rule.name }}</text>
            <StatusTag :text="rule.enabled ? '启用' : '停用'" :kind="rule.enabled ? 'green' : 'gray'" />
          </view>
          <switch :checked="rule.enabled" color="#1F6B4F" @change="onToggle(rule, $event)" />
        </view>
        <text class="rule-line">{{ rule.condition }} → {{ rule.action }}</text>
        <view class="rule-actions">
          <view class="rule-delete" @tap="onDelete(rule)">
            <text class="rule-delete-text">删除</text>
          </view>
        </view>
      </view>
    </view>
    <EmptyState v-else text="暂无自动化规则" />

    <!-- 新增按钮 -->
    <view class="footer-add">
      <button class="btn-primary" @tap="openAdd">新增规则</button>
    </view>

    <!-- 新增规则弹层（内嵌表单） -->
    <view v-if="showAdd" class="mask" @tap="closeAdd">
      <view class="form-sheet" @tap.stop>
        <text class="form-title">新增自动化规则</text>

        <text class="form-label">规则名称</text>
        <input v-model="form.name" class="form-input" placeholder="例如：夜间自动通风" placeholder-class="ph" />

        <text class="form-label">监测指标</text>
        <view class="seg">
          <view v-for="m in metrics" :key="m" class="seg-item" :class="{ active: form.metric === m }" @tap="form.metric = m">
            <text class="seg-text">{{ m }}</text>
          </view>
        </view>

        <text class="form-label">运算符</text>
        <view class="seg">
          <view v-for="op in operators" :key="op" class="seg-item" :class="{ active: form.operator === op }" @tap="form.operator = op">
            <text class="seg-text">{{ op }}</text>
          </view>
        </view>

        <text class="form-label">阈值</text>
        <input v-model="form.threshold" class="form-input" type="digit" placeholder="例如：30" placeholder-class="ph" />

        <text class="form-label">执行动作</text>
        <input v-model="form.action" class="form-input" placeholder="例如：打开通风设备" placeholder-class="ph" />

        <view class="form-actions">
          <button class="btn-secondary form-btn" @tap="closeAdd">取消</button>
          <button class="btn-primary form-btn" @tap="saveRule">保存</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
// 自动化规则页：规则开关、新增（内嵌表单）、删除确认（仅修改演示状态）
import { ref, reactive } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import StatusTag from '../../components/StatusTag.vue'
import EmptyState from '../../components/EmptyState.vue'
import { useDemoStore, addRule, deleteRule, toggleRule } from '../../utils/demo-store.js'

const store = useDemoStore()

// 监测指标选项
const metrics = ['温度', '湿度', '水位']

// 运算符选项
const operators = ['>', '<', '=']

// 是否显示新增弹层
const showAdd = ref(false)

// 新增规则表单数据
const form = reactive({
  name: '',
  metric: '温度',
  operator: '>',
  threshold: '',
  action: ''
})

// 打开新增规则弹层
function openAdd() {
  showAdd.value = true
}

// 关闭新增规则弹层
function closeAdd() {
  showAdd.value = false
}

// 保存规则：校验必填项后写入演示状态
function saveRule() {
  if (!form.name.trim() || !form.threshold.trim() || !form.action.trim()) {
    uni.showToast({ title: '请填写完整规则', icon: 'none' })
    return
  }
  if (!Number.isFinite(Number(form.threshold))) {
    uni.showToast({ title: '阈值必须为有效数字', icon: 'none' })
    return
  }
  const unit = form.metric === '湿度' ? '%' : form.metric === '水位' ? 'cm' : '℃'
  addRule({
    name: form.name.trim(),
    condition: form.metric + ' ' + form.operator + ' ' + form.threshold.trim() + unit,
    action: form.action.trim()
  })
  form.name = ''
  form.threshold = ''
  form.action = ''
  showAdd.value = false
  uni.showToast({ title: '已添加', icon: 'none' })
}

// 切换规则启用状态
function onToggle(rule) {
  toggleRule(rule.id)
}

// 删除规则：二次确认后移除
function onDelete(rule) {
  uni.showModal({
    title: '删除规则',
    content: '确认删除「' + rule.name + '」？',
    success: (res) => {
      if (res.confirm) {
        deleteRule(rule.id)
        uni.showToast({ title: '已删除', icon: 'none' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  padding-bottom: 40rpx;
}
.rule-card {
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  padding: 24rpx;
  margin: 0 $space-page 20rpx;
}
.rule-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.rule-name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
}
.rule-name {
  font-size: 28rpx;
  font-weight: 600;
  color: $color-text;
  margin-right: 12rpx;
  word-break: break-all;
}
.rule-line {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: $color-text-secondary;
  line-height: 1.5;
}
.rule-actions {
  margin-top: 20rpx;
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
}
.rule-delete {
  padding: 8rpx 28rpx;
  background-color: $color-red-soft;
  border-radius: $radius-pill;
}
.rule-delete-text {
  font-size: 24rpx;
  color: $color-red;
}
.footer-add {
  margin: 8rpx $space-page 40rpx;
}
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.form-sheet {
  width: 100%;
  max-height: 80vh;
  overflow-y: auto;
  background-color: $color-bg;
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx;
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
}
.form-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: $color-text;
  margin-bottom: 24rpx;
}
.form-label {
  display: block;
  font-size: 26rpx;
  color: $color-text;
  margin: 16rpx 0 8rpx;
}
.form-input {
  height: 88rpx;
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: $color-text;
}
.ph {
  color: $color-text-light;
}
.seg {
  display: flex;
  flex-direction: row;
  background-color: $color-card;
  border: 1rpx solid $color-border;
  border-radius: $radius-card;
  padding: 6rpx;
}
.seg-item {
  flex: 1;
  text-align: center;
  padding: 12rpx 0;
  border-radius: $radius-pill;
}
.seg-item.active {
  background-color: $color-primary;
}
.seg-text {
  font-size: 26rpx;
  color: $color-text-secondary;
}
.seg-item.active .seg-text {
  color: #FFFFFF;
}
.form-actions {
  display: flex;
  flex-direction: row;
  margin-top: 32rpx;
}
.form-btn {
  flex: 1;
  margin: 0 8rpx;
}
</style>
