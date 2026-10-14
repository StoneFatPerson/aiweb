<template>
  <div class="page-container">
    <section class="panel">
      <h1 class="section-title">备考计划</h1>
      <p class="section-desc">
        基于你的学情诊断结果（BKT 算法算出的各知识点掌握度）动态生成基础 / 强化 / 冲刺三阶段计划。薄弱科目会自动分到更多精力。
      </p>

      <!-- 加载中 -->
      <div v-if="loading" class="state-box">正在读取你的诊断结果…</div>

      <!-- 未测评：引导去做题 -->
      <el-result
        v-else-if="!data"
        icon="info"
        title="你还没做学情诊断"
        sub-title="先做几道数学题，这里就会生成专属于你的备考计划。"
      >
        <template #extra>
          <el-button type="primary" @click="goAssess">去学情诊断</el-button>
        </template>
      </el-result>

      <!-- 已测评：展示真实计划 -->
      <template v-else>
        <!-- 薄弱点 -->
        <div v-if="data.weakPoints && data.weakPoints.length" class="block">
          <h3 class="block-title">薄弱点（优先攻克）</h3>
          <div class="weak-box">
            <el-tag
              v-for="w in data.weakPoints"
              :key="w"
              type="danger"
              size="small"
              class="weak-tag"
            >{{ w }}</el-tag>
          </div>
        </div>

        <!-- 三阶段计划 -->
        <div class="block">
          <h3 class="block-title">为你定制的计划</h3>
          <el-alert
            :closable="false"
            type="success"
            show-icon
            :title="data.plan.summary"
            class="plan-alert"
          />
          <el-timeline class="plan-timeline">
            <el-timeline-item
              v-for="ph in data.plan.phases"
              :key="ph.name"
              :timestamp="`${ph.name}（${ph.weeks}）`"
              placement="top"
              type="primary"
            >
              <p class="phase-focus">{{ ph.focus }}</p>
              <div class="phase-points">
                <el-tag
                  v-for="p in ph.points"
                  :key="p"
                  size="small"
                  class="phase-tag"
                >{{ p }}</el-tag>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 掌握度明细 -->
        <div class="block">
          <h3 class="block-title">各知识点掌握度</h3>
          <div class="mastery-list">
            <div
              v-for="d in sortedDiagnosis"
              :key="d.pointCode"
              class="mastery-row"
            >
              <span class="mastery-name">{{ d.pointName }}</span>
              <el-progress
                :percentage="Math.round(d.mastery * 100)"
                :status="d.level === '薄弱' ? 'exception' : (d.level === '扎实' ? 'success' : '')"
                :stroke-width="10"
                class="mastery-bar"
              />
              <span class="mastery-level" :class="d.level">{{ d.level }}</span>
            </div>
          </div>
        </div>

        <div class="actions">
          <el-button type="primary" @click="goAssess">重新测评</el-button>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getDiagnosis } from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(true)
const data = ref(null)

// 掌握度从低到高排列，薄弱的排最前，方便优先看
const sortedDiagnosis = computed(() => {
  if (!data.value || !data.value.diagnosis) return []
  return [...data.value.diagnosis].sort((a, b) => a.mastery - b.mastery)
})

// 和学情诊断页用同一个匿名 ID（无登录，纯本地存储）
function getStudentId() {
  let id = localStorage.getItem('aiweb_student_id')
  if (!id) {
    id = 'stu-' + (crypto.randomUUID
      ? crypto.randomUUID()
      : Date.now() + '-' + Math.random().toString(16).slice(2))
    localStorage.setItem('aiweb_student_id', id)
  }
  return id
}

async function load() {
  loading.value = true
  try {
    const res = await getDiagnosis(getStudentId())
    // 后端用 assessed 标记是否真的做过测评：没做过就不展示计划，引导去答题
    data.value = (res && res.assessed) ? res : null
  } catch (e) {
    ElMessage.error(e.message || '读取诊断失败，请确认后端已启动')
    data.value = null
  } finally {
    loading.value = false
  }
}

function goAssess() {
  router.push('/assessment')
}

onMounted(load)
</script>

<style scoped>
.state-box {
  padding: 40px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 14px;
}

.block {
  margin-top: 28px;
}
.block-title {
  font-size: 16px;
  margin: 0 0 14px;
}

.plan-alert {
  margin-bottom: 16px;
}
.phase-focus {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--text-muted);
}
.phase-points {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.weak-box {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.weak-tag {
  margin-bottom: 4px;
}

.mastery-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mastery-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.mastery-name {
  width: 240px;
  flex-shrink: 0;
  font-size: 13px;
}
.mastery-bar {
  flex: 1;
}
.mastery-level {
  width: 40px;
  text-align: right;
  font-size: 12px;
}
.mastery-level.薄弱 { color: #dc2626; }
.mastery-level.中等 { color: #d97706; }
.mastery-level.扎实 { color: #16a34a; }

.actions {
  margin-top: 24px;
}

@media (max-width: 640px) {
  .mastery-name { width: 120px; }
}
</style>
