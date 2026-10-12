# AI Web — AI 驱动的 Web 应用

全国高校 AI Web 开发挑战赛参赛项目。后端 Java + Spring Boot + Spring AI，前端原生 HTML/JS，大模型使用智谱 GLM-4.7-Flash。

## 一、技术栈

| 层 | 选型 | 版本 | 理由 |
|---|---|---|---|
| 语言 | Java | 17 (LTS) | Spring Boot 3.x 的基线版本，生态最稳 |
| 框架 | Spring Boot | 3.5.16 | 自带服务器，无需额外安装 Tomcat |
| AI 框架 | Spring AI | 1.1.8 | Spring 官方出品；换模型只改一行配置；自带 RAG、对话记忆、MCP 扩展点 |
| 大模型 | 智谱 GLM-4.7-Flash | 免费 | 永久免费、200K 上下文、兼容 OpenAI 协议，国内直连 |
| 数据层 | MyBatis-Plus + MySQL | 3.5.17 / 8.0 | 少写 SQL，开发快 |
| 构建 | Maven | 3.9.x | 本机已安装 |
| 接口文档 | springdoc-openapi | 2.9.1 | 自动生成文档页 |

> 版本已锁定，请勿升级到 Spring AI 2.x（里程碑版，包名有变动）。

## 二、启动前必须配置的环境变量

| 变量名 | 必填 | 说明 | 缺失时的表现 |
|---|---|---|---|
| `ZHIPU_API_KEY` | ✅ 必填 | 智谱开放平台的 API Key，从 https://open.bigmodel.cn 控制台获取 | **启动失败**，控制台打印中文排查指引 |
| `DB_PASSWORD` | ❌ 选填 | MySQL root 密码 | 启动报警告，以空密码尝试连接，可能 `Access denied` |

> 变量清单另见 `.env.example`。注意：**Java 不会自动读取 `.env` 文件**，
> 那个文件只是模板/备忘，真正生效必须配到 Windows 系统环境变量里。

### 代码里是怎么读的

Java 没有 `os.environ` / `process.env`，等价写法是 `System.getenv()`。本项目没有手写它，
而是用 Spring Boot 的**占位符**机制，写在 `src/main/resources/application.yml`：

```yaml
api-key: ${ZHIPU_API_KEY}     # 启动时自动去系统环境变量里找同名变量
password: ${DB_PASSWORD:}     # 冒号后是默认值，留空表示不设默认
```

额外的启动前检查写在 `AiwebApplication.main()` 里，用 `System.getenv()` 读取，
缺失时打印中文指引并 `System.exit(1)`，避免你面对一大段英文堆栈。

### Windows 设置步骤

1. `Win + S` 搜索「编辑系统环境变量」→ 打开 → 点「环境变量」
2. 在「用户变量」区域点「新建」
3. 变量名 `ZHIPU_API_KEY`，变量值填你的 Key
4. 需要的话再新建 `DB_PASSWORD`
5. 全部点确定，**然后重启 IntelliJ IDEA**（不重启读不到）

命令行验证（新开一个 CMD）：
```
echo %ZHIPU_API_KEY%
```

> 密钥只存在系统环境变量里，代码中只有 `${ZHIPU_API_KEY}` 占位符，**不会进 Git 仓库**。

## 三、启动方式

### 方式 A：IDEA 中运行（推荐）

1. IDEA → File → Open → 选择 `aiweb` 文件夹
2. 等待 Maven 依赖下载完成（右下角进度条走完）
3. 打开 `src/main/java/com/aiweb/AiwebApplication.java`
4. 点 `main` 方法左边的绿色三角 → Run

### 方式 B：命令行运行

```
cd C:\Users\DELL\Desktop\AI_Web\aiweb
mvn spring-boot:run
```

启动成功后访问：

| 地址 | 内容 |
|---|---|
| http://localhost:8080 | 聊天页面 |
| http://localhost:8080/api/health | 健康检查 |
| http://localhost:8080/swagger-ui.html | 自动生成的接口文档 |

## 四、接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| POST | `/api/chat` | 普通对话，body：`{"sessionId":"可选","message":"你好"}` |
| GET | `/api/chat/stream` | 流式对话，参数：`message`、`sessionId` |
| GET | `/api/history` | 查询历史，参数：`sessionId` |

## 五、数据库

- 数据库名：`aiweb`，**启动时自动创建**（连接串里有 `createDatabaseIfNotExist=true`）
- 表：`chat_message`（对话历史），**启动时自动建表**（`src/main/resources/schema.sql`）
- 如需手动执行：

```sql
CREATE DATABASE IF NOT EXISTS aiweb DEFAULT CHARSET utf8mb4;
USE aiweb;
-- 然后执行 src/main/resources/schema.sql
```

## 六、目录结构

```
aiweb/
├── pom.xml                          依赖与构建配置
├── src/main/java/com/aiweb/
│   ├── AiwebApplication.java        启动入口
│   ├── controller/ChatController.java  对外接口
│   ├── service/ChatService.java     AI 对话核心逻辑
│   ├── entity/ChatMessage.java      数据库表对应类
│   └── mapper/ChatMessageMapper.java   数据库操作
├── src/main/resources/
│   ├── application.yml              所有配置（含 AI 与数据库）
│   ├── schema.sql                   建表脚本
│   └── static/index.html            聊天页面
└── src/test/java/com/aiweb/         测试
```

## 七、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| `【启动失败】缺少环境变量 ZHIPU_API_KEY` | 环境变量没配，或配完没重启 IDEA | 配好后**重启 IDEA** |
| `Access denied for user 'root'` | MySQL 密码不对或未设 `DB_PASSWORD` | 设置 `DB_PASSWORD` 为真实密码 |
| `Communications link failure` | MySQL 没启动 | 启动 MySQL 服务 |
| 依赖下载很慢 / 超时 | 网络问题 | 已配置阿里云镜像；仍慢可改用华为云或腾讯云镜像 |
| 端口被占用 | 8080 已被别的程序占用 | 改 `application.yml` 里的 `server.port` |
