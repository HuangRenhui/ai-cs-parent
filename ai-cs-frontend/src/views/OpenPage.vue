<template>
  <div class="page-card">
    <p class="hint">
      行业包可插拔：打开电商即走物流/退款工具，关掉电商再「仅启用」金融即切到查账。内核不变。多个包可同时开（例如既有商城又有支付），不必互斥。
    </p>

    <div class="page-toolbar">
      <h3>行业包</h3>
    </div>
    <el-table :data="packs" stripe empty-text="请先执行 V1.6.0 行业包脚本">
      <el-table-column prop="name" label="行业" width="100" />
      <el-table-column prop="code" label="编码" width="120" />
      <el-table-column prop="remark" label="说明" min-width="200" />
      <el-table-column label="启用" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => togglePack(row, on)" />
        </template>
      </el-table-column>
      <el-table-column label="热切换" width="140">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="onlyThis(row)">仅启用此包</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-toolbar" style="margin-top: 24px">
      <h3>连接器</h3>
      <el-button type="primary" @click="openConnector()">新增连接器</el-button>
    </div>
    <el-table :data="connectors" stripe empty-text="暂无连接器">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="packCode" label="行业包" width="110" />
      <el-table-column prop="baseUrl" label="REST 地址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openConnector(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="removeConnector(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-toolbar" style="margin-top: 24px">
      <h3>工具</h3>
      <el-button type="primary" @click="openTool()">注册工具</el-button>
    </div>
    <el-table :data="tools" stripe empty-text="暂无工具">
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="intentBind" label="绑定意图" width="100" />
      <el-table-column prop="packCode" label="行业包" width="110" />
      <el-table-column prop="risk" label="风险" width="90" />
      <el-table-column prop="connectorId" label="连接器ID" width="100" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="openTool(row)">编辑</el-button>
          <el-button size="small" @click="testTool(row)">测通</el-button>
          <el-button size="small" type="danger" @click="removeTool(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-toolbar" style="margin-top: 24px">
      <h3>入口场景配置</h3>
      <el-button type="primary" @click="openScene()">新增场景</el-button>
    </div>
    <p class="hint">
      访客从订单/产品/售后等入口打开聊窗（/widget?scene=ORDER&entityType=order&entityId=xxx）时，按场景下发开场白与快捷动作；开场白支持 {entityId} 占位符。
    </p>
    <el-table :data="scenes" stripe empty-text="暂无场景配置，请先执行 scene_config.sql">
      <el-table-column prop="scene" label="场景编码" width="120" />
      <el-table-column prop="sceneName" label="名称" width="110" />
      <el-table-column prop="greeting" label="开场白" min-width="220" show-overflow-tooltip />
      <el-table-column label="快捷动作" width="90" align="center">
        <template #default="{ row }">{{ sceneActionCount(row) }}</template>
      </el-table-column>
      <el-table-column prop="sortNum" label="排序" width="70" align="center" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled === 1" @change="(on) => toggleScene(row, on)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openScene(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="removeScene(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="sceneForm.id ? '编辑场景' : '新增场景'" v-model="sceneVisible" width="560px">
      <el-form :model="sceneForm" label-width="110px">
        <el-form-item label="场景编码" required>
          <el-input v-model="sceneForm.scene" placeholder="ORDER / PRODUCT / AFTER_SALE" :disabled="!!sceneForm.id" />
        </el-form-item>
        <el-form-item label="场景名称" required>
          <el-input v-model="sceneForm.sceneName" placeholder="订单入口" />
        </el-form-item>
        <el-form-item label="开场白">
          <el-input v-model="sceneForm.greeting" type="textarea" :rows="2" placeholder="支持 {entityId} 占位符" />
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
          <el-radio-group v-model="connForm.type">
            <el-radio label="MOCK">MOCK 演示</el-radio>
            <el-radio label="REST">REST</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="baseUrl"><el-input v-model="connForm.baseUrl" placeholder="仅 REST，如 https://api.example.com" /></el-form-item>
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
        <el-form-item label="名称"><el-input v-model="toolForm.name" placeholder="query_logistics" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="toolForm.description" /></el-form-item>
        <el-form-item label="连接器">
          <el-select v-model="toolForm.connectorId" style="width: 100%">
            <el-option v-for="c in connectors" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定意图"><el-input v-model="toolForm.intentBind" placeholder="查物流 / 退款，可空" /></el-form-item>
        <el-form-item label="行业包">
          <el-select v-model="toolForm.packCode" clearable placeholder="可选" style="width: 100%">
            <el-option v-for="p in packs" :key="p.code" :label="p.name" :value="p.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险">
          <el-select v-model="toolForm.risk" style="width: 100%">
            <el-option label="read 只读" value="read" />
            <el-option label="write 写入" value="write" />
            <el-option label="critical 高风险" value="critical" />
          </el-select>
        </el-form-item>
        <el-form-item label="HTTP 方法"><el-input v-model="toolForm.httpMethod" placeholder="POST" /></el-form-item>
        <el-form-item label="HTTP 路径"><el-input v-model="toolForm.httpPath" placeholder="/api/xxx" /></el-form-item>
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
  activatePack, deleteConnector, deleteOpenTool, deleteSceneConfig, invokeOpenTool, listConnectors, listOpenPacks, listOpenTools,
  listSceneConfigs, saveConnector, saveOpenTool, saveSceneConfig, setPackEnabled, setSceneConfigEnabled,
  updateConnector, updateOpenTool, updateSceneConfig
} from '../api'

const packs = ref([])
const connectors = ref([])
const tools = ref([])
const scenes = ref([])
const connVisible = ref(false)
const toolVisible = ref(false)
const sceneVisible = ref(false)
const connForm = reactive({ id: null, name: '', type: 'MOCK', baseUrl: '', packCode: '', enabled: 1 })
const toolForm = reactive({
  id: null, name: '', description: '', connectorId: null, intentBind: '', packCode: '', risk: 'read', httpMethod: 'POST', httpPath: ''
})
const sceneForm = reactive({ id: null, scene: '', sceneName: '', greeting: '', greetingEmpty: '', quickActions: '', sortNum: 0 })

const load = async () => {
  try {
    const [p, c, t, s] = await Promise.all([listOpenPacks(), listConnectors(), listOpenTools(), listSceneConfigs()])
    packs.value = p.data || []
    connectors.value = c.data || []
    tools.value = t.data || []
    scenes.value = s.data || []
  } catch {
    packs.value = []
    connectors.value = []
    tools.value = []
    scenes.value = []
  }
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

const testTool = async (row) => {
  try {
    const res = await invokeOpenTool({
      toolName: row.name,
      entityId: 'TEST',
      entityType: 'entity',
      sessionId: 'open-test',
      confirmed: true,
      idempotencyKey: 'open-test:' + row.name
    })
    const data = res.data
    if (data?.success) {
      ElMessage.success((data.output || '测通成功') + ' [' + (data.source || '') + ']')
    } else {
      ElMessage.warning(data?.output || '测通失败')
    }
  } catch {
    /* 拦截器已提示 */
  }
}

const sceneActionCount = (row) => {
  try {
    const arr = JSON.parse(row.quickActions || '[]')
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return 0
  }
}

const openScene = (row) => {
  Object.assign(sceneForm, { id: null, scene: '', sceneName: '', greeting: '', greetingEmpty: '', quickActions: '', sortNum: 0 })
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
      ElMessage.warning('快捷动作需为 JSON 数组，如 [{"label":"查看订单","send":"查订单"}]')
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
h3 { margin: 0; }
@media (max-width: 640px) {
  :deep(.el-table) {
    font-size: 13px;
  }
  .page-toolbar > h3 {
    font-size: 15px;
  }
}
</style>
