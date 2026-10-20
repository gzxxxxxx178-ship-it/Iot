<template>
  <view class="page">
    <PageHeader :title="mode === 'login' ? '账号登录' : '创建账号'" :subtitle="mode === 'login' ? '使用现有系统账号' : '注册成功后自动登录'" />
    <view class="card">
      <text class="hint">后端：{{ config.apiBaseUrl }}</text>
      <text class="label">用户名</text>
      <input v-model="username" class="field" :disabled="busy" :maxlength="mode === 'register' ? 100 : 255" placeholder="用户名" />
      <text class="label">密码</text>
      <input v-model="password" class="field" :disabled="busy" password :placeholder="mode === 'register' ? '至少 6 位密码' : '密码不保存'" />
      <template v-if="mode === 'register'">
        <text class="label">确认密码</text>
        <input v-model="confirmPassword" class="field" :disabled="busy" password placeholder="再次输入密码" />
        <text class="hint password-note">注册密码至少 6 位。</text>
      </template>
      <text v-if="error" class="hint">{{ error }}</text>
      <button class="btn-primary submit" :loading="busy" :disabled="busy" @tap="mode === 'login' ? onLogin() : onRegister()">{{ busy ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}</button>
      <view class="mode-switch">
        <text class="hint">{{ mode === 'login' ? '还没有账号？' : '已有账号？' }}</text>
        <text class="switch-action" :class="{ disabled: busy }" @tap="switchMode">{{ mode === 'login' ? '立即注册' : '返回登录' }}</text>
      </view>
      <text class="hint security-note">密码不会保存在小程序中。访问令牌仅保存在运行内存；已登录设备会保存可撤销的刷新凭据，最长有效30天。</text>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onHide } from '@dcloudio/uni-app'
import PageHeader from '../../components/PageHeader.vue'
import { APP_CONFIG as config } from '../../config/index.js'
import { login, register, logout } from '../../utils/live-store.js'
const mode = ref('login')
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const busy = ref(false)
const error = ref('')

// 提交真实账号并清空密码输入，失败时保留可操作提示。
async function onLogin() {
  if (busy.value) return
  if (!username.value.trim() || !password.value) { error.value = '请输入用户名和密码'; return }
  busy.value = true; error.value = ''
  const submitted = password.value
  password.value = ''
  confirmPassword.value = ''
  try {
    await login(username.value, submitted)
    busy.value = false
    uni.switchTab({ url: '/pages/overview/overview' })
  } catch (err) { if (!err.stale) error.value = err.message }
  finally { busy.value = false }
}

// 校验注册信息，调用后端注册接口并自动进入已登录状态。
async function onRegister() {
  if (busy.value) return
  const account = username.value.trim()
  if (!account) { error.value = '请输入用户名'; return }
  if (account.length > 100) { error.value = '用户名不能超过100个字符'; return }
  if (!password.value) { error.value = '请输入密码'; return }
  if (password.value.length < 6) { error.value = '密码长度至少6位'; return }
  if (password.value !== confirmPassword.value) { error.value = '两次输入的密码不一致'; return }
  busy.value = true; error.value = ''
  const submittedPassword = password.value
  password.value = ''
  confirmPassword.value = ''
  try {
    await register(account, submittedPassword)
    busy.value = false
    uni.showToast({ title: '注册成功', icon: 'success' })
    uni.switchTab({ url: '/pages/overview/overview' })
  } catch (err) { if (!err.stale) error.value = err.message || '注册失败' }
  finally { busy.value = false }
}

// 切换登录或注册表单并清除敏感输入。
function switchMode() {
  if (busy.value) return
  mode.value = mode.value === 'login' ? 'register' : 'login'
  password.value = ''
  confirmPassword.value = ''
  error.value = ''
}

// 离开时清理密码，并作废未完成的登录。
onHide(() => { password.value = ''; if (busy.value) logout() })
</script>
<style lang="scss" scoped>
.label { display: block; margin: 24rpx 0 8rpx; }
.field { height: 88rpx; padding: 0 20rpx; border: 1rpx solid $color-border; border-radius: $radius-card; background: $color-bg; }
.submit { margin: 28rpx 0 20rpx; }
.hint { overflow-wrap: anywhere; }
.password-note { display: block; margin-top: 8rpx; }
.mode-switch { display: flex; justify-content: center; gap: 10rpx; margin: 8rpx 0 22rpx; }
.switch-action { color: $color-primary; font-size: 24rpx; }
.switch-action.disabled { opacity: .5; }
.security-note { display: block; line-height: 1.5; }
</style>
