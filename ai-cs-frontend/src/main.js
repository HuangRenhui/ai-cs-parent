import { createApp } from 'vue'
import ElementPlus from 'element-plus'
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
app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.component('TblAct', TblAct)
app.component('TbBtn', TbBtn)
app.component('FilterActions', FilterActions)
app.component('TypeTag', TypeTag)
app.component('CellText', CellText)
app.component('StubBanner', StubBanner)
app.mount('#app')