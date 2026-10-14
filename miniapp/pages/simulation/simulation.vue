<template>
  <view class="page">
    <PageHeader title="仿真验证" subtitle="尚未接入真实接口" badge="待接入" />
    <view class="card"><EmptyState text="该模块尚未迁移真实业务接口" /><text class="hint">当前不生成本地数据或模拟执行结果。</text></view>
  </view>
</template>
<script setup>
import PageHeader from '../../components/PageHeader.vue'
import EmptyState from '../../components/EmptyState.vue'
</script>
