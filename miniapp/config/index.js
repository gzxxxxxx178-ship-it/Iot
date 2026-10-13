// config/index.js
// 小程序配置模块：说明当前运行模式与未来后端地址，不发起任何网络请求

// 全局配置对象：所有页面只读取这里的静态说明，不据此发送请求
export const APP_CONFIG = {
  // 运行模式：'demo' 表示仅使用本地演示数据；接入真实后端后再改为 'production'
  mode: 'demo',
  // 未来后端基础地址（HTTPS）。待域名备案与微信后台域名白名单确认后再填写，当前保持为空
  apiBaseUrl: '',
  // 未来实时推送地址（WSS）。仅在接入真实后端时使用，当前保持为空
  wsBaseUrl: '',
  // 是否在页面顶部展示“演示”状态标记
  showDemoBadge: true
}

// 判断当前是否处于演示模式
export function isDemoMode() {
  return APP_CONFIG.mode === 'demo'
}
