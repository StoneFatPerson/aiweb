package com.aiweb.agent;

import com.aiweb.dto.AssessmentResult;
import com.aiweb.entity.College;
import com.aiweb.entity.Major;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 编排的共享上下文：贯穿一次运行的「意图 + 各工具产出」。
 * 位置：src/main/java/com/aiweb/agent/AgentContext.java
 * 作用：IntentTool 先分析需求、决定要跑哪些工具；后续工具把结果写回这里；
 *       最后 Orchestrator 用这里的全部信息合成答案。这样每一步都可被透明展示。
 */
public class AgentContext {

    private final String goal;
    private final String studentId;

    // 意图判断结果（由 IntentTool 填写）
    private String intentSummary = "";
    private boolean runCollege;
    private boolean runScience;
    private boolean runDiagnosis;

    // 院校/专业检索产出
    private final List<College> colleges = new ArrayList<>();
    private final List<Major> majors = new ArrayList<>();
    private String collegeNote = "";

    // 科普检索产出
    private String scienceAnswer = "";
    private final List<String> scienceSources = new ArrayList<>();

    // 学情诊断与计划产出
    private AssessmentResult.DiagnosisView diagnosis; // 可能为 null
    private String planSummary = "";

    public AgentContext(String goal, String studentId) {
        this.goal = goal == null ? "" : goal;
        this.studentId = studentId == null ? "" : studentId;
    }

    public String getGoal() { return goal; }
    public String getStudentId() { return studentId; }

    public String getIntentSummary() { return intentSummary; }
    public void setIntentSummary(String s) { this.intentSummary = s; }

    public boolean isRunCollege() { return runCollege; }
    public void setRunCollege(boolean b) { this.runCollege = b; }
    public boolean isRunScience() { return runScience; }
    public void setRunScience(boolean b) { this.runScience = b; }
    public boolean isRunDiagnosis() { return runDiagnosis; }
    public void setRunDiagnosis(boolean b) { this.runDiagnosis = b; }

    public List<College> getColleges() { return colleges; }
    public List<Major> getMajors() { return majors; }
    public String getCollegeNote() { return collegeNote; }
    public void setCollegeNote(String s) { this.collegeNote = s; }

    public String getScienceAnswer() { return scienceAnswer; }
    public void setScienceAnswer(String s) { this.scienceAnswer = s; }
    public List<String> getScienceSources() { return scienceSources; }

    public AssessmentResult.DiagnosisView getDiagnosis() { return diagnosis; }
    public void setDiagnosis(AssessmentResult.DiagnosisView d) { this.diagnosis = d; }
    public String getPlanSummary() { return planSummary; }
    public void setPlanSummary(String s) { this.planSummary = s; }

    /** 是否已做过测评（有诊断数据且 assessed=true） */
    public boolean hasAssessedDiagnosis() {
        return diagnosis != null && diagnosis.assessed();
    }
}
