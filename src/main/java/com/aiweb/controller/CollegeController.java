package com.aiweb.controller;

import com.aiweb.entity.College;
import com.aiweb.entity.Major;
import com.aiweb.service.CollegeService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 院校专业库查询接口。
 * 位置：src/main/java/com/aiweb/controller/CollegeController.java
 *
 * 接口一览：
 *   GET /api/colleges              按条件查院校
 *   GET /api/colleges/{id}         查单个院校（含专业列表）
 *   GET /api/majors                按条件查专业
 *   GET /api/colleges/stats        库的整体统计
 *   GET /api/colleges/cities       所有城市（前端做筛选下拉用）
 */
@RestController
@RequestMapping("/api/colleges")
@CrossOrigin(origins = "*")
public class CollegeController {

    private final CollegeService collegeService;

    public CollegeController(CollegeService collegeService) {
        this.collegeService = collegeService;
    }

    @GetMapping
    public List<College> list(@RequestParam(required = false) String city,
                              @RequestParam(required = false) String level,
                              @RequestParam(required = false) Integer is985,
                              @RequestParam(required = false) Integer is211,
                              @RequestParam(required = false) Integer isSelfLine,
                              @RequestParam(required = false) String keyword) {
        return collegeService.listColleges(city, level, is985, is211, isSelfLine, keyword);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return collegeService.stats();
    }

    @GetMapping("/cities")
    public List<String> cities() {
        return collegeService.listColleges(null, null, null, null, null, null)
                .stream()
                .map(College::getCity)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        College college = collegeService.getCollegeDetail(id);
        Map<String, Object> result = new HashMap<>();
        if (college == null) {
            result.put("error", "未找到该院校，id=" + id);
            return result;
        }
        result.put("college", college);
        result.put("majors", college.getMajors());
        return result;
    }

    @GetMapping("/{id}/majors")
    public List<Major> collegeMajors(@PathVariable Long id) {
        return collegeService.listMajors(id, null, null, null);
    }
}
