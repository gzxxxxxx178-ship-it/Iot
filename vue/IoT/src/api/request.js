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

// 响应拦截器：401 时清除登录态并跳转登录页，其他错误弹出提示
request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      removeToken()
      removeUsername()
      window.location.hash = '#/login'
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || error.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  },
)

export default request
