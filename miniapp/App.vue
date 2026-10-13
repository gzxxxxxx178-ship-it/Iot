<script>
// App.vue
// 应用根组件：定义全局生命周期与全局样式，不包含任何网络请求
export default {
  // 应用启动回调：当前仅演示模式，无需初始化网络或登录态
  onLaunch() {
    // 演示模式：不做任何真实初始化，仅保留空实现
  },
  // 应用进入前台回调
  onShow() {},
  // 应用进入后台回调
  onHide() {}
}
</script>

<style lang="scss">
/* 全局基础样式：浅灰背景 + 墨绿主色，无渐变、无外部字体/CDN */
page {
  background-color: $color-bg;
  color: $color-text;
  font-size: 28rpx;
  line-height: 1.5;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", sans-serif;
}

view, text, button, input, scroll-view {
  box-sizing: border-box;
}

/* 白色卡片容器 */
.card {
  background-color: $color-card;
  border-radius: $radius-card;
  padding: $space-card;
  margin: 0 $space-page $space-page;
  border: 1rpx solid $color-border;
}

/* 区块标题 */
.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $color-text;
  margin: 8rpx 0 20rpx;
}

/* 主按钮（墨绿） */
.btn-primary {
  background-color: $color-primary;
  color: #FFFFFF;
  border-radius: $radius-pill;
  font-size: 28rpx;
  line-height: 2.6;
  text-align: center;
  padding: 0 40rpx;
  border: none;
}
.btn-primary::after {
  border: none;
}

/* 次级按钮（白底描边） */
.btn-secondary {
  background-color: $color-card;
  color: $color-primary;
  border: 1rpx solid $color-primary;
  border-radius: $radius-pill;
  font-size: 28rpx;
  line-height: 2.6;
  text-align: center;
  padding: 0 40rpx;
}
.btn-secondary::after {
  border: none;
}

/* 危险按钮（红） */
.btn-danger {
  background-color: $color-red-soft;
  color: $color-red;
  border: 1rpx solid $color-red;
  border-radius: $radius-pill;
  font-size: 28rpx;
  line-height: 2.6;
  text-align: center;
  padding: 0 40rpx;
}
.btn-danger::after {
  border: none;
}

/* 通用行布局 */
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.row-between {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

/* 次级说明文字 */
.hint {
  font-size: 24rpx;
  color: $color-text-secondary;
  line-height: 1.6;
}

/* 页面级演示提示条 */
.demo-note {
  margin: 0 $space-page 20rpx;
  padding: 16rpx 24rpx;
  background-color: $color-primary-soft;
  color: $color-primary;
  font-size: 24rpx;
  border-radius: $radius-card;
  line-height: 1.5;
}
</style>
