<template>
  <div class="help-center" :class="{ embedded }">
    <header v-if="!embedded" class="hc-header">
      <h1>帮助中心</h1>
      <p>自助服务，快速找到您需要的答案</p>
    </header>

    <div class="hc-search">
      <el-input v-model="question" placeholder="输入您的问题，如：如何退款？" size="large" clearable @keyup.enter="doSearch" />
      <div class="hc-search-actions">
        <el-button type="primary" :loading="searching" @click="doSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="resetSearch">
          <el-icon><RefreshRight /></el-icon>
          重置
        </el-button>
      </div>
    </div>

    <div v-if="searchReply" class="hc-result">
      <div class="hc-result-title">智能答复</div>
      <p>{{ searchReply }}</p>
      <div v-if="searchCitations.length" class="hc-citations">
        <el-tag v-for="c in searchCitations" :key="c.faqId" size="small" type="info" style="margin-right: 8px;">
          相关：#{{ c.faqId }} {{ c.question }}
        </el-tag>
      </div>
    </div>

    <div class="hc-body">
      <aside class="hc-side">
        <div class="hc-side-title">问题分类</div>
        <div class="hc-cat" :class="{ active: !category }" @click="selectCategory('')">全部</div>
        <div v-for="cat in categories" :key="cat" class="hc-cat" :class="{ active: category === cat }" @click="selectCategory(cat)">
          {{ cat }}
        </div>
      </aside>

      <main class="hc-main" v-loading="loading">
        <div v-for="faq in faqs" :key="faq.id" class="hc-faq" :class="{ open: expanded === faq.id }">
          <div class="hc-q" @click="toggleFaq(faq)">
            <span class="hc-q-text">{{ faq.question }}</span>
            <span class="hc-q-meta">{{ faq.viewCount || 0 }} 次浏览</span>
          </div>
          <div v-if="expanded === faq.id" class="hc-a">
            <p class="hc-a-text">{{ faq.answer }}</p>
            <div class="hc-actions">
              <el-button size="small" :type="faq.liked ? 'primary' : 'default'" @click.stop="vote(faq, 'like')">
                有用（{{ faq.likeCount || 0 }}）
              </el-button>
              <el-button size="small" @click.stop="vote(faq, 'dislike')">
                没用（{{ faq.dislikeCount || 0 }}）
              </el-button>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && !faqs.length" description="暂无相关内容" />
        <TablePager v-if="total > 0" v-model:page="page" v-model:size="size" :total="total" @change="loadFaqs" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import { helpSearch, helpFaqs, helpCategories, helpFeedback, helpView } from '../api'
import TablePager from '../components/TablePager.vue'

/** embedded：嵌在知识库弹窗里时隐藏外层大标题，避免和对话框标题重复 */
defineProps({
  embedded: { type: Boolean, default: false }
})

const tenantCode = 'default'
const question = ref('')
const searching = ref(false)
const searchReply = ref('')
const searchCitations = ref([])
const categories = ref([])
const category = ref('')
const faqs = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const expanded = ref(null)

const doSearch = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  searching.value = true
  try {
    const res = await helpSearch(question.value.trim(), tenantCode)
    const data = res.data
    searchReply.value = data?.reply || ''
    searchCitations.value = data?.citations || []
  } catch {
    searchReply.value = ''
  } finally {
    searching.value = false
  }
}

/** 清空问题与智能答复，列表回到全部分类 */
const resetSearch = () => {
  question.value = ''
  searchReply.value = ''
  searchCitations.value = []
  category.value = ''
  page.value = 1
  loadFaqs()
}

const loadCategories = async () => {
  try {
    const res = await helpCategories(tenantCode)
    categories.value = res.data || []
  } catch {
    categories.value = []
  }
}

const loadFaqs = async () => {
  loading.value = true
  try {
    const params = { tenantCode, page: page.value, size: size.value }
    if (category.value) params.category = category.value
    const res = await helpFaqs(params)
    const data = res.data
    faqs.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    faqs.value = []
  } finally {
    loading.value = false
  }
}

const selectCategory = (cat) => {
  category.value = cat
  page.value = 1
  loadFaqs()
}

const toggleFaq = async (faq) => {
  if (expanded.value === faq.id) {
    expanded.value = null
    return
  }
  expanded.value = faq.id
  try {
    await helpView(faq.id)
  } catch {
    /* 忽略浏览计数失败 */
  }
}

const vote = async (faq, type) => {
  try {
    await helpFeedback(faq.id, type)
    ElMessage.success(type === 'like' ? '感谢您的反馈' : '已记录，我们会持续改进')
    if (type === 'like') faq.likeCount = (faq.likeCount || 0) + 1
    else faq.dislikeCount = (faq.dislikeCount || 0) + 1
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  loadCategories()
  loadFaqs()
})
</script>

<style scoped>
.help-center {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 16px 48px;
}
.help-center.embedded {
  max-width: none;
  padding: 0 4px 8px;
}
.help-center.embedded .hc-side {
  position: static;
}
.hc-header {
  text-align: center;
  margin-bottom: 24px;
}
.hc-header h1 {
  font-size: 28px;
  color: #1c2b4a;
  margin: 0 0 8px;
}
.hc-header p {
  color: #8492a6;
  font-size: 14px;
  margin: 0;
}
.hc-search {
  max-width: 680px;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.hc-search .el-input {
  flex: 1;
}
.hc-search-actions {
  display: inline-flex;
  gap: 8px;
  flex-shrink: 0;
}
.hc-result {
  max-width: 680px;
  margin: 0 auto 24px;
  padding: 16px 20px;
  background: linear-gradient(180deg, #f7f9ff 0%, #edf2ff 100%);
  border: 1px solid #d9e4ff;
  border-radius: 12px;
}
.hc-result-title {
  font-weight: 600;
  color: #2a3f5f;
  margin-bottom: 8px;
  font-size: 14px;
}
.hc-result p {
  margin: 0 0 8px;
  color: #303133;
  line-height: 1.7;
}
.hc-body {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.hc-side {
  width: 180px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #e7edf5;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 8px 28px rgba(28, 43, 74, 0.06);
  position: sticky;
  top: 16px;
}
.hc-side-title {
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 12px;
  font-size: 14px;
}
.hc-cat {
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  color: #5e6d82;
  font-size: 14px;
  transition: all 0.2s;
}
.hc-cat:hover {
  background: #edf2ff;
  color: #2f6bff;
}
.hc-cat.active {
  background: #edf2ff;
  color: #2f6bff;
  font-weight: 600;
}
.hc-main {
  flex: 1;
  min-height: 300px;
}
.hc-faq {
  background: #fff;
  border: 1px solid #e7edf5;
  border-radius: 12px;
  margin-bottom: 12px;
  box-shadow: 0 8px 28px rgba(28, 43, 74, 0.04);
  overflow: hidden;
}
.hc-faq.open {
  border-color: #c4d4ff;
  box-shadow: 0 8px 24px rgba(47, 107, 255, 0.1);
}
.hc-q {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  cursor: pointer;
  transition: background 0.2s;
}
.hc-q:hover {
  background: #fafbfc;
}
.hc-q-text {
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 500;
}
.hc-q-meta {
  color: #b1b9c4;
  font-size: 12px;
  flex-shrink: 0;
}
.hc-a {
  padding: 0 20px 18px;
  border-top: 1px dashed #eef1f5;
  animation: hcFade 0.2s ease;
}
.hc-a-text {
  color: #5e6d82;
  line-height: 1.8;
  white-space: pre-wrap;
  margin: 16px 0;
}
.hc-actions {
  display: flex;
  gap: 10px;
}
.hc-pager {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
@keyframes hcFade {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
@media (max-width: 640px) {
  .hc-body {
    flex-direction: column;
  }
  .hc-side {
    width: 100%;
    position: static;
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
  }
  .hc-side-title {
    width: 100%;
    margin-bottom: 4px;
  }
}
</style>
