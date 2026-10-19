package com.aiweb.controller;

import com.aiweb.service.WrongQuestionService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 错题本接口。
 * 位置：src/main/java/com/aiweb/controller/WrongQuestionController.java
 *
 * 接口一览：
 *   GET    /api/wrongbook          错题列表（?studentId=&subject= 可选）
 *   GET    /api/wrongbook/stats    统计（?studentId=）
 *   POST   /api/wrongbook/redo     随机抽题进入重做（{studentId, subject?, count?}）
 *   POST   /api/wrongbook/grade    重做判分（{wrongId, answer}），答对自动移出错题本
 *   POST   /api/wrongbook/import   用户导入错题（{studentId, subject, stem, ...}）
 *   DELETE /api/wrongbook/{id}     移除一条错题（?studentId=）
 */
@RestController
@RequestMapping("/api/wrongbook")
@CrossOrigin(origins = "*")
public class WrongQuestionController {

    private final WrongQuestionService service;

    public WrongQuestionController(WrongQuestionService service) {
        this.service = service;
    }

    /** 错题列表 */
    @GetMapping
    public List<com.aiweb.entity.WrongQuestion> list(
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String subject) {
        return service.list(studentId, subject);
    }

    /** 统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) String studentId) {
        return service.stats(studentId);
    }

    /** 随机抽题（重做模式）：返回不带答案的题目 */
    @PostMapping("/redo")
    public List<WrongQuestionService.RedoItem> redo(@RequestBody Map<String, Object> body) {
        String studentId = str(body.get("studentId"));
        String subject = str(body.get("subject"));
        int count = body.get("count") instanceof Number n ? n.intValue() : 0;
        return service.randomRedo(studentId, subject, count);
    }

    /** 重做判分：答对即移除 */
    @PostMapping("/grade")
    public WrongQuestionService.RedoResult grade(@RequestBody Map<String, Object> body) {
        Long wrongId = body.get("wrongId") instanceof Number n ? n.longValue() : null;
        String studentId = str(body.get("studentId"));
        String answer = str(body.get("answer"));
        return service.gradeRedo(wrongId, studentId, answer);
    }

    /**
     * 批量重做判分：一次提交一整轮（交互与学情诊断「提交并诊断」一致）。
     * body：{"studentId":"xxx", "submissions":[{"wrongId":1,"answer":"B"}, ...]}
     */
    @PostMapping("/grade-batch")
    public List<WrongQuestionService.RedoResult> gradeBatch(@RequestBody Map<String, Object> body) {
        String studentId = str(body.get("studentId"));
        List<WrongQuestionService.RedoSubmission> submissions = new ArrayList<>();
        if (body.get("submissions") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Long wrongId = m.get("wrongId") instanceof Number n ? n.longValue() : null;
                    submissions.add(new WrongQuestionService.RedoSubmission(wrongId, str(m.get("answer"))));
                }
            }
        }
        return service.gradeBatch(studentId, submissions);
    }

    /** 用户导入错题 */
    @PostMapping("/import")
    public com.aiweb.entity.WrongQuestion importWrong(@RequestBody Map<String, Object> body) {
        return service.importManual(
                str(body.get("studentId")),
                str(body.get("subject")),
                str(body.get("stem")),
                str(body.get("userAnswer")),
                str(body.get("correctAnswer")),
                str(body.get("note")));
    }

    /** 移除一条错题 */
    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable Long id,
                                      @RequestParam(required = false) String studentId) {
        boolean ok = service.remove(id, studentId);
        return Map.of("success", ok);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
