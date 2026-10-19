package com.aiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 错题本条目。
 * 位置：src/main/java/com/aiweb/entity/WrongQuestion.java
 *
 * 两条来源：
 *   SYSTEM —— 学情诊断提交时判错，由 AssessmentService 自动记录（question_code 关联题库）；
 *   MANUAL —— 用户自己导入（题干与答案自带，不一定在题库里）。
 */
@TableName("wrong_question")
public class WrongQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String studentId;

    /** 学科：math / english / politics */
    private String subject;

    /** 来源：SYSTEM / MANUAL */
    private String source;

    /** 题库题号（SYSTEM 必有；MANUAL 可空） */
    private String questionCode;

    /** 题干（MANUAL 导入时必填；SYSTEM 记录时也冗余一份，避免题库变动后丢失上下文） */
    private String stem;

    /** 当时的错误答案 */
    private String userAnswer;

    /** 正确答案 */
    private String correctAnswer;

    /** 用户备注（错因 / 心得） */
    private String note;

    /** 重做次数 */
    private Integer redoCount;

    /** 最近一次重做是否答对：null 未重做 / 0 错 / 1 对 */
    private Integer lastRedoCorrect;

    private LocalDateTime createdAt;

    // ===================== getter / setter =====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getQuestionCode() { return questionCode; }
    public void setQuestionCode(String questionCode) { this.questionCode = questionCode; }

    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }

    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getRedoCount() { return redoCount; }
    public void setRedoCount(Integer redoCount) { this.redoCount = redoCount; }

    public Integer getLastRedoCorrect() { return lastRedoCorrect; }
    public void setLastRedoCorrect(Integer lastRedoCorrect) { this.lastRedoCorrect = lastRedoCorrect; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
