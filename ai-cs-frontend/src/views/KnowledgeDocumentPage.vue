<template>
  <div class="page-card">
    <p class="hint">上传文档 → 解析切片 → 向量入库。切片规则可自定义，效果不理想时可重切。</p>
    <!-- 顶栏切片已标明本页，不再重复「文档知识库」标题 -->

    <SectionSwitch v-model="section" :options="sectionOptions" />

    <!-- ===================== 文档列表 ===================== -->
    <template v-if="section === 'docs'">
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <TbBtn act="add" label="上传文档" @click="openUpload" />
          <el-button :disabled="!selectedRows.length" @click="openRechunk(selectedRows)">
            批量重切{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
          </el-button>
          <el-button @click="load">刷新</el-button>
        </div>
      </div>

      <div class="metric-row">
        <div class="metric"><span class="metric-num">{{ list.length }}</span><span class="metric-label">文档数</span></div>
        <div class="metric"><span class="metric-num">{{ totalSegments }}</span><span class="metric-label">切片总数</span></div>
        <div class="metric"><span class="metric-num">{{ totalSizeText }}</span><span class="metric-label">文件总大小</span></div>
      </div>

      <el-form inline>
        <el-form-item label="文档名称">
          <el-input v-model="keyword" placeholder="文档名称 / 文档ID" clearable style="width: 220px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table
        :data="records"
        stripe
        v-loading="loading"
        empty-text="暂无文档，点右上角「上传文档」开始"
        table-layout="fixed"
        max-height="560"
        @selection-change="onSelect"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="documentId" label="文档ID" min-width="130" show-overflow-tooltip />
        <el-table-column prop="documentName" label="文档名称" min-width="170" show-overflow-tooltip />
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.version ?? 1 }}</template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="80" show-overflow-tooltip />
        <el-table-column prop="segmentCount" label="切片数" width="86" />
        <el-table-column label="切片策略" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ profileName(row.profileId) }}</template>
        </el-table-column>
        <el-table-column label="大小" width="96">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="100" show-overflow-tooltip />
        <el-table-column label="更新时间" width="146" show-overflow-tooltip>
          <template #default="{ row }">{{ row.updateTime || row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="178" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openChunks(row)">切片</el-button>
              <el-button link type="primary" @click="openVersions(row)">版本</el-button>
              <el-button link type="primary" @click="openRechunk([row])">重切</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <TablePager v-model:page="page" v-model:size="size" :total="total" />
    </template>

    <!-- ===================== 切片策略 ===================== -->
    <template v-else-if="section === 'profile'">
      <el-alert
        v-if="!splitImplemented"
        type="warning"
        :closable="false"
        show-icon
        title="后端未完成"
        description="切片策略为内存桩或假数据，保存与重切暂不会影响实际切片结果。"
        style="margin: 12px 0"
      />
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <TbBtn act="add" label="新建策略" @click="openProfile()" />
          <el-button @click="loadConfig">刷新</el-button>
        </div>
      </div>

      <el-table :data="allProfiles" stripe empty-text="暂无切片策略" table-layout="fixed" max-height="520">
        <el-table-column label="策略" width="156">
          <template #default="{ row }">
            <span class="strong-text">{{ row.name }}</span>
            <el-tag v-if="row.builtin" size="small" type="info" effect="plain" style="margin-left: 6px">内置</el-tag>
            <el-tag v-if="row.isDefault === 1" size="small" type="success" effect="plain" style="margin-left: 6px">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="切片方式" width="110">
          <template #default="{ row }">{{ STRATEGY_TEXT[row.strategy] || row.strategy }}</template>
        </el-table-column>
        <el-table-column prop="chunkSize" label="块大小" width="86" />
        <el-table-column prop="chunkOverlap" label="重叠" width="76" />
        <el-table-column prop="minChunkLength" label="最小长度" width="94" />
        <el-table-column label="段落/句子上限" width="128">
          <template #default="{ row }">{{ row.paragraphMaxLength }} / {{ row.sentenceMaxLength }}</template>
        </el-table-column>
        <el-table-column prop="docCount" label="文档数" width="80" />
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="234" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openPreview(row)">试切</el-button>
              <template v-if="!row.builtin">
                <el-button link type="primary" @click="openProfile(row)">编辑</el-button>
                <el-button link type="primary" :disabled="row.isDefault === 1" @click="setDefault(row)">设默认</el-button>
                <el-button link type="danger" @click="removeProfile(row)">删除</el-button>
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <p class="hint">内置预设不可修改，可直接拿去试切；自定义策略会被记住并在上传时可选用。</p>
    </template>

    <!-- ===================== 重切任务 ===================== -->
    <template v-else>
      <el-alert
        v-if="!splitImplemented"
        type="warning"
        :closable="false"
        show-icon
        title="后端未完成"
        description="重切任务为内存桩，不会真正重切向量。"
        style="margin: 12px 0"
      />
      <div class="page-toolbar is-actions-only">
        <div class="table-actions">
          <TbBtn act="add" label="发起重切" @click="openRechunk([])" />
          <el-button @click="loadTasks">刷新</el-button>
        </div>
      </div>

      <el-table :data="tasks" stripe v-loading="taskLoading" empty-text="暂无重切任务" table-layout="fixed" max-height="560">
        <el-table-column prop="documentName" label="文档" min-width="170" show-overflow-tooltip />
        <el-table-column prop="profileName" label="切片策略" min-width="130" show-overflow-tooltip />
        <el-table-column label="模式" width="110">
          <template #default="{ row }">{{ row.mode === 'version' ? '生成新版本' : '覆盖当前版本' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="TASK_TAG[row.status] || 'info'" effect="plain">{{ TASK_TEXT[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="切片数" width="86">
          <template #default="{ row }">{{ row.segmentCount || '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.costMs ? row.costMs + ' ms' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="110" show-overflow-tooltip />
        <el-table-column prop="createTime" label="发起时间" min-width="150" show-overflow-tooltip />
        <el-table-column prop="errorMsg" label="失败原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-if="row.status === 'failed'" link type="primary" @click="retryTask(row)">重试</el-button>
              <span v-else class="muted">-</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- ===================== 上传入库 ===================== -->
    <el-dialog title="上传文档入库" v-model="uploadVisible" width="580px">
      <el-form label-width="100px">
        <el-form-item label="文档文件" required>
          <el-upload
            ref="uploadRef"
            class="doc-upload"
            drag
            :auto-upload="false"
            :limit="1"
            :accept="ACCEPT"
            :on-change="onFileChange"
            :on-exceed="onFileExceed"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 pdf / txt / docx / md / html / csv，单文件上传</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="文档名称" required>
          <el-input v-model="form.documentName" maxlength="120" placeholder="便于检索识别的名称" />
        </el-form-item>
        <el-form-item label="文档ID">
          <el-input v-model="form.documentId" placeholder="留空取文档名称；同 ID 再次上传即生成新版本" />
        </el-form-item>
        <el-form-item label="切片策略">
          <el-select v-model="form.profileId" placeholder="用默认策略" clearable style="width: 100%">
            <el-option v-for="p in allProfiles" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本描述">
          <el-input v-model="form.versionDescription" type="textarea" :rows="2" placeholder="选填：本次变更说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUpload">上传并入库</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 切片策略编辑 ===================== -->
    <el-dialog :title="profileForm.id ? '编辑切片策略' : '新建切片策略'" v-model="profileVisible" width="680px">
      <el-form :model="profileForm" label-width="120px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="策略名称" required>
          <el-input v-model="profileForm.name" maxlength="30" placeholder="如：售后条款专用" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="profileForm.description" placeholder="选填，写清适用场景便于他人复用" />
        </el-form-item>

        <el-divider content-position="left">
          切片方式
        </el-divider>
        <el-form-item label="切片方式">
          <el-radio-group v-model="profileForm.strategy">
            <el-radio-button label="hierarchical">分层切片</el-radio-button>
            <el-radio-button label="semantic">语义切片</el-radio-button>
            <el-radio-button label="recursive">递归定长</el-radio-button>
          </el-radio-group>
          <div class="strategy-tip">{{ STRATEGY_TIP[profileForm.strategy] }}</div>
        </el-form-item>

        <el-divider content-position="left">
          参数<span class="divider-sub">仅显示当前方式真正生效的参数</span>
        </el-divider>

        <!-- 分层切片：按段落下钻到句子，用段落/句子上限，不读块大小 -->
        <template v-if="profileForm.strategy === 'hierarchical'">
          <el-form-item label="段落最大长度">
            <el-input-number v-model="profileForm.paragraphMaxLength" :min="200" :max="8000" :step="100" />
            <div class="chips">
              <span class="chips-label">常用：</span>
              <el-tag
                v-for="n in PARAGRAPH_PRESETS"
                :key="n"
                size="small"
                class="chip"
                :effect="profileForm.paragraphMaxLength === n ? 'dark' : 'plain'"
                @click="profileForm.paragraphMaxLength = n"
              >{{ n }}</el-tag>
            </div>
            <span class="form-tip">段落不超过此长度就整段成为一个片段，超过才下钻到句子层</span>
          </el-form-item>
          <el-form-item label="句子最大长度">
            <el-input-number v-model="profileForm.sentenceMaxLength" :min="100" :max="2000" :step="50" />
            <div class="chips">
              <span class="chips-label">常用：</span>
              <el-tag
                v-for="n in SENTENCE_PRESETS"
                :key="n"
                size="small"
                class="chip"
                :effect="profileForm.sentenceMaxLength === n ? 'dark' : 'plain'"
                @click="profileForm.sentenceMaxLength = n"
              >{{ n }}</el-tag>
            </div>
            <span class="form-tip">句子层拼接的目标块大小</span>
          </el-form-item>
        </template>

        <!-- 语义 / 递归：按块大小切，用块大小与重叠 -->
        <template v-else>
          <el-form-item label="块大小">
            <el-input-number v-model="profileForm.chunkSize" :min="100" :max="4000" :step="50" />
            <div class="chips">
              <span class="chips-label">常用：</span>
              <el-tag
                v-for="n in CHUNK_PRESETS"
                :key="n"
                size="small"
                class="chip"
                :effect="profileForm.chunkSize === n ? 'dark' : 'plain'"
                @click="profileForm.chunkSize = n"
              >{{ n }} 字</el-tag>
            </div>
            <span class="form-tip">单个片段的目标字符数</span>
          </el-form-item>
          <el-form-item label="重叠长度">
            <el-input-number v-model="profileForm.chunkOverlap" :min="0" :max="1000" :step="10" />
            <div class="chips">
              <span class="chips-label">常用：</span>
              <el-tag
                v-for="n in OVERLAP_PRESETS"
                :key="n"
                size="small"
                class="chip"
                :effect="profileForm.chunkOverlap === n ? 'dark' : 'plain'"
                @click="profileForm.chunkOverlap = n"
              >{{ n }}</el-tag>
            </div>
            <span class="form-tip">相邻片段重叠，避免语义在边界被切断；建议为块大小的 10%~20%</span>
          </el-form-item>
        </template>

        <!-- 分层 / 语义都会过滤短片段，递归定长不过滤 -->
        <el-form-item v-if="profileForm.strategy !== 'recursive'" label="最小片段长度">
          <el-input-number v-model="profileForm.minChunkLength" :min="0" :max="500" :step="10" />
          <div class="chips">
            <span class="chips-label">常用：</span>
            <el-tag
              v-for="n in MIN_PRESETS"
              :key="n"
              size="small"
              class="chip"
              :effect="profileForm.minChunkLength === n ? 'dark' : 'plain'"
              @click="profileForm.minChunkLength = n"
            >{{ n }}</el-tag>
          </div>
          <span class="form-tip">短于此长度的片段直接丢弃，避免产生无意义碎片</span>
        </el-form-item>

        <!-- 语义切片专用分隔符：可点选插入，也可手打 -->
        <el-form-item v-if="profileForm.strategy === 'semantic'" label="语义分隔符">
          <el-input v-model="profileForm.separators" placeholder="多个分隔符用英文逗号分隔，换行写 \n" />
          <div class="chips">
            <span class="chips-label">点击插入：</span>
            <el-tag
              v-for="s in SEPARATOR_CHIPS"
              :key="s.value"
              size="small"
              class="chip"
              effect="plain"
              @click="insertSeparator(s.value)"
            >{{ s.label }}</el-tag>
          </div>
          <div class="chips">
            <span class="chips-label">常用组合：</span>
            <el-tag
              v-for="p in SEPARATOR_PRESETS"
              :key="p.label"
              size="small"
              class="chip"
              effect="plain"
              @click="profileForm.separators = p.value"
            >{{ p.label }}</el-tag>
          </div>
          <div class="sep-preview">
            <span class="chips-label">当前生效：</span>
            <template v-if="separatorTokens.length">
              <el-tag v-for="(t, i) in separatorTokens" :key="i" size="small" type="info" effect="plain" class="chip">
                {{ separatorLabel(t) }}
              </el-tag>
            </template>
            <span v-else class="sep-empty">未配置分隔符，会退化为按段落切分</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button :loading="profileSaving" @click="submitProfile(false)">保存</el-button>
        <el-button type="primary" :loading="profileSaving" @click="submitProfile(true)">保存并试切</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 试切预览 ===================== -->
    <el-dialog :title="`试切预览 · ${previewProfile?.name || ''}`" v-model="previewVisible" width="680px">
      <el-form label-width="100px">
        <el-form-item label="取材方式">
          <el-radio-group v-model="previewSource">
            <el-radio value="doc">选已有文档</el-radio>
            <el-radio value="text">粘贴文本</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="previewSource === 'doc'" label="文档">
          <el-select v-model="previewDocId" filterable placeholder="选择要试切的文档" style="width: 100%">
            <el-option v-for="d in list" :key="d.documentId" :label="d.documentName" :value="d.documentId" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="待切文本">
          <el-input v-model="previewText" type="textarea" :rows="5" placeholder="粘贴一段文本，用于比较不同参数下的切片效果" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="previewing" @click="runPreview">开始试切</el-button>
          <span class="form-tip">只做试算，不写入向量库</span>
        </el-form-item>
      </el-form>

      <template v-if="previewResult">
        <el-divider content-position="left">试切结果</el-divider>
        <div class="metric-row">
          <div class="metric"><span class="metric-num">{{ previewResult.totalSegments }}</span><span class="metric-label">片段数</span></div>
          <div class="metric"><span class="metric-num">{{ previewResult.avgLength }}</span><span class="metric-label">平均长度</span></div>
          <div class="metric"><span class="metric-num">{{ previewResult.maxLength }}</span><span class="metric-label">最长</span></div>
          <div class="metric"><span class="metric-num">{{ previewResult.minLength }}</span><span class="metric-label">最短</span></div>
        </div>
        <div v-if="previewResult.totalSegments" class="preview-list">
          <div v-for="s in previewResult.samples" :key="s.index" class="preview-item">
            <div class="preview-head">第 {{ s.index }} 段 · {{ s.length }} 字</div>
            <div class="preview-body">{{ s.content }}</div>
          </div>
        </div>
        <el-empty v-else description="没有切出任何片段，试试调小最小片段长度" />
      </template>
    </el-dialog>

    <!-- ===================== 发起重切 ===================== -->
    <el-dialog title="发起重切" v-model="rechunkVisible" width="560px">
      <el-form label-width="100px">
        <el-form-item label="文档" required>
          <el-select v-model="rechunkForm.documentIds" multiple filterable placeholder="选择要重切的文档" style="width: 100%">
            <el-option v-for="d in list" :key="d.documentId" :label="d.documentName" :value="d.documentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="切片策略" required>
          <el-select v-model="rechunkForm.profileId" placeholder="选择策略" style="width: 100%">
            <el-option v-for="p in allProfiles" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="重切模式">
          <el-radio-group v-model="rechunkForm.mode">
            <el-radio value="version">生成新版本</el-radio>
            <el-radio value="overwrite">覆盖当前版本</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          :title="rechunkForm.mode === 'version'
            ? '生成新版本：旧版本保留，可随时回退，切片数会体现在新版本上。'
            : '覆盖当前版本：直接替换现有向量，不产生新版本，无法回退。'"
        />
      </el-form>
      <template #footer>
        <el-button @click="rechunkVisible = false">取消</el-button>
        <el-button type="primary" :loading="rechunking" @click="submitRechunk">提交重切</el-button>
      </template>
    </el-dialog>

    <!-- ===================== 切片预览 ===================== -->
    <el-drawer v-model="chunkVisible" :title="`切片预览 · ${chunkDoc?.documentName || ''}`" size="620px">
      <el-alert
        v-if="chunkVisible && !chunksImplemented"
        type="warning"
        :closable="false"
        show-icon
        title="后端未完成"
        description="入库切片尚未单独留存可回读的分片，因此这里预览不到内容。"
        style="margin-bottom: 12px"
      />
      <el-table :data="chunks" stripe v-loading="chunkLoading" empty-text="暂无切片数据" max-height="560">
        <el-table-column prop="index" label="序号" width="70" />
        <el-table-column prop="content" label="切片内容" min-width="360" show-overflow-tooltip />
        <el-table-column prop="length" label="字数" width="80" />
      </el-table>
    </el-drawer>

    <!-- ===================== 版本历史 ===================== -->
    <el-drawer v-model="versionVisible" :title="`版本历史 · ${versionDoc?.documentName || ''}`" size="680px">
      <div class="drawer-actions">
        <el-button size="small" :disabled="versions.length < 2" :loading="comparing" @click="compareLatest">
          对比最新两个版本
        </el-button>
      </div>
      <el-table :data="versions" stripe v-loading="versionLoading" empty-text="暂无版本记录" max-height="520">
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="segmentCount" label="切片数" width="90" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="versionDescription" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column prop="uploaderName" label="上传人" width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" :disabled="row.isCurrent === 1" @click="rollback(row)">回退</el-button>
              <el-button link type="danger" @click="removeVersion(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog title="版本差异" v-model="compareVisible" width="640px">
      <pre class="diff-box">{{ compareResult || '（无差异内容）' }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  listDocuments, listDocumentVersions, listDocumentChunks,
  uploadPdfVersioned, uploadFileVersioned,
  rollbackDocumentVersion, compareDocumentVersions, deleteDocumentVersion,
  getSplitConfig, saveSplitProfile, deleteSplitProfile, setDefaultSplitProfile,
  previewSplit, rechunkDocuments, listRechunkTasks, retryRechunkTask
} from '../api'
import TablePager from '../components/TablePager.vue'
import TbBtn from '../components/TbBtn.vue'
import SectionSwitch from '../components/SectionSwitch.vue'
import { useClientPager } from '../composables/useClientPager'

const ACCEPT = '.pdf,.txt,.docx,.md,.html,.csv'
const STRATEGY_TEXT = { hierarchical: '分层切片', semantic: '语义切片', recursive: '递归定长' }
const TASK_TEXT = { pending: '排队中', running: '进行中', success: '已完成', failed: '失败' }
const TASK_TAG = { pending: 'info', running: 'warning', success: 'success', failed: 'danger' }

const section = ref('docs')
const sectionOptions = [
  { value: 'docs', label: '文档列表' },
  { value: 'profile', label: '切片策略' },
  { value: 'task', label: '重切任务' }
]

// ===================== 文档列表 =====================
const list = ref([])
const { page, size, total, records } = useClientPager(list)
const loading = ref(false)
const keyword = ref('')
const selectedRows = ref([])
const onSelect = (rows) => { selectedRows.value = rows }

const totalSegments = computed(() => list.value.reduce((s, r) => s + (Number(r.segmentCount) || 0), 0))
const totalSizeText = computed(() => formatSize(list.value.reduce((s, r) => s + (Number(r.fileSize) || 0), 0)))

/** 字节转可读体积 */
function formatSize(bytes) {
  const n = Number(bytes) || 0
  if (!n) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}

// ===================== 切片策略 =====================
const profiles = ref([])
const presets = ref([])
const splitImplemented = ref(true)

/** 内置预设 + 自定义策略，预设在前 */
const allProfiles = computed(() => [...presets.value, ...profiles.value])
const profileName = (id) => {
  if (!id) return '默认策略'
  const p = allProfiles.value.find((x) => String(x.id) === String(id))
  return p ? p.name : `策略 #${id}`
}

const load = async () => {
  loading.value = true
  try {
    const res = await listDocuments({ keyword: keyword.value })
    const data = res.data
    const all = Array.isArray(data) ? data : (data?.records || [])
    const kw = keyword.value.trim().toLowerCase()
    list.value = kw
      ? all.filter((d) => String(d.documentName || '').toLowerCase().includes(kw) || String(d.documentId || '').toLowerCase().includes(kw))
      : all
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

const loadConfig = async () => {
  try {
    const res = await getSplitConfig()
    const d = res.data || {}
    presets.value = d.presets || []
    profiles.value = d.profiles || []
    splitImplemented.value = d.implemented !== false
  } catch {
    presets.value = []
    profiles.value = []
  }
}

// ===================== 上传入库 =====================
const uploadVisible = ref(false)
const submitting = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)
const form = reactive({ documentName: '', documentId: '', versionDescription: '', profileId: null })

const openUpload = () => {
  Object.assign(form, { documentName: '', documentId: '', versionDescription: '', profileId: null })
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  uploadVisible.value = true
}

const onFileChange = (file) => {
  selectedFile.value = file.raw
  // 名称留空时用文件名（去掉扩展名）兜底，减少手工输入
  if (!form.documentName && file.name) {
    form.documentName = file.name.replace(/\.[^.]+$/, '')
  }
}

const onFileExceed = () => {
  ElMessage.warning('一次只能上传一个文件，请先移除已选文件')
}

/** 当前登录人信息，作为文档上传人 */
const currentUser = () => {
  try {
    const u = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return { id: u.id, name: u.realName || u.username || '' }
  } catch {
    return { id: null, name: '' }
  }
}

const submitUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要上传的文档')
    return
  }
  if (!form.documentName.trim()) {
    ElMessage.warning('请填写文档名称')
    return
  }
  submitting.value = true
  try {
    const user = currentUser()
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    fd.append('documentId', form.documentId.trim() || form.documentName.trim())
    fd.append('documentName', form.documentName.trim())
    if (form.versionDescription.trim()) fd.append('versionDescription', form.versionDescription.trim())
    if (form.profileId) fd.append('profileId', form.profileId)
    if (user.id) fd.append('uploaderId', user.id)
    if (user.name) fd.append('uploaderName', user.name)

    // PDF 与非 PDF 是后端两个不同入口
    const isPdf = /\.pdf$/i.test(selectedFile.value.name || '')
    const res = await (isPdf ? uploadPdfVersioned : uploadFileVersioned)(fd)

    ElMessage.success(res?.data || '上传入库成功')
    uploadVisible.value = false
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

// ===================== 切片策略编辑 =====================
/** 三种切片方式各自用到哪些参数，写清楚免得调了不生效的项 */
const STRATEGY_TIP = {
  hierarchical: '文档 → 段落 → 句子 逐层下钻，只有超长段落才切细。适合段落结构清晰的长文。',
  semantic: '按段落与标点等语义边界切分，可自定义分隔符。适合条款、FAQ 这类短文本。',
  recursive: '只按块大小硬切（带重叠），不识别语义边界。适合没有段落结构的纯文本。'
}
const CHUNK_PRESETS = [300, 500, 800, 1200]
const OVERLAP_PRESETS = [0, 50, 80, 120]
const MIN_PRESETS = [20, 30, 50, 80]
const PARAGRAPH_PRESETS = [800, 1200, 2000, 3000]
const SENTENCE_PRESETS = [200, 300, 500, 900]
/** 可一键插入的常用分隔符（\n 表示换行，与后端配置写法一致） */
const SEPARATOR_CHIPS = [
  { label: '空行 / 段落', value: '\\n\\n' },
  { label: '换行', value: '\\n' },
  { label: '句号', value: '。' },
  { label: '中文逗号', value: '，' },
  { label: '分号', value: '；' },
  { label: '问号', value: '？' },
  { label: '感叹号', value: '！' },
  { label: '英文句点', value: '.' },
  { label: '英文逗号', value: ',' }
]
/** 常用分隔符组合，一键套用 */
const SEPARATOR_PRESETS = [
  { label: '按段落', value: '\\n\\n' },
  { label: '段落 + 句号', value: '\\n\\n,。' },
  { label: '条款：段落 + 分号 + 句号', value: '\\n\\n,。；;' },
  { label: '细致：含问号感叹号', value: '\\n\\n,。；;！!？?' },
  { label: '中英文混排', value: '\\n\\n,。,；;，,.！!？?' }
]
const SEP_LABEL = {
  '\n\n': '空行',
  '\n': '换行',
  '。': '句号',
  '，': '中文逗号',
  '；': '分号',
  '！': '感叹号',
  '？': '问号',
  '.': '英文句点',
  ',': '英文逗号',
  ';': '英文分号',
  '!': '英文叹号',
  '?': '英文问号'
}

/** 界面上用 \n 显示换行，提交前还原成真实换行，避免后端拿到两个字符 */
const toRealSeparators = (s) => String(s || '').replace(/\\n/g, '\n').replace(/\\t/g, '\t')
const toDisplaySeparators = (s) => String(s || '').replace(/\n/g, '\\n').replace(/\t/g, '\\t')

const profileVisible = ref(false)
const profileSaving = ref(false)
const PROFILE_EMPTY = {
  id: null,
  name: '',
  description: '',
  strategy: 'hierarchical',
  chunkSize: 500,
  chunkOverlap: 80,
  minChunkLength: 50,
  paragraphMaxLength: 2000,
  sentenceMaxLength: 500,
  separators: '\\n\\n,。；;'
}
const profileForm = reactive({ ...PROFILE_EMPTY })

/** 已配置的分隔符（还原成真实字符后展示成人话标签） */
const separatorTokens = computed(() =>
  toRealSeparators(profileForm.separators).split(',').filter((s) => s !== '')
)
const separatorLabel = (t) => SEP_LABEL[t] || t.replace(/\n/g, '\\n')

/** 点选插入分隔符，避免手打转义字符写错 */
const insertSeparator = (v) => {
  const parts = String(profileForm.separators || '').split(',').filter((s) => s !== '')
  if (!parts.includes(v)) parts.push(v)
  profileForm.separators = parts.join(',')
}

const openProfile = (row) => {
  Object.assign(profileForm, PROFILE_EMPTY)
  if (row) Object.assign(profileForm, row)
  profileForm.separators = toDisplaySeparators(profileForm.separators)
  profileVisible.value = true
}

const submitProfile = async (andPreview = false) => {
  if (!profileForm.name.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  // 重叠必须小于块大小，否则切片会原地打转
  if (profileForm.strategy !== 'hierarchical' && Number(profileForm.chunkOverlap) >= Number(profileForm.chunkSize)) {
    ElMessage.warning('重叠长度必须小于块大小')
    return
  }
  if (profileForm.strategy === 'semantic' && !separatorTokens.value.length) {
    ElMessage.warning('语义切片至少要配置一个分隔符')
    return
  }
  profileSaving.value = true
  try {
    const payload = { ...profileForm, name: profileForm.name.trim(), separators: toRealSeparators(profileForm.separators) }
    const res = await saveSplitProfile(payload)
    ElMessage.success(res?.data || '已保存')
    profileVisible.value = false
    await loadConfig()
    if (andPreview) {
      const saved = allProfiles.value.find((p) => p.name === payload.name) || { ...payload }
      openPreview(saved)
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    profileSaving.value = false
  }
}

const setDefault = async (row) => {
  try {
    await setDefaultSplitProfile(row.id)
    ElMessage.success('已设为默认策略')
    loadConfig()
  } catch {
    /* 拦截器已提示 */
  }
}

const removeProfile = async (row) => {
  await ElMessageBox.confirm(`确认删除策略「${row.name}」？使用该策略的文档不受影响。`, '删除策略', { type: 'warning' })
  try {
    await deleteSplitProfile(row.id)
    ElMessage.success('已删除')
    loadConfig()
  } catch {
    /* 拦截器已提示 */
  }
}

// ===================== 试切预览 =====================
const previewVisible = ref(false)
const previewing = ref(false)
const previewProfile = ref(null)
const previewSource = ref('doc')
const previewDocId = ref('')
const previewText = ref('')
const previewResult = ref(null)

const openPreview = (row) => {
  previewProfile.value = row
  previewSource.value = 'doc'
  previewDocId.value = list.value[0]?.documentId || ''
  previewText.value = ''
  previewResult.value = null
  previewVisible.value = true
}

const runPreview = async () => {
  if (previewSource.value === 'doc' && !previewDocId.value) {
    ElMessage.warning('请选择要试切的文档')
    return
  }
  if (previewSource.value === 'text' && !previewText.value.trim()) {
    ElMessage.warning('请粘贴要试切的文本')
    return
  }
  previewing.value = true
  try {
    const res = await previewSplit({
      documentId: previewSource.value === 'doc' ? previewDocId.value : undefined,
      text: previewSource.value === 'text' ? previewText.value : undefined,
      profile: { ...previewProfile.value }
    })
    previewResult.value = res.data || null
  } catch {
    previewResult.value = null
  } finally {
    previewing.value = false
  }
}

// ===================== 重切 =====================
const rechunkVisible = ref(false)
const rechunking = ref(false)
const rechunkForm = reactive({ documentIds: [], profileId: null, mode: 'version' })
const tasks = ref([])
const taskLoading = ref(false)

const openRechunk = (rows) => {
  rechunkForm.documentIds = (rows || []).map((r) => r.documentId)
  rechunkForm.profileId = allProfiles.value.find((p) => p.isDefault === 1)?.id || allProfiles.value[0]?.id || null
  rechunkForm.mode = 'version'
  rechunkVisible.value = true
}

const submitRechunk = async () => {
  if (!rechunkForm.documentIds.length) {
    ElMessage.warning('请选择要重切的文档')
    return
  }
  if (!rechunkForm.profileId) {
    ElMessage.warning('请选择切片策略')
    return
  }
  rechunking.value = true
  try {
    const res = await rechunkDocuments({ ...rechunkForm })
    ElMessage.success(res?.data || '重切任务已提交')
    rechunkVisible.value = false
    section.value = 'task'
    loadTasks()
  } catch {
    /* 拦截器已提示 */
  } finally {
    rechunking.value = false
  }
}

const loadTasks = async () => {
  taskLoading.value = true
  try {
    const res = await listRechunkTasks()
    tasks.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
  } catch {
    tasks.value = []
  } finally {
    taskLoading.value = false
  }
}

const retryTask = async (row) => {
  try {
    const res = await retryRechunkTask(row.id)
    ElMessage.success(res?.data || '已提交重试')
    loadTasks()
  } catch {
    /* 拦截器已提示 */
  }
}

// ===================== 切片预览 =====================
const chunkVisible = ref(false)
const chunkLoading = ref(false)
const chunks = ref([])
const chunkDoc = ref(null)
const chunksImplemented = ref(true)

const openChunks = async (row) => {
  chunkDoc.value = row
  chunks.value = []
  chunksImplemented.value = true
  chunkVisible.value = true
  chunkLoading.value = true
  try {
    const res = await listDocumentChunks({ documentId: row.documentId, page: 1, size: 50 })
    const data = res.data || {}
    chunks.value = data.records || []
    chunksImplemented.value = data.implemented !== false
  } catch {
    chunks.value = []
  } finally {
    chunkLoading.value = false
  }
}

// ===================== 版本历史 =====================
const versionVisible = ref(false)
const versionLoading = ref(false)
const versions = ref([])
const versionDoc = ref(null)
const comparing = ref(false)
const compareVisible = ref(false)
const compareResult = ref('')

const openVersions = async (row) => {
  versionDoc.value = row
  versions.value = []
  versionVisible.value = true
  versionLoading.value = true
  try {
    const res = await listDocumentVersions(row.documentId)
    const data = res.data
    versions.value = Array.isArray(data) ? data : (data?.records || [])
  } catch {
    versions.value = []
  } finally {
    versionLoading.value = false
  }
}

const rollback = async (row) => {
  await ElMessageBox.confirm(`确认将文档回退到 v${row.version}？`, '版本回退', { type: 'warning' })
  try {
    await rollbackDocumentVersion({ documentId: versionDoc.value.documentId, targetVersion: row.version })
    ElMessage.success('已回退')
    openVersions(versionDoc.value)
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

const removeVersion = async (row) => {
  await ElMessageBox.confirm(`确认删除 v${row.version} 版本记录？`, '删除版本', { type: 'warning' })
  try {
    await deleteDocumentVersion(row.id)
    ElMessage.success('已删除')
    openVersions(versionDoc.value)
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

const compareLatest = async () => {
  const [a, b] = versions.value
  if (!a || !b) return
  comparing.value = true
  try {
    const res = await compareDocumentVersions({ version1Id: a.id, version2Id: b.id })
    compareResult.value = typeof res.data === 'string' ? res.data : JSON.stringify(res.data, null, 2)
    compareVisible.value = true
  } catch {
    /* 拦截器已提示 */
  } finally {
    comparing.value = false
  }
}

onMounted(() => {
  load()
  loadConfig()
  loadTasks()
})
</script>

<style scoped>
.metric-row { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.metric {
  flex: 1 1 140px;
  min-width: 140px;
  padding: 12px 14px;
  border-radius: 10px;
  background: linear-gradient(180deg, #f7faff 0%, #eef4ff 100%);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-num { font-size: 20px; font-weight: 700; color: #1c2b4a; }
.metric-label { font-size: 12px; color: #6b7280; }
.doc-upload { width: 100%; }
.drawer-actions { margin-bottom: 12px; }
.strong-text { font-weight: 600; color: #1c2b4a; }
.muted { color: #98a2b3; }
/* 表单里成组出现的小控件，统一换行显示在控件下方 */
.form-tip {
  flex-basis: 100%;
  margin: 6px 0 0;
  font-size: 12px;
  color: #98a2b3;
}
.strategy-tip {
  flex-basis: 100%;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #98a2b3;
}
.divider-sub {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #9aa3b2;
}
.chips {
  flex-basis: 100%;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.chips-label {
  font-size: 12px;
  color: #98a2b3;
}
.chip {
  cursor: pointer;
}
.chip:hover {
  border-color: #c4d4ff;
  color: #2f6bff;
}
.sep-preview {
  flex-basis: 100%;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f7f9fc;
}
.sep-empty {
  font-size: 12px;
  color: #e6a23c;
}
.preview-list { max-height: 300px; overflow: auto; }
.preview-item {
  border: 1px solid #eef1f6;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #fbfcfe;
}
.preview-head { font-size: 12px; color: #98a2b3; margin-bottom: 6px; }
.preview-body { font-size: 13px; line-height: 1.6; color: #4b5563; word-break: break-all; }
.diff-box {
  max-height: 420px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f7f8fa;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
