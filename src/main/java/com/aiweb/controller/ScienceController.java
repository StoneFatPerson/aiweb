package com.aiweb.controller;

import com.aiweb.service.RagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认知科普接口。
 * 位置：src/main/java/com/aiweb/controller/ScienceController.java
 * 作用：
 *  - GET  /api/science/topics  返回可讲解的主题列表（左侧目录用）
 *  - POST /api/science/explain 接收 { topic }，返回主动说明式讲解文章 { title, content, sources }
 *  - POST /api/science/ask     旧版问答（保留兼容）
 */
@RestController
@RequestMapping("/api/science")
public class ScienceController {

    private final RagService ragService;

    public ScienceController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/topics")
    public List<RagService.Topic> topics() {
        return ragService.topics();
    }

    @PostMapping("/explain")
    public RagService.TopicExplain explain(@RequestBody Map<String, String> body) {
        String topic = (body == null) ? "" : body.getOrDefault("topic", "");
        return ragService.explain(topic);
    }

    @PostMapping("/ask")
    public RagService.RagAnswer ask(@RequestBody Map<String, String> body) {
        String question = (body == null) ? "" : body.getOrDefault("question", "");
        return ragService.ask(question);
    }
}
