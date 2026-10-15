package com.aiweb.controller;

import com.aiweb.service.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认知科普问答接口。
 * 位置：src/main/java/com/aiweb/controller/ScienceController.java
 * 作用：接收用户问题，交给 RagService 做"检索 + 生成"，返回答案与参考来源。
 */
@RestController
@RequestMapping("/api/science")
public class ScienceController {

    private final RagService ragService;

    public ScienceController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public RagService.RagAnswer ask(@RequestBody Map<String, String> body) {
        String question = (body == null) ? "" : body.getOrDefault("question", "");
        return ragService.ask(question);
    }
}
