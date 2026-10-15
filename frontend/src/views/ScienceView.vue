<template>
  <div class="page-container">
    <section class="panel science-panel">
      <header class="science-header">
        <h1 class="section-title">认知科普</h1>
        <p class="section-desc">
          系统讲解考研常见术语、学硕与专硕的区别、完整备考时间线等。下方为<strong>预设的固定科普文本</strong>，
          内容稳定、可随时阅读；若对某段讲解还有疑问，直接用右侧的 AI 助手追问即可。
        </p>
      </header>

      <!-- 左右两栏：左为主讲解（固定文本），右为 AI 答疑（独立对话窗口） -->
      <div class="science-layout">
        <!-- 左栏：主题切换 + 固定科普正文 -->
        <div class="science-main">
          <nav class="topic-tabs" aria-label="科普主题">
            <button
              v-for="t in topics"
              :key="t.key"
              class="topic-tab"
              :class="{ active: t.key === activeKey }"
              @click="selectTopic(t.key)"
            >
              {{ t.title }}
            </button>
          </nav>

          <article class="article" aria-live="polite">
            <h2 class="article-title">{{ active.title }}</h2>
            <div v-if="active.tags && active.tags.length" class="article-tags">
              <el-tag
                v-for="tag in active.tags"
                :key="tag"
                size="small"
                type="info"
                effect="plain"
                class="article-tag"
              >
                {{ tag }}
              </el-tag>
            </div>
            <div class="article-text">{{ active.content }}</div>
          </article>
        </div>

        <!-- 右栏：独立 AI 答疑窗口，全程可见可用 -->
        <aside class="science-aside">
          <ScienceChat :context="active.title" />
        </aside>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ScienceChat from '@/components/ScienceChat.vue'
import { scienceTopics, findTopic } from '@/data/scienceContent'

// 科普内容全部来自本地静态数据，不调用模型，打开页面即可阅读
const topics = scienceTopics
const activeKey = ref(topics[0]?.key || '')
const active = ref(findTopic(activeKey.value) || { title: '', content: '', tags: [] })

const selectTopic = (key) => {
  const t = findTopic(key)
  if (!t) return
  activeKey.value = key
  active.value = t
}
</script>

<style scoped>
.science-panel {
  display: flex;
  flex-direction: column;
}

.science-header {
  margin-bottom: 16px;
}

/* 左右两栏：左边自适应，右边固定宽度 */
.science-layout {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
  align-items: start;
}

/* 左栏主讲解区 */
.science-main {
  min-width: 0;
}

.topic-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.topic-tab {
  padding: 7px 14px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #fff;
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.topic-tab:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.topic-tab.active {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
  font-weight: 600;
}

.article {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  padding: 22px 26px;
}

.article-title {
  margin: 0 0 10px;
  font-size: 20px;
  color: var(--text);
}

.article-tags {
  margin-bottom: 14px;
}

.article-tag {
  margin: 0 6px 6px 0;
}

.article-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 15px;
  line-height: 1.95;
  color: var(--text);
}

/* 右栏：独立 AI 答疑，钉在视口，滚动左侧时始终可见 */
.science-aside {
  position: sticky;
  top: 16px;
  height: calc(100vh - 120px);
  min-height: 420px;
}

/* 窄屏：上下堆叠，AI 窗口放到正文下方 */
@media (max-width: 920px) {
  .science-layout {
    grid-template-columns: 1fr;
  }

  .science-aside {
    position: static;
    height: 520px;
  }
}
</style>
