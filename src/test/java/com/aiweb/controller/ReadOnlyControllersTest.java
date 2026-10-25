package com.aiweb.controller;

import com.aiweb.service.ChatService;
import com.aiweb.service.CollegeService;
import com.aiweb.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 其余只读 Controller 的入口冒烟测试（standalone：不启动上下文、不连库、不调模型）。
 * 位置：src/test/java/com/aiweb/controller/ReadOnlyControllersTest.java
 *
 * 目的：把 College / Major / Science / Chat 四个 Controller 的 HTTP 入口覆盖率从 0 补起来，
 * 验证路由映射、参数绑定、默认值处理这些「不改业务逻辑却最容易写错」的部分。
 * Service 全部用 mock 替换，断言只看 HTTP 200 与路由是否通。
 */
class ReadOnlyControllersTest {

    private MockMvc mockMvc;
    private CollegeService collegeService;
    private RagService ragService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        collegeService = mock(CollegeService.class);
        ragService = mock(RagService.class);
        chatService = mock(ChatService.class);

        // 空结果即可：只验证入口能正常返回，不关心数据内容
        when(collegeService.listColleges(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(collegeService.listMajors(any(), any(), any(), any())).thenReturn(List.of());
        when(collegeService.stats()).thenReturn(Map.of());
        when(collegeService.getCollegeDetail(anyLong())).thenReturn(null);
        when(collegeService.countColleges()).thenReturn(0L);
        when(ragService.topics()).thenReturn(List.of());
        when(chatService.history(anyString())).thenReturn(List.of());

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CollegeController(collegeService),
                        new MajorController(collegeService),
                        new ScienceController(ragService),
                        new ChatController(chatService, collegeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/colleges 院校列表")
    void colleges_list() throws Exception {
        mockMvc.perform(get("/api/colleges")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/colleges/stats 院校统计")
    void colleges_stats() throws Exception {
        mockMvc.perform(get("/api/colleges/stats")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/colleges/cities 城市列表")
    void colleges_cities() throws Exception {
        mockMvc.perform(get("/api/colleges/cities")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/colleges/{id} 院校详情（未找到也返回 200 + error 字段）")
    void colleges_detail() throws Exception {
        mockMvc.perform(get("/api/colleges/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/colleges/{id}/majors 院校专业")
    void colleges_majors() throws Exception {
        mockMvc.perform(get("/api/colleges/1/majors")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/majors 专业列表")
    void majors_list() throws Exception {
        mockMvc.perform(get("/api/majors")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/majors/categories 学科门类")
    void majors_categories() throws Exception {
        mockMvc.perform(get("/api/majors/categories")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/science/topics 科普主题")
    void science_topics() throws Exception {
        mockMvc.perform(get("/api/science/topics")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/health 健康检查（含数据库探测）")
    void health_ok() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/history 历史消息")
    void history_list() throws Exception {
        mockMvc.perform(get("/api/history").param("sessionId", "s1")).andExpect(status().isOk());
    }
}
