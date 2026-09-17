<template>
  <div>
    <p class="panel-desc">
      规则推理把业务规则写成可执行的规则，跑一遍就能从已有关系推出新事实（例如「可退金额」）。
      规则停用后不参与全量推理，但保留配置。
    </p>

    <div class="page-toolbar is-actions-only">
      <div class="table-actions">
        <el-button type="primary" :loading="executingAll" @click="executeAll">执行全部启用规则</el-button>
        <el-button :loading="loading" @click="loadRules">刷新</el-button>
      </div>
    </div>

    <el-table :data="rules" stripe v-loading="loading" empty-text="暂无推理规则" table-layout="fixed" max-height="360">
      <el-table-column prop="ruleName" label="规则" min-width="150" show-overflow-tooltip />
      <el-table-column label="分类" min-width="100">
        <template #default="{ row }"><TypeTag :text="row.category" /></template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="280" show-overflow-tooltip />
      <el-table-column label="命中次数" min-width="90">
        <template #default="{ row }">{{ row.hitCount }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="100" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button link type="primary" :disabled="row.enabled !== 1" @click="executeOne(row)">执行</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <h5 class="sub-title">推理工具</h5>
    <el-tabs v-model="tool" class="tool-tabs">
      <el-tab-pane label="推理路径" name="paths">
        <p class="muted">从起点到终点经过哪些中间节点，权重越高说明关系越紧密。</p>
        <el-table :data="paths" stripe class="mini-table" table-layout="fixed">
          <el-table-column label="路径" min-width="260">
            <template #default="{ row }">{{ (row.path || []).join(' → ') }}</template>
          </el-table-column>
          <el-table-column prop="hops" label="跳数" min-width="80" />
          <el-table-column prop="weight" label="权重" min-width="80" />
        </el-table>
        <el-button :loading="busy === 'paths'" @click="loadPaths">加载路径</el-button>
      </el-tab-pane>

      <el-tab-pane label="概念层级" name="hierarchy">
        <p class="muted">实体之间的上下位关系，用于把笼统的问题收敛到具体概念。</p>
        <el-tree :data="hierarchy" :props="{ label: 'name', children: 'children' }" default-expand-all class="tree-box" />
        <el-button :loading="busy === 'hierarchy'" @click="loadHierarchy">加载层级</el-button>
      </el-tab-pane>

      <el-tab-pane label="实体消歧" name="disambiguation">
        <p class="muted">同一个词可能指不同实体（如「苹果」既是水果也是品牌），这里看候选与判定依据。</p>
        <div class="form-row">
          <el-input v-model="disambKeyword" placeholder="输入有歧义的词，例如 苹果" style="width: 220px" />
          <el-button type="primary" :loading="busy === 'disambiguation'" @click="loadDisambiguation">消歧</el-button>
        </div>
        <el-table v-if="disambiguation.length" :data="disambiguation" stripe class="mini-table" table-layout="fixed">
          <el-table-column prop="candidate" label="候选实体" min-width="150" />
          <el-table-column prop="confidence" label="置信度" min-width="90" />
          <el-table-column prop="evidence" label="判定依据" min-width="240" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="推理链" name="chain">
        <p class="muted">从一个节点出发的完整推理链条与结论，用于解释「为什么这么答」。</p>
        <div class="form-row">
          <el-input v-model="chainNode" placeholder="起点节点，例如 退款" style="width: 220px" />
          <el-button type="primary" :loading="busy === 'chain'" @click="loadChain">推演</el-button>
        </div>
        <el-alert v-if="chain" type="success" :closable="false">
          <template #title>{{ (chain.chain || []).join(' → ') }}</template>
          <template #default>{{ chain.conclusion }}</template>
        </el-alert>
      </el-tab-pane>

      <el-tab-pane label="实体合并" name="merge">
        <p class="muted">把指向同一事物的多个实体合为一个，合并后原关系会迁移到保留的实体上。</p>
        <el-form label-width="110px" style="max-width: 560px">
          <el-form-item label="保留实体"><el-input v-model="mergeForm.keptId" placeholder="例如 n-3" /></el-form-item>
          <el-form-item label="合并实体"><el-input v-model="mergeForm.mergeIds" placeholder="逗号分隔，例如 n-8,n-9" /></el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="busy === 'merge'" @click="doMerge">合并</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="execTitle" v-model="execVisible" width="560px">
      <el-alert type="success" :closable="false" :title="execMsg" />
      <h5 class="sub-title">新推出的事实</h5>
      <ul class="fact-list">
        <li v-for="(s, i) in execSamples" :key="i">{{ s }}</li>
      </ul>
    </el-dialog>
  </div>
</template>

<script setup>
/** 图谱推理：规则管理、路径、层级、消歧、推理链、实体合并 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  executeAllReasoning, executeReasoningRule, getReasoningChain, getReasoningDisambiguation,
  getReasoningHierarchy, listReasoningPaths, listReasoningRules, mergeEntities
} from '../../../api'

const rules = ref([])
const loading = ref(false)
const executingAll = ref(false)
const busy = ref('')
const tool = ref('paths')
const paths = ref([])
const hierarchy = ref([])
const disambiguation = ref([])
const disambKeyword = ref('苹果')
const chainNode = ref('退款')
const chain = ref(null)
const mergeForm = reactive({ keptId: 'n-3', mergeIds: '' })
const execVisible = ref(false)
const execTitle = ref('')
const execMsg = ref('')
const execSamples = ref([])

const loadRules = async () => {
  loading.value = true
  try {
    const res = await listReasoningRules()
    rules.value = Array.isArray(res.data) ? res.data : []
  } catch {
    rules.value = []
  } finally {
    loading.value = false
  }
}

const showExecResult = (title, msg, samples) => {
  execTitle.value = title
  execMsg.value = msg
  execSamples.value = samples || []
  execVisible.value = true
}

const executeAll = async () => {
  executingAll.value = true
  try {
    const res = await executeAllReasoning({})
    showExecResult('全量推理结果', res.msg || '执行完成', ['退款：可退金额 = 实付金额 − 已用优惠券'])
    loadRules()
  } catch {
    /* 拦截器已提示 */
  } finally {
    executingAll.value = false
  }
}

const executeOne = async (row) => {
  try {
    await ElMessageBox.confirm(`确认执行规则「${row.ruleName}」？`, '执行规则', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await executeReasoningRule(row.ruleName, {})
    showExecResult(`规则「${row.ruleName}」执行结果`, res.msg || '执行完成', res.data?.samples)
    loadRules()
  } catch {
    /* 拦截器已提示 */
  }
}

const loadPaths = async () => {
  busy.value = 'paths'
  try {
    const res = await listReasoningPaths({})
    paths.value = Array.isArray(res.data) ? res.data : []
  } catch {
    paths.value = []
  } finally {
    busy.value = ''
  }
}

const loadHierarchy = async () => {
  busy.value = 'hierarchy'
  try {
    const res = await getReasoningHierarchy()
    hierarchy.value = Array.isArray(res.data) ? res.data : []
  } catch {
    hierarchy.value = []
  } finally {
    busy.value = ''
  }
}

const loadDisambiguation = async () => {
  busy.value = 'disambiguation'
  try {
    const res = await getReasoningDisambiguation({ keyword: disambKeyword.value })
    disambiguation.value = Array.isArray(res.data) ? res.data : []
  } catch {
    disambiguation.value = []
  } finally {
    busy.value = ''
  }
}

const loadChain = async () => {
  busy.value = 'chain'
  try {
    const res = await getReasoningChain({ nodeId: chainNode.value })
    chain.value = res.data
  } catch {
    chain.value = null
  } finally {
    busy.value = ''
  }
}

const doMerge = async () => {
  if (!mergeForm.mergeIds.trim()) {
    ElMessage.warning('请填写要合并的实体ID')
    return
  }
  busy.value = 'merge'
  try {
    const res = await mergeEntities({
      keptId: mergeForm.keptId,
      mergeIds: mergeForm.mergeIds.split(',').map((s) => s.trim()).filter(Boolean)
    })
    ElMessage.success(res.msg || '合并完成')
    mergeForm.mergeIds = ''
  } catch {
    /* 拦截器已提示 */
  } finally {
    busy.value = ''
  }
}

onMounted(() => {
  loadRules()
  loadPaths()
})
</script>

<style scoped>
.panel-desc {
  color: #667085;
  font-size: 13px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.sub-title {
  margin: 20px 0 10px;
  font-size: 13.5px;
  color: #1c2b4a;
}
.tool-tabs { margin-top: 4px; }
.mini-table { margin: 10px 0; max-width: 780px; }
.tree-box {
  max-width: 420px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 10px;
}
.form-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 10px 0;
}
.fact-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: #475467;
}
.muted { color: #98a2b3; font-size: 12.5px; margin: 0 0 8px; }
</style>
