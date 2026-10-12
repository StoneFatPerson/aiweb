package com.aiweb;

import org.junit.jupiter.api.Test;

/**
 * 单元测试占位。
 * 位置：src/test/java/com/aiweb/AiwebApplicationTests.java
 *
 * 注意：这里故意没有加 @SpringBootTest。
 * 因为启动 Spring 容器需要数据库和 ZHIPU_API_KEY，
 * 加了会导致 mvn package 在你的环境变量没配好之前就失败。
 * 等你配好环境后，可以自行改成：
 *
 * @SpringBootTest
 * class AiwebApplicationTests { @Test void contextLoads() { } }
 */
class AiwebApplicationTests {

    @Test
    void placeholder() {
        org.junit.jupiter.api.Assertions.assertTrue(true);
    }
}
