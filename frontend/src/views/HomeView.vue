<template>
  <div class="home">
    <!-- ============ 首屏：深色渐变 Hero（视觉焦点） ============ -->
    <section class="hero" aria-labelledby="hero-title">
      <!-- 装饰层：两团径向光斑 + 细网格，纯 CSS 绘制，无图片请求 -->
      <div class="hero-glow hero-glow-a" aria-hidden="true" />
      <div class="hero-glow hero-glow-b" aria-hidden="true" />
      <div class="hero-grid" aria-hidden="true" />

      <div class="hero-inner">
        <p class="hero-badge">AI 驱动的考研备考助手</p>
        <h1 id="hero-title" class="hero-title">
          搞清「该不该考、<br class="title-break" />考什么、怎么考」
        </h1>
        <p class="hero-desc">
          面向在校大学生（大二 ~ 大四）：查院校专业、诊断知识薄弱点、
          让 AI 把一句话目标拆成看得懂、做得到的备考路径。
        </p>
        <div class="hero-actions">
          <el-button class="hero-btn-primary" size="large" @click="$router.push('/college')">
            浏览院校专业库
          </el-button>
          <el-button class="hero-btn-ghost" size="large" @click="$router.push('/agent')">
            让 AI 帮我规划
          </el-button>
        </div>
      </div>

      <!-- 浮动装饰圆（纯装饰，读屏软件忽略） -->
      <span class="hero-orb hero-orb-1" aria-hidden="true" />
      <span class="hero-orb hero-orb-2" aria-hidden="true" />
    </section>

    <!-- ============ 数据概览：艺术化统计条 ============ -->
    <section class="stats" aria-label="数据概览">
      <template v-if="statsLoading">
        <div v-for="i in 4" :key="'sk-' + i" class="stat">
          <el-skeleton animated :rows="1" style="width: 60px; height: 34px; margin: 0 auto 10px" />
          <el-skeleton animated :rows="1" style="width: 70%; margin: 0 auto" />
        </div>
      </template>
      <div v-for="item in statCards" v-else :key="item.label" class="stat">
        <div class="stat-value">{{ item.value ?? '—' }}</div>
        <div class="stat-label">{{ item.label }}</div>
      </div>
    </section>

    <!-- ============ 三大入口卡片（首页导航的视觉化） ============ -->
    <section class="entries" aria-labelledby="entries-title">
      <h2 id="entries-title" class="entries-title">从这里开始</h2>
      <div class="entry-grid">
        <button
          v-for="(entry, i) in entries"
          :key="entry.title"
          type="button"
          class="entry"
          :style="{ animationDelay: i * 90 + 'ms' }"
          @click="$router.push(entry.to)"
        >
          <span class="entry-icon" aria-hidden="true">{{ entry.icon }}</span>
          <span class="entry-title">{{ entry.title }}</span>
          <span class="entry-desc">{{ entry.desc }}</span>
          <span class="entry-go" aria-hidden="true">→</span>
        </button>
      </div>
    </section>

    <!-- ============ 理念轻收尾：一句话，不占版面 ============ -->
    <footer class="creed">
      <p>事实来自数据库与知识库，AI 只负责组织语言——答案有据可查。</p>
    </footer>
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

// 三大入口：覆盖项目的三条主线（查信息 → 诊断 → 规划）
const entries = [
  {
    icon: '🏫',
    title: '院校专业库',
    desc: '按城市、层次、985/211 筛选山东 20+ 所院校的热门考研专业与考试科目。',
    to: '/college'
  },
  {
    icon: '📊',
    title: '学情诊断',
    desc: '做几道题，算法算出每个知识点的掌握度，把「感觉不行」变成「具体哪章不行」。',
    to: '/assess'
  },
  {
    icon: '🧭',
    title: 'AI 备考规划',
    desc: '一句话说清目标，AI 自动拆步骤：查院校、检索知识、结合诊断生成三阶段计划。',
    to: '/agent'
  }
]
</script>

<style scoped>
/* ============ Hero：深色渐变 + 光斑 + 网格 ============ */
.home {
  /* 挣脱 page-container 的窄约束：Hero 通栏，下面内容再回收宽度 */
  max-width: none;
  padding: 0;
}

.hero {
  position: relative;
  overflow: hidden;
  margin: 0;
  border: none;
  border-radius: 0 0 28px 28px;
  padding: 72px 24px 88px;
  background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #3b82f6 100%);
  color: #fff;
}

/* 两团径向光斑，营造纵深 */
.hero-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(6px);
  pointer-events: none;
}
.hero-glow-a {
  width: 520px;
  height: 520px;
  top: -180px;
  right: -120px;
  background: radial-gradient(circle, rgba(147, 197, 253, 0.35) 0%, transparent 65%);
}
.hero-glow-b {
  width: 420px;
  height: 420px;
  bottom: -160px;
  left: -100px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.4) 0%, transparent 65%);
}

/* 细网格纹理 */
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 80% 70% at 50% 40%, #000 30%, transparent 75%);
  pointer-events: none;
}

.hero-inner {
  position: relative;
  max-width: 760px;
  margin: 0 auto;
  text-align: center;
}

.hero-badge {
  display: inline-block;
  margin: 0 0 18px;
  padding: 5px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(6px);
  color: #dbeafe;
  font-size: 13px;
  letter-spacing: 0.5px;
}

.hero-title {
  margin: 0 0 16px;
  font-size: clamp(28px, 5vw, 44px);
  line-height: 1.3;
  font-weight: 800;
  letter-spacing: 0.5px;
  text-shadow: 0 2px 20px rgba(0, 0, 0, 0.15);
}

.hero-desc {
  margin: 0 auto 32px;
  max-width: 560px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 15px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
}

/* 深色底上的主按钮：白底 brand 字，比蓝底白字更精神 */
.hero-btn-primary.el-button {
  background: #fff;
  border-color: #fff;
  color: var(--brand-dark);
  font-weight: 600;
  padding: 12px 26px;
}
.hero-btn-primary.el-button:hover {
  background: #eff6ff;
  border-color: #eff6ff;
  color: var(--brand-dark);
}

.hero-btn-ghost.el-button {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.45);
  color: #fff;
  padding: 12px 26px;
}
.hero-btn-ghost.el-button:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: #fff;
  color: #fff;
}

/* 浮动装饰圆 */
.hero-orb {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.22);
  pointer-events: none;
  animation: floaty 7s ease-in-out infinite;
}
.hero-orb-1 {
  width: 130px;
  height: 130px;
  top: 18%;
  left: 8%;
}
.hero-orb-2 {
  width: 74px;
  height: 74px;
  bottom: 16%;
  right: 11%;
  animation-delay: -3.5s;
}
@keyframes floaty {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-14px); }
}

/* ============ 统计条：大数字 + 渐变字 ============ */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  max-width: 1120px;
  margin: -34px auto 0;
  padding: 0 16px;
  position: relative;
}

.stat {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
  text-align: center;
  padding: 20px 10px 16px;
}
/* 条内卡片之间用细缝分隔，像一块整体的数据板 */
.stat + .stat {
  margin-left: 10px;
}

.stat-value {
  font-size: 32px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  background: linear-gradient(135deg, var(--brand-dark), #60a5fa);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  line-height: 1.1;
}

.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-muted);
}

/* ============ 三大入口卡片 ============ */
.entries {
  max-width: 1120px;
  margin: 0 auto;
  padding: 48px 16px 8px;
}

.entries-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 700;
  text-align: center;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.entry {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  text-align: left;
  cursor: pointer;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 24px 22px 22px;
  font-family: inherit;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  animation: riseIn 0.5s ease both;
}
.entry:hover,
.entry:focus-visible {
  transform: translateY(-5px);
  border-color: #bfdbfe;
  box-shadow: 0 14px 32px rgba(37, 99, 235, 0.12);
}

.entry-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  font-size: 24px;
  margin-bottom: 4px;
}

.entry-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
}

.entry-desc {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-muted);
}

.entry-go {
  margin-top: 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--brand);
  transition: transform 0.2s ease;
}
.entry:hover .entry-go {
  transform: translateX(4px);
}

@keyframes riseIn {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ============ 理念轻收尾 ============ */
.creed {
  max-width: 1120px;
  margin: 0 auto;
  padding: 36px 16px 52px;
  text-align: center;
}
.creed p {
  display: inline-block;
  margin: 0;
  padding: 10px 20px;
  border-radius: 999px;
  background: #f1f5f9;
  color: var(--text-muted);
  font-size: 13px;
}

/* ============ 响应式 ============ */
@media (max-width: 900px) {
  .entry-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .hero {
    padding: 52px 18px 64px;
    border-radius: 0 0 20px 20px;
  }
  .title-break {
    display: none;
  }
  .hero-orb {
    display: none;
  }
  .stats {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
  .stat + .stat {
    margin-left: 0;
  }
  .entries {
    padding-top: 36px;
  }
}

/* 尊重「减少动态效果」的系统偏好（全局已禁动画，这里兜底关掉浮动） */
@media (prefers-reduced-motion: reduce) {
  .hero-orb {
    animation: none;
  }
}
</style>
