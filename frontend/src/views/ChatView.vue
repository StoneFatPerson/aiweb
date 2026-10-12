<template>
  <div class="page-container">
    <section class="panel chat-panel">
      <h1 class="section-title">AI 问答</h1>
      <p class="section-desc">
        可以问考研流程、名词解释、备考安排等问题。回答由大模型生成，
        <strong>涉及政策与数字的信息请务必核对官方来源</strong>。
      </p>

      <!-- 消息区 -->
      <div ref="messageBox" class="message-box" role="log" aria-live="polite" aria-label="对话记录">
        <div v-if="messages.length === 0" class="empty-tip">
          <p>还没有对话。试试这些问题：</p>
          <div class="suggest-list">
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
          class="msg"
          :class="m.role === 'user' ? 'msg-user' : 'msg-ai'"
        >
          <div class="avatar" aria-hidden="true">{{ m.role === 'user' ? '我' : 'AI' }}</div>
          <div class="bubble">
            <span v-if="m.loading" class="typing">思考中…</span>
            <span v-else class="bubble-text">{{ m.content }}</span>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="1000"
          placeholder="输入你的问题，Enter 发送，Shift + Enter 换行"
          @keydown.enter.exact.prevent="send"
        />
        <div class="input-actions">
          <el-button text size="small" @click="clearChat">清空对话</el-button>
          <el-button type="primary" :loading="sending" :disabled="!canSend" @click="send">
            发送
          </el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getHistory, sendChat } from '@/api'

const STORAGE_KEY = 'aiweb.chat.sessionId'

const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const messageBox = ref(null)
const sessionId = ref('')

const suggestions = [
  '考研数学一和数学二有什么区别？',
  '学硕和专硕该怎么选？',
  '什么是自主划线高校？',
  '数二和英二是什么意思？'
]

const canSend = computed(() => inputText.value.trim().length > 0 && !sending.value)

// 用 localStorage 保存会话 ID：刷新页面后还能接着上一轮聊
onMounted(() => {
  sessionId.value = localStorage.getItem(STORAGE_KEY) || ''
  if (sessionId.value) {
    restoreHistory()
  }
})

const restoreHistory = async () => {
  try {
    const list = await getHistory(sessionId.value)
    if (Array.isArray(list)) {
      messages.value = list.map((item) => ({
        role: item.role === 'user' ? 'user' : 'ai',
        content: item.content
      }))
      scrollToBottom()
    }
  } catch (e) {
    // 历史加载失败不影响新对话
  }
}

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
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'ai', content: '', loading: true })
  scrollToBottom()

  sending.value = true
  try {
    const data = await sendChat({ sessionId: sessionId.value || null, message: text })

    if (data && data.sessionId) {
      sessionId.value = data.sessionId
      localStorage.setItem(STORAGE_KEY, data.sessionId)
    }

    const last = messages.value[messages.value.length - 1]
    if (data && data.error) {
      last.loading = false
      last.content = '调用失败：' + data.error
      ElMessage.warning('大模型调用失败，可能是限流，稍后再试')
    } else {
      last.loading = false
      last.content = (data && data.answer) || '（没有返回内容）'
    }
  } catch (e) {
    const last = messages.value[messages.value.length - 1]
    last.loading = false
    last.content = '请求失败：' + e.message
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const clearChat = () => {
  messages.value = []
  sessionId.value = ''
  localStorage.removeItem(STORAGE_KEY)
  ElMessage.success('已清空对话')
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
}

.message-box {
  height: 420px;
  overflow-y: auto;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fcfcfd;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.empty-tip {
  margin: auto;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

.suggest-list {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.msg {
  display: flex;
  gap: 10px;
  max-width: 100%;
}

.msg-user {
  flex-direction: row-reverse;
}

.avatar {
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

.msg-user .avatar {
  background: var(--brand);
}

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--line);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.7;
}

.msg-user .bubble {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
}

.typing {
  color: var(--text-muted);
}

.input-area {
  margin-top: 14px;
}

.input-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}

@media (max-width: 720px) {
  .message-box {
    height: 340px;
  }

  .bubble {
    max-width: 86%;
  }
}
</style>
