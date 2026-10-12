package com.aiweb.config;

import com.aiweb.entity.College;
import com.aiweb.entity.Major;
import com.aiweb.mapper.CollegeMapper;
import com.aiweb.mapper.MajorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 院校专业数据的自动导入。
 * 位置：src/main/java/com/aiweb/config/CollegeDataInitializer.java
 *
 * 作用：应用启动时读取 data/colleges.json，如果 college 表是空的就自动灌进去。
 * 好处：你不用手动执行任何 SQL，启动即可用。
 * 如果表里已经有数据，则跳过，不会重复插入。
 *
 * 数据来源：项目自己整理的结构化数据（原创 + 公开信息整理），
 * 不含任何需要登录或违反协议的抓取内容。
 */
@Component
public class CollegeDataInitializer implements ApplicationRunner {

    private final CollegeMapper collegeMapper;
    private final MajorMapper majorMapper;
    private final ObjectMapper objectMapper;

    public CollegeDataInitializer(CollegeMapper collegeMapper,
                                  MajorMapper majorMapper,
                                  ObjectMapper objectMapper) {
        this.collegeMapper = collegeMapper;
        this.majorMapper = majorMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = collegeMapper.selectCount(null);
        if (existing > 0) {
            System.out.println("[数据初始化] college 表已有 " + existing + " 条院校，跳过导入。");
            return;
        }

        try (InputStream in = new ClassPathResource("data/colleges.json").getInputStream()) {
            List<CollegeSeed> seeds = objectMapper.readValue(
                    in,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CollegeSeed.class));

            int collegeCount = 0;
            int majorCount = 0;
            for (CollegeSeed seed : seeds) {
                College college = new College();
                college.setCode(seed.code());
                college.setName(seed.name());
                college.setShortName(seed.shortName());
                college.setCity(seed.city());
                college.setLevel(seed.level());
                college.setIs985(nvl(seed.is985()));
                college.setIs211(nvl(seed.is211()));
                college.setIsDoubleFirst(nvl(seed.isDoubleFirst()));
                college.setIsSelfLine(nvl(seed.isSelfLine()));
                college.setWebsite(seed.website());
                college.setVerified(0);
                collegeMapper.insert(college);
                collegeCount++;

                if (seed.majors() != null) {
                    for (MajorSeed ms : seed.majors()) {
                        Major major = new Major();
                        major.setCollegeId(college.getId());
                        major.setCode(ms.code());
                        major.setName(ms.name());
                        major.setDegreeType(ms.degreeType());
                        major.setCategory(ms.category());
                        major.setDiscipline(ms.discipline());
                        major.setDepartment(ms.department());
                        major.setPublicCourses(ms.publicCourses());
                        major.setDuration(ms.duration());
                        major.setIsPopular(1);
                        major.setVerified(0);
                        majorMapper.insert(major);
                        majorCount++;
                    }
                }
            }
            System.out.println("[数据初始化] 已导入 " + collegeCount + " 所院校、" + majorCount + " 个专业。");
            System.out.println("[数据初始化] 提示：数据为初版，字段 verified=0，正式提交前需与研招网逐条核对。");
        } catch (Exception e) {
            System.err.println("[数据初始化] 导入失败：" + e.getMessage());
        }
    }

    private Integer nvl(Integer value) {
        return value == null ? 0 : value;
    }

    /** 与 data/colleges.json 中单个院校对象对应的结构 */
    public record CollegeSeed(String code, String name, String shortName, String city, String level,
                              Integer is985, Integer is211, Integer isDoubleFirst, Integer isSelfLine,
                              String website, List<MajorSeed> majors) {
    }

    /** 与 data/colleges.json 中单个专业对象对应的结构 */
    public record MajorSeed(String code, String name, String degreeType, String category,
                            String discipline, String department, String publicCourses, Integer duration) {
    }
}
