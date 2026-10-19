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
        String subject = (req.subject() == null || req.subject().isBlank()) ? "math" : req.subject();
        return assessmentService.start(req.studentId(), subject, req.targetMath(), count);
    }

    @PostMapping("/submit")
    public AssessmentResult submit(@RequestBody AssessmentSubmitRequest req) {
        return assessmentService.submit(req.studentId(), req.answers());
    }

    /**
     * 知识点列表：GET /api/assess/points?subject=math
     * subject 可选 math / english / politics（默认 math），前端按学科展示。
     */
    @GetMapping("/points")
    public List<KnowledgePoint> points(@RequestParam(required = false) String subject) {
        String s = (subject == null || subject.isBlank()) ? "math" : subject;
        return assessmentService.allPoints(s);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return assessmentService.stats();
    }

    /**
     * 查看诊断与计划：GET /api/assess/diagnosis?studentId=xxx&subject=math
     * subject 可选 math / english / politics（默认 math），各学科的掌握度独立追踪、互不影响。
     */
    @GetMapping("/diagnosis")
    public AssessmentResult.DiagnosisView diagnosis(@RequestParam String studentId,
                                                    @RequestParam(required = false) String subject) {
        String s = (subject == null || subject.isBlank()) ? "math" : subject;
        return assessmentService.getDiagnosis(studentId, s);
    }

    /**
     * 做题历史（含标准答案）：GET /api/assess/history?studentId=xxx
     * 返回该学生全部历史作答记录，每条附带对应题目的标准答案。
     */
    @GetMapping("/history")
    public List<AssessmentResult.HistoryItem> history(@RequestParam String studentId) {
        return assessmentService.getHistory(studentId);
    }

    /**
     * 清除历史数据：DELETE /api/assess/clear?studentId=xxx
     * 清除该学生的掌握度 + 做题历史，回到「未测评」状态。
     */
    @DeleteMapping("/clear")
    public Map<String, Object> clear(@RequestParam String studentId) {
        assessmentService.clearData(studentId);
        Map<String, Object> result = new HashMap<>();
        result.put("cleared", true);
        return result;
    }
}
