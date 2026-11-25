import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import './assets/responsive.css'
import './assets/theme.css'
import App from './App.vue'
import router from './router'
import TblAct from './components/TblAct.vue'
import TbBtn from './components/TbBtn.vue'
import FilterActions from './components/FilterActions.vue'
import TypeTag from './components/TypeTag.vue'
import CellText from './components/CellText.vue'
import StubBanner from './components/StubBanner.vue'

// 日期面板、分页「共 x 条」、空状态等组件文案统一用中文
dayjs.locale('zh-cn')

const app = createApp(App)

// 兜底：组件渲染/生命周期里未捕获的异常不再静默失败，至少留下可排查的日志
let uiErrorNotified = false
app.config.errorHandler = (err, instance, info) => {
  console.error('[ui-error]', info, err)
  if (!uiErrorNotified) {
    uiErrorNotified = true
    ElMessage.error('页面出现异常，如无法操作请刷新重试')
    setTimeout(() => { uiErrorNotified = false }, 5000)
  }
}

// 被请求去重逻辑主动取消的 Promise 不应触发全局错误提示
window.addEventListener('unhandledrejection', (event) => {
  if (event.reason && (event.reason.__canceled || event.reason.name === 'CanceledError')) {
    event.preventDefault()
  }
})

app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.component('TblAct', TblAct)
app.component('TbBtn', TbBtn)
app.component('FilterActions', FilterActions)
app.component('TypeTag', TypeTag)
app.component('CellText', CellText)
app.component('StubBanner', StubBanner)
app.mount('#app')