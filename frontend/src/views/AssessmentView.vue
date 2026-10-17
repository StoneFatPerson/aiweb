<template>
  <div class="page-container">
    <section class="panel">
      <h1 class="section-title">学情诊断与测评</h1>
      <p class="section-desc">
        做几道{{ subjectName }}题，系统用 BKT 算法（贝叶斯知识追踪）算你每个知识点的掌握度，标出薄弱点并生成三阶段备考计划。
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
            <label>测评科目</label>
            <el-select v-model="subject" class="grow">
              <el-option label="数学" value="math" />
              <el-option label="英语" value="english" />
              <el-option label="政治" value="politics" />
            </el-select>
          </div>
          <div class="field" v-if="subject === 'math'">
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
          <el-button text type="danger" :disabled="loading" @click="clearData">清除历史数据</el-button>
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

          <div v-else class="q-fill-wrap">
            <el-input
              v-model="answers[q.code]"
              placeholder="请输入答案，如 1/2"
              class="q-fill"
            />
            <div class="q-upload">
              <el-upload
                :auto-upload="false"
                :show-file-list="false"
                accept="image/*"
                :on-change="(file) => onImageSelected(file, q.code)"
              >
                <el-button
                  size="small"
                  :type="images[q.code] ? 'success' : 'primary'"
                  plain
                >{{ images[q.code] ? '重传答案图片' : '上传答案图片' }}</el-button>
              </el-upload>
              <span class="q-upload-tip">含特殊符号不便打字时，可拍照/截图上传</span>
              <div v-if="images[q.code]" class="q-img-preview">
                <img :src="images[q.code].dataUrl" alt="答案图片" />
                <el-button size="small" text type="danger" @click="removeImage(q.code)">移除</el-button>
              </div>
            </div>
          </div>
        </div>

        <div class="actions">
          <el-button type="primary" :loading="loading" @click="submit">提交并诊断</el-button>
          <el-button :disabled="loading" @click="step = 'setup'">取消</el-button>
        </div>
      </div>

      <!-- 步骤 3：结果 -->
      <div v-else-if="step === 'result' && result">
        <el-result
          v-if="!result.isView"
          :icon="result.score >= 60 ? 'success' : 'warning'"
          :title="`本次得分 ${result.score}`"
          :sub-title="`正确 ${result.correctCount} / ${result.total} 题`"
        />

        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="这是你最近的学情诊断"
          description="下方为各知识点掌握度、备考计划与历史做题记录。"
          class="mb"
        />

        <!-- 逐题反馈（仅本次提交有） -->
        <div v-if="!result.isView && result.feedback && result.feedback.length" class="block">
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
          <h3 class="block-title">{{ subjectName }}知识点掌握度诊断</h3>
          <div v-if="result.weakPoints && result.weakPoints.length" class="weak-box">
            <span class="weak-label">薄弱点：</span>
            <el-tag v-for="w in result.weakPoints" :key="w" type="danger" size="small" class="weak-tag">
              {{ w }}
            </el-tag>
          </div>
          <div class="mastery-list">
            <div v-for="d in result.diagnosis" :key="d.pointCode" class="mastery-row">
              <span class="mastery-name">{{ d.pointName }}</span>
              <span class="mastery-pct">{{ Math.round(d.mastery * 100) }}%</span>
              <span class="mastery-level" :class="d.level">{{ d.level }}</span>
            </div>
          </div>
        </div>

        <!-- 备考计划：文字摘要 + 三阶段列表（已移除二维图表） -->
        <div class="block">
          <h3 class="block-title">备考计划（基础 / 强化 / 冲刺）</h3>
          <p v-if="result.plan && result.plan.summary" class="plan-summary">{{ result.plan.summary }}</p>
          <ol v-if="result.plan && result.plan.phases" class="phase-list">
            <li v-for="(p, i) in result.plan.phases" :key="i" class="phase-item">
              <div class="phase-name">{{ i + 1 }}. {{ p.name }}</div>
              <div class="phase-meta">周次：{{ p.weeks }}</div>
              <div class="phase-focus">重点：{{ p.focus }}</div>
            </li>
          </ol>
        </div>

        <!-- 做题历史（含标准答案） -->
        <div class="block">
          <h3 class="block-title">做题历史（含标准答案）</h3>
          <el-table :data="history" border size="small" class="history-table">
            <!-- 空态：还没有记录时引导去做一次测评 -->
            <template #empty>
              <EmptyState icon="📝" title="还没有做题记录" desc="完成一次测评后，这里会显示每题的作答与标准答案">
                <el-button size="small" type="primary" @click="step = 'setup'">去做测评</el-button>
              </EmptyState>
            </template>
            <el-table-column prop="createdAt" label="提交时间" width="150" />
            <el-table-column prop="stem" label="题目" min-width="220" show-overflow-tooltip />
            <el-table-column prop="yourAnswer" label="你的答案" width="110" show-overflow-tooltip />
            <el-table-column prop="correctAnswer" label="标准答案" width="110" show-overflow-tooltip />
            <el-table-column label="结果" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.correct ? 'success' : 'danger'" size="small" disable-transitions>
                  {{ row.correct ? '答对' : '答错' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="actions">
          <el-button type="primary" @click="step = 'setup'">再做一次</el-button>
          <el-button text type="danger" @click="clearData">清除历史数据</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import {
  startAssessment, submitAssessment, getDiagnosis,
  getAnswerHistory, clearAssessment
} from '@/api'

const step = ref('setup')
const loading = ref(false)
const studentId = ref('')
const subject = ref('math')
const targetMath = ref('all')
const count = ref(10)

// 科目中文名（数学默认，英语/政治为后续扩展科目）
const SUBJECT_NAMES = { math: '数学', english: '英语', politics: '政治' }
const subjectName = computed(() => SUBJECT_NAMES[subject.value] || '数学')

const questions = ref([])
const answers = reactive({})
// 图片答案：以题号 code 为键，存 { base64, mime, dataUrl }（与文字答案并存）
const images = reactive({})
const result = ref(null)
const history = ref([])

// 页面加载时取/建匿名 ID，存到 localStorage（无登录，纯本地）
onMounted(() => {
  let id = localStorage.getItem('aiweb_student_id')
  if (!id) {
    id = 'stu-' + (crypto.randomUUID ? crypto.randomUUID() : Date.now() + '-' + Math.random().toString(16).slice(2))
    localStorage.setItem('aiweb_student_id', id)
  }
  studentId.value = id
})

// 拉取该学生的做题历史（含标准答案），无论走「提交」还是「查看诊断」都刷新
async function loadHistory() {
  try {
    history.value = (await getAnswerHistory(studentId.value)) || []
  } catch {
    history.value = []
  }
}

async function start() {
  loading.value = true
  try {
    const data = await startAssessment({
      studentId: studentId.value,
      subject: subject.value,
      targetMath: targetMath.value,
      count: count.value
    })
    questions.value = data.questions || []
    for (const k in answers) delete answers[k]
    for (const k in images) delete images[k]
    step.value = 'answering'
  } catch (e) {
    ElMessage.error(e.message || '抽题失败，请确认后端已启动')
  } finally {
    loading.value = false
  }
}

async function submit() {
  const missing = questions.value.filter((q) => !answers[q.code])
  if (missing.length) {
    ElMessage.warning(`还有 ${missing.length} 题没作答`)
    return
  }
  loading.value = true
  try {
    const payload = {
      studentId: studentId.value,
      answers: questions.value.map((q) => {
        const img = images[q.code]
        return {
          questionCode: q.code,
          answer: answers[q.code] || '',
          // 带图片则随答案一起提交（后端优先用 OCR 结果判分），文字框保留可并行使用
          imageBase64: img ? img.base64 : '',
          imageMime: img ? img.mime : ''
        }
      })
    }
    result.value = await submitAssessment(payload)
    step.value = 'result'
    await loadHistory()
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    loading.value = false
  }
}

/**
 * 选好图片后：用 canvas 压到约 1000px 边、JPEG 质量 0.82，再转 base64 随答案提交。
 * 为什么压缩：base64 图片直接塞进 JSON 请求体，太大既慢又容易超请求上限。
 */
async function onImageSelected(file, code) {
  const raw = file && file.raw
  if (!raw) return
  try {
    images[code] = await compressImage(raw)
  } catch {
    ElMessage.error('图片处理失败，请换一张或改用文字输入')
  }
}

function removeImage(code) {
  delete images[code]
}

function compressImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const img = new Image()
      img.onload = () => {
        const maxEdge = 1000
        let { width, height } = img
        if (width > maxEdge || height > maxEdge) {
          const scale = maxEdge / Math.max(width, height)
          width = Math.round(width * scale)
          height = Math.round(height * scale)
        }
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        canvas.getContext('2d').drawImage(img, 0, 0, width, height)
        const dataUrl = canvas.toDataURL('image/jpeg', 0.82)
        const base64 = dataUrl.slice(dataUrl.indexOf(',') + 1)
        resolve({ base64, mime: 'image/jpeg', dataUrl })
      }
      img.onerror = reject
      img.src = reader.result
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

async function viewDiagnosis() {
  loading.value = true
  try {
    const res = await getDiagnosis(studentId.value, subject.value)
    // 用诊断视图拼一个「只读」结果对象，不显示本次得分/逐题解析
    result.value = {
      diagnosis: res.diagnosis,
      weakPoints: res.weakPoints,
      plan: res.plan,
      assessed: res.assessed,
      isView: true
    }
    step.value = 'result'
    await loadHistory()
  } catch (e) {
    ElMessage.error(e.message || '获取诊断失败')
  } finally {
    loading.value = false
  }
}

// 清除历史数据：清空掌握度 + 做题历史，回到未测评状态
async function clearData() {
  try {
    await ElMessageBox.confirm(
      '将清空你在本机的全部测评记录与掌握度数据（不可恢复），确定吗？',
      '清除历史数据',
      { type: 'warning', confirmButtonText: '确定清除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await clearAssessment(studentId.value)
    ElMessage.success('已清除历史学习数据')
    result.value = null
    history.value = []
    step.value = 'setup'
  } catch (e) {
    ElMessage.error(e.message || '清除失败')
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

.actions { display: flex; gap: 12px; flex-wrap: wrap; }

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
.mastery-row { display: flex; align-items: center; gap: 12px; justify-content: space-between; }
.mastery-name { flex: 1; min-width: 120px; font-size: 13px; }
.mastery-pct { width: 52px; text-align: right; font-size: 13px; color: var(--text-secondary, #475569); flex-shrink: 0; }
.mastery-level { width: 40px; text-align: right; font-size: 12px; flex-shrink: 0; }
.mastery-level.薄弱 { color: #dc2626; }
.mastery-level.中等 { color: #d97706; }
.mastery-level.扎实 { color: #16a34a; }

/* 填空题：文字输入 + 图片上传 两种提交方式并存 */
.q-fill-wrap { margin-top: 10px; max-width: 360px; }
.q-upload { margin-top: 10px; display: flex; flex-direction: column; gap: 6px; }
.q-upload-tip { font-size: 12px; color: var(--text-muted); }
.q-img-preview { display: flex; align-items: center; gap: 10px; margin-top: 4px; }
.q-img-preview img { width: 120px; height: 90px; object-fit: cover; border: 1px solid var(--line); border-radius: 8px; }

/* 备考计划：纯文字 + 列表（无图表） */
.phase-list { margin: 0; padding-left: 20px; display: flex; flex-direction: column; gap: 12px; }
.phase-item { padding: 10px 14px; border: 1px solid var(--line); border-radius: 10px; background: var(--bg-soft, #f8fafc); }
.phase-name { font-size: 14px; font-weight: 600; margin-bottom: 4px; }
.phase-meta { font-size: 13px; color: var(--text-secondary, #475569); margin-bottom: 2px; }
.phase-focus { font-size: 13px; color: var(--text-muted); }

.plan-summary { font-size: 14px; margin: 0 0 16px; }

.history-table { font-size: 13px; }

@media (max-width: 640px) {
  .mastery-name { width: 120px; }
}
</style>
