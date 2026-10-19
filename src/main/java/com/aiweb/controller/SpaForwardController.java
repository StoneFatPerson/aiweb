package com.aiweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由回退：把前端的 history 模式路由（/college、/assess、/wrongbook、/science 等）
 * 全部转发到 index.html，由前端路由自己渲染。
 *
 * 位置：src/main/java/com/aiweb/controller/SpaForwardController.java
 *
 * 为什么需要：前端用 createWebHistory()（地址栏没有 # 号，更美观）。
 * 直接访问或刷新子路由时，Spring Boot 默认找不到映射会返回 404 Whitelabel 页。
 * dev 模式没这个问题（Vite dev server 自带 SPA fallback），
 * 但「构建产物放进 static/ 用 8080 一个端口跑完整应用」时必须有它。
 *
 * 踩坑记录（两个都导致过 500）：
 *   1) 不能映射 "/"——否则 forward:/index.html 会被自己再拦一次，死循环；
 *   2) path 变量必须排除点号（[^\\.]*）——否则 /index.html（含点号）也会被拦，同样死循环。
 *   本项目路由全是单段（/college、/assess…），单 pattern 足够；多段场景再按需扩展。
 */
@Controller
public class SpaForwardController {

    @GetMapping("/{path:[^\\.]*}")
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
