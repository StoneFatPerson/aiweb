package com.aiweb.controller;

import com.aiweb.dto.AssessmentStartResponse;
import com.aiweb.service.AssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AssessmentController 的 Web 层测试（standalone 模式：不启动 Spring 上下文、不连数据库、不调大模型）。
 * 位置：src/test/java/com/aiweb/controller/AssessmentControllerTest.java
 *
 * 为什么用 standaloneSetup 而不是 @WebMvcTest：
 *   本项目用了 @MapperScan，@WebMvcTest 切片虽排除 MyBatis 自动配置，
 *   但 Mapper bean 仍会被创建（缺 sqlSessionFactory 直接上下文启动失败）。
 *   standaloneSetup 只把 Controller + 全局异常处理装配进来，干净、秒级、零环境依赖，
 *   正适合验证「参数默认值 / 缺参 400 / 业务异常 400」这些入口行为。
 *   CORS 白名单则在服务启动后用真实 HTTP 请求验证（见回归脚本）。
 */
class AssessmentControllerTest {

    private MockMvc mockMvc;
    private AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        assessmentService = mock(AssessmentService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AssessmentController(assessmentService))
                // 装配全局异常处理，才能验证 IllegalArgumentException -> 400 的映射
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /stats 返回 200 与统计内容")
    void stats_returns200() throws Exception {
        given(assessmentService.stats()).willReturn(Map.of("total", 116));
        mockMvc.perform(get("/api/assess/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(116));
    }

    @Test
    @DisplayName("GET /points 不传 subject 时默认 math")
    void points_defaultsToMath() throws Exception {
        given(assessmentService.allPoints("math")).willReturn(List.of());
        mockMvc.perform(get("/api/assess/points"))
                .andExpect(status().isOk());
        verify(assessmentService).allPoints("math");
    }

    @Test
    @DisplayName("GET /points 正确透传 subject 参数")
    void points_respectsSubjectParam() throws Exception {
        given(assessmentService.allPoints("english")).willReturn(List.of());
        mockMvc.perform(get("/api/assess/points").param("subject", "english"))
                .andExpect(status().isOk());
        verify(assessmentService).allPoints("english");
    }

    @Test
    @DisplayName("GET /diagnosis 缺少 studentId 时返回 400 而非 500")
    void diagnosis_missingStudentId_returns400() throws Exception {
        mockMvc.perform(get("/api/assess/diagnosis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error", containsString("studentId")));
    }

    @Test
    @DisplayName("POST /start 缺省 count 与 subject 时套用默认值（10 / math）")
    void start_appliesDefaultCountAndSubject() throws Exception {
        given(assessmentService.start(anyString(), anyString(), any(), anyInt()))
                .willReturn(new AssessmentStartResponse("sess-1", 0, 0, List.of()));
        mockMvc.perform(post("/api/assess/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"s1\"}"))
                .andExpect(status().isOk());
        verify(assessmentService).start(eq("s1"), eq("math"), isNull(), eq(10));
    }

    @Test
    @DisplayName("业务异常 IllegalArgumentException 映射为 400 且带可读消息")
    void businessError_mapsTo400() throws Exception {
        given(assessmentService.allPoints(anyString()))
                .willThrow(new IllegalArgumentException("学科不存在"));
        mockMvc.perform(get("/api/assess/points").param("subject", "xxx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("学科不存在"));
    }
}
