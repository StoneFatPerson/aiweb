import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

// 路由表
// 除首页外都用「懒加载」：只有用户点到那个页面时才下载对应代码，首屏更快
const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView,
    meta: { title: '首页' }
  },
  {
    path: '/college',
    name: 'college',
    component: () => import('@/views/CollegeView.vue'),
    meta: { title: '院校专业库' }
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { title: 'AI 问答' }
  },
  {
    path: '/science',
    name: 'science',
    component: () => import('@/views/ScienceView.vue'),
    meta: { title: '认知科普' }
  },
  {
    path: '/plan',
    name: 'plan',
    component: () => import('@/views/PlanView.vue'),
    meta: { title: '备考计划' }
  },
  {
    path: '/assess',
    name: 'assess',
    component: () => import('@/views/AssessmentView.vue'),
    meta: { title: '学情诊断' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

// 切换页面时同步更新浏览器标签标题（也算一种无障碍友好）
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 智研` : '智研 · AI 考研备考助手'
})

export default router
