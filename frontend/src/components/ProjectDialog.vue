<template>
  <el-dialog
    v-model="dialogVisible"
    :title="mode === 'create' ? '新建项目' : '打开项目'"
    width="600px"
    @close="handleClose"
  >
    <!-- 新建项目模式 -->
    <div v-if="mode === 'create'">
      <!-- 步骤选择 -->
      <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 20px">
        <el-step title="填写信息" />
        <el-step title="选择方式" />
        <el-step title="完成创建" />
      </el-steps>

      <!-- 步骤 1：填写项目信息 -->
      <div v-if="step === 0">
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

      <!-- 步骤 2：选择创建方式 -->
      <div v-else-if="step === 1">
        <el-alert
          title="请选择项目创建方式"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        >
          <p style="margin: 0 0 8px 0"><strong>方式一：选择影像（推荐）</strong></p>
          <p style="margin: 0 0 12px 0">项目将使用影像的地理范围作为初始范围，可直接在影像上标绘。</p>
          <p style="margin: 0 0 8px 0"><strong>方式二：绘制项目区域</strong></p>
          <p style="margin: 0 0 12px 0">在地图上绘制多边形定义项目范围，系统将创建该范围的占位底图。</p>
        </el-alert>

        <el-radio-group v-model="createMethod" style="width: 100%">
          <el-card
            :style="{
              marginBottom: '10px',
              cursor: 'pointer',
              border: createMethod === 'image' ? '2px solid #409eff' : '1px solid #dcdfe6'
            }"
            @click="createMethod = 'image'"
          >
            <div style="display: flex; align-items: center">
              <el-radio value="image" style="flex: 1">选择影像</el-radio>
              <el-tag type="success">推荐</el-tag>
            </div>
          </el-card>
          <el-card
            :style="{
              cursor: 'pointer',
              border: createMethod === 'draw' ? '2px solid #409eff' : '1px solid #dcdfe6'
            }"
            @click="createMethod = 'draw'"
          >
            <el-radio value="draw" style="flex: 1">绘制项目区域</el-radio>
          </el-card>
        </el-radio-group>

        <!-- 选择影像 -->
        <div v-if="createMethod === 'image'" style="margin-top: 16px">
          <el-form label-width="80px">
            <el-form-item label="选择影像">
              <el-select
                v-model="selectedImageId"
                placeholder="请选择影像"
                style="width: 100%"
                filterable
              >
                <el-option
                  v-for="img in imageList"
                  :key="img.id"
                  :label="img.name"
                  :value="img.id"
                >
                  <span>{{ img.name }}</span>
                  <span style="float: right; color: #8492a6; font-size: 13px">{{ img.fileSize ? (img.fileSize / 1024 / 1024).toFixed(2) + ' MB' : '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- 步骤 3：确认 -->
      <div v-else-if="step === 2">
        <el-result
          icon="success"
          :title="createMethod === 'image' ? '已选择影像' : '请绘制项目区域'"
          :sub-title="createMethod === 'image' ? `影像：${selectedImageName}` : '在地图上绘制多边形定义项目范围'"
        >
          <template v-if="createMethod === 'draw'" #extra>
            <p style="color: #666; margin-bottom: 10px">
              请在地图上使用 <strong>面标注</strong> 工具绘制项目范围，然后点击保存按钮。
            </p>
          </template>
        </el-result>
      </div>
    </div>

    <!-- 打开项目模式 -->
    <div v-else>
      <el-table :data="projects" style="width: 100%" max-height="400" v-loading="loading">
        <el-table-column prop="name" label="项目名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="baseImageName" label="图像文件名" show-overflow-tooltip />
        <el-table-column prop="annotationCount" label="标注数量" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button
              type="primary"
              size="small"
              @click="handleOpen(scope.row)"
            >
              打开
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button v-if="mode === 'create' && step > 0" @click="prevStep">上一步</el-button>
        <el-button
          v-if="mode === 'create' && step < 2"
          type="primary"
          @click="nextStep"
          :disabled="!canGoNext"
        >
          下一步
        </el-button>
        <el-button
          v-if="mode === 'create' && step === 2 && createMethod === 'draw'"
          type="success"
          @click="handleDrawComplete"
        >
          完成绘制，创建项目
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
const BUILD_VERSION = '20260409-01'
console.log('[ProjectDialog] 当前版本:', BUILD_VERSION)
import { ElMessage, ElMessageBox } from 'element-plus'
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

// 步骤控制
const step = ref(0)
const createMethod = ref('image') // 'image' 或 'draw'
const selectedImageId = ref(null)
const selectedImageName = ref('')
const imageList = ref([])

const canGoNext = computed(() => {
  if (step.value === 0) {
    return form.value.name && form.value.name.trim() !== ''
  }
  if (step.value === 1) {
    if (createMethod.value === 'image') {
      return selectedImageId.value !== null && selectedImageId.value !== undefined
    }
    return true // 绘制方式可以直接下一步
  }
  return false
})

const projects = ref([])
const loading = ref(false)

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
    step.value = 0
    createMethod.value = 'image'
    selectedImageId.value = null
    selectedImageName.value = ''
    loadImageList()
  }
})

// 加载影像列表
const loadImageList = async () => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/images', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })
    const result = await response.json()
    if (result.code === 200) {
      imageList.value = result.data || []
    }
  } catch (error) {
    console.error('加载影像列表失败:', error)
  }
}

// 监听选择的影像
watch(selectedImageId, (newId) => {
  const img = imageList.value.find(i => i.id === newId)
  if (img) {
    selectedImageName.value = img.name
  }
})

// 上一步
const prevStep = () => {
  step.value--
}

// 下一步
const nextStep = () => {
  if (step.value === 0) {
    step.value++
  } else if (step.value === 1) {
    if (createMethod.value === 'image') {
      // 直接创建
      emit('create', {
        name: form.value.name.trim(),
        description: form.value.description?.trim() || '',
        crs: form.value.crs,
        method: 'image',
        imageId: selectedImageId.value
      })
      handleClose()
    } else {
      // 绘制方式，进入确认步骤
      step.value++
      // 通知 MapView 进入绘制模式
      emit('startDrawArea')
    }
  }
}

// 绘制完成
const handleDrawComplete = () => {
  emit('create', {
    name: form.value.name.trim(),
    description: form.value.description?.trim() || '',
    crs: form.value.crs,
    method: 'draw'
  })
  handleClose()
}

// 打开项目
const handleOpen = (project) => {
  emit('open', project)
}

// 删除项目
const handleDelete = async (project) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除项目"${project.name}"吗？删除后无法恢复！`,
      '警告',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const token = localStorage.getItem('token')
    const response = await fetch(`/api/projects/${project.id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    const result = await response.json()

    if (result.code === 200) {
      ElMessage.success('项目已删除')
      // 从列表中移除
      projects.value = projects.value.filter(p => p.id !== project.id)
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除项目失败:', error)
      ElMessage.error('删除失败：' + (error.message || '网络错误'))
    }
  }
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
