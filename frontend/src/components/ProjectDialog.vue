<template>
  <el-dialog
    v-model="dialogVisible"
    :title="mode === 'create' ? '新建项目' : '打开项目'"
    width="600px"
    @close="handleClose"
  >
    <!-- 新建项目模式 -->
    <div v-if="mode === 'create'">
      <el-form :model="form" label-width="100px" label-position="left">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入项目描述（可选）"
          />
        </el-form-item>
        <el-form-item label="坐标系">
          <el-select v-model="form.crs" placeholder="请选择坐标系">
            <el-option label="WGS84 / Pseudo-Mercator (EPSG:3857)" value="EPSG:3857" />
            <el-option label="WGS84 (EPSG:4326)" value="EPSG:4326" />
            <el-option label="CGCS2000 (EPSG:4490)" value="EPSG:4490" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <!-- 打开项目模式 -->
    <div v-else>
      <el-table :data="projects" style="width: 100%" max-height="400" v-loading="loading">
        <el-table-column prop="name" label="项目名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="annotationCount" label="标注数量" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button
              type="primary"
              size="small"
              @click="handleOpen(scope.row)"
            >
              打开
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm" v-if="mode === 'create'">
          创建
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
const BUILD_VERSION = '20260401-1930'
console.log('[ProjectDialog] 当前版本:', BUILD_VERSION)
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  modelValue: Boolean,
  mode: {
    type: String,
    default: 'create'
  },
  currentProject: Object
})

const emit = defineEmits(['update:modelValue', 'create', 'open', 'close'])

const userStore = useUserStore()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const form = ref({
  name: '',
  description: '',
  crs: 'EPSG:3857'
})

const projects = ref([])
const loading = ref(false)

// 监听对话框打开
watch(dialogVisible, async (val) => {
  if (val && props.mode === 'open') {
    await loadProjects()
  } else if (val && props.mode === 'create') {
    // 重置表单
    form.value = {
      name: '',
      description: '',
      crs: 'EPSG:3857'
    }
  }
})

// 加载项目列表
const loadProjects = async () => {
  loading.value = true
  try {
    const token = localStorage.getItem('token')
    console.log('[Load Projects] token:', token ? token.substring(0, 20) + '...' : 'null')
    const response = await fetch('/api/projects', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })

    console.log('[Load Projects] response status:', response.status)

    const result = await response.json()

    if (result.code === 200) {
      projects.value = result.data || []
    } else {
      ElMessage.error(result.message || '加载项目列表失败')
    }
  } catch (error) {
    console.error('加载项目列表失败:', error)
    ElMessage.error('加载项目列表失败：' + error.message)
  } finally {
    loading.value = false
  }
}

// 确认操作
const handleConfirm = () => {
  if (props.mode === 'create') {
    if (!form.value.name || form.value.name.trim() === '') {
      ElMessage.warning('请输入项目名称')
      return
    }

    emit('create', {
      name: form.value.name.trim(),
      description: form.value.description?.trim() || '',
      crs: form.value.crs
    })
  }
}

// 打开项目
const handleOpen = (project) => {
  emit('open', project)
}

// 关闭对话框
const handleClose = () => {
  emit('close')
  emit('update:modelValue', false)
}

onMounted(() => {
  if (props.mode === 'open' && props.modelValue) {
    loadProjects()
  }
})
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
