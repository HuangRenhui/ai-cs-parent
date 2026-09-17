<template>
  <div class="page-card">
    <p class="hint">
      图谱基础维护：节点与关系的增删改查、按标签筛、子图查看与整体统计。
      与「知识图谱」页的分工：那边是从知识自动抽取，这里管手工修正与补录。
    </p>

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <!-- 节点 -->
    <template v-if="section === 'nodes'">
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <el-button type="primary" @click="openNode()">新增节点</el-button>
          <el-input v-model="labelFilter" placeholder="按类型筛选，例如 业务实体" clearable style="width: 200px" @keyup.enter="loadNodes">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="loadNodes">查询</el-button>
        </div>
      </div>

      <el-table :data="nodes" stripe v-loading="busy === 'nodes'" empty-text="暂无节点" table-layout="fixed" max-height="420">
        <el-table-column prop="nodeId" label="节点ID" min-width="100" />
        <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" min-width="110">
          <template #default="{ row }"><TypeTag :text="row.label" /></template>
        </el-table-column>
        <el-table-column prop="degree" label="连接数" min-width="90" />
        <el-table-column prop="community" label="所属社区" min-width="110" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="showRelations(row)">关系</el-button>
              <el-button link type="primary" @click="showSubgraph(row)">子图</el-button>
              <el-button link type="primary" @click="openNode(row)">编辑</el-button>
              <el-button link type="danger" @click="removeNode(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 关系 -->
    <template v-else-if="section === 'relations'">
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <el-button type="primary" @click="openRelation()">新增关系</el-button>
          <el-input v-model="relationNodeId" placeholder="按节点ID查关系，例如 n-1" style="width: 200px" />
          <el-button @click="loadRelations">查询</el-button>
        </div>
      </div>

      <el-table :data="relations" stripe v-loading="busy === 'relations'" empty-text="暂无关系" table-layout="fixed" max-height="420">
        <el-table-column prop="relationId" label="关系ID" min-width="100" />
        <el-table-column prop="source" label="起点" min-width="140" show-overflow-tooltip />
        <el-table-column prop="relType" label="关系" min-width="100">
          <template #default="{ row }"><TypeTag :text="row.relType" /></template>
        </el-table-column>
        <el-table-column prop="target" label="终点" min-width="140" show-overflow-tooltip />
        <el-table-column prop="weight" label="权重" min-width="90" />
        <el-table-column label="操作" min-width="100" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="danger" @click="removeRelation(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 可视化与统计 -->
    <template v-else>
      <div class="form-row">
        <el-input v-model="vizNodeId" placeholder="以哪个节点为中心，例如 n-3" style="width: 220px" />
        <el-input-number v-model="vizDepth" :min="1" :max="3" />
        <span class="muted">跳</span>
        <el-button type="primary" :loading="busy === 'viz'" @click="loadVisualization">生成视图</el-button>
        <el-button :loading="busy === 'stats'" @click="loadStats">图谱统计</el-button>
      </div>

      <!-- 用两列布局近似关系图的层次，不引入额外绘图库 -->
      <div v-if="viz" class="viz-box">
        <div class="viz-center">
          <div class="viz-node center">{{ viz.center }}</div>
        </div>
        <div class="viz-ring">
          <div v-for="n in viz.nodes || []" :key="n.nodeId" class="viz-node" :title="n.label">
            {{ n.name }}
          </div>
        </div>
        <p class="muted">
          中心节点 {{ viz.center }}　关联 {{ (viz.nodes || []).length }} 个节点、{{ (viz.edges || []).length }} 条关系
          （演示为示意布局，实际可视化由前端图库渲染）
        </p>
      </div>

      <el-descriptions v-if="stats" :column="4" border size="small" class="mini-table">
        <el-descriptions-item label="节点总数">{{ stats.nodeCount }}</el-descriptions-item>
        <el-descriptions-item label="关系总数">{{ stats.relationCount }}</el-descriptions-item>
        <el-descriptions-item label="类型数">{{ stats.labelCount }}</el-descriptions-item>
        <el-descriptions-item label="平均连接数">{{ stats.avgDegree }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <!-- 节点编辑 -->
    <el-dialog :title="nodeForm.id ? '编辑节点' : '新增节点'" v-model="nodeVisible" width="480px">
      <el-form :model="nodeForm" label-width="90px">
        <el-form-item label="名称" required><el-input v-model="nodeForm.name" placeholder="例如 退款" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="nodeForm.label" filterable allow-create default-first-option style="width: 100%">
            <el-option v-for="l in LABELS" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属社区"><el-input v-model="nodeForm.community" placeholder="例如 售后域" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'saveNode'" @click="submitNode">保存</el-button>
      </template>
    </el-dialog>

    <!-- 关系编辑 -->
    <el-dialog title="新增关系" v-model="relationVisible" width="480px">
      <el-form :model="relationForm" label-width="90px">
        <el-form-item label="起点" required><el-input v-model="relationForm.source" placeholder="例如 退款" /></el-form-item>
        <el-form-item label="关系" required><el-input v-model="relationForm.relType" placeholder="例如 关联" /></el-form-item>
        <el-form-item label="终点" required><el-input v-model="relationForm.target" placeholder="例如 订单" /></el-form-item>
        <el-form-item label="权重">
          <el-slider v-model="relationForm.weight" :min="0" :max="1" :step="0.01" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy === 'saveRel'" @click="submitRelation">保存</el-button>
      </template>
    </el-dialog>

    <!-- 节点关系 -->
    <el-dialog :title="`「${relationTitle}」的关系`" v-model="relListVisible" width="680px">
      <el-table :data="nodeRelations" stripe empty-text="该节点暂无关系" table-layout="fixed">
        <el-table-column prop="source" label="起点" min-width="140" />
        <el-table-column prop="relType" label="关系" min-width="100" />
        <el-table-column prop="target" label="终点" min-width="140" />
        <el-table-column prop="weight" label="权重" min-width="90" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
/** 图谱基础管理：节点/关系 CRUD、按标签筛、子图、可视化与统计 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SectionSwitch from '../../components/SectionSwitch.vue'
import {
  deleteGraphNode, deleteGraphRelation, getGraphStatistics, getGraphSubgraph,
  getGraphVisualization, listGraphNodesByLabel, listGraphRelations, saveGraphNode, saveGraphRelation
} from '../../api'

const LABELS = ['业务实体', '业务动作', '业务对象', '业务属性', '营销对象']

const section = ref('nodes')
const sectionOptions = [
  { value: 'nodes', label: '节点管理' },
  { value: 'relations', label: '关系管理' },
  { value: 'view', label: '可视化与统计' }
]

const busy = ref('')
const nodes = ref([])
const relations = ref([])
const nodeRelations = ref([])
const labelFilter = ref('')
const relationNodeId = ref('n-1')
const relationTitle = ref('')
const relListVisible = ref(false)
const nodeVisible = ref(false)
const relationVisible = ref(false)
const nodeForm = reactive({ id: null, name: '', label: '业务实体', community: '' })
const relationForm = reactive({ source: '', relType: '', target: '', weight: 0.8 })
const vizNodeId = ref('n-3')
const vizDepth = ref(2)
const viz = ref(null)
const stats = ref(null)

const loadNodes = async () => {
  busy.value = 'nodes'
  try {
    const res = labelFilter.value.trim()
      ? await listGraphNodesByLabel(labelFilter.value.trim())
      : await getGraphSubgraph({ depth: 1 })
    // 子图接口返回 { nodes, edges }，统一取节点数组
    nodes.value = Array.isArray(res.data) ? res.data : (res.data?.nodes || [])
  } catch {
    nodes.value = []
  } finally {
    busy.value = ''
  }
}

const loadRelations = async () => {
  busy.value = 'relations'
  try {
    const res = await listGraphRelations(relationNodeId.value)
    relations.value = Array.isArray(res.data) ? res.data : []
  } catch {
    relations.value = []
  } finally {
    busy.value = ''
  }
}

const showRelations = async (row) => {
  const res = await listGraphRelations(row.nodeId)
  nodeRelations.value = Array.isArray(res.data) ? res.data : []
  relationTitle.value = row.name
  relListVisible.value = true
}

const showSubgraph = async (row) => {
  vizNodeId.value = row.nodeId
  section.value = 'view'
  loadVisualization()
}

const openNode = (row) => {
  Object.assign(nodeForm, {
    id: row?.nodeId ?? null,
    name: row?.name || '',
    label: row?.label || '业务实体',
    community: row?.community || ''
  })
  nodeVisible.value = true
}

const submitNode = async () => {
  if (!nodeForm.name.trim()) {
    ElMessage.warning('请填写节点名称')
    return
  }
  busy.value = 'saveNode'
  try {
    await saveGraphNode({ nodeId: nodeForm.id, name: nodeForm.name.trim(), label: nodeForm.label, community: nodeForm.community })
    ElMessage.success('已保存')
    nodeVisible.value = false
    loadNodes()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const removeNode = async (row) => {
  try {
    await ElMessageBox.confirm(`删除节点「${row.name}」会同时移除它的所有关系，确认删除？`, '删除节点', { type: 'warning' })
  } catch {
    return
  }
  await deleteGraphNode(row.nodeId)
  ElMessage.success('已删除')
  loadNodes()
}

const openRelation = () => {
  Object.assign(relationForm, { source: '', relType: '', target: '', weight: 0.8 })
  relationVisible.value = true
}

const submitRelation = async () => {
  if (!relationForm.source.trim() || !relationForm.target.trim() || !relationForm.relType.trim()) {
    ElMessage.warning('请填写起点、关系与终点')
    return
  }
  busy.value = 'saveRel'
  try {
    await saveGraphRelation({ ...relationForm })
    ElMessage.success('已保存')
    relationVisible.value = false
    loadRelations()
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

const removeRelation = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除关系「${row.source} ${row.relType} ${row.target}」？`, '删除关系', { type: 'warning' })
  } catch {
    return
  }
  await deleteGraphRelation(row.relationId)
  ElMessage.success('已删除')
  loadRelations()
}

const loadVisualization = async () => {
  busy.value = 'viz'
  try {
    const res = await getGraphVisualization({ nodeId: vizNodeId.value, depth: vizDepth.value })
    viz.value = res.data || { center: vizNodeId.value, nodes: [], edges: [] }
  } catch {
    viz.value = null
  } finally {
    busy.value = ''
  }
}

const loadStats = async () => {
  busy.value = 'stats'
  try {
    const res = await getGraphStatistics()
    stats.value = res.data
  } catch {
    stats.value = null
  } finally {
    busy.value = ''
  }
}

onMounted(() => {
  loadNodes()
  loadRelations()
  loadStats()
})
</script>

<style scoped>
.hint {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
  margin: 0 0 12px;
}
.form-row { display: flex; gap: 10px; align-items: center; margin: 12px 0; flex-wrap: wrap; }
.mini-table { margin: 12px 0; }
/* 示意关系图：中心 + 一圈关联节点 */
.viz-box {
  padding: 20px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  margin-top: 12px;
}
.viz-center { display: flex; justify-content: center; margin-bottom: 16px; }
.viz-ring {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}
.viz-node {
  padding: 8px 16px;
  border-radius: 999px;
  background: #eef3fd;
  color: #1f4fd8;
  font-size: 13px;
  font-weight: 600;
}
.viz-node.center {
  background: linear-gradient(160deg, #4d86ff, #2f6bff);
  color: #fff;
  font-size: 14px;
  padding: 12px 24px;
}
.muted { color: #98a2b3; font-size: 12.5px; }
</style>
