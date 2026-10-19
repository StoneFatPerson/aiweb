<template>
  <div class="page-container">
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

    <!-- 随机重做：闯关式 dialog（一题一答，答对即攻克） -->
    <el-dialog v-model="redoVisible" title="随机重做" width="560px" :close-on-click-modal="false">
      <!-- 进度 -->
      <div v-if="redoItems.length" class="redo-progress">
        第 {{ currentIndex + 1 }} / {{ redoItems.length }} 题
        <span class="redo-done">已攻克 {{ redoSolved }} 题</span>
      </div>

      <!-- 答题中 -->
      <div v-if="redoItems.length && currentIndex < redoItems.length">
        <p class="redo-stem">{{ currentItem.stem }}</p>
        <div v-if="currentItem.type === 'SINGLE_CHOICE'" class="redo-options">
          <button
            v-for="opt in parseOptions(currentItem.options)"
            :key="opt.key"
            type="button"
            class="redo-option"
            :class="{ picked: redoAnswer === opt.key, correct: showResult && opt.key === lastResult?.correctAnswer?.trim(), wrong: showResult && redoAnswer === opt.key && !lastResult?.correct }"
            :disabled="showResult"
            @click="redoAnswer = opt.key"
          >
            <b>{{ opt.key }}.</b> {{ opt.content }}
          </button>
        </div>
        <el-input
          v-else
          v-model="redoAnswer"
          placeholder="输入你的答案"
          :disabled="showResult"
          @keyup.enter="submitRedo"
        />
        <div class="redo-actions">
          <el-button v-if="!showResult" type="primary" :disabled="!redoAnswer.trim()" @click="submitRedo">
            提交答案
          </el-button>
          <template v-else>
            <el-alert
              :type="lastResult.correct ? 'success' : 'error'"
              :closable="false"
              show-icon
              class="redo-result"
              :title="lastResult.correct
                ? '答对了，这道题已从错题本攻克移除！'
                : '答错了，正确答案：' + (lastResult.correctAnswer || '（未记录）')"
            />
            <el-button type="primary" @click="nextRedo">
              {{ currentIndex + 1 >= redoItems.length ? '查看总结' : '下一题' }}
            </el-button>
          </template>
        </div>
      </div>

      <!-- 全部完成 -->
      <div v-else-if="redoItems.length" class="redo-summary">
        <EmptyState
          :icon="redoSolved === redoItems.length ? '🎉' : '💪'"
          :title="`本轮完成：攻克 ${redoSolved} / ${redoItems.length} 题`"
          :desc="redoSolved === redoItems.length ? '全部消灭，错题本又薄了一层！' : '没答对的题仍留在错题本，下次随机还会遇到它。'"
        >
          <el-button type="primary" size="small" @click="startRedo">再来一轮</el-button>
          <el-button size="small" @click="redoVisible = false">回到列表</el-button>
        </EmptyState>
      </div>
    </el-dialog>

    <!-- 导入错题 -->
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import {
  getWrongBook,
  getWrongBookStats,
  redoWrongBook,
  gradeRedo,
  importWrongQuestion,
  removeWrongQuestion
} from '@/api'

const SUBJECT_NAMES = { math: '数学', english: '英语', politics: '政治' }
const subjectName = (s) => SUBJECT_NAMES[s] || s || '未知'

// 学号：与学情诊断共用同一个 localStorage key（同一学生在两个页面看到同一份错题）
const studentId = ref('')
const list = ref([])
const stats = ref({})
const loading = ref(false)
const subjectFilter = ref('')

// ---------- 随机重做状态 ----------
const redoVisible = ref(false)
const redoItems = ref([])
const currentIndex = ref(0)
const redoAnswer = ref('')
const showResult = ref(false)
const lastResult = ref(null)
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

// ---------- 随机重做 ----------

const currentItem = computed(() => redoItems.value[currentIndex.value] || null)

async function startRedo() {
  try {
    const items = await redoWrongBook(studentId.value, subjectFilter.value, 5)
    if (!items.length) {
      ElMessage.info('当前筛选下没有错题')
      return
    }
    redoItems.value = items
    currentIndex.value = 0
    redoSolved.value = 0
    redoVisible.value = true
    resetCurrent()
  } catch (e) {
    ElMessage.error(e.message || '抽题失败')
  }
}

function resetCurrent() {
  redoAnswer.value = ''
  showResult.value = false
  lastResult.value = null
}

async function submitRedo() {
  if (!redoAnswer.value.trim() || !currentItem.value) return
  try {
    lastResult.value = await gradeRedo(currentItem.value.id, redoAnswer.value.trim(), studentId.value)
    showResult.value = true
    if (lastResult.value.correct) redoSolved.value++
  } catch (e) {
    ElMessage.error(e.message || '判分失败')
  }
}

function nextRedo() {
  if (currentIndex.value + 1 >= redoItems.value.length) {
    currentIndex.value = redoItems.value.length // 进入总结视图
    loadList()
    loadStats()
    return
  }
  currentIndex.value++
  resetCurrent()
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

/* 随机重做 dialog */
.redo-progress {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 12px;
}
.redo-done {
  color: #059669;
  font-weight: 600;
}
.redo-stem {
  font-size: 15px;
  line-height: 1.8;
  margin: 0 0 16px;
}
.redo-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.redo-option {
  text-align: left;
  font-family: inherit;
  font-size: 14px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.redo-option:hover:not(:disabled) {
  border-color: var(--brand);
}
.redo-option.picked {
  border-color: var(--brand);
  background: #eff6ff;
}
.redo-option.correct {
  border-color: #059669;
  background: #ecfdf5;
}
.redo-option.wrong {
  border-color: #dc2626;
  background: #fef2f2;
}
.redo-option:disabled {
  cursor: default;
}
.redo-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.redo-result {
  margin: 0;
}
.redo-summary {
  padding: 8px 0;
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
