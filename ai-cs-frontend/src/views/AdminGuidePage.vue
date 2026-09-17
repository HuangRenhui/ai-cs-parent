<template>
  <div class="page-card">
    <div class="guide-head">
      <div>
        <h3>功能指南</h3>
        <p class="page-desc">
          管理端帮助中心。按模块讲清「解决什么问题、有哪些概念、先点哪里、别踩什么坑」，
          零业务背景也能照着把系统跑起来。
          模块按<b>上手顺序</b>编排（配大脑 → 喂知识 → 接系统 → 接待），
          与侧栏按<b>使用频率</b>排的顺序不同——找某个功能在哪，请对照侧栏。
        </p>
      </div>
      <el-input v-model="keyword" placeholder="搜索概念，例如 向量 / 工单" clearable class="guide-search">
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <div class="guide-body">
      <nav class="guide-nav">
        <button
          v-for="m in modules"
          :key="m.key"
          type="button"
          class="guide-nav-item"
          :class="{ active: m.key === activeKey }"
          @click="goModule(m.key)"
        >
          <el-icon><component :is="MENU_ICONS[m.icon]" /></el-icon>
          <span>{{ m.title }}</span>
        </button>
      </nav>

      <section class="guide-main">
        <!-- 命中概念时优先给结果，避免只在当前模块里翻 -->
        <template v-if="searching">
          <h4 class="guide-h4">「{{ keyword.trim() }}」命中 {{ hits.length }} 条概念</h4>
          <el-empty v-if="!hits.length" description="没找到相关概念，换个词试试" />
          <div v-else class="hit-list">
            <button v-for="h in hits" :key="h.moduleKey + h.term" type="button" class="hit" @click="goModule(h.moduleKey)">
              <div class="hit-term">
                {{ h.term }}
                <span class="hit-module">{{ h.module }}</span>
              </div>
              <p class="hit-desc">{{ h.desc }}</p>
            </button>
          </div>
        </template>

        <template v-else-if="current">
          <div class="guide-title">
            <el-icon :size="20"><component :is="MENU_ICONS[current.icon]" /></el-icon>
            <h4>{{ current.title }}</h4>
            <span class="guide-tagline">{{ current.tagline }}</span>
          </div>

          <div class="guide-block">
            <h5>这个模块解决什么</h5>
            <p class="guide-text">{{ current.what }}</p>
          </div>

          <div class="guide-block">
            <h5>关键概念</h5>
            <dl class="concept-list">
              <template v-for="c in current.concepts" :key="c.term">
                <dt>{{ c.term }}</dt>
                <dd>{{ c.desc }}</dd>
              </template>
            </dl>
          </div>

          <div class="guide-block">
            <h5>上手步骤</h5>
            <ol class="step-list">
              <li v-for="(s, i) in current.steps" :key="i">{{ s }}</li>
            </ol>
          </div>

          <div class="guide-block" v-if="current.pitfalls && current.pitfalls.length">
            <h5>容易踩的坑</h5>
            <ul class="pitfall-list">
              <li v-for="(p, i) in current.pitfalls" :key="i">{{ p }}</li>
            </ul>
          </div>

          <div class="guide-block" v-if="current.related && current.related.length">
            <h5>直接去做</h5>
            <div class="related-links">
              <el-button v-for="p in current.related" :key="p" size="small" @click="$router.push(p)">
                {{ pathLabel(p) }} →
              </el-button>
            </div>
          </div>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
/**
 * 管理端功能指南。内容全部来自 config/guide.js，改文案不用动组件。
 * 与 C 端帮助中心的区别：那边是访客查 FAQ，这边是给接手系统的人做业务扫盲。
 */
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { GUIDE_MODULES, GUIDE_SEARCH_INDEX } from '../config/guide'
import { MENUS, MENU_ICONS } from '../config/menus'

const modules = GUIDE_MODULES
const activeKey = ref(GUIDE_MODULES[0].key)
const keyword = ref('')

const searching = computed(() => !!keyword.value.trim())
const current = computed(() => modules.find((m) => m.key === activeKey.value))

const hits = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return []
  return GUIDE_SEARCH_INDEX.filter((h) => `${h.term}${h.desc}${h.module}`.toLowerCase().includes(k))
})

/** 页面路径 → 中文名，从菜单配置反推，避免再维护一份同名映射 */
const PATH_LABEL = (() => {
  const map = {}
  const walk = (list) => list.forEach((m) => {
    map[m.path] = m.label
    if (m.children) walk(m.children)
  })
  walk(MENUS)
  return map
})()
const pathLabel = (p) => PATH_LABEL[p] || p

/** 点模块同时清掉搜索词，否则内容被搜索结果挡住 */
const goModule = (key) => {
  activeKey.value = key
  keyword.value = ''
}
</script>

<style scoped>
.guide-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.guide-head h3 {
  margin: 0 0 6px;
  font-size: 17px;
  color: #1c2b4a;
}
.guide-search {
  width: 260px;
  flex-shrink: 0;
}
.guide-body {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
/* 左侧模块导航 */
.guide-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: sticky;
  top: 12px;
}
.guide-nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  border: none;
  border-radius: 8px;
  background: #f7f9fc;
  color: #344054;
  font-size: 13.5px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.guide-nav-item:hover {
  background: #eef3fd;
  color: #1f4fd6;
}
.guide-nav-item.active {
  background: #2f6bff;
  color: #fff;
}
/* 右侧内容 */
.guide-main {
  min-width: 0;
}
.guide-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef2f7;
}
.guide-title h4 {
  margin: 0;
  font-size: 17px;
  color: #1c2b4a;
}
.guide-tagline {
  font-size: 12.5px;
  color: #667085;
  background: #f4f7fb;
  border-radius: 999px;
  padding: 3px 10px;
}
.guide-block {
  margin-top: 18px;
}
.guide-block h5 {
  margin: 0 0 8px;
  font-size: 13.5px;
  font-weight: 700;
  color: #1c2b4a;
}
.guide-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.85;
  color: #475467;
}
.concept-list {
  margin: 0;
  display: grid;
  /* 列宽要容得下「知识库（Knowledge）」这类中英混排术语，窄了会折成两行 */
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 6px 14px;
}
.concept-list dt {
  font-size: 13px;
  font-weight: 650;
  color: #1f4fd6;
}
.concept-list dd {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: #475467;
}
.step-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13.5px;
  line-height: 1.9;
  color: #475467;
}
.step-list li { margin: 3px 0; }
.pitfall-list {
  margin: 0;
  padding: 12px 16px 12px 32px;
  background: #fffaeb;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.8;
  color: #93370d;
}
.pitfall-list li { margin: 3px 0; }
.related-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.guide-h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #1c2b4a;
}
.hit-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hit {
  display: block;
  width: 100%;
  text-align: left;
  padding: 12px 14px;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  background: #fbfcfe;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.hit:hover {
  border-color: #9ab6ff;
  background: #f4f8ff;
}
.hit-term {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 700;
  color: #1f4fd6;
}
.hit-module {
  font-size: 11.5px;
  font-weight: 600;
  color: #667085;
  background: #f4f7fb;
  border-radius: 999px;
  padding: 2px 8px;
}
.hit-desc {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: #475467;
}

/* 窄屏：导航横排，内容占满 */
@media (max-width: 860px) {
  .guide-body {
    grid-template-columns: 1fr;
  }
  .guide-nav {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
  }
  .guide-nav-item {
    width: auto;
  }
  .guide-search {
    width: 100%;
  }
  .concept-list {
    grid-template-columns: 1fr;
    gap: 2px;
  }
  .concept-list dd {
    margin-bottom: 8px;
  }
}
</style>
