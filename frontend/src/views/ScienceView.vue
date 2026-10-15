<template>
  <div class="page-container">
    <section class="panel explain-panel">
      <header class="explain-header">
        <h1 class="section-title">认知科普</h1>
        <p class="section-desc">
          系统讲解考研常见术语、学硕与专硕的区别、完整备考时间线等。
          下方内容由 AI 依据考研知识库<strong>主动整理成说明性文章</strong>，只来自已收录资料、不凭空编造。
        </p>
      </header>

      <div class="explain-body">
        <!-- 左侧：主题目录（点击即主动讲解，而非问答） -->
        <aside class="topic-list" aria-label="科普主题">
          <el-skeleton v-if="loadingTopics" :rows="6" animated />
          <button
            v-for="t in topics"
            :key="t.key"
            class="topic-item"
            :class="{ active: t.key === activeKey }"
            @click="selectTopic(t.key)"
          >
            {{ t.title }}
          </button>
        </aside>

        <!-- 右侧：说明性文章 -->
        <article class="article" aria-live="polite">
          <el-skeleton v-if="loading" :rows="12" animated />
          <template v-else>
            <h2 class="article-title">{{ article.title }}</h2>
            <div class="article-text">{{ article.content }}</div>

            <div v-if="article.sources && article.sources.length" class="sources">
              <div class="sources-title">参考来源（知识库）</div>
              <el-tag
                v-for="s in article.sources"
                :key="s"
                size="small"
                type="info"
                effect="plain"
                class="source-tag"
              >
                {{ s }}
              </el-tag>
            </div>

            <div class="article-actions">
              <el-button text size="small" :loading="loading" @click="regenerate">
                重新生成讲解
              </el-button>
            </div>
          </template>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getScienceTopics, explainScience } from '@/api'

const topics = ref([])
const activeKey = ref('')
const article = ref({ title: '', content: '', sources: [] })
const loadingTopics = ref(false)
const loading = ref(false)

// 进入页面即主动加载第一个主题，体现"主动说明"而非"等用户提问"
const loadTopics = async () => {
  loadingTopics.value = true
  try {
    const list = await getScienceTopics()
    topics.value = list || []
    if (topics.value.length) {
      await selectTopic(topics.value[0].key)
    }
  } catch (e) {
    ElMessage.error('主题加载失败：' + e.message)
  } finally {
    loadingTopics.value = false
  }
}

const selectTopic = async (key) => {
  if (loading.value) return
  activeKey.value = key
  loading.value = true
  try {
    // 后端先去知识库检索相关资料，再让大模型把资料整理成连贯说明性文章
    const data = await explainScience(key)
    article.value = {
      title: (data && data.title) || '',
      content: (data && data.content) || '（没有返回内容）',
      sources: (data && data.sources) || []
    }
  } catch (e) {
    article.value = { title: '', content: '请求失败：' + e.message, sources: [] }
  } finally {
    loading.value = false
  }
}

const regenerate = () => {
  if (activeKey.value) selectTopic(activeKey.value)
}

onMounted(loadTopics)
</script>

<style scoped>
.explain-panel {
  display: flex;
  flex-direction: column;
}

.explain-header {
  margin-bottom: 16px;
}

.explain-body {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 20px;
  align-items: start;
}

/* 左侧目录 */
.topic-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: sticky;
  top: 16px;
}

.topic-item {
  text-align: left;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  color: var(--text);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.topic-item:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.topic-item.active {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
  font-weight: 600;
}

/* 右侧文章 */
.article {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
  padding: 22px 26px;
  min-height: 320px;
}

.article-title {
  margin: 0 0 14px;
  font-size: 20px;
  color: var(--text);
}

.article-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 15px;
  line-height: 1.9;
  color: var(--text);
}

.sources {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.sources-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.source-tag {
  margin: 0 6px 6px 0;
}

.article-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .explain-body {
    grid-template-columns: 1fr;
  }

  .topic-list {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .topic-item {
    flex: 1 1 auto;
    text-align: center;
  }
}
</style>
