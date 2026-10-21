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
import { onMounted, onScopeDispose, ref } from 'vue'
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

// 探一次后端。
// notify=true 时才弹提示（仅页面首次加载用，数据库有问题必须让用户知道）；
// 自动探活（窗口还原 / 定时重试）一律静默——只更新右上角圆点。
// 上一版在这里无条件弹窗，导致「还原窗口 → 警告框自动弹出」，是 bug 本身的一部分。
let probing = false
async function probe({ notify = false } = {}) {
  if (probing) return healthy.value // 在途去重：visibilitychange 与定时器可能同时触发
  probing = true
  try {
    await checkHealth()
    healthy.value = true
    return true
  } catch (e) {
    healthy.value = false
    const reason = (e && e.message) || ''
    if (notify && (reason.includes('数据库') || reason.includes('503'))) {
      ElMessage({ type: 'warning', message: reason, duration: 8000, showClose: true })
    }
    return false
  } finally {
    probing = false
  }
}

// 窗口可见性监听与重试定时器的句柄（提升到 setup 作用域，供同步注册的清理函数使用）
let retryTimer = null
const onVisibility = () => {
  if (document.visibilityState === 'visible' && !healthy.value) probe()
}

// 卸载时清理监听器与定时器，杜绝泄漏。
// 注意：必须在 setup 同步区注册——放在 onMounted 的 async 回调里会脱离 effect scope，
// Vue 会警告「onScopeDispose() is called when there is no active effect scope」。
onScopeDispose(() => {
  if (retryTimer) clearInterval(retryTimer)
  document.removeEventListener('visibilitychange', onVisibility)
})

onMounted(async () => {
  // 首次加载：后端/数据库有问题要明确告知
  await probe({ notify: true })

  // 「最小化窗口后状态不恢复」修复：
  //  场景——用户最小化窗口去启动后端，还原后页面还显示「后端未连接」（后端其实已就绪）。
  //  机制一：窗口从最小化/切标签页还原时（visibilitychange → visible）主动重查，静默；
  document.addEventListener('visibilitychange', onVisibility)

  //  机制二：检查失败时有限次重试（36 次 × 5 秒 ≈ 3 分钟，覆盖「启动后端+加载模型+建向量库」全程），
  //  后端就绪后自动转绿并停止；永不无限轮询（上一版会一直转到天荒地老）。
  let retries = 0
  retryTimer = setInterval(async () => {
    if (healthy.value || ++retries > 36) {
      clearInterval(retryTimer)
      retryTimer = null
      return
    }
    if (await probe()) {
      clearInterval(retryTimer)
      retryTimer = null
    }
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
