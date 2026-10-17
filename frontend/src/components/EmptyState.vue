<template>
  <!--
    统一空态组件：图标 + 标题 + 描述 + 行动按钮。
    用于「查询无结果 / 还没有数据 / 对话未开始」等场景，保证各页面空态风格一致。
    用法：<EmptyState icon="🔍" title="没有找到相关院校" desc="换个关键词试试">
            <el-button type="primary">去院校库</el-button>
          </EmptyState>
  -->
  <div class="empty-state" role="status">
    <div class="empty-icon" aria-hidden="true">{{ icon }}</div>
    <p class="empty-title">{{ title }}</p>
    <p v-if="desc" class="empty-desc">{{ desc }}</p>
    <div v-if="$slots.default" class="empty-actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
// 空态组件：纯展示，无逻辑。图标用 emoji（跨平台、无额外资源请求）。
defineProps({
  icon: { type: String, default: '📭' },
  title: { type: String, required: true },
  desc: { type: String, default: '' }
})
</script>
