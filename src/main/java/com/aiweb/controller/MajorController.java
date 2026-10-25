package com.aiweb.controller;

import com.aiweb.entity.College;
import com.aiweb.entity.Major;
import com.aiweb.service.CollegeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 专业查询接口（单独挂 /api/majors，避免和 /api/colleges/{id} 路径冲突）。
 * 位置：src/main/java/com/aiweb/controller/MajorController.java
 */
@RestController
@RequestMapping("/api/majors")
public class MajorController {

    private final CollegeService collegeService;

    public MajorController(CollegeService collegeService) {
        this.collegeService = collegeService;
    }

    /**
     * 按条件查专业，返回时会附带院校名称，前端不用再查一次。
     * 参数：collegeId、category（学科门类）、degreeType（学硕/专硕）、keyword（关键词）
     */
    @GetMapping
    public List<Major> list(@RequestParam(required = false) Long collegeId,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String degreeType,
                            @RequestParam(required = false) String keyword) {
        List<Major> majors = collegeService.listMajors(collegeId, category, degreeType, keyword);
        attachCollegeName(majors);
        return majors;
    }

    /** 所有学科门类，前端做筛选下拉用 */
    @GetMapping("/categories")
    public List<String> categories() {
        return collegeService.listMajors(null, null, null, null).stream()
                .map(Major::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /** 补全院校名称：一次性把涉及的院校查出来，避免逐条查库 */
    private void attachCollegeName(List<Major> majors) {
        if (majors.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>();
        collegeService.listColleges(null, null, null, null, null, null)
                .forEach(c -> nameMap.put(c.getId(), c.getName()));
        majors.forEach(m -> m.setCollegeName(nameMap.get(m.getCollegeId())));
    }
}
