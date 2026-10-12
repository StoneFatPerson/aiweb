<template>
  <div class="page-container">
    <section class="panel">
      <h1 class="section-title">备考计划</h1>
      <p class="section-desc">
        按考试倒计时切分基础 / 强化 / 冲刺三阶段，并根据你的实际完成情况动态调整。
      </p>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="功能开发中"
        description="计划引擎依赖「学情诊断」输出的掌握度数据，目前正在实现中。完成后你会在这里看到：三阶段时间轴、每周任务清单、每日时长分配，以及根据进度勾选自动重算的后续计划。"
        class="dev-alert"
      />

      <!-- 先展示交互形态，让用户提前看到产品长什么样 -->
      <div class="preview">
        <h3 class="preview-title">计划长什么样（示意）</h3>

        <el-steps :active="1" finish-status="success" align-center class="preview-steps">
          <el-step title="基础阶段" description="约占总天数 50%" />
          <el-step title="强化阶段" description="约占总天数 30%" />
          <el-step title="冲刺阶段" description="约占总天数 20%" />
        </el-steps>

        <el-table :data="previewRows" border size="small" class="preview-table">
          <el-table-column prop="subject" label="科目" width="120" />
          <el-table-column prop="weight" label="时间占比" width="100" />
          <el-table-column prop="base" label="基础阶段" />
          <el-table-column prop="strong" label="强化阶段" />
          <el-table-column prop="sprint" label="冲刺阶段" />
        </el-table>

        <p class="tip-text">
          说明：上表仅为交互示意，实际占比会由你的掌握度数据动态计算——薄弱科目会自动分到更多时间。
        </p>
      </div>
    </section>
  </div>
</template>

<script setup>
const previewRows = [
  {
    subject: '数学',
    weight: '40%',
    base: '过一遍教材 + 基础题',
    strong: '题型专项 + 真题分章',
    sprint: '整套真题 + 错题回顾'
  },
  {
    subject: '专业课',
    weight: '30%',
    base: '通读指定教材',
    strong: '按章节背诵与练习',
    sprint: '真题与重点复述'
  },
  {
    subject: '英语',
    weight: '20%',
    base: '词汇 + 长难句',
    strong: '阅读专项',
    sprint: '作文模板 + 模考'
  },
  {
    subject: '政治',
    weight: '10%',
    base: '暂不启动',
    strong: '选择题知识点',
    sprint: '背诵 + 时政'
  }
]
</script>

<style scoped>
.dev-alert {
  margin-bottom: 24px;
}

.preview-title {
  margin: 0 0 16px;
  font-size: 15px;
}

.preview-steps {
  margin-bottom: 24px;
}

.preview-table {
  width: 100%;
}

.tip-text {
  margin-top: 14px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
