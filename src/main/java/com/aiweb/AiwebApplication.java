package com.aiweb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动入口。
 * 位置：src/main/java/com/aiweb/AiwebApplication.java
 * 作用：IDEA 里直接运行这个类的 main 方法即可启动整个网站。
 *
 * @MapperScan 告诉 MyBatis-Plus 去哪个包下面找数据库操作接口。
 */
@SpringBootApplication
@MapperScan("com.aiweb.mapper")
public class AiwebApplication {

    public static void main(String[] args) {
        // 启动前先检查环境变量。放在 Spring 启动之前，
        // 这样报错信息是我们自己写的中文提示，而不是 Spring 的一大段英文堆栈。
        checkEnvironment();

        SpringApplication.run(AiwebApplication.class, args);
    }

    /**
     * 环境变量检查。
     * 为什么写在 main 方法里：这是整个程序最先执行的地方，
     * 比 Spring 创建任何一个组件都早，能保证第一时间给出明确提示。
     */
    private static void checkEnvironment() {
        if (isBlank(readEnv("ZHIPU_API_KEY"))) {
            System.err.println("""
                    \n============================================================
                    【启动失败】缺少环境变量 ZHIPU_API_KEY

                    原因：大模型密钥没有配置，程序不知道用谁的账号去调用 AI。

                    解决办法（三步）：
                      1. Win + S 搜索「编辑系统环境变量」→ 打开 → 点「环境变量」
                      2. 在「用户变量」点「新建」
                         变量名：ZHIPU_API_KEY
                         变量值：从 https://open.bigmodel.cn 控制台复制的 Key
                      3. 点确定后【重启 IntelliJ IDEA】(不重启读不到)

                    验证：新开一个 CMD 执行  echo %ZHIPU_API_KEY%
                    ============================================================\n""");
            System.exit(1);
        }

        if (isBlank(readEnv("DB_PASSWORD"))) {
            System.err.println("""
                    \n============================================================
                    【警告】环境变量 DB_PASSWORD 未设置

                    将尝试用空密码连接 MySQL。如果你的 MySQL 设置了密码，
                    稍后会看到 "Access denied for user 'root'" 的错误。

                    解决办法：
                      新建用户变量 DB_PASSWORD，值填你的 MySQL root 密码，
                      然后重启 IntelliJ IDEA。
                    ============================================================\n""");
        }
    }

    /**
     * 读取环境变量。
     * 说明：Java 读取系统环境变量用的是 System.getenv()，
     * 等价于 Python 的 os.environ、Node.js 的 process.env。
     * 这里额外兼容 -D 参数传入的情况（例如 IDEA 的 VM options）。
     */
    private static String readEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
