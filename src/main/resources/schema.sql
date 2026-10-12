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
