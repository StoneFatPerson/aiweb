import axios from 'axios'

/**
 * 统一的后端请求封装。
 *
 * 为什么 baseURL 只写 '/api'：
 * 开发时 Vite 会把 /api 开头的请求转发到 http://localhost:8080（见 vite.config.js），
 * 所以前端代码里不需要写死后端地址，部署时也不用改代码。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' }
})

// 响应拦截：直接返回 data，页面里少写一层 .data
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    // 后端返回的结构化错误（如 503 带数据库排查提示）优先展示
    let message =
      (error.response && error.response.data && (error.response.data.message || error.response.data.error)) || ''
    if (!message) {
      const status = error.response && error.response.status
      if (status === 503) {
        message = '服务暂时不可用：数据库可能没连上，请检查 MySQL 与 DB_PASSWORD'
      } else if (status === 500) {
        message = '服务器内部错误，请打开后端黑窗口查看日志最后几行'
      } else if (error.code === 'ECONNABORTED') {
        message = '请求超时：AI 生成较慢或后端未响应，请稍后重试'
      }
    }
    if (!message) message = error.message || '请求失败，请检查后端服务是否已启动'
    return Promise.reject(new Error(message))
  }
)

/* ---------------- 通用 ---------------- */

/** 健康检查 */
export const checkHealth = () => request.get('/health')

/* ---------------- AI 对话 ---------------- */

/** 发送一条消息，返回 { sessionId, answer } */
export const sendChat = (payload) => request.post('/chat', payload)

/** 查询某个会话的历史消息 */
export const getHistory = (sessionId) => request.get('/history', { params: { sessionId } })

/* ---------------- 院校专业库 ---------------- */

/** 院校列表，params 支持 city / level / is985 / is211 / isSelfLine / keyword */
export const listColleges = (params) => request.get('/colleges', { params })

/** 院校详情（含专业列表） */
export const getCollege = (id) => request.get(`/colleges/${id}`)

/** 所有城市（下拉筛选用） */
export const getCities = () => request.get('/colleges/cities')

/** 题库/院校库统计 */
export const getStats = () => request.get('/colleges/stats')

/** 专业列表，params 支持 collegeId / category / degreeType / keyword */
export const listMajors = (params) => request.get('/majors', { params })

/** 所有学科门类（下拉筛选用） */
export const getCategories = () => request.get('/majors/categories')

/* ---------------- 学情诊断与题库 ---------------- */

/** 开始测评：按学情抽题（不含答案） */
export const startAssessment = (payload) => request.post('/assess/start', payload)

/** 提交答案：判分 + BKT 诊断 + 生成计划 */
export const submitAssessment = (payload) => request.post('/assess/submit', payload)

/** 查看某学生的诊断与计划（不重新答题）。subject 可选 math/english/politics，默认 math */
export const getDiagnosis = (studentId, subject) => request.get('/assess/diagnosis', { params: { studentId, subject: subject || 'math' } })

/** AI 润色计划：用大模型把规则计划+诊断改写成个性化解读（可选增强，失败有兜底）。subject 可选 math/english/politics */
/** 查看某学生的做题历史（每条记录含对应题目的标准答案） */
export const getAnswerHistory = (studentId) => request.get('/assess/history', { params: { studentId } })

/* ===================== 错题本 ===================== */

/** 错题列表（studentId 必填，subject 可选：math/english/politics） */
export const getWrongBook = (studentId, subject) =>
  request.get('/wrongbook', { params: subject ? { studentId, subject } : { studentId } })

/** 错题本统计（总数 / 按科目 / 按来源 / 已重做） */
export const getWrongBookStats = (studentId) =>
  request.get('/wrongbook/stats', { params: { studentId } })

/** 随机抽题进入重做（返回不带答案的题目） */
export const redoWrongBook = (studentId, subject, count) =>
  request.post('/wrongbook/redo', { studentId, subject: subject || null, count: count || 5 })

/** 重做判分（答对自动移出错题本） */
export const gradeRedo = (wrongId, answer, studentId) =>
  request.post('/wrongbook/grade', { wrongId, answer, studentId })

/** 导入用户自己的错题 */
export const importWrongQuestion = (payload) => request.post('/wrongbook/import', payload)

/** 移除一条错题 */
export const removeWrongQuestion = (id, studentId) =>
  request.delete(`/wrongbook/${id}`, { params: { studentId } })

/** 清除某学生的历史学习数据（掌握度 + 做题历史），回到未测评状态 */
export const clearAssessment = (studentId) => request.delete('/assess/clear', { params: { studentId } })

/* ---------------- 认知科普（左侧固定文本 + 右侧 AI 答疑） ---------------- */

/**
 * 右侧 AI 答疑窗口调用：基于考研知识库的会话式问答。
 * 传入 { question, history: [{role, content}], context }，
 * 返回 { answer, sources: string[] }。
 *  - question：本次提问
 *  - history：之前的对话轮次，用于"追问"上下文
 *  - context：用户当前正在阅读的主题标题，帮助 AI 理解背景
 */
export const askScienceChat = (payload) => request.post('/science/chat', payload)

/* ---------------- AI 规划助手（Agent 编排） ---------------- */

/**
 * 运行一次 Agent 编排：传入 { goal, studentId? }，
 * 返回 { goal, answer, degraded, steps: [{index,name,status,durationMs,output}], sources }。
 * steps 用于前端「透明展示」AI 的每一步执行过程；degraded 表示大模型不可用、已用规则兜底。
 */
export const runAgent = (payload) => request.post('/agent/run', payload)
