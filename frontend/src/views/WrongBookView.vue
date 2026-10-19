<template>
  <div class="page-container">
    <!-- ============ 状态一：错题列表（默认） ============ -->
    <template v-if="mode === 'list'">
      <header class="page-head">
        <h1 class="section-title">错题本</h1>
        <p class="section-desc">
          学情诊断中判错的题会自动收录到这里，也可以导入自己的错题。
          随机重做、答对即「攻克」——错题的价值就在于被一道道消灭。
        </p>
      </header>

      <!-- 统计 chips -->
      <section class="stat-bar" aria-label="错题本统计">
        <span class="stat-chip stat-total">共 <b>{{ stats.total || 0 }}</b> 题</span>
        <span v-if="stats.bySubject?.math" class="stat-chip">数学 <b>{{ stats.bySubject.math }}</b></span>
        <span v-if="stats.bySubject?.english" class="stat-chip">英语 <b>{{ stats.bySubject.english }}</b></span>
        <span v-if="stats.bySubject?.politics" class="stat-chip">政治 <b>{{ stats.bySubject.politics }}</b></span>
        <span v-if="stats.bySource?.MANUAL" class="stat-chip stat-manual">导入 <b>{{ stats.bySource.MANUAL }}</b></span>
      </section>

      <!-- 操作行：科目筛选 + 入口 -->
      <section class="tool-bar">
        <el-radio-group v-model="subjectFilter" size="default" @change="loadList">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="math">数学</el-radio-button>
          <el-radio-button value="english">英语</el-radio-button>
          <el-radio-button value="politics">政治</el-radio-button>
        </el-radio-group>
        <div class="tool-actions">
          <el-button type="primary" :disabled="!stats.total" @click="startRedo">🎲 随机重做</el-button>
          <el-button @click="importVisible = true">➕ 导入错题</el-button>
        </div>
      </section>

      <!-- 错题列表 -->
      <section class="list" aria-label="错题列表">
        <div v-if="loading" class="state-box">正在加载错题…</div>
        <div v-else-if="!list.length">
          <EmptyState
            icon="📕"
            title="错题本还是空的"
            desc="去学情诊断答一组题，判错的题会自动收录到这里；也可以手动导入自己的错题。"
          >
            <el-button type="primary" size="small" @click="$router.push('/assess')">去学情诊断</el-button>
            <el-button size="small" @click="importVisible = true">导入错题</el-button>
          </EmptyState>
        </div>
        <article v-for="w in list" :key="w.id" class="wrong-card">
          <div class="wrong-head">
            <div class="wrong-tags">
              <el-tag :type="w.source === 'MANUAL' ? 'warning' : 'info'" size="small" disable-transitions>
                {{ w.source === 'MANUAL' ? '手动导入' : '诊断收录' }}
              </el-tag>
              <el-tag size="small" disable-transitions>{{ subjectName(w.subject) }}</el-tag>
              <span v-if="w.redoCount > 0" class="redo-badge">
                已重做 {{ w.redoCount }} 次{{ w.lastRedoCorrect === 1 ? ' · 上次答对' : w.lastRedoCorrect === 0 ? ' · 上次答错' : '' }}
              </span>
            </div>
            <el-button text type="danger" size="small" @click="remove(w)">移除</el-button>
          </div>
          <p class="wrong-stem">{{ w.stem }}</p>
          <div class="wrong-answers">
            <span class="ans ans-wrong">我的答案：{{ w.userAnswer || '（空）' }}</span>
            <span class="ans ans-right">正确答案：{{ w.correctAnswer || '（未记录）' }}</span>
          </div>
          <p v-if="w.note" class="wrong-note">备注：{{ w.note }}</p>
          <p class="wrong-time">收录于 {{ formatTime(w.createdAt) }}</p>
        </article>
      </section>
    </template>

    <!-- ============ 状态二：重做作答（布局与学情诊断做题页一致） ============ -->
    <div v-else-if="mode === 'redo'">
      <div class="answering-head">
        <span>随机重做：共 {{ redoItems.length }} 题，请作答：</span>
        <el-button text @click="mode = 'list'">返回错题本</el-button>
      </div>

      <div v-for="(q, i) in redoItems" :key="q.id" class="q-card">
        <div class="q-title">
          <span class="q-no">{{ i + 1 }}.</span>
          <span class="q-type" :class="q.type === 'FILL_BLANK' ? 'fill' : 'choice'">
            {{ q.type === 'FILL_BLANK' ? '填空' : '选择' }}
          </span>
          <span class="q-stem">{{ q.stem }}</span>
        </div>

        <el-radio-group v-if="q.type === 'SINGLE_CHOICE'" v-model="redoAnswers[q.id]" class="q-options">
          <el-radio v-for="opt in parseOptions(q.options)" :key="opt.key" :value="opt.key" border>
            {{ opt.key }}. {{ opt.content }}
          </el-radio>
        </el-radio-group>

        <el-input
          v-else
          v-model="redoAnswers[q.id]"
          placeholder="请输入答案，如 1/2"
          class="q-fill"
        />
        <p v-if="q.note" class="q-note">收录备注：{{ q.note }}</p>
      </div>

      <div class="actions">
        <el-button type="primary" :loading="grading" @click="submitRedo">提交答案</el-button>
        <el-button :disabled="grading" @click="mode = 'list'">取消</el-button>
      </div>
    </div>

    <!-- ============ 状态三：重做结果（逐题反馈 + 总结） ============ -->
    <div v-else-if="mode === 'result'">
      <el-result
        :icon="redoSolved === redoItems.length ? 'success' : 'warning'"
        :title="`攻克 ${redoSolved} / ${redoItems.length} 题`"
        :sub-title="redoSolved === redoItems.length
          ? '全部消灭，错题本又薄了一层！'
          : '没答对的题仍留在错题本，下次随机还会遇到它'"
      />

      <!-- 逐题反馈（与学情诊断「逐题解析」一致的折叠面板） -->
      <div class="block">
        <h3 class="block-title">逐题解析</h3>
        <el-collapse>
          <el-collapse-item v-for="(r, i) in redoResults" :key="r.id" :name="i">
            <template #title>
              <span class="fb-code">{{ i + 1 }}</span>
              <el-tag :type="r.correct ? 'success' : 'danger'" size="small" class="fb-tag">
                {{ r.correct ? '攻克' : '未攻克' }}
              </el-tag>
              <span class="fb-your">你的答案：{{ redoAnswers[r.id] || '（空）' }}</span>
            </template>
            <p class="fb-line"><b>正确答案：</b>{{ r.correctAnswer || '（未记录）' }}</p>
            <p v-if="r.correct" class="fb-line fb-ok">这道题已从错题本移除。</p>
            <p v-else class="fb-line fb-no">这道题仍留在错题本，下次随机重做还会遇到。</p>
          </el-collapse-item>
        </el-collapse>
      </div>

      <div class="actions">
        <el-button type="primary" :disabled="!stats.total" @click="startRedo">再来一轮</el-button>
        <el-button @click="backToList">返回错题本</el-button>
      </div>
    </div>

    <!-- ============ 导入错题弹窗 ============ -->
    <el-dialog v-model="importVisible" title="导入错题" width="520px">
      <el-form label-width="72px">
        <el-form-item label="科目">
          <el-radio-group v-model="importForm.subject">
            <el-radio-button value="math">数学</el-radio-button>
            <el-radio-button value="english">英语</el-radio-button>
            <el-radio-button value="politics">政治</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干" required>
          <el-input v-model="importForm.stem" type="textarea" :rows="3" resize="none" placeholder="粘贴或输入题目内容" />
        </el-form-item>
        <el-form-item label="我的答案">
          <el-input v-model="importForm.userAnswer" placeholder="当时错写的答案（选填）" />
        </el-form-item>
        <el-form-item label="正确答案">
          <el-input v-model="importForm.correctAnswer" placeholder="正确答案（选填，填了才能判分）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="importForm.note" placeholder="错因 / 心得（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importForm.stem.trim()" @click="submitImport">
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import {
  getWrongBook,
  getWrongBookStats,
  redoWrongBook,
  gradeRedoBatch,
  importWrongQuestion,
  removeWrongQuestion
} from '@/api'

const SUBJECT_NAMES = { math: '数学', english: '英语', politics: '政治' }
const subjectName = (s) => SUBJECT_NAMES[s] || s || '未知'

// 页面模式：list 列表 / redo 重做作答 / result 重做结果（与学情诊断的步骤切换同思路）
const mode = ref('list')

// 学号：与学情诊断共用同一个 localStorage key（同一学生在两个页面看到同一份错题）
const studentId = ref('')
const list = ref([])
const stats = ref({})
const loading = ref(false)
const subjectFilter = ref('')

// ---------- 随机重做状态 ----------
const redoItems = ref([])
const redoAnswers = ref({})
const grading = ref(false)
const redoResults = ref([])
const redoSolved = ref(0)

// ---------- 导入 ----------
const importVisible = ref(false)
const importing = ref(false)
const importForm = ref({ subject: 'math', stem: '', userAnswer: '', correctAnswer: '', note: '' })

onMounted(async () => {
  let id = localStorage.getItem('aiweb_student_id')
  if (!id) {
    id = 'stu-' + (crypto.randomUUID ? crypto.randomUUID() : Date.now() + '-' + Math.random().toString(16).slice(2))
    localStorage.setItem('aiweb_student_id', id)
  }
  studentId.value = id
  await Promise.all([loadList(), loadStats()])
})

async function loadList() {
  loading.value = true
  try {
    list.value = await getWrongBook(studentId.value, subjectFilter.value)
  } catch (e) {
    ElMessage.error(e.message || '加载错题失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await getWrongBookStats(studentId.value)
  } catch (e) {
    stats.value = {}
  }
}

// ---------- 随机重做（列表 → 做题 → 结果） ----------

async function startRedo() {
  try {
    const items = await redoWrongBook(studentId.value, subjectFilter.value, 5)
    if (!items.length) {
      ElMessage.info('当前筛选下没有错题')
      return
    }
    redoItems.value = items
    redoAnswers.value = {}
    redoResults.value = []
    redoSolved.value = 0
    mode.value = 'redo'
  } catch (e) {
    ElMessage.error(e.message || '抽题失败')
  }
}

async function submitRedo() {
  const unanswered = redoItems.value.filter((q) => !(redoAnswers.value[q.id] || '').trim())
  if (unanswered.length) {
    ElMessage.warning(`还有 ${unanswered.length} 题未作答`)
    return
  }
  grading.value = true
  try {
    // 一次提交整轮（交互与学情诊断「提交并诊断」一致）
    const submissions = redoItems.value.map((q) => ({ wrongId: q.id, answer: redoAnswers.value[q.id] }))
    redoResults.value = await gradeRedoBatch(studentId.value, submissions)
    redoSolved.value = redoResults.value.filter((r) => r.correct).length
    mode.value = 'result'
    await Promise.all([loadList(), loadStats()])
  } catch (e) {
    ElMessage.error(e.message || '判分失败')
  } finally {
    grading.value = false
  }
}

function backToList() {
  mode.value = 'list'
}

// ---------- 导入 ----------

async function submitImport() {
  if (!importForm.value.stem.trim()) return
  importing.value = true
  try {
    await importWrongQuestion({ studentId: studentId.value, ...importForm.value })
    ElMessage.success('导入成功')
    importVisible.value = false
    importForm.value = { subject: 'math', stem: '', userAnswer: '', correctAnswer: '', note: '' }
    await Promise.all([loadList(), loadStats()])
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    importing.value = false
  }
}

// ---------- 移除 ----------

async function remove(w) {
  try {
    await ElMessageBox.confirm('移除后这道题将不在错题本中，确定移除？', '移除错题', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }
  try {
    await removeWrongQuestion(w.id, studentId.value)
    ElMessage.success('已移除')
    await Promise.all([loadList(), loadStats()])
  } catch (e) {
    ElMessage.error(e.message || '移除失败')
  }
}

// ---------- 工具 ----------

function parseOptions(json) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
/* ============ 列表态 ============ */
.stat-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.stat-chip {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 4px 14px;
  font-size: 13px;
  color: var(--text-muted);
}
.stat-chip b {
  color: var(--brand);
  font-size: 15px;
}
.stat-total b {
  font-size: 17px;
}
.stat-manual b {
  color: #d97706;
}

.tool-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}
.tool-actions {
  display: flex;
  gap: 8px;
}

.state-box {
  padding: 32px 0;
  text-align: center;
  color: var(--text-muted);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.wrong-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-left: 3px solid #f87171;
  border-radius: var(--radius);
  padding: 16px 18px;
  transition: box-shadow 0.2s ease;
}
.wrong-card:hover {
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

.wrong-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.wrong-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.redo-badge {
  font-size: 12px;
  color: var(--text-muted);
}

.wrong-stem {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.7;
}

.wrong-answers {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  margin-bottom: 6px;
}
.ans-wrong {
  color: #dc2626;
}
.ans-right {
  color: #059669;
}

.wrong-note {
  margin: 0 0 6px;
  font-size: 13px;
  color: #b45309;
  background: #fffbeb;
  border-radius: 6px;
  padding: 6px 10px;
}

.wrong-time {
  margin: 0;
  font-size: 12px;
  color: var(--text-muted);
}

/* ============ 做题态：与学情诊断做题页保持一致的类名与样式 ============ */
.answering-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 15px;
  font-weight: 600;
}

.q-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 18px 20px;
  margin-bottom: 14px;
}
.q-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
  line-height: 1.7;
}
.q-no {
  font-weight: 700;
  color: var(--brand);
}
.q-type {
  flex-shrink: 0;
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
}
.q-type.choice {
  color: #1d4ed8;
  background: #dbeafe;
}
.q-type.fill {
  color: #b45309;
  background: #fef3c7;
}
.q-stem {
  font-size: 15px;
}

.q-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: stretch;
}
/* 与学情诊断一致的选项对齐修复：EP border 单选默认 inline-flex+32px 右 margin+固定高度，
   竖排会参差；这里撑满整行等宽对齐、允许换行、圆圈与首行顶部对齐 */
.q-options :deep(.el-radio) {
  margin-right: 0;
  width: 100%;
  height: auto;
  padding: 8px 12px;
  align-items: flex-start;
}
.q-options :deep(.el-radio__label) {
  white-space: normal;
  line-height: 1.6;
}
.q-options :deep(.el-radio__input) {
  margin-top: 3px;
}
.q-fill {
  max-width: 320px;
}
.q-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: #b45309;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

/* ============ 结果态 ============ */
.block {
  margin-top: 8px;
}
.block-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 12px;
}
.fb-code {
  font-weight: 700;
  color: var(--brand);
}
.fb-tag {
  margin: 0 8px;
}
.fb-your {
  font-size: 13px;
  color: var(--text-muted);
}
.fb-line {
  margin: 0 0 6px;
  font-size: 14px;
  line-height: 1.7;
}
.fb-ok {
  color: #059669;
}
.fb-no {
  color: #dc2626;
}

@media (max-width: 720px) {
  .tool-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .tool-actions .el-button {
    flex: 1;
  }
}
</style>
