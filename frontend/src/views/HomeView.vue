<template>
  <div class="page-container">
    <!-- 首屏 -->
    <section class="hero panel">
      <p class="badge">AI 驱动的 Web 应用</p>
      <h1 class="hero-title">搞清「该不该考、考什么、怎么考」</h1>
      <p class="hero-desc">
        面向在校大学生（大二 ~ 大四）的考研备考助手。用知识点体系与掌握度建模，
        把「感觉自己不行」变成「具体哪章不行」，再据此生成可执行的备考路径。
      </p>
      <div class="hero-actions">
        <el-button type="primary" size="large" @click="$router.push('/college')">
          浏览院校专业库
        </el-button>
        <el-button size="large" @click="$router.push('/chat')">问问 AI</el-button>
      </div>
    </section>

    <!-- 数据概览：加载中显示骨架屏，失败显示占位符（不报错、不白屏） -->
    <section class="stats">
      <template v-if="statsLoading">
        <el-skeleton v-for="i in 4" :key="i" animated class="stat-card panel">
          <template #template>
            <el-skeleton-item variant="h1" style="width: 60px; height: 32px; margin: 0 auto 8px" />
            <el-skeleton-item variant="text" style="width: 70%; margin: 0 auto" />
          </template>
        </el-skeleton>
      </template>
      <div v-for="item in statCards" :key="item.label" class="stat-card panel">
        <div class="stat-value">{{ item.value ?? '—' }}</div>
        <div class="stat-label">{{ item.label }}</div>
      </div>
    </section>

    <!-- 功能卡片 -->
    <section class="panel">
      <h2 class="section-title">核心能力</h2>
      <p class="section-desc">按开发进度排列，打勾的表示已经能用</p>

      <div class="feature-grid">
        <div v-for="f in features" :key="f.title" class="feature">
          <div class="feature-head">
            <span class="feature-title">{{ f.title }}</span>
            <el-tag :type="f.done ? 'success' : 'info'" size="small" disable-transitions>
              {{ f.done ? '已上线' : '开发中' }}
            </el-tag>
          </div>
          <p class="feature-desc">{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 设计原则 -->
    <section class="panel">
      <h2 class="section-title">我们如何避免 AI 胡说</h2>
      <p class="section-desc">这是整个项目最重要的一条设计原则</p>
      <ul class="principle-list">
        <li>
          <strong>事实放在数据库里</strong>：院校、专业、科目这类信息查库得到，不让模型自由发挥。
        </li>
        <li>
          <strong>算法放在代码里</strong>：掌握度、阶段切分、间隔重复都是可测试、可复现的 Java 代码。
        </li>
        <li>
          <strong>模型只做它擅长的</strong>：组织语言、个性化推理、生成讲解。
        </li>
        <li>
          <strong>输出可溯源</strong>：涉及政策与数据的内容标注来源，并提示以官方公告为准。
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getStats } from '@/api'

const stats = ref(null)
const statsLoading = ref(true)

onMounted(async () => {
  try {
    stats.value = await getStats()
  } catch (e) {
    // 后端没启动时保持 null，页面显示占位符，不报错
    stats.value = null
  } finally {
    statsLoading.value = false
  }
})

const statCards = computed(() => [
  { label: '收录院校', value: stats.value?.collegeCount },
  { label: '收录专业', value: stats.value?.majorCount },
  { label: '覆盖城市', value: stats.value?.cityCount },
  { label: '自主划线高校', value: stats.value?.selfLineCount }
])

const features = [
  {
    title: '院校专业库',
    desc: '按城市、层次、985/211、自主划线、关键词筛选院校与考研专业，数据可追溯。',
    done: true
  },
  {
    title: 'AI 问答',
    desc: '接入大模型的考研问题解答，支持多轮上下文与历史记录。',
    done: true
  },
  {
    title: '学情诊断',
    desc: '知识点体系 + 测评，输出每个知识点的掌握度，明确指出薄弱章节。',
    done: false
  },
  {
    title: '备考计划',
    desc: '按考试倒计时切分基础/强化/冲刺三阶段，并根据实际进度动态调整。',
    done: false
  },
  {
    title: '认知科普',
    desc: '考研全流程、时间节点与常见名词解释，答案基于知识库、可溯源。',
    done: false
  },
  {
    title: '错题复盘',
    desc: '错题归因分类与间隔重复，薄弱点按遗忘曲线自动重现。',
    done: false
  }
]
</script>

<style scoped>
.hero {
  padding: 36px 28px;
}

.badge {
  display: inline-block;
  margin: 0 0 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: var(--brand);
  font-size: 12px;
}

.hero-title {
  margin: 0 0 12px;
  font-size: 30px;
  line-height: 1.35;
  font-weight: 700;
}

.hero-desc {
  margin: 0 0 24px;
  max-width: 640px;
  color: var(--text-muted);
  font-size: 15px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 16px;
}

.stat-card {
  text-align: center;
  padding: 18px 12px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--brand);
}

.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-muted);
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.feature {
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 16px;
  background: #fcfcfd;
}

.feature-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.feature-title {
  font-weight: 600;
}

.feature-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.principle-list {
  margin: 0;
  padding-left: 20px;
}

.principle-list li {
  margin-bottom: 8px;
  font-size: 14px;
}

@media (max-width: 900px) {
  .feature-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 720px) {
  .hero {
    padding: 24px 18px;
  }

  .hero-title {
    font-size: 22px;
  }

  .stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }
}
</style>
