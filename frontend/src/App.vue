<template>
  <div class="app-shell">
    <!-- 顶部导航 -->
    <header class="app-header">
      <div class="header-inner">
        <router-link to="/" class="brand">
          <span class="brand-mark" aria-hidden="true">研</span>
          <span class="brand-text">智研 · AI 考研备考助手</span>
        </router-link>

        <nav class="nav nav-scroll" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="nav-link"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <!-- 后端连通状态：一眼看出后端有没有启动 -->
        <div class="status" :title="healthy ? '后端服务正常' : '后端未连接，请先启动 Spring Boot 应用'">
          <span class="dot" :class="healthy ? 'ok' : 'bad'" aria-hidden="true"></span>
          <span class="status-text">{{ healthy ? '服务正常' : '后端未连接' }}</span>
        </div>
      </div>
    </header>

    <!-- 主体：路由页面渲染在这里（带轻微淡入过渡，减少切换生硬感） -->
    <main id="main-content" tabindex="-1">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- 回到顶部：长页面（院校库 / 测评 / 规划）滚动后出现（默认监听 window 滚动） -->
    <el-backtop :right="24" :bottom="24" />

    <footer class="app-footer">
      <p>智研 · AI 考研备考助手 · 全国高校 AI Web 开发挑战赛参赛作品</p>
      <p class="muted">
        考试政策、时间节点、招生信息一律以中国研究生招生信息网等官方渠道公告为准
      </p>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkHealth } from '@/api'

const navItems = [
  { to: '/', label: '首页' },
  { to: '/college', label: '院校专业库' },
  { to: '/assess', label: '学情诊断' },
  { to: '/wrongbook', label: '错题本' },
  { to: '/science', label: '认知科普' }
]

const healthy = ref(false)

// 探一次后端；成功转绿，失败保持灰并说明原因
async function probe() {
  try {
    await checkHealth()
    healthy.value = true
    return true
  } catch (e) {
    healthy.value = false
    // 区分两种情况给提示：完全连不上 vs 连上了但数据库有问题（health 返回 503 带说明）
    const reason = e && e.message ? e.message : ''
    if (reason.includes('数据库') || reason.includes('503')) {
      ElMessage({ type: 'warning', message: reason, duration: 8000, showClose: true })
    }
    return false
  }
}

onMounted(async () => {
  await probe()

  // 修复「最小化窗口后状态不恢复」：
  //   场景——用户最小化窗口去启动后端，还原后页面还显示「后端未连接」（后端其实已就绪），
  //   因为健康检查原来只在页面加载时跑一次，之后再也不更新。
  // 修复 1：窗口从最小化/切标签页还原时（visibilitychange → 可见）主动重查；
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && !healthy.value) probe()
  })
  // 修复 2：检查失败时每 5 秒自动重试，后端在用户等待期间就绪也能自己转绿（成功后停止）
  const timer = setInterval(async () => {
    if (healthy.value) {
      clearInterval(timer)
      return
    }
    if (await probe()) clearInterval(timer)
  }, 5000)
})
</script>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.header-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text);
  font-weight: 600;
  flex-shrink: 0;
}

.brand-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.nav {
  display: flex;
  gap: 4px;
  flex: 1;
}

.nav-link {
  padding: 6px 12px;
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 14px;
}

.nav-link:hover {
  background: #f3f4f6;
  color: var(--text);
}

/* vue-router 自动给当前页面的链接加 router-link-active 类 */
.nav-link.router-link-active {
  background: #eff6ff;
  color: var(--brand);
}

.status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #9ca3af;
}

.dot.ok {
  background: #16a34a;
}

.dot.bad {
  background: #dc2626;
}

main {
  flex: 1;
}

.app-footer {
  border-top: 1px solid var(--line);
  background: #fff;
  padding: 20px 16px;
  text-align: center;
}

.app-footer p {
  margin: 0 0 4px;
  font-size: 13px;
}

.app-footer .muted {
  color: var(--text-muted);
  font-size: 12px;
}

/* 窄屏时把状态文字藏起来，省空间；导航改为不换行（配合 .nav-scroll 横向滚动） */
@media (max-width: 720px) {
  .brand-text {
    display: none;
  }

  .status-text {
    display: none;
  }

  .nav {
    gap: 0;
    flex-wrap: nowrap;
  }

  .nav-link {
    padding: 6px 8px;
    font-size: 13px;
    white-space: nowrap;
    flex-shrink: 0;
  }
}
</style>
