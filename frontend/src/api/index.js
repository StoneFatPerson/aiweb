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
    const message =
      (error.response && error.response.data && error.response.data.error) ||
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '请求失败，请检查后端服务是否已启动'
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

/** 查看某学生的诊断与计划（不重新答题） */
export const getDiagnosis = (studentId) => request.get('/assess/diagnosis', { params: { studentId } })

/** 所有知识点（展示用） */
export const getPoints = () => request.get('/assess/points')

/** AI 润色计划：用大模型把规则计划+诊断改写成个性化解读（可选增强，失败有兜底） */
export const getAiPlan = (studentId) => request.post('/assess/plan/ai', { studentId })

/** 查看某学生的做题历史（每条记录含对应题目的标准答案） */
export const getAnswerHistory = (studentId) => request.get('/assess/history', { params: { studentId } })

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
