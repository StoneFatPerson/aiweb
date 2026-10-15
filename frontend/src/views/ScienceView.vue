<template>
  <div class="page-container">
    <section class="panel chat-panel">
      <h1 class="section-title">认知科普</h1>
      <p class="section-desc">
        基于考研知识库的检索问答（RAG）。回答<strong>只来自下方「参考来源」中的资料，不凭空编造</strong>。
        涉及政策与数字的信息，请务必以官方公告为准。
      </p>

      <!-- 消息区 -->
      <div ref="messageBox" class="message-box" role="log" aria-live="polite" aria-label="问答记录">
        <div v-if="messages.length === 0" class="empty-tip">
          <p>还没有提问。试试这些问题：</p>
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
            <span v-if="m.loading" class="typing">检索资料并思考中…</span>
            <template v-else>
              <span class="bubble-text">{{ m.content }}</span>
              <!-- 参考来源：点开可看这条回答依据了知识库里的哪些资料 -->
              <div v-if="m.role === 'ai' && m.sources && m.sources.length" class="sources">
                <div class="sources-title">参考来源</div>
                <el-tag
                  v-for="s in m.sources"
                  :key="s"
                  size="small"
                  type="info"
                  effect="plain"
                  class="source-tag"
                >
                  {{ s }}
                </el-tag>
              </div>
            </template>
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
          maxlength="500"
          placeholder="输入考研相关问题，Enter 发送，Shift + Enter 换行"
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
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { askScience } from '@/api'

const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const messageBox = ref(null)

const suggestions = [
  '学硕和专硕有什么区别？',
  '数学一、数学二、数学三怎么选？',
  'A区和B区是什么意思？',
  '山东大学是什么层次的学校？'
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
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'ai', content: '', loading: true })
  scrollToBottom()

  sending.value = true
  try {
    // 后端先去知识库检索相关片段，再让大模型只依据片段作答
    const data = await askScience({ question: text })
    const last = messages.value[messages.value.length - 1]
    last.loading = false
    last.content = (data && data.answer) || '（没有返回内容）'
    last.sources = (data && data.sources) || []
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
  ElMessage.success('已清空对话')
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
}

.message-box {
  height: 440px;
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

.msg-user .bubble {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
}

.typing {
  color: var(--text-muted);
}

.sources {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.sources-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.source-tag {
  margin: 0 6px 6px 0;
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
    height: 360px;
  }

  .bubble {
    max-width: 86%;
  }
}
</style>
