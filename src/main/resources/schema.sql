-- 建表脚本。应用启动时会自动执行（spring.sql.init.mode=always）。
-- 用了 IF NOT EXISTS，所以重复启动不会报错、不会覆盖已有数据。
-- 位置：src/main/resources/schema.sql

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id  VARCHAR(64)  NOT NULL COMMENT '会话ID，区分不同对话',
    role        VARCHAR(16)  NOT NULL COMMENT '角色：user=用户 / assistant=AI',
    content     TEXT         NOT NULL COMMENT '消息内容',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_session (session_id),
    KEY idx_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '对话历史表';

-- ============================================================
-- 考研院校专业库（只收录考研，不含考公）
-- 数据以山东地区院校为主，仅收录各校最热门的几个考研专业
-- 注意：本表为初版数据，字段 verified=0 表示尚未与研招网逐条核对
-- ============================================================

CREATE TABLE IF NOT EXISTS college (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code            VARCHAR(16)  COMMENT '院校代码（研招网）',
    name            VARCHAR(64)  NOT NULL COMMENT '院校全称',
    short_name      VARCHAR(32)  COMMENT '简称',
    city            VARCHAR(32)  COMMENT '所在城市',
    level           VARCHAR(32)  COMMENT '层次：985 / 211 / 省属重点 / 省属',
    is_985          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否 985',
    is_211          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否 211',
    is_double_first TINYINT      NOT NULL DEFAULT 0 COMMENT '是否双一流',
    is_self_line    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否自主划线（34所）',
    website         VARCHAR(128) COMMENT '官网',
    verified        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已与官方核对：0未核对 1已核对',
    UNIQUE KEY uk_name (name),
    KEY idx_city (city),
    KEY idx_level (level)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '院校表';

CREATE TABLE IF NOT EXISTS major (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    college_id         BIGINT       NOT NULL COMMENT '所属院校',
    code               VARCHAR(16)  COMMENT '专业代码',
    name               VARCHAR(64)  NOT NULL COMMENT '专业名称',
    degree_type        VARCHAR(16)  COMMENT '学位类型：学硕 / 专硕',
    category           VARCHAR(32)  COMMENT '学科门类：工学 / 医学 / 管理学 等',
    discipline         VARCHAR(64)  COMMENT '一级学科',
    department         VARCHAR(64)  COMMENT '所属学院',
    public_courses     VARCHAR(64)  COMMENT '公共课组合，如 政治+英二+数二',
    duration           TINYINT      COMMENT '学制（年）',
    is_popular         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否热门专业',
    verified           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已与官方核对',
    KEY idx_college (college_id),
    KEY idx_category (category),
    KEY idx_degree (degree_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考研专业表';

-- ============================================================
-- 考研数学题库（第一批：34 道原创种子题，全部非真题）
-- 来源：data/math-seed-batch1.json
-- ============================================================

CREATE TABLE IF NOT EXISTS question_bank (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code           VARCHAR(32)  NOT NULL COMMENT '题目编号，如 MATH-Q0001',
    subject_code   VARCHAR(16)  COMMENT '学科代码，如 math',
    chapter_code   VARCHAR(16)  COMMENT '章节代码，如 MATH-01',
    point_codes    VARCHAR(255) COMMENT '知识点代码（逗号分隔，可多个）',
    applicable_to  VARCHAR(64)  COMMENT '适用数学类型：math1/math2/math3（逗号分隔）',
    type           VARCHAR(16)  NOT NULL COMMENT '题型：SINGLE_CHOICE / FILL_BLANK',
    difficulty     TINYINT      NOT NULL DEFAULT 1 COMMENT '难度 1-3',
    year           VARCHAR(8)   COMMENT '年份，种子题多为 null',
    source         VARCHAR(16)  NOT NULL DEFAULT 'ORIGINAL_SEED' COMMENT '来源：ORIGINAL_SEED / AI_GENERATED',
    stem           TEXT         NOT NULL COMMENT '题干',
    options        TEXT         COMMENT '选择题选项（JSON 数组 [{key,content}]）',
    answer         TEXT         NOT NULL COMMENT '正确答案',
    explanation    TEXT         COMMENT '解析',
    keywords       VARCHAR(255) COMMENT '关键词（逗号分隔）',
    verified       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已核对：0/1',
    UNIQUE KEY uk_qcode (code),
    KEY idx_point (point_codes(64)),
    KEY idx_chapter (chapter_code),
    KEY idx_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考研数学题库';

-- 知识点表：用于诊断时按知识点追踪掌握度（取自同一 JSON 的 chapters/points）
CREATE TABLE IF NOT EXISTS knowledge_point (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code           VARCHAR(32)  NOT NULL COMMENT '知识点代码，如 MATH-01-01',
    name           VARCHAR(128) NOT NULL COMMENT '知识点名称',
    chapter_code   VARCHAR(16)  COMMENT '所属章节代码',
    chapter_name   VARCHAR(128) COMMENT '所属章节名称',
    applicable_to  VARCHAR(64)  COMMENT '适用数学类型：math1/math2/math3',
    UNIQUE KEY uk_pcode (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考研数学知识点';

-- 掌握度表：按「学生 + 知识点」记录 BKT 估计的掌握概率，支持持续追踪
CREATE TABLE IF NOT EXISTS mastery (
    student_id     VARCHAR(64) NOT NULL COMMENT '学生标识（前端 localStorage 生成）',
    point_code     VARCHAR(32) NOT NULL COMMENT '知识点代码',
    mastery_prob   DECIMAL(4,3) NOT NULL DEFAULT 0.300 COMMENT 'BKT 估计的掌握概率 0-1',
    attempts       INT         NOT NULL DEFAULT 0 COMMENT '作答次数',
    last_updated   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, point_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生各知识点掌握度（BKT 追踪）';

-- 做题历史表：记录每次提交中每道题的作答（含标准答案），
-- 用于「历史做题记录」展示与「清除历史数据」功能。
CREATE TABLE IF NOT EXISTS answer_history (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    student_id     VARCHAR(64)  NOT NULL COMMENT '学生标识（前端 localStorage 生成）',
    question_code  VARCHAR(32)  NOT NULL COMMENT '题目编号，如 MATH-Q0001',
    user_answer    TEXT         COMMENT '用户作答（已归一化）',
    correct_answer TEXT         NOT NULL COMMENT '该题标准答案',
    is_correct     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否正确：0 否 / 1 是',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    KEY idx_student (student_id),
    KEY idx_qcode (question_code),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '做题历史（含标准答案）';
