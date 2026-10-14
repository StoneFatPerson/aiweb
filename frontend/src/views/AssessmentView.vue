<template>
  <div class="page-container">
    <section class="panel">
      <h1 class="section-title">学情诊断与测评</h1>
      <p class="section-desc">
        做几道数学题，系统用 BKT 算法（贝叶斯知识追踪）算你每个知识点的掌握度，标出薄弱点并生成三阶段备考计划。
      </p>

      <!-- 步骤 1：设置 + 开始 -->
      <div v-if="step === 'setup'" class="setup">
        <el-alert
          type="info"
          show-icon
          :closable="false"
          title="关于你的身份"
          description="本功能不登录，会用浏览器本地保存一个匿名 ID 来追踪你的掌握度变化。换浏览器或清缓存会重置。"
          class="mb"
        />

        <div class="form-row">
          <div class="field">
            <label>你的匿名 ID</label>
            <el-input :value="studentId" readonly />
          </div>
          <div class="field">
            <label>目标数学类型</label>
            <el-select v-model="targetMath" class="grow">
              <el-option label="全部（math1/2/3 通用题）" value="all" />
              <el-option label="数学一" value="math1" />
              <el-option label="数学二" value="math2" />
              <el-option label="数学三" value="math3" />
            </el-select>
          </div>
          <div class="field">
            <label>抽题数量</label>
            <el-input-number v-model="count" :min="3" :max="30" />
          </div>
        </div>

        <div class="actions">
          <el-button type="primary" :loading="loading" @click="start">开始测评</el-button>
          <el-button :disabled="loading" @click="viewDiagnosis">查看我的诊断</el-button>
        </div>
      </div>

      <!-- 步骤 2：作答 -->
      <div v-else-if="step === 'answering'">
        <div class="answering-head">
          <span>共 {{ questions.length }} 题，请作答：</span>
          <el-button text @click="step = 'setup'">返回</el-button>
        </div>

        <div v-for="(q, i) in questions" :key="q.code" class="q-card">
          <div class="q-title">
            <span class="q-no">{{ i + 1 }}.</span>
            <span class="q-type" :class="q.type === 'FILL_BLANK' ? 'fill' : 'choice'">
              {{ q.type === 'FILL_BLANK' ? '填空' : '选择' }}
            </span>
            <span class="q-stem">{{ q.stem }}</span>
          </div>

          <el-radio-group v-if="q.type === 'SINGLE_CHOICE'" v-model="answers[q.code]" class="q-options">
            <el-radio v-for="opt in q.options" :key="opt.key" :value="opt.key" border>
              {{ opt.key }}. {{ opt.content }}
            </el-radio>
          </el-radio-group>

          <el-input
            v-else
            v-model="answers[q.code]"
            placeholder="请输入答案，如 1/2"
            class="q-fill"
          />
        </div>

        <div class="actions">
          <el-button type="primary" :loading="loading" @click="submit">提交并诊断</el-button>
          <el-button :disabled="loading" @click="step = 'setup'">取消</el-button>
        </div>
      </div>

      <!-- 步骤 3：结果 -->
      <div v-else-if="step === 'result' && result">
        <el-result
          :icon="result.score >= 60 ? 'success' : 'warning'"
          :title="`本次得分 ${result.score}`"
          :sub-title="`正确 ${result.correctCount} / ${result.total} 题`"
        >
          <template #extra>
            <el-button @click="step = 'setup'">再做一次</el-button>
          </template>
        </el-result>

        <!-- 逐题反馈 -->
        <div class="block">
          <h3 class="block-title">逐题解析</h3>
          <el-collapse>
            <el-collapse-item v-for="(f, i) in result.feedback" :key="f.code" :name="i">
              <template #title>
                <span class="fb-code">{{ f.code }}</span>
                <el-tag :type="f.correct ? 'success' : 'danger'" size="small" class="fb-tag">
                  {{ f.correct ? '答对' : '答错' }}
                </el-tag>
                <span class="fb-your">你的答案：{{ f.yourAnswer || '（空）' }}</span>
              </template>
              <p class="fb-line"><b>正确答案：</b>{{ f.correctAnswer }}</p>
              <p class="fb-line"><b>解析：</b>{{ f.explanation }}</p>
            </el-collapse-item>
          </el-collapse>
        </div>

        <!-- 知识点诊断 -->
        <div class="block">
          <h3 class="block-title">知识点掌握度诊断</h3>
          <div v-if="result.weakPoints.length" class="weak-box">
            <span class="weak-label">薄弱点：</span>
            <el-tag v-for="w in result.weakPoints" :key="w" type="danger" size="small" class="weak-tag">
              {{ w }}
            </el-tag>
          </div>
          <div class="mastery-list">
            <div v-for="d in result.diagnosis" :key="d.pointCode" class="mastery-row">
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

        <!-- 备考计划 -->
        <div class="block">
          <h3 class="block-title">备考计划（基础 / 强化 / 冲刺）</h3>
          <p class="plan-summary">{{ result.plan.summary }}</p>
          <el-timeline>
            <el-timeline-item
              v-for="ph in result.plan.phases"
              :key="ph.name"
              :timestamp="`${ph.name}（${ph.weeks}）`"
              placement="top"
              type="primary"
            >
              <p class="phase-focus">{{ ph.focus }}</p>
              <div class="phase-points">
                <el-tag v-for="p in ph.points" :key="p" size="small" class="phase-tag">{{ p }}</el-tag>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { startAssessment, submitAssessment, getDiagnosis } from '@/api'

const step = ref('setup')
const loading = ref(false)
const studentId = ref('')
const targetMath = ref('all')
const count = ref(10)

const questions = ref([])
const answers = reactive({})
const result = ref(null)

// 页面加载时取/建匿名 ID，存到 localStorage（无登录，纯本地）
onMounted(() => {
  let id = localStorage.getItem('aiweb_student_id')
  if (!id) {
    id = 'stu-' + (crypto.randomUUID ? crypto.randomUUID() : Date.now() + '-' + Math.random().toString(16).slice(2))
    localStorage.setItem('aiweb_student_id', id)
  }
  studentId.value = id
})

async function start() {
  loading.value = true
  try {
    const data = await startAssessment({
      studentId: studentId.value,
      targetMath: targetMath.value,
      count: count.value
    })
    questions.value = data.questions || []
    for (const k in answers) delete answers[k]
    step.value = 'answering'
  } catch (e) {
    ElMessage.error(e.message || '抽题失败，请确认后端已启动')
  } finally {
    loading.value = false
  }
}

async function submit() {
  // 校验是否全部作答
  const missing = questions.value.filter((q) => !answers[q.code])
  if (missing.length) {
    ElMessage.warning(`还有 ${missing.length} 题没作答`)
    return
  }
  loading.value = true
  try {
    const payload = {
      studentId: studentId.value,
      answers: questions.value.map((q) => ({ questionCode: q.code, answer: answers[q.code] }))
    }
    result.value = await submitAssessment(payload)
    step.value = 'result'
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    loading.value = false
  }
}

async function viewDiagnosis() {
  loading.value = true
  try {
    result.value = await getDiagnosis(studentId.value)
    step.value = 'result'
  } catch (e) {
    ElMessage.error(e.message || '获取诊断失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.mb { margin-bottom: 16px; }

.setup .form-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
}
.field { display: flex; flex-direction: column; gap: 6px; }
.field label { font-size: 13px; color: var(--text-muted); }
.field .grow { width: 220px; }

.actions { display: flex; gap: 12px; }

.answering-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  font-size: 14px;
}

.q-card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 14px 16px;
  margin-bottom: 14px;
}
.q-title { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
.q-no { font-weight: 600; }
.q-type { font-size: 12px; padding: 1px 8px; border-radius: 6px; color: #fff; }
.q-type.choice { background: #2563eb; }
.q-type.fill { background: #f59e0b; }
.q-stem { flex: 1; min-width: 200px; }
.q-options { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.q-fill { margin-top: 10px; max-width: 320px; }

.block { margin-top: 28px; }
.block-title { font-size: 16px; margin: 0 0 14px; }

.fb-code { font-weight: 600; margin-right: 8px; }
.fb-tag { margin-right: 10px; }
.fb-your { font-size: 13px; color: var(--text-muted); }
.fb-line { margin: 6px 0; font-size: 14px; }

.weak-box { margin-bottom: 14px; display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.weak-label { font-size: 13px; color: var(--text-muted); }
.weak-tag { margin-bottom: 4px; }

.mastery-list { display: flex; flex-direction: column; gap: 8px; }
.mastery-row { display: flex; align-items: center; gap: 12px; }
.mastery-name { width: 240px; flex-shrink: 0; font-size: 13px; }
.mastery-bar { flex: 1; }
.mastery-level { width: 40px; text-align: right; font-size: 12px; }
.mastery-level.薄弱 { color: #dc2626; }
.mastery-level.中等 { color: #d97706; }
.mastery-level.扎实 { color: #16a34a; }

.plan-summary { font-size: 14px; margin: 0 0 16px; }
.phase-focus { margin: 0 0 8px; font-size: 13px; color: var(--text-muted); }
.phase-points { display: flex; flex-wrap: wrap; gap: 6px; }

@media (max-width: 640px) {
  .mastery-name { width: 120px; }
}
</style>
