import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken, removeUsername } from '../utils/auth'

const request = axios.create({
  baseURL: import.meta.env.DEV ? (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080') : '',
  timeout: 10000,
})

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
