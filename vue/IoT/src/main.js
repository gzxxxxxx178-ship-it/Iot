import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElAlert from 'element-plus/es/components/alert/index.mjs'
import ElButton from 'element-plus/es/components/button/index.mjs'
import ElCard from 'element-plus/es/components/card/index.mjs'
import ElCol from 'element-plus/es/components/col/index.mjs'
import ElDatePicker from 'element-plus/es/components/date-picker/index.mjs'
import ElDialog from 'element-plus/es/components/dialog/index.mjs'
import ElDropdown, {
  ElDropdownItem,
  ElDropdownMenu,
} from 'element-plus/es/components/dropdown/index.mjs'
import ElEmpty from 'element-plus/es/components/empty/index.mjs'
import ElForm, { ElFormItem } from 'element-plus/es/components/form/index.mjs'
import ElIcon from 'element-plus/es/components/icon/index.mjs'
import ElInput from 'element-plus/es/components/input/index.mjs'
import ElInputNumber from 'element-plus/es/components/input-number/index.mjs'
import ElPagination from 'element-plus/es/components/pagination/index.mjs'
import ElResult from 'element-plus/es/components/result/index.mjs'
import ElRow from 'element-plus/es/components/row/index.mjs'
import ElSelect, { ElOption } from 'element-plus/es/components/select/index.mjs'
import ElSwitch from 'element-plus/es/components/switch/index.mjs'
import ElTable, { ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import ElTag from 'element-plus/es/components/tag/index.mjs'
import ElTooltip from 'element-plus/es/components/tooltip/index.mjs'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()

// 注册页面模板实际使用的Element Plus组件，避免引入未使用的组件。
const elementComponents = [
  ElAlert, ElButton, ElCard, ElCol, ElDatePicker, ElDialog, ElDropdown,
  ElDropdownItem, ElDropdownMenu, ElEmpty, ElForm, ElFormItem, ElIcon,
  ElInput, ElInputNumber, ElOption, ElPagination, ElResult, ElRow,
  ElSelect, ElSwitch, ElTable, ElTableColumn, ElTag, ElTooltip,
]
elementComponents.forEach((component) => app.component(component.name, component))

app.use(pinia)
app.use(router)
app.mount('#app')
