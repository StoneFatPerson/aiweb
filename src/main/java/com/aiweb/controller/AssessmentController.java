package com.aiweb.controller;

import com.aiweb.dto.AssessmentResult;
import com.aiweb.dto.AssessmentStartRequest;
import com.aiweb.dto.AssessmentStartResponse;
import com.aiweb.dto.AssessmentSubmitRequest;
import com.aiweb.entity.KnowledgePoint;
import com.aiweb.service.AssessmentService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情诊断与题库接口。
 * 位置：src/main/java/com/aiweb/controller/AssessmentController.java
 *
 * 接口一览：
 *   POST /api/assess/start     按学情抽题（不含答案）
 *   POST /api/assess/submit    提交答案 -> 判分 + 诊断 + 计划
 *   GET  /api/assess/points    全部知识点（前端展示用）
 *   GET  /api/assess/stats     题库统计
 *   GET  /api/assess/diagnosis 查看某学生的诊断与计划（不重新答题）
 */
@RestController
@RequestMapping("/api/assess")
@CrossOrigin(origins = "*")
public class AssessmentController {

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping("/start")
    public AssessmentStartResponse start(@RequestBody AssessmentStartRequest req) {
        int count = (req.count() == null || req.count() <= 0) ? 10 : req.count();
        return assessmentService.start(req.studentId(), req.targetMath(), count);
    }

    @PostMapping("/submit")
    public AssessmentResult submit(@RequestBody AssessmentSubmitRequest req) {
        return assessmentService.submit(req.studentId(), req.answers());
    }

    @GetMapping("/points")
    public List<KnowledgePoint> points() {
        return assessmentService.allPoints();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return assessmentService.stats();
    }

    @GetMapping("/diagnosis")
    public AssessmentResult.DiagnosisView diagnosis(@RequestParam String studentId) {
        return assessmentService.getDiagnosis(studentId);
    }

    /**
     * AI 润色计划（可选增强）：用大模型把规则计划 + 诊断改写成个性化解读。
     * 规则计划始终兜底：若大模型不可用（如免费档限流），返回 fallback=true，前端保留规则计划。
     */
    @PostMapping("/plan/ai")
    public Map<String, Object> aiPlan(@RequestBody AssessmentAiPlanRequest req) {
        String plan = assessmentService.generateAiPlan(req.studentId());
        Map<String, Object> result = new HashMap<>();
        if (plan == null || plan.isBlank()) {
            result.put("fallback", true);
            result.put("message", "AI 润色暂时不可用（可能命中免费档限流），已为你保留规则计划");
        } else {
            result.put("fallback", false);
            result.put("aiPlan", plan);
        }
        return result;
    }

    /** POST /api/assess/plan/ai 的请求体：{"studentId":"xxx"} */
    public record AssessmentAiPlanRequest(String studentId) {
    }
}
