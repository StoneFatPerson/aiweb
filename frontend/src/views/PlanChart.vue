<template>
  <div class="plan-chart">
    <svg :viewBox="`0 0 ${W} ${H}`" class="chart-svg" preserveAspectRatio="xMidYMid meet">
      <defs>
        <linearGradient
          v-for="(c, i) in colors"
          :key="'g' + i"
          :id="`grad${i}`"
          x1="0" y1="0" x2="1" y2="0"
        >
          <stop offset="0%" :stop-color="c[0]" />
          <stop offset="100%" :stop-color="c[1]" />
        </linearGradient>
      </defs>

      <!-- 周坐标轴 -->
      <line :x1="padL" :y1="axisY" :x2="W - padR" :y2="axisY" class="axis" />
      <text
        v-for="w in totalWeeks"
        :key="'t' + w"
        :x="weekX(w)"
        :y="axisY + 16"
        class="tick"
      >{{ w }}</text>
      <text :x="padL" :y="axisY - 8" class="axis-label">备考周次 →</text>

      <!-- 三阶段色条 -->
      <g v-for="(p, i) in laid" :key="p.name">
        <rect
          :x="weekX(p.start)"
          :y="barY(i)"
          :width="Math.max(weekW * (p.end - p.start + 1), weekW)"
          :height="barH"
          :rx="9"
          :fill="`url(#grad${i})`"
          class="bar"
        />
        <text :x="weekX(p.start) + 12" :y="barY(i) + barH / 2 - 3" class="bar-name">
          {{ p.name }}
        </text>
        <text :x="weekX(p.start) + 12" :y="barY(i) + barH / 2 + 14" class="bar-weeks">
          {{ p.weeks }}
        </text>
        <!-- 阶段重点，作为副标题，降低文字冗余 -->
        <text :x="padL" :y="barY(i) + barH + 16" class="focus">
          {{ i + 1 }}. {{ p.focus }}
        </text>
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 二维备考计划甘特图（纯 SVG，无额外依赖）。
 * 把「基础 / 强化 / 冲刺」三阶段按周次渲染成彩色横向色条，
 * 比原来的时间线+标签列表更直观、信息更精简。
 * 位置：frontend/src/views/PlanChart.vue
 */
const props = defineProps({
  phases: { type: Array, default: () => [] }
})

const W = 760
const padL = 96
const padR = 18
const axisTop = 30
const barH = 42
const rowGap = 70

// 三阶段配色（浅→深渐变，视觉更柔和）
const colors = [
  ['#7cb0ff', '#3b82f6'], // 基础：蓝
  ['#c9a0ff', '#a855f7'], // 强化：紫
  ['#5fe0b0', '#10b981']  // 冲刺：绿
]

function parseWeeks(w) {
  if (!w) return [1, 1]
  const m = String(w).match(/(\d+)\s*[-~]\s*(\d+)/)
  if (m) return [parseInt(m[1], 10), parseInt(m[2], 10)]
  const s = String(w).match(/(\d+)/)
  if (s) return [parseInt(s[1], 10), parseInt(s[1], 10)]
  return [1, 1]
}

const laid = computed(() => (props.phases || []).map((p) => {
  const [s, e] = parseWeeks(p.weeks)
  return { name: p.name, weeks: p.weeks, focus: p.focus, start: s, end: e }
}))

const totalWeeks = computed(() => laid.value.reduce((m, p) => Math.max(m, p.end), 1))
const chartW = computed(() => W - padL - padR)
const weekW = computed(() => chartW.value / (totalWeeks.value || 1))
const H = computed(() => axisTop + laid.value.length * rowGap + 8)

const axisY = axisTop - 8
function weekX(w) { return padL + (w - 1) * weekW.value }
function barY(i) { return axisTop + i * rowGap }
</script>

<style scoped>
.plan-chart {
  width: 100%;
  background: var(--bg-soft, #f8fafc);
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 12px;
  padding: 12px 8px 6px;
}
.chart-svg {
  width: 100%;
  height: auto;
  display: block;
}
.axis {
  stroke: var(--text-muted, #94a3b8);
  stroke-width: 1;
}
.axis-label {
  fill: var(--text-muted, #94a3b8);
  font-size: 11px;
}
.tick {
  fill: var(--text-muted, #94a3b8);
  font-size: 11px;
  text-anchor: middle;
}
.bar {
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.08));
}
.bar-name {
  fill: #fff;
  font-size: 14px;
  font-weight: 600;
}
.bar-weeks {
  fill: rgba(255, 255, 255, 0.92);
  font-size: 11px;
}
.focus {
  fill: var(--text-secondary, #475569);
  font-size: 12px;
}
</style>
