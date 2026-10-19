# 智研 · AI 考研备考助手

> 面向在校大学生（大二 ~ 大四）的考研备考助手，帮助考生搞清「**该不该考、考什么、怎么考**」，并输出可执行的备考路径。
>
> 参赛项目：全国高校 AI Web 开发挑战赛（主题「智启未来：AI 驱动的 Web 应用创新挑战」）

---

## 一、项目简介

考研备考的典型困境不是"不努力"，而是**信息不对称与计划失焦**：不知道流程和时间节点、不清楚自己哪块薄弱、计划做出来却执行不下去。

本项目用 AI 打通三条链路：

| 环节 | 解决的问题 | 做法 |
|---|---|---|
| **认知科普** | 不懂流程和名词 | 左侧固定科普文本（术语 / 学硕专硕 / 时间线等）；右侧基于自建知识库的 AI 答疑（RAG，答案可溯源，不靠模型自由发挥） |
| **学情诊断** | 不知道自己哪不会 | 知识点体系 + 测评 + 掌握度建模，把"感觉不行"变成"具体哪章不行" |
| **错题复盘** | 错了还错，找不到薄弱 | 判错自动收录错题本；随机重做、答对即攻克 |

**核心设计原则**：事实与算法放在代码里（可复现、可测试、可验证），大模型只负责组织语言与个性化推理。这条原则贯穿整个项目，也是避免 AI 幻觉的根本手段。

---

## 二、当前进度

| 模块 | 状态 |
|---|---|
| 后端骨架（Spring Boot + Spring AI + MyBatis-Plus + MySQL） | ✅ 已完成 |
| AI 对话（接入智谱 GLM，多轮上下文，历史落库） | ✅ 已完成 |
| 考研院校专业库（28 所院校 / 104 个专业，山东为主） | ✅ 已完成 |
| 数学题库第一批（14 章节 / 25 知识点 / 34 道原创题） | ✅ 已完成 |
| 英语题库第一批（4 章 / 12 知识点 / 26 道原创题：词汇语法 / 完形 / 阅读 / 写作翻译基础） | ✅ 已完成 |
| 政治题库第一批（5 章 / 10 知识点 / 24 道原创题：马原 / 毛中特 / 史纲 / 思修 / 形策） | ✅ 已完成 |
| 知识点体系与统一数据格式 | ✅ 已完成 |
| 测评 / 掌握度 / 诊断（BKT 掌握度建模） | ✅ 已完成 |
| 错题本（诊断答错自动收录 + 用户导入 + 随机重做，答对即攻克） | ✅ 已完成 |
| 认知科普：左侧固定讲解 + 右侧 AI 答疑（本地 RAG） | ✅ 已完成 |
| Vue 3 前端（Element Plus，响应式 + 无障碍） | ✅ 已完成 |
| AI 规划助手（Agent 编排：多步工具调用 + 透明执行轨迹） | ✅ 已完成 |
| MCP 工具服务（5 个工具按 MCP 协议暴露，SSE 传输，可被第三方 AI 客户端调用） | ✅ 已完成 |

---

## 三、技术架构

| 层 | 选型 | 版本 | 理由 |
|---|---|---|---|
| 运行时 | Java | 17 (LTS) | Spring Boot 3.x 基线版本 |
| 主框架 | Spring Boot | 3.5.16 | 自带 Web 容器，无需额外部署 |
| AI 框架 | Spring AI | 1.1.8 | 换模型只改一行配置；自带 RAG / 对话记忆 / MCP 扩展点 |
| 大模型 | 智谱 GLM-4.7-Flash | 免费档 | 国内直连、200K 上下文、兼容 OpenAI 协议 |
| 数据层 | MySQL + MyBatis-Plus | 8.0 / 3.5.17 | 关系数据，开发效率高 |
| RAG | 本地 ONNX Embedding + SimpleVectorStore | — | **无需任何 API Key、无需注册、无需额外运维** |
| Agent 编排 | 自研 Orchestrator | — | 逻辑可控、好排查，避免过度依赖框架 |
| 接口文档 | springdoc-openapi | 2.9.1 | 自动生成文档页 |

> 版本已锁定，请勿升级到 Spring AI 2.x（里程碑版，包名有变动）。

**分层结构**：

```
前端（Vue 3）
  ↓
编排层 Orchestrator（意图判断 → 选 Agent → 调工具 → 汇总 → 附来源）
  ↓
专职 Agent（科普 / 诊断 / 计划 / 复盘）
  ↓
算法层（掌握度、自适应选题、间隔重复、阶段切分）  ← 纯 Java，不经过模型
  ↓
数据层（MySQL：知识点 / 题目 / 作答记录 / 掌握度 / 院校专业）
```

---

## 四、目录结构

```
aiweb/
├── pom.xml                                 依赖与构建配置
├── src/main/java/com/aiweb/
│   ├── AiwebApplication.java               启动入口 + 环境变量检查
│   ├── config/                             RagConfig / CollegeDataInitializer / CorpusInitializer / QuestionDataInitializer
│   ├── controller/                         Chat / College / Major / Assessment / Science / Agent（共 6 个）
│   ├── entity/                             ChatMessage / College / Major / 题目与作答 / 掌握度 等
│   ├── mapper/                             数据访问接口
│   ├── mcp/                                 MCP 工具服务：KaoyanMcpTools（@McpTool 暴露 5 个标准工具）
│   ├── service/                            Chat / College / Assessment / Diagnosis / Ocr / WrongQuestion / Rag（共 7 个）
│   └── agent/                              Agent 编排：IntentTool / CollegeSearchTool / ScienceTool / DiagnosisTool + AgentOrchestrator（多步工具调用、轨迹透明）
├── src/main/resources/
│   ├── application.yml                     配置（密钥走环境变量）
│   ├── schema.sql                          建表脚本（启动自动执行）
│   ├── kaoyan-corpus.json                  认知科普语料（RAG 检索源；不收录分数线，全是稳定的结构化事实）
│   ├── data/colleges.json                  院校专业数据（28 所 / 104 个专业）
│   ├── data/math-seed-batch1.json          数学第一批种子题（34 题）
│   ├── data/english-seed-batch1.json       英语第一批种子题（26 题）
│   ├── data/politics-seed-batch1.json      政治第一批种子题（24 题）
│   └── static/index.html                  后端根路径引导页（展示服务入口，前端在 5173）
├── src/test/java/com/aiweb/               单元测试（35 个用例：BKT/判分/计划/意图/编排兜底/输入防护）
├── frontend/                               Vue 3 前端（详见 frontend/README.md）
├── scripts/                                题库生成器与校验器 + 一键启动脚本（start-backend.bat / start-frontend.bat）
├── docs/题库数据格式说明.md                  统一数据格式规范
├── docs/技术文档.md                          参赛技术文档（概述/架构/AI 方案/实现/测试部署）
```

> 前端与后端放在同一个仓库里，方便一次提交、一次评审。

---

## 五、环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Node.js（前端，后续需要） | 18+ |

---

## 六、环境变量

| 变量名 | 必填 | 说明 |
|---|---|---|
| `ZHIPU_API_KEY` | ✅ | 智谱开放平台 API Key（https://open.bigmodel.cn） |
| `DB_PASSWORD` | ❌ | MySQL 密码，未设置时以空密码连接 |

### Windows 配置步骤

1. `Win + S` → 搜索「编辑系统环境变量」→ 打开 → 点「环境变量」
2. 「用户变量」→ 新建 → 变量名 `ZHIPU_API_KEY`，变量值填你的 Key
3. 需要的话再新建 `DB_PASSWORD`
4. 确定后**重启 IDE**（不重启读不到）

验证：`echo %ZHIPU_API_KEY%`

> **密钥只存在系统环境变量中，代码中只有 `${ZHIPU_API_KEY}` 占位符，绝不入库。**
> 详见 `.env.example`。

---

## 七、安装与运行

```bash
# 1. 编译
mvn clean compile

# 2. 启动（固定 8080 端口）
#    命令行参数 --server.port=8080 优先级高于环境变量，
#    可避免被 SERVER_PORT / SERVER__PORT 之类变量改成别的端口（改了前端 Vite 代理就连不上后端）。
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
```

或在 IDE 中直接运行 `AiwebApplication.java`。

> **Windows 一键启动**：双击 `scripts/start-backend.bat`（后端，固定 8080）和 `scripts/start-frontend.bat`（前端，5173），不用手动敲命令。
>
> **端口被抢怎么办**：若页面提示"后端未连接"，先看后端启动日志里 `Tomcat started on port` 是不是 **8080**；不是的话，说明环境里有 `SERVER_PORT` / `SERVER__PORT` 变量抢了端口。因为环境变量优先级高于配置文件，解法是加命令行参数 `--server.port=8080`（命令行优先级最高，能压过环境变量）——上面的启动命令和 `start-backend.bat` 已经带上；在 IDE 里运行则在"运行配置 → 程序参数"中加上它。

启动后访问：

| 地址 | 内容 |
|---|---|
| http://localhost:8080 | 后端服务引导页（应用首页提示，前端在 5173） |
| http://localhost:8080/api/health | 健康检查 |
| http://localhost:8080/swagger-ui.html | 接口文档 |

**数据库无需手动建库建表**——启动时自动创建 `aiweb` 库及相关表，并导入院校专业数据。

### 前端运行

前端是独立的 Vue 3 工程，放在 `frontend/` 目录。

```bash
cd frontend
npm install --registry=https://registry.npmmirror.com
npm run dev

> 也可直接双击 `scripts/start-frontend.bat`（会自动判断是否已装依赖）。
```

前端跑在 http://localhost:5173，它会把 `/api` 请求自动转发到后端 8080（Vite 代理，不需要后端配跨域）。

**必须先启动后端**，否则前端页面右上角会显示「后端未连接」。详细说明见 `frontend/README.md`。

---

## 八、接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查（含数据库连通性探测：连不上会返回 503 和中文排查提示） |
| POST | `/api/chat` | AI 对话 |
| GET | `/api/chat/stream` | 流式对话（打字机效果） |
| GET | `/api/history` | 对话历史 |
| GET | `/api/colleges` | 院校查询（支持城市/层次/985/211/自划线/关键词） |
| GET | `/api/colleges/{id}` | 院校详情（含专业列表） |
| GET | `/api/majors` | 专业查询（支持门类/学位类型/关键词） |
| GET | `/api/majors/categories` | 学科门类（筛选用） |
| GET | `/api/colleges/cities` | 院校所在城市（筛选用） |
| GET | `/api/colleges/stats` | 院校 / 专业库统计 |
| GET | `/api/colleges/{id}/majors` | 某院校的专业列表 |
| POST | `/api/assess/start` | 开始测评（按学科抽题，不含答案；body 传 `subject`，不传默认 math） |
| POST | `/api/assess/submit` | 提交答案（判分 + BKT 诊断 + 错题自动收录） |
| GET | `/api/assess/points` | 知识点体系（`?subject=math/english/politics`，默认 math） |
| GET | `/api/assess/stats` | 题库统计（总量 + 各科目分别统计） |
| GET | `/api/assess/diagnosis` | 查看学情诊断（`?subject=` 分学科，各科掌握度独立追踪） |
| GET | `/api/assess/history` | 做题历史 |
| DELETE | `/api/assess/clear` | 清除学习数据（回到未测评） |
| GET | `/api/science/topics` | 认知科普主题列表 |
| POST | `/api/science/explain` | 主动说明式讲解（RAG 合成文章） |
| POST | `/api/science/ask` | RAG 问答（保留兼容） |
| POST | `/api/science/chat` | 右侧 AI 答疑（会话式 RAG，带历史与上下文） |
| POST | `/api/agent/run` | AI 规划助手：输入目标，多步编排（查院校 / 检索科普 / 学情诊断），返回每步执行轨迹 + 汇总建议 |
| GET | `/api/wrongbook` | 错题列表（`?studentId=&subject=`） |
| GET | `/api/wrongbook/stats` | 错题统计 |
| POST | `/api/wrongbook/redo` | 随机抽题重做（返回不带答案的题） |
| POST | `/api/wrongbook/grade` | 重做判分（答对自动移出错题本） |
| POST | `/api/wrongbook/import` | 导入用户自己的错题 |
| DELETE | `/api/wrongbook/{id}` | 移除一条错题 |

---

## 九、MCP 工具服务

本项目同时是一个 **MCP Server**（Model Context Protocol，模型上下文协议——让 AI 应用标准化调用外部工具的开放协议）。启动后端后，任何支持 MCP 的客户端都能直接调用本项目能力，无需改动业务代码。

- **连接地址**：`http://localhost:8080/sse`（SSE 传输，与 REST 接口共存于同一应用）
- **注册方式**：`KaoyanMcpTools` 方法上的 `@McpTool` 注解由 MCP starter 自动扫描暴露
- **安全提示**：当前未做鉴权，仅限本地 / 内网演示；对外暴露前必须加访问控制

### 工具清单

| 工具名 | 功能 | 数据来源 |
|---|---|---|
| `search_colleges` | 查院校（城市/层次/985/211/关键词） | MySQL 院校库 |
| `get_college_detail` | 院校详情（含招生专业列表） | MySQL 院校库 + 专业库 |
| `search_majors` | 查专业（院校/门类/学位类型/关键词） | MySQL 专业库 |
| `ask_kaoyan_knowledge` | 考研知识咨询（附参考来源） | 本地 RAG 知识库 |
| `get_study_diagnosis` | 学情诊断（可选数学/英语/政治学科，掌握度 + 薄弱点） | MySQL 作答记录 + BKT 算法 |

### 客户端接入示例

**Cherry Studio**（设置 → MCP 服务器 → 添加，类型选 SSE）：

```json
{
  "mcpServers": {
    "aiweb-kaoyan": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

**Claude Desktop**（`claude_desktop_config.json`）：

```json
{
  "mcpServers": {
    "aiweb-kaoyan": {
      "url": "http://localhost:8080/sse"
    }
  }
}
```

接入后即可在对话中直接提问，例如「山东有哪些 985 院校」「学硕和专硕有什么区别」，客户端会自动调用对应工具并基于返回数据作答。

---

## 十、数据与合规

| 项 | 说明 |
|---|---|
| 院校专业数据 | 自行整理的结构化信息，**不收录分数线**（每年变动且易产生幻觉）。当前标记 `verified=0`，正式提交前需与研招网逐条核对 |
| 题库 | **全部原创编写**，不照搬任何真实真题原文；题目来源用 `source` 字段严格区分（原创种子题 / AI 生成题 / 用户导入 / 官方公开）。当前覆盖数学（34 题）/ 英语（26 题）/ 政治（24 题）三科，英语政治仅收适合客观化考查的部分（翻译写作为主观题，暂不收录）；各科掌握度独立追踪、互不影响 |
| 第三方内容 | **不抓取**任何需要登录或有反爬的站点，避免著作权与个人信息风险 |
| 密钥 | 全部走环境变量，不进入版本库 |

---

## 十一、团队信息

| 项 | 内容 |
|---|---|
| 参赛组别 | 待填写 |
| 成员 | 待填写 |
| 指导教师 | 待填写 |
| 院校 | 待填写 |

---

## 十二、许可与声明

- 本项目为参赛作品，题目与知识点数据为整理/原创内容，仅供参考。
- 涉及考试政策、时间节点、招生信息的内容，**一律以中国研究生招生信息网等官方渠道公告为准**。
