<template>
  <div class="page-container">
    <section class="panel">
      <h1 class="section-title">院校专业库</h1>
      <p class="section-desc">
        收录山东地区为主的考研院校与热门专业。数据为结构化整理，
        <strong>不含分数线</strong>（每年变动，且容易产生误导），请以研招网官方公告为准。
      </p>

      <!-- 筛选条件 -->
      <el-form :inline="true" :model="filters" class="filter-form" label-width="auto">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="院校名或简称"
            clearable
            style="width: 180px"
            @keyup.enter="loadColleges"
          />
        </el-form-item>

        <el-form-item label="城市">
          <el-select v-model="filters.city" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="c in cities" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>

        <el-form-item label="层次">
          <el-select v-model="filters.level" placeholder="全部" clearable style="width: 120px">
            <el-option label="985" value="985" />
            <el-option label="211" value="211" />
            <el-option label="省属重点" value="省属重点" />
            <el-option label="省属" value="省属" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="filters.isSelfLine" :true-label="1" :false-label="null">
            仅自主划线
          </el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadColleges">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 结果列表 -->
    <section class="panel">
      <div class="result-head">
        <span>共 {{ colleges.length }} 所院校</span>
        <span v-if="error" class="error-text">{{ error }}</span>
      </div>

      <el-table
        v-loading="loading"
        :data="colleges"
        border
        stripe
        style="width: 100%"
        empty-text="没有符合条件的院校"
      >
        <el-table-column prop="name" label="院校" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.name }}</el-link>
            <div v-if="row.shortName" class="sub-text">{{ row.shortName }}</div>
          </template>
        </el-table-column>

        <el-table-column prop="city" label="城市" width="80" />

        <el-table-column label="层次" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.is985" type="danger" size="small" disable-transitions>985</el-tag>
            <el-tag v-else-if="row.is211" type="warning" size="small" disable-transitions>211</el-tag>
            <el-tag v-else type="info" size="small" disable-transitions>{{ row.level || '—' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="标识" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.isDoubleFirst" size="small" disable-transitions>双一流</el-tag>
            <el-tag v-if="row.isSelfLine" type="success" size="small" disable-transitions>
              自主划线
            </el-tag>
            <span v-if="!row.isDoubleFirst && !row.isSelfLine" class="sub-text">—</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看专业</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 专业详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="currentCollege ? currentCollege.name : '院校详情'"
      size="55%"
    >
      <template v-if="currentCollege">
        <el-descriptions :column="2" border size="small" class="detail-desc">
          <el-descriptions-item label="院校代码">
            {{ currentCollege.code || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="所在城市">
            {{ currentCollege.city || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="层次">
            {{ currentCollege.level || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="官网">
            <el-link v-if="currentCollege.website" :href="currentCollege.website" target="_blank">
              {{ currentCollege.website }}
            </el-link>
            <span v-else>—</span>
          </el-descriptions-item>
        </el-descriptions>

        <h3 class="detail-title">热门考研专业（{{ majors.length }} 个）</h3>

        <el-table :data="majors" border size="small" empty-text="暂无专业数据">
          <el-table-column prop="code" label="代码" width="90" />
          <el-table-column prop="name" label="专业" min-width="140" />
          <el-table-column prop="degreeType" label="类型" width="70" />
          <el-table-column prop="publicCourses" label="公共课" min-width="150" />
          <el-table-column prop="duration" label="学制" width="60" />
        </el-table>

        <p class="tip-text">
          标注说明：以上为结构化整理信息，具体考试科目与招生名额以目标院校当年招生简章为准。
        </p>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCities, getCollege, listColleges } from '@/api'

const colleges = ref([])
const cities = ref([])
const majors = ref([])
const loading = ref(false)
const error = ref('')

const detailVisible = ref(false)
const currentCollege = ref(null)

const filters = reactive({
  keyword: '',
  city: '',
  level: '',
  isSelfLine: null
})

const buildParams = () => {
  const params = {}
  if (filters.keyword) params.keyword = filters.keyword
  if (filters.city) params.city = filters.city
  if (filters.level) params.level = filters.level
  if (filters.isSelfLine) params.isSelfLine = 1
  return params
}

const loadColleges = async () => {
  loading.value = true
  error.value = ''
  try {
    colleges.value = await listColleges(buildParams())
  } catch (e) {
    error.value = e.message
    colleges.value = []
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.keyword = ''
  filters.city = ''
  filters.level = ''
  filters.isSelfLine = null
  loadColleges()
}

const openDetail = async (row) => {
  currentCollege.value = row
  majors.value = []
  detailVisible.value = true
  try {
    const data = await getCollege(row.id)
    majors.value = (data && data.majors) || []
  } catch (e) {
    ElMessage.error('加载专业失败：' + e.message)
  }
}

onMounted(async () => {
  await loadColleges()
  try {
    cities.value = await getCities()
  } catch (e) {
    cities.value = []
  }
})
</script>

<style scoped>
.filter-form {
  margin-top: 8px;
}

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-muted);
}

.error-text {
  color: #dc2626;
}

.sub-text {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-muted);
}

.detail-desc {
  margin-bottom: 20px;
}

.detail-title {
  margin: 0 0 12px;
  font-size: 15px;
}

.tip-text {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
