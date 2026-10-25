package com.aiweb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域（CORS）白名单配置。
 * 位置：src/main/java/com/aiweb/config/CorsConfig.java
 *
 * 为什么这样改：之前 7 个 Controller 每个都写着 @CrossOrigin(origins = "*")，
 * 含义是「任何网站都能调用本服务的接口」。开发阶段图方便，但上线后是安全问题——
 * 别人可以在自己的网页里诱导用户浏览器去调你的接口。现在统一收敛成一份配置：
 * 允许来源从环境变量 APP_CORS_ALLOWED_ORIGINS 读取（逗号分隔，如
 * "https://kaoyan.example.com,https://www.example.com"），默认只放行本机开发地址。
 *
 * 部署提醒：把 APP_CORS_ALLOWED_ORIGINS 设置成真实的站点域名即可，无需改代码。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 逗号分隔的允许来源。默认只有本机开发用的 5173（Vite）与 8080（后端自带页面）。
     * 用 @Value 注入字符串再 split，避免 "a,b" 到 String[] 的隐式转换在不同版本下行为差异。
     */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split("\\s*,\\s*");

        // 业务接口：只允许白名单来源，带 Cookie 的请求也放行
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // MCP 的 SSE 端点：浏览器类客户端可能带 Origin，单独放行（不做长连接预检缓存）
        registry.addMapping("/sse/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
