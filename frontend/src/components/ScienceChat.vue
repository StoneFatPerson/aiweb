<template>
  <div class="sci-chat">
    <header class="sci-chat-header">
      <div class="sci-chat-title">
        <span class="sci-chat-dot" aria-hidden="true"></span>
        AI 辅助答疑
      </div>
      <p class="sci-chat-sub">对左侧讲解有疑问？在这里随时追问</p>
    </header>

    <!-- 消息区 -->
    <div ref="messageBox" class="sci-chat-box" role="log" aria-live="polite" aria-label="答疑对话记录">
      <div v-if="messages.length === 0" class="sci-empty">
        <p>还没有对话。试试这些问题：</p>
        <div class="sci-suggest">
          <el-button
            v-for="q in suggestions"
            :key="q"
            size="small"
            plain
            @click="useSuggestion(q)"
          >
            {{ q }}
          </el-button>
        </div>
      </div>

      <div
        v-for="(m, index) in messages"
        :key="index"
        class="sci-msg"
        :class="m.role === 'user' ? 'sci-msg-user' : 'sci-msg-ai'"
      >
        <div class="sci-avatar" aria-hidden="true">{{ m.role === 'user' ? '我' : 'AI' }}</div>
        <div class="sci-bubble">
          <span v-if="m.loading" class="sci-typing">思考中…</span>
          <template v-else>
            <span class="sci-bubble-text">{{ m.content }}</span>
            <div v-if="m.sources && m.sources.length" class="sci-sources">
              <span class="sci-sources-label">参考来源</span>
              <el-tag
                v-for="s in m.sources"
                :key="s"
                size="small"
                type="info"
                effect="plain"
                class="sci-source-tag"
              >
                {{ s }}
              </el-tag>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="sci-input">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        resize="none"
        maxlength="1000"
        placeholder="针对讲解内容提问，Enter 发送，Shift + Enter 换行"
        @keydown.enter.exact.prevent="send"
      />
      <div class="sci-input-actions">
        <el-button text size="small" :disabled="sending" @click="clearChat">清空</el-button>
        <el-button type="primary" size="small" :loading="sending" :disabled="!canSend" @click="send">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { askScienceChat } from '@/api'

/**
 * 当前用户正在阅读的主题标题，作为上下文传给后端，
 * 让 AI 知道用户在看哪部分内容，回答更贴合。
 */
const props = defineProps({
  context: {
    type: String,
    default: ''
  }
})

const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const messageBox = ref(null)

const suggestions = [
  '学硕和专硕哪个更适合我？',
  '数学一和数学二怎么选？',
  '报名和确认有什么注意事项？',
  '山东有哪些适合跨考的院校？'
]

const canSend = computed(() => inputText.value.trim().length > 0 && !sending.value)

const scrollToBottom = async () => {
  await nextTick()
  if (messageBox.value) {
    messageBox.value.scrollTop = messageBox.value.scrollHeight
  }
}

const useSuggestion = (q) => {
  inputText.value = q
  send()
}

const send = async () => {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  inputText.value = ''
  // 把之前已经成型的对话作为历史传给后端，实现「追问」而不是每轮孤立问答
  const history = messages.value
    .filter((m) => !m.loading && !(m.content || '').startsWith('请求失败'))
    .map((m) => ({ role: m.role, content: m.content }))

  messages.value.push({ role: 'user', content: text })
  // 持有本条 AI 气泡的引用（而不是返回后按索引取「最后一条」）：
  // 请求最长可达几十秒，期间用户点「清空」会清空数组，按索引取会拿到 undefined 抛 TypeError，
  // 被全局错误处理变成「弹窗自动打开」。stale 标记让过期响应直接丢弃。
  const pending = { role: 'ai', content: '', loading: true }
  messages.value.push(pending)
  scrollToBottom()

  sending.value = true
  try {
    const data = await askScienceChat({
      question: text,
      history,
      context: props.context || ''
    })
    if (pending.stale) return // 已被清空，静默丢弃
    pending.loading = false
    if (data && data.answer) {
      pending.content = data.answer
      pending.sources = data.sources || []
    } else {
      pending.content = '（没有返回内容，可能是接口限流，请稍后重试）'
    }
  } catch (e) {
    if (pending.stale) return
    pending.loading = false
    pending.content = '请求失败：' + e.message
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const clearChat = () => {
  // 把在途请求全部标记为过期：它们返回时不会写进任何气泡（也不会抛错）
  messages.value.forEach((m) => {
    if (m.loading) m.stale = true
  })
  messages.value = []
  ElMessage.success('已清空对话')
}
</script>

<style scoped>
.sci-chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  overflow: hidden;
}

.sci-chat-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(135deg, #eff4ff, #f7faff);
}

.sci-chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.sci-chat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.18);
}

.sci-chat-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--text-muted);
}

.sci-chat-box {
  flex: 1;
  min-height: 240px;
  max-height: calc(100vh - 320px);
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #fcfcfd;
}

.sci-empty {
  margin: auto;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

.sci-suggest {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.sci-msg {
  display: flex;
  gap: 10px;
  max-width: 100%;
}

.sci-msg-user {
  flex-direction: row-reverse;
}

.sci-avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background: #64748b;
}

.sci-msg-user .sci-avatar {
  background: var(--brand);
}

.sci-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--line);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.7;
}

.sci-msg-user .sci-bubble {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
}

.sci-typing {
  color: var(--text-muted);
}

.sci-sources {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.sci-sources-label {
  font-size: 11px;
  color: var(--text-muted);
}

.sci-source-tag {
  margin: 0;
}

.sci-input {
  padding: 12px 14px 14px;
  border-top: 1px solid var(--line);
  background: #fff;
}

.sci-input-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}
</style>
