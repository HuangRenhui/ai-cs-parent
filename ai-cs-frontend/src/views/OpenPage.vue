<template>
  <div class="page-card">
    <p class="hint">
      行业包可插拔：打开电商即走物流/退款工具，关掉电商再「仅启用」金融即切到查账。内核不变。多个包可同时开（例如既有商城又有支付），不必互斥。
    </p>

    <!-- 行业包 / 连接器 / 工具 / 场景横排切片，避免四个标题竖着占满一屏 -->
    <SectionSwitch v-model="section" :options="sectionOptions" />

    <template v-if="section === 'pack'">
      <div class="page-toolbar is-actions-only">
        <TbBtn act="add" label="新增行业包" @click="openPack()" />
        <TbBtn act="delete" :disabled="!packSelected.length" :loading="packRemoving"
          @click="batchRemovePack(deleteOpenPack, '行业包', load, (row) => row.code)" />
      </div>
      <el-table :data="packRecords" stripe empty-text="暂无行业包，可点「新增行业包」添加" table-layout="fixed" max-height="680" @selection-change="onPackSelect">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="行业" min-width="100" />
        <el-table-column prop="code" label="编码" min-width="120" />
        <el-table-column label="说明" min-width="160">
          <template #default="{ row }">
            <CellText title="说明" :text="row.remark" />
          </template>
        </el-table-column>
        <el-table-column label="启用" min-width="72">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="(on) => togglePack(row, on)" />
          </template>
        </el-table-column>
        <el-table-column label="热切换" min-width="148">
          <template #default="{ row }">
            <div class="table-actions">
              <TblAct act="only" @click="onlyThis(row)" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="148">
          <template #default="{ row }">
            <div class="table-actions">
              <TblAct act="edit" @click="openPack(row)" />
              <TblAct act="delete" @click="removePack(row)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="packPage" v-model:size="packSize" :total="packTotal" />
    </template>

    <template v-else-if="section === 'connector'">
      <div class="page-toolbar is-actions-only">
        <TbBtn act="add" label="新增连接器" @click="openConnector()" />
        <TbBtn act="delete" :disabled="!connSelected.length" :loading="connRemoving" @click="batchRemoveConn(deleteConnector, '连接器', load)" />
      </div>
      <el-table :data="connRecords" stripe empty-text="暂无连接器" table-layout="fixed" max-height="680" @selection-change="onConnSelect">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="编号" min-width="56" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" min-width="88">
          <template #default="{ row }">
            <TypeTag :text="displayText(CONNECTOR_TYPE_TEXT, row.type)" />
          </template>
        </el-table-column>
        <el-table-column label="行业包" min-width="100">
          <template #default="{ row }">{{ displayText(PACK_CODE_TEXT, row.packCode) }}</template>
        </el-table-column>
        <el-table-column label="接口地址" min-width="160">
          <template #default="{ row }">
            <CellText title="接口地址" :text="row.baseUrl" />
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" min-width="64">
          <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="148">
          <template #default="{ row }">
            <div class="table-actions">
              <TblAct act="edit" @click="openConnector(row)" />
              <TblAct act="delete" @click="removeConnector(row.id)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="connPage" v-model:size="connSize" :total="connTotal" />
    </template>

    <template v-else-if="section === 'tool'">
      <div class="page-toolbar is-actions-only">
        <TbBtn act="add" label="注册工具" @click="openTool()" />
        <el-button type="primary" :disabled="!toolSelected.length" :loading="toolRemoving" @click="batchTestTool">批量测通</el-button>
        <TbBtn act="delete" :disabled="!toolSelected.length" :loading="toolRemoving" @click="batchRemoveTool(deleteOpenTool, '工具', load)" />
      </div>
      <el-table :data="toolRecords" stripe empty-text="暂无工具" table-layout="fixed" max-height="680" @selection-change="onToolSelect">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="说明" min-width="140">
          <template #default="{ row }">
            <CellText title="说明" :text="row.description" />
          </template>
        </el-table-column>
        <el-table-column label="绑定意图" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ intentName(row.intentBind) }}</template>
        </el-table-column>
        <el-table-column label="行业包" min-width="100">
          <template #default="{ row }">{{ displayText(PACK_CODE_TEXT, row.packCode) }}</template>
        </el-table-column>
        <el-table-column label="风险" min-width="88">
          <template #default="{ row }">
            <TypeTag :text="displayText(RISK_TEXT, row.risk)" />
          </template>
        </el-table-column>
        <el-table-column label="连接器" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ connectorName(row.connectorId) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="200">
          <template #default="{ row }">
            <div class="table-actions">
              <TblAct act="edit" @click="openTool(row)" />
              <TblAct act="test" @click="testTool(row)" />
              <TblAct act="delete" @click="removeTool(row.id)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="toolPage" v-model:size="toolSize" :total="toolTotal" />
    </template>

    <template v-else>
      <div class="page-toolbar is-actions-only">
        <TbBtn act="add" label="新增场景" @click="openScene()" />
        <TbBtn act="delete" :disabled="!sceneSelected.length" :loading="sceneRemoving" @click="batchRemoveScene(deleteSceneConfig, '场景', load)" />
      </div>
      <p class="hint">
        访客从订单、产品、售后等入口打开聊窗时，按场景下发开场白与快捷动作；开场白支持实体编号占位。
      </p>
      <el-table :data="sceneRecords" stripe empty-text="暂无场景配置，请先初始化场景数据" table-layout="fixed" max-height="680" @selection-change="onSceneSelect">
        <el-table-column type="selection" width="48" />
        <el-table-column label="场景编码" min-width="120">
          <template #default="{ row }">{{ displayText(SCENE_CODE_TEXT, row.scene) }}</template>
        </el-table-column>
        <el-table-column prop="sceneName" label="名称" min-width="100" show-overflow-tooltip />
        <el-table-column label="开场白" min-width="160">
          <template #default="{ row }">
            <CellText title="开场白" :text="row.greeting" />
          </template>
        </el-table-column>
        <el-table-column label="快捷动作" min-width="88" align="center">
          <template #default="{ row }">{{ sceneActionCount(row) }}</template>
        </el-table-column>
        <el-table-column prop="sortNum" label="排序" min-width="64" align="center" />
        <el-table-column label="启用" min-width="64">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="(on) => toggleScene(row, on)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="148">
          <template #default="{ row }">
            <div class="table-actions">
              <TblAct act="edit" @click="openScene(row)" />
              <TblAct act="delete" @click="removeScene(row.id)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="scenePage" v-model:size="sceneSize" :total="sceneTotal" />
    </template>

    <el-dialog :title="packForm.editing ? '编辑行业包' : '新增行业包'" v-model="packVisible" width="480px">
      <el-form :model="packForm" label-width="90px">
        <el-form-item label="编码" required>
          <el-input v-model="packForm.code" :disabled="packForm.editing" placeholder="英文小写，例如 ecommerce" maxlength="32" />
        </el-form-item>
        <el-form-item label="行业名称" required>
          <el-input v-model="packForm.name" placeholder="例如 电商" maxlength="32" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="packForm.remark" type="textarea" :rows="2" placeholder="这个包覆盖哪些业务，例如 物流 / 退款" />
        </el-form-item>
        <el-form-item label="创建后启用">
          <el-switch v-model="packForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPack">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="sceneForm.id ? '编辑场景' : '新增场景'" v-model="sceneVisible" width="560px">
      <el-form :model="sceneForm" label-width="110px">
        <el-form-item label="场景编码" required>
          <el-select v-model="sceneForm.scene" filterable allow-create default-first-option :disabled="!!sceneForm.id" style="width: 100%">
            <el-option v-for="s in SCENE_CODE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="场景名称" required>
          <el-input v-model="sceneForm.sceneName" placeholder="订单入口" />
        </el-form-item>
        <el-form-item label="开场白">
          <el-input v-model="sceneForm.greeting" type="textarea" :rows="2" placeholder="支持实体编号占位" />
        </el-form-item>
        <el-form-item label="无实体开场白">
          <el-input v-model="sceneForm.greetingEmpty" type="textarea" :rows="2" placeholder="入口未带实体时使用，可留空" />
        </el-form-item>
        <el-form-item label="快捷动作">
          <el-input v-model="sceneForm.quickActions" type="textarea" :rows="4"
            placeholder='[{"label":"查看订单情况","send":"帮我查一下这个订单的情况"}]' />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="sceneForm.sortNum" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sceneVisible = false">取消</el-button>
        <el-button type="primary" @click="submitScene">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="connForm.id ? '编辑连接器' : '新增连接器'" v-model="connVisible" width="480px">
      <el-form :model="connForm" label-width="90px">
        <el-form-item label="名称"><el-input v-model="connForm.name" /></el-form-item>
        <el-form-item label="类型">
          <!-- 演示 / 接口等分居中，与其它全宽表单项对齐 -->
          <el-radio-group v-model="connForm.type">
            <el-radio-button label="MOCK">演示</el-radio-button>
            <el-radio-button label="REST">接口</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="接口地址"><el-input v-model="connForm.baseUrl" placeholder="仅接口类型需要，填写对方服务地址" /></el-form-item>
        <el-form-item label="行业包">
          <el-select v-model="connForm.packCode" clearable placeholder="可选" style="width: 100%">
            <el-option v-for="p in packs" :key="p.code" :label="p.name" :value="p.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="connVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConnector">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="toolForm.id ? '编辑工具' : '注册工具'" v-model="toolVisible" width="520px">
      <el-form :model="toolForm" label-width="100px">
        <el-form-item label="名称"><el-input v-model="toolForm.name" placeholder="例如查询物流" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="toolForm.description" /></el-form-item>
        <el-form-item label="连接器">
          <el-select v-model="toolForm.connectorId" style="width: 100%">
            <el-option v-for="c in connectors" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定意图">
          <el-select v-model="toolForm.intentBind" filterable clearable placeholder="选择意图，可空" style="width: 100%">
            <el-option v-for="i in intents" :key="i.intentCode" :label="i.intentName" :value="i.intentCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="行业包">
          <el-select v-model="toolForm.packCode" clearable placeholder="可选" style="width: 100%">
            <el-option v-for="p in packs" :key="p.code" :label="p.name" :value="p.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险">
          <el-radio-group v-model="toolForm.risk">
            <el-radio-button label="read">只读</el-radio-button>
            <el-radio-button label="write">写入</el-radio-button>
            <el-radio-button label="critical">高风险</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="请求方法">
          <el-select v-model="toolForm.httpMethod" style="width: 100%">
            <el-option v-for="m in HTTP_METHOD_OPTIONS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="接口路径"><el-input v-model="toolForm.httpPath" placeholder="例如 /接口路径" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="toolVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTool">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activatePack, deleteConnector, deleteOpenPack, deleteOpenTool, deleteSceneConfig, invokeOpenTool, listConnectors, listIntents, listOpenPacks, listOpenTools,
  listSceneConfigs, saveConnector, saveOpenPack, saveOpenTool, saveSceneConfig, setPackEnabled, setSceneConfigEnabled,
  updateConnector, updateOpenPack, updateOpenTool, updateSceneConfig
} from '../api'
import { CONNECTOR_TYPE_TEXT, displayText, HTTP_METHOD_OPTIONS, PACK_CODE_TEXT, RISK_TEXT, SCENE_CODE_OPTIONS, SCENE_CODE_TEXT } from '../utils/selectOptions'
import { useClientPager } from '../composables/useClientPager'
import { useBatchSelect } from '../composables/useBatchSelect'
import TablePager from '../components/TablePager.vue'
import SectionSwitch from '../components/SectionSwitch.vue'

/** 当前切片：pack 行业包 / connector 连接器 / tool 工具 / scene 入口场景 */
const section = ref('pack')
const sectionOptions = [
  { value: 'pack', label: '行业包' },
  { value: 'connector', label: '连接器' },
  { value: 'tool', label: '工具' },
  { value: 'scene', label: '入口场景' }
]

const packs = ref([])
const connectors = ref([])
const tools = ref([])
const scenes = ref([])
const { page: packPage, size: packSize, total: packTotal, records: packRecords } = useClientPager(packs)
const { page: connPage, size: connSize, total: connTotal, records: connRecords } = useClientPager(connectors)
const { page: toolPage, size: toolSize, total: toolTotal, records: toolRecords } = useClientPager(tools)
const { page: scenePage, size: sceneSize, total: sceneTotal, records: sceneRecords } = useClientPager(scenes)
const { selectedRows: packSelected, batchRemoving: packRemoving, onSelect: onPackSelect, batchRemove: batchRemovePack } = useBatchSelect()
const { selectedRows: connSelected, batchRemoving: connRemoving, onSelect: onConnSelect, batchRemove: batchRemoveConn } = useBatchSelect()
const { selectedRows: toolSelected, batchRemoving: toolRemoving, onSelect: onToolSelect, batchRemove: batchRemoveTool, batchAct: batchActTool } = useBatchSelect()
const { selectedRows: sceneSelected, batchRemoving: sceneRemoving, onSelect: onSceneSelect, batchRemove: batchRemoveScene } = useBatchSelect()
const intents = ref([])
const connVisible = ref(false)
const toolVisible = ref(false)
const sceneVisible = ref(false)
const packVisible = ref(false)
/** editing 只用于切换弹窗标题与编码是否可改，提交时按它决定新增还是更新 */
const packForm = reactive({ editing: false, code: '', name: '', remark: '', enabled: 1 })
const connForm = reactive({ id: null, name: '', type: 'MOCK', baseUrl: '', packCode: '', enabled: 1 })
const toolForm = reactive({
  id: null, name: '', description: '', connectorId: null, intentBind: '', packCode: '', risk: 'read', httpMethod: 'POST', httpPath: ''
})
const sceneForm = reactive({ id: null, scene: '', sceneName: '', greeting: '', greetingEmpty: '', quickActions: '', sortNum: 0 })

const load = async () => {
  try {
    const [p, c, t, s, i] = await Promise.all([listOpenPacks(), listConnectors(), listOpenTools(), listSceneConfigs(), listIntents('default')])
    packs.value = p.data || []
    connectors.value = c.data || []
    tools.value = t.data || []
    scenes.value = s.data || []
    intents.value = i.data || []
  } catch {
    packs.value = []
    connectors.value = []
    tools.value = []
    scenes.value = []
    intents.value = []
  }
}

/** 工具表把连接器编号显示成名称 */
const connectorName = (id) => {
  const c = connectors.value.find((x) => String(x.id) === String(id))
  return c ? c.name : (id ? `#${id}` : '—')
}
/** 工具表把意图编码显示成中文名称 */
const intentName = (code) => {
  if (!code) return '—'
  const i = intents.value.find((x) => x.intentCode === code)
  return i ? i.intentName : code
}

const togglePack = async (row, on) => {
  await setPackEnabled(row.code, on ? 1 : 0)
  ElMessage.success(on ? `已启用「${row.name}」` : `已关闭「${row.name}」`)
  load()
}

const onlyThis = async (row) => {
  await activatePack(row.code)
  ElMessage.success(`已切换为仅「${row.name}」，其它行业包已关闭`)
  load()
}

/** 新增传空行，编辑传当前行；编码是业务主键，建好后不允许改 */
const openPack = (row) => {
  Object.assign(packForm, {
    editing: !!row,
    code: row?.code || '',
    name: row?.name || '',
    remark: row?.remark || '',
    enabled: row ? (row.enabled === 1 ? 1 : 0) : 1
  })
  packVisible.value = true
}

const submitPack = async () => {
  const code = String(packForm.code || '').trim()
  if (!code || !packForm.name) {
    ElMessage.warning('请填写行业包编码与名称')
    return
  }
  try {
    if (packForm.editing) {
      await updateOpenPack({ code, name: packForm.name, remark: packForm.remark })
    } else {
      await saveOpenPack({ code, name: packForm.name, remark: packForm.remark, enabled: packForm.enabled })
    }
    ElMessage.success('已保存')
    packVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

const removePack = async (row) => {
  try {
    await ElMessageBox.confirm(
      `删除行业包「${row.name}」？挂在它下面的连接器与工具会失去归属。`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteOpenPack(row.code)
  ElMessage.success('删除成功')
  load()
}

const openConnector = (row) => {
  Object.assign(connForm, { id: null, name: '', type: 'MOCK', baseUrl: '', packCode: '', enabled: 1 })
  if (row) Object.assign(connForm, row)
  connVisible.value = true
}

const submitConnector = async () => {
  if (connForm.id) await updateConnector({ ...connForm })
  else await saveConnector({ ...connForm, id: undefined })
  ElMessage.success('已保存')
  connVisible.value = false
  load()
}

const removeConnector = async (id) => {
  await ElMessageBox.confirm('删除连接器？绑定工具将无法调用。', '提示', { type: 'warning' })
  await deleteConnector(id)
  load()
}

const openTool = (row) => {
  Object.assign(toolForm, {
    id: null, name: '', description: '', connectorId: connectors.value[0]?.id, intentBind: '', packCode: '', risk: 'read', httpMethod: 'POST', httpPath: ''
  })
  if (row) Object.assign(toolForm, row)
  toolVisible.value = true
}

const submitTool = async () => {
  if (toolForm.id) await updateOpenTool({ ...toolForm })
  else await saveOpenTool({ ...toolForm, id: undefined })
  ElMessage.success('已保存')
  toolVisible.value = false
  load()
}

const removeTool = async (id) => {
  await ElMessageBox.confirm('删除该工具？', '提示', { type: 'warning' })
  await deleteOpenTool(id)
  load()
}

const pingTool = async (row) => {
  const res = await invokeOpenTool({
    toolName: row.name,
    entityId: 'TEST',
    entityType: 'entity',
    sessionId: 'open-test',
    confirmed: true,
    idempotencyKey: 'open-test:' + row.name
  })
  const data = res.data
  if (!data?.success) {
    throw new Error(data?.output || '测通失败')
  }
}

const testTool = async (row) => {
  try {
    await ElMessageBox.confirm(`确定测通工具「${row.name}」？`, '确认测通', { type: 'warning' })
  } catch {
    return
  }
  try {
    await pingTool(row)
    ElMessage.success('测通成功')
  } catch (e) {
    ElMessage.warning(e?.message || '测通失败')
  }
}

/** 对勾选工具逐条测通 */
const batchTestTool = () => batchActTool(
  pingTool,
  { noun: '工具', verb: '测通', title: '确认测通', reload: load, labelOf: (row) => row.name }
)

const sceneActionCount = (row) => {
  try {
    const arr = JSON.parse(row.quickActions || '[]')
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return 0
  }
}

const openScene = (row) => {
  Object.assign(sceneForm, { id: null, scene: 'ORDER', sceneName: '', greeting: '', greetingEmpty: '', quickActions: '', sortNum: 0 })
  if (row) Object.assign(sceneForm, row)
  sceneVisible.value = true
}

const submitScene = async () => {
  if (!sceneForm.scene || !sceneForm.sceneName) {
    ElMessage.warning('请填写场景编码与名称')
    return
  }
  if (sceneForm.quickActions) {
    try {
      const arr = JSON.parse(sceneForm.quickActions)
      if (!Array.isArray(arr)) throw new Error('not array')
    } catch {
      ElMessage.warning('快捷动作需为数组，例如 [{"label":"查看订单","send":"查订单"}]')
      return
    }
  }
  if (sceneForm.id) await updateSceneConfig({ ...sceneForm })
  else await saveSceneConfig({ ...sceneForm, id: undefined })
  ElMessage.success('已保存')
  sceneVisible.value = false
  load()
}

const toggleScene = async (row, on) => {
  await setSceneConfigEnabled(row.id, on ? 1 : 0)
  ElMessage.success(on ? `已启用「${row.sceneName}」` : `已停用「${row.sceneName}」`)
  load()
}

const removeScene = async (id) => {
  await ElMessageBox.confirm('删除该场景配置？对应入口将回退到通用开场白。', '提示', { type: 'warning' })
  await deleteSceneConfig(id)
  load()
}

onMounted(load)
</script>

<style scoped>
.hint { color: #6b7280; font-size: 13px; margin: 0 0 16px; }
@media (max-width: 640px) {
  :deep(.el-table) {
    font-size: 13px;
  }
}
</style>
