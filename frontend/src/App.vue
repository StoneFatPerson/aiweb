<template>
  <div class="app-shell">
    <!-- 顶部导航 -->
    <header class="app-header">
      <div class="header-inner">
        <router-link to="/" class="brand">
          <span class="brand-mark" aria-hidden="true">研</span>
          <span class="brand-text">智研 · AI 考研备考助手</span>
        </router-link>

        <nav class="nav" aria-label="主导航">
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
        <div class="status" :title="health ? '后端服务正常' : '后端未连接，请先启动 Spring Boot 应用'">
          <span class="dot" :class="healthy ? 'ok' : 'bad'" aria-hidden="true"></span>
          <span class="status-text">{{ healthy ? '服务正常' : '后端未连接' }}</span>
        </div>
      </div>
    </header>

    <!-- 主体：路由页面渲染在这里 -->
    <main id="main-content" tabindex="-1">
      <router-view />
    </main>

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
import { checkHealth } from '@/api'

const navItems = [
  { to: '/', label: '首页' },
  { to: '/college', label: '院校专业库' },
  { to: '/assess', label: '学情诊断' },
  { to: '/plan', label: '备考计划' },
  { to: '/science', label: '认知科普' },
  { to: '/chat', label: 'AI 问答' }
]

const healthy = ref(false)

// 页面加载时探一次后端，失败就显示「后端未连接」
onMounted(async () => {
  try {
    await checkHealth()
    healthy.value = true
  } catch (e) {
    healthy.value = false
  }
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

/* 窄屏时把状态文字藏起来，省空间 */
@media (max-width: 720px) {
  .brand-text {
    display: none;
  }

  .status-text {
    display: none;
  }

  .nav {
    gap: 0;
  }

  .nav-link {
    padding: 6px 8px;
    font-size: 13px;
  }
}
</style>
