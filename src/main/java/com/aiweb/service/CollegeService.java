package com.aiweb.service;

import com.aiweb.entity.College;
import com.aiweb.entity.Major;
import com.aiweb.mapper.CollegeMapper;
import com.aiweb.mapper.MajorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 院校专业库的查询逻辑。
 * 位置：src/main/java/com/aiweb/service/CollegeService.java
 * 作用：按城市、层次、学科门类等条件筛选院校与专业。
 *
 * 设计说明：这里全部是精确的数据库查询，不经过大模型。
 * 院校、专业、科目这类事实型数据交给模型自由生成会出错，
 * 所以采用「数据库存事实 + 模型只负责组织语言」的方式。
 */
@Service
public class CollegeService {

    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;

    public CollegeService(CollegeMapper collegeMapper, MajorMapper majorMapper) {
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
    }

    /** 按条件查院校 */
    public List<College> listColleges(String city, String level, Integer is985, Integer is211,
                                      Integer isSelfLine, String keyword) {
        LambdaQueryWrapper<College> query = new LambdaQueryWrapper<>();

        if (hasText(city)) {
            query.eq(College::getCity, city);
        }
        if (hasText(level)) {
            query.eq(College::getLevel, level);
        }
        if (is985 != null && is985 == 1) {
            query.eq(College::getIs985, 1);
        }
        if (is211 != null && is211 == 1) {
            query.eq(College::getIs211, 1);
        }
        if (isSelfLine != null && isSelfLine == 1) {
            query.eq(College::getIsSelfLine, 1);
        }
        if (hasText(keyword)) {
            // 同时匹配全称和简称，方便输入「山大」「青大」这类简称
            query.and(w -> w.like(College::getName, keyword)
                    .or()
                    .like(College::getShortName, keyword));
        }
        query.orderByAsc(College::getId);
        return collegeMapper.selectList(query);
    }

    /** 查单个院校，并把它的专业一起带出来 */
    public College getCollegeDetail(Long id) {
        College college = collegeMapper.selectById(id);
        if (college == null) {
            return null;
        }
        LambdaQueryWrapper<Major> query = new LambdaQueryWrapper<Major>()
                .eq(Major::getCollegeId, id)
                .orderByAsc(Major::getId);
        college.setMajors(majorMapper.selectList(query));
        return college;
    }

    /** 按条件查专业 */
    public List<Major> listMajors(Long collegeId, String category, String degreeType, String keyword) {
        LambdaQueryWrapper<Major> query = new LambdaQueryWrapper<>();
        if (collegeId != null) {
            query.eq(Major::getCollegeId, collegeId);
        }
        if (hasText(category)) {
            query.eq(Major::getCategory, category);
        }
        if (hasText(degreeType)) {
            query.eq(Major::getDegreeType, degreeType);
        }
        if (hasText(keyword)) {
            query.and(w -> w.like(Major::getName, keyword)
                    .or()
                    .like(Major::getDiscipline, keyword));
        }
        query.orderByAsc(Major::getId);
        return majorMapper.selectList(query);
    }

    /** 库的整体统计，用于首页展示和自检 */
    public Map<String, Object> stats() {
        Map<String, Object> result = new HashMap<>();
        result.put("collegeCount", collegeMapper.selectCount(null));
        result.put("majorCount", majorMapper.selectCount(null));
        result.put("cityCount", collegeMapper.selectList(null).stream()
                .map(College::getCity).filter(this::hasText).distinct().count());
        result.put("selfLineCount", collegeMapper.selectCount(
                new LambdaQueryWrapper<College>().eq(College::getIsSelfLine, 1)));
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
