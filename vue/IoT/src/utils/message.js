import ElMessage from 'element-plus/es/components/message/index.mjs'
import ElMessageBox from 'element-plus/es/components/message-box/index.mjs'

// 统一从组件级入口提供通知能力，避免业务模块依赖Element Plus聚合入口。
export { ElMessage, ElMessageBox }
