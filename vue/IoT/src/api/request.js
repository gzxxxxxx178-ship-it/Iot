import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken, removeUsername } from '../utils/auth'

// 创建 axios 实例：开发环境走 VITE_API_BASE_URL 或 localhost:8080，生产环境取环境变量
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || (import.meta.env.DEV ? 'http://localhost:8080' : ''),
  timeout: 10000,
})

// 请求拦截器：自动注入 JWT Bearer token
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：自动解包后端统一响应格式 {code, message, data}，401 时清除登录态
request.interceptors.response.use(
  (response) => {
    const body = response.data
    // 识别统一响应格式 {code, message, data} 并自动解包
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        // 成功 → 只返回 data 字段，调用方无需再 .data
        return body.data
      }
      // 业务错误 (code != 200) → 弹出错误提示
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 非统一格式（如纯文本响应），原样返回
    return body
  },
  (error) => {
    // HTTP 级别错误（网络异常、Spring Security 401 等）
    if (error.response && error.response.status === 401) {
      removeToken()
      removeUsername()
      window.location.hash = '#/login'
      return Promise.reject(error)
    }
    // 如果响应体是 ApiResponse 格式，优先使用其 message
    const body = error.response?.data
    const msg = (body && typeof body === 'object' && body.message)
      ? body.message
      : error.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default request
