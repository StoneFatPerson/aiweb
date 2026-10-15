<template>
  <div class="page-container">
    <header class="page-head">
      <h1 class="section-title">AI 规划助手</h1>
      <p class="section-desc">
        用一句话说清你的考研目标，AI 会<b>自动拆解步骤</b>：查院校专业 → 检索科普知识 →
        （填了学号）结合个人学情诊断 → 汇总成可执行建议。每一步清晰可见，答案有据可查。
      </p>
    </header>

    <!-- 输入区 -->
    <section class="panel input-panel">
      <label class="field-label" for="goal">你的目标</label>
      <el-input
        id="goal"
        v-model="goal"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="例如：我想考山东大学的计算机专业，该怎么准备？"
      />
      <div class="examples">
        <span class="examples-label">试试：</span>
        <button
          v-for="(ex, i) in examples"
          :key="i"
          type="button"
          class="example-chip"
          @click="goal = ex"
        >
          {{ ex }}
        </button>
      </div>
      <div class="run-row">
        <div class="sid-wrap">
          <label class="field-label" for="sid">学号（选填）</label>
          <el-input
            id="sid"
            v-model="studentId"
            placeholder="填学号可结合个人学情，如 demo"
          />
        </div>
        <el-button type="primary" size="large" :loading="loading" :disabled="!goal.trim()" @click="run">
          {{ hasResult ? '重新规划' : '开始规划' }}
        </el-button>
      </div>
    </section>

    <!-- 结果区 -->
    <section v-if="loading || hasResult || error" class="panel result-panel" aria-live="polite">
      <el-alert
        v-if="degraded"
        type="warning"
        show-icon
        :closable="false"
        title="大模型接口暂时不可用（免费档限流），已用知识库与数据库结果兜底；稍后可重试获取智能润色。"
        class="degraded-alert"
      />
      <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" />

      <!-- 分步执行轨迹 -->
      <div v-if="steps.length" class="steps">
        <h2 class="block-title">执行步骤</h2>
        <ol class="step-list">
          <li v-for="s in steps" :key="s.index" class="step" :class="s.status">
            <span class="step-icon" aria-hidden="true">
              {{ s.status === 'skipped' ? '–' : s.status === 'running' ? '…' : '✓' }}
            </span>
            <div class="step-body">
              <div class="step-head">
                <span class="step-name">{{ s.index }}. {{ s.name }}</span>
                <span class="step-meta">
                  <span class="badge" :class="s.status">{{ statusText(s.status) }}</span>
                  <span v-if="s.status === 'done' && s.durationMs" class="dur">{{ s.durationMs }}ms</span>
                </span>
              </div>
              <p class="step-output">{{ s.output }}</p>
            </div>
          </li>
        </ol>
      </div>

      <!-- 汇总建议 -->
      <div v-if="answer" class="answer-block">
        <h2 class="block-title">规划建议</h2>
        <div class="answer">{{ displayAnswer }}</div>
        <div v-if="sources.length" class="sources">
          <span class="sources-label">参考来源：</span>
          <el-tag v-for="(src, i) in sources" :key="i" size="small" type="info" effect="plain">
            {{ src }}
          </el-tag>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { runAgent } from '@/api'

const goal = ref('')
const studentId = ref('')
const loading = ref(false)
const hasResult = ref(false)
const steps = ref([])
const answer = ref('')
const sources = ref([])
const degraded = ref(false)
const error = ref('')

const examples = [
  '我想考山东大学的计算机专业，该怎么准备？',
  '学硕和专硕有什么区别？我该怎么选？',
  '考研完整时间线是怎样的？现在大二该怎么规划？',
  '帮我看看数学学情并给复习计划（填上学号更准）'
]

// 去掉模型输出里的 ** 加粗标记，配合 pre-wrap 保留换行，纯文本渲染更安全
const displayAnswer = computed(() => answer.value.replace(/\*\*/g, ''))

const statusText = (s) => (s === 'done' ? '已完成' : s === 'skipped' ? '已跳过' : '进行中')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function run() {
  if (!goal.value.trim()) {
    ElMessage.warning('请先描述你的目标')
    return
  }
  loading.value = true
  error.value = ''
  answer.value = ''
  sources.value = []
  degraded.value = false
  steps.value = []
  hasResult.value = false

  let data
  try {
    data = await runAgent({ goal: goal.value.trim(), studentId: studentId.value.trim() || undefined })
  } catch (e) {
    loading.value = false
    error.value = '请求失败：' + (e.message || '请检查后端服务是否已启动')
    return
  }

  degraded.value = !!data.degraded
  sources.value = data.sources || []

  // 分步轨迹逐条"播放"，让用户直观看到 AI 是如何一步步得出结论的
  const allSteps = data.steps || []
  for (const s of allSteps) {
    steps.value.push({ ...s, status: 'running' })
    await sleep(280)
    const cur = steps.value.find((x) => x.index === s.index)
    if (cur) cur.status = s.status
    await sleep(150)
  }

  answer.value = data.answer || ''
  hasResult.value = true
  loading.value = false
}
</script>

<style scoped>
.page-head {
  margin-bottom: 16px;
}

.section-desc {
  margin: 4px 0 0;
  color: var(--text-muted);
  max-width: 720px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 6px;
}

.examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.examples-label {
  font-size: 13px;
  color: var(--text-muted);
}

.example-chip {
  border: 1px solid var(--line);
  background: var(--bg);
  color: var(--text);
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}

.example-chip:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.run-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.sid-wrap {
  flex: 1;
  min-width: 220px;
  max-width: 320px;
}

/* ---------- 结果区 ---------- */
.degraded-alert {
  margin-bottom: 16px;
}

.block-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px;
}

.step-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.step {
  display: flex;
  gap: 12px;
  position: relative;
  padding-bottom: 18px;
}

/* 步骤之间的竖向连接线 */
.step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 13px;
  top: 30px;
  bottom: 0;
  width: 2px;
  background: var(--line);
}

.step-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  background: var(--bg);
  color: var(--text-muted);
  border: 1px solid var(--line);
}

.step.done .step-icon {
  background: #dcfce7;
  color: #16a34a;
  border-color: #bbf7d0;
}

.step.skipped .step-icon {
  background: #f3f4f6;
  color: #9ca3af;
}

.step.running .step-icon {
  background: #eff6ff;
  color: var(--brand);
  border-color: #bfdbfe;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.step-body {
  flex: 1;
  min-width: 0;
}

.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.step-name {
  font-weight: 600;
  font-size: 14px;
}

.step-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.badge {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid var(--line);
  color: var(--text-muted);
}

.badge.done {
  color: #16a34a;
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.badge.skipped {
  color: #9ca3af;
}

.badge.running {
  color: var(--brand);
  border-color: #bfdbfe;
  background: #eff6ff;
}

.dur {
  font-size: 12px;
  color: var(--text-muted);
}

.step-output {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.answer-block {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.answer {
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 16px;
  line-height: 1.7;
}

.sources {
  margin-top: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.sources-label {
  font-size: 13px;
  color: var(--text-muted);
}

@media (max-width: 720px) {
  .run-row {
    flex-direction: column;
    align-items: stretch;
  }

  .sid-wrap {
    max-width: none;
  }

  .run-row .el-button {
    width: 100%;
  }
}

/* 尊重「减少动态效果」的系统偏好 */
@media (prefers-reduced-motion: reduce) {
  .step.running .step-icon {
    animation: none;
  }
}
</style>
