<template>
  <div class="road-network-downloader">
    <!-- 任务列表 -->
    <div v-if="taskList.length > 0" class="task-list-section">
      <div class="task-list-header">
        <span class="section-title">下载任务</span>
        <div class="task-list-actions">
          <el-link type="primary" :underline="false" @click="showAllTasks = !showAllTasks">
            {{ showAllTasks ? '收起' : '查看全部任务' }}
          </el-link>
          <el-button link type="primary" size="small" @click="loadTaskList">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="task-items" :class="{ 'show-all': showAllTasks }">
        <div v-for="task in displayTasks" :key="task.id" class="task-item">
          <div class="task-header">
            <span class="task-name">{{ task.taskName }}</span>
            <el-tag :type="getStatusTagType(task.status)" size="small">{{ getStatusText(task.status) }}</el-tag>
          </div>
          <el-progress
            :percentage="task.progress"
            :status="task.status === 'FAILED' ? 'exception' : (task.status === 'COMPLETED' ? 'success' : null)"
          />
          <div v-if="task.status === 'FAILED'" class="error-message">
            {{ task.errorMessage }}
          </div>
          <div class="task-actions">
            <el-button
              v-if="task.status === 'FAILED' || task.status === 'CANCELLED'"
              type="primary"
              size="small"
              @click="handleRetry(task.id)"
            >
              重试
            </el-button>
            <el-button
              v-if="task.status === 'PENDING'"
              type="warning"
              size="small"
              @click="handleCancel(task.id)"
            >
              取消
            </el-button>
            <el-button
              v-if="task.status === 'COMPLETED'"
              type="success"
              size="small"
              @click="handleViewResult(task)"
            >
              查看
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <el-form :model="form" label-width="100px" label-position="left">
      <!-- 路网名称 -->
      <el-form-item label="路网名称">
        <el-input v-model="form.name" placeholder="例如：北京市朝阳区路网" />
      </el-form-item>

      <!-- 区域 -->
      <el-form-item label="区域">
        <el-input v-model="form.region" placeholder="例如：北京市" />
      </el-form-item>

      <!-- 坐标范围 -->
      <el-form-item label="坐标范围" required>
        <el-alert
          title="请点击按钮后在地图上框选区域"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 10px"
        />
        <div class="coordinate-inputs">
          <el-input
            v-model="form.minLat"
            placeholder="最小纬度"
            :disabled="true"
            size="small"
          >
            <template #prepend>最小纬度</template>
          </el-input>
          <el-input
            v-model="form.minLon"
            placeholder="最小经度"
            :disabled="true"
            size="small"
          >
            <template #prepend>最小经度</template>
          </el-input>
          <el-input
            v-model="form.maxLat"
            placeholder="最大纬度"
            :disabled="true"
            size="small"
          >
            <template #prepend>最大纬度</template>
          </el-input>
          <el-input
            v-model="form.maxLon"
            placeholder="最大经度"
            :disabled="true"
            size="small"
          >
            <template #prepend>最大经度</template>
          </el-input>
        </div>
        <el-button
          type="primary"
          size="small"
          @click="startDraw"
          style="margin-top: 10px"
        >
          在地图上框选区域
        </el-button>
      </el-form-item>

      <!-- 创建任务按钮 -->
      <el-form-item>
        <el-button
          type="primary"
          @click="createTask"
          :loading="creatingTask"
          :disabled="!canDownload"
        >
          下载路网数据
        </el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { createDownloadTask, getTaskList, retryTask, cancelTask, getTaskDetail } from '@/api/roadNetwork'

const props = defineProps({
  map: Object  // 地图实例
})

const emit = defineEmits(['draw-start', 'download-complete', 'task-created'])

const form = ref({
  name: '',
  region: '',
  minLat: '',
  minLon: '',
  maxLat: '',
  maxLon: ''
})

const taskList = ref([])
const creatingTask = ref(false)
const pollingTimers = ref({})  // 存储轮询定时器
const showAllTasks = ref(false)  // 是否显示全部任务

const canDownload = computed(() => {
  return form.value.name &&
         form.value.minLat &&
         form.value.minLon &&
         form.value.maxLat &&
         form.value.maxLon
})

// 显示的任务列表（限制显示数量）
const displayTasks = computed(() => {
  if (showAllTasks.value) {
    return taskList.value
  }
  // 只显示最近 3 个任务
  return taskList.value.slice(0, 3)
})

// 获取状态标签类型
const getStatusTagType = (status) => {
  const types = {
    'PENDING': 'info',
    'DOWNLOADING': 'warning',
    'PROCESSING': 'warning',
    'COMPLETED': 'success',
    'FAILED': 'danger',
    'CANCELLED': 'info'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    'PENDING': '等待下载',
    'DOWNLOADING': '下载中',
    'PROCESSING': '处理中',
    'COMPLETED': '已完成',
    'FAILED': '失败',
    'CANCELLED': '已取消'
  }
  return texts[status] || status
}

// 开始绘制
const startDraw = () => {
  if (!props.map) {
    ElMessage.warning('地图未初始化')
    return
  }

  emit('draw-start', (bbox) => {
    form.value.minLat = bbox[1].toFixed(6)
    form.value.minLon = bbox[0].toFixed(6)
    form.value.maxLat = bbox[3].toFixed(6)
    form.value.maxLon = bbox[2].toFixed(6)
    ElMessage.success('区域已选择')
  })
}

// 创建任务
const createTask = async () => {
  if (!canDownload.value) {
    ElMessage.warning('请先填写完整信息并选择区域')
    return
  }

  try {
    creatingTask.value = true

    const params = {
      name: form.value.name,
      region: form.value.region,
      minLat: parseFloat(form.value.minLat),
      minLon: parseFloat(form.value.minLon),
      maxLat: parseFloat(form.value.maxLat),
      maxLon: parseFloat(form.value.maxLon)
    }

    const result = await createDownloadTask(params)

    ElMessage.success('任务已创建，ID: ' + result.data.id)

    // 开始轮询任务状态
    startPolling(result.data.id)

    // 刷新任务列表
    loadTaskList()

    // 通知父组件
    emit('task-created', result.data)

    // 重置表单
    resetForm()

  } catch (error) {
    ElMessage.error('创建任务失败：' + error.message)
  } finally {
    creatingTask.value = false
  }
}

// 加载任务列表
const loadTaskList = async () => {
  try {
    const res = await getTaskList()
    if (res.code === 200) {
      taskList.value = res.data || []
      // 为进行中的任务启动轮询
      taskList.value.forEach(task => {
        if (task.status === 'PENDING' || task.status === 'DOWNLOADING' || task.status === 'PROCESSING') {
          if (!pollingTimers.value[task.id]) {
            startPolling(task.id)
          }
        }
      })
    }
  } catch (error) {
    console.error('加载任务列表失败:', error)
  }
}

// 开始轮询任务状态
const startPolling = (taskId) => {
  if (pollingTimers.value[taskId]) {
    return
  }

  pollingTimers.value[taskId] = setInterval(async () => {
    try {
      const res = await getTaskDetail(taskId)
      if (res.code === 200) {
        const task = res.data
        // 更新任务列表中的任务状态
        const index = taskList.value.findIndex(t => t.id === taskId)
        if (index !== -1) {
          taskList.value[index] = task
        }

        // 如果任务已完成或失败，停止轮询
        if (task.status === 'COMPLETED' || task.status === 'FAILED' || task.status === 'CANCELLED') {
          stopPolling(taskId)
          if (task.status === 'COMPLETED') {
            ElMessage.success('下载任务完成')
            emit('download-complete', task)
          } else if (task.status === 'FAILED') {
            ElMessage.error('下载任务失败：' + task.errorMessage)
          }
        }
      }
    } catch (error) {
      console.error('轮询任务状态失败:', error)
    }
  }, 2000)  // 每 2 秒轮询一次
}

// 停止轮询
const stopPolling = (taskId) => {
  if (pollingTimers.value[taskId]) {
    clearInterval(pollingTimers.value[taskId])
    delete pollingTimers.value[taskId]
  }
}

// 重试任务
const handleRetry = async (taskId) => {
  try {
    await retryTask(taskId)
    ElMessage.success('任务已重新执行')
    startPolling(taskId)
    loadTaskList()
  } catch (error) {
    ElMessage.error('重试任务失败：' + error.message)
  }
}

// 取消任务
const handleCancel = async (taskId) => {
  try {
    await cancelTask(taskId)
    ElMessage.success('任务已取消')
    stopPolling(taskId)
    loadTaskList()
  } catch (error) {
    ElMessage.error('取消任务失败：' + error.message)
  }
}

// 查看结果
const handleViewResult = (task) => {
  if (task.roadNetworkId) {
    ElMessage.success('路网已添加到列表，请在路网管理中查看')
    emit('download-complete', task)
  }
}

// 重置表单
const resetForm = () => {
  form.value = {
    name: '',
    region: '',
    minLat: '',
    minLon: '',
    maxLat: '',
    maxLon: ''
  }
}

// 组件挂载时加载任务列表
onMounted(() => {
  loadTaskList()
})

// 组件卸载时清理定时器
onUnmounted(() => {
  Object.values(pollingTimers.value).forEach(timer => {
    clearInterval(timer)
  })
  pollingTimers.value = {}
})
</script>

<style scoped>
.road-network-downloader {
  padding: 10px;
}

.task-list-section {
  margin-bottom: 15px;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
}

.task-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.section-title {
  font-weight: 500;
  color: #303133;
  font-size: 14px;
}

.task-list-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-items {
  max-height: 200px;
  overflow-y: auto;
}

.task-items.show-all {
  max-height: none;
}

.task-item {
  padding: 10px 0;
  border-bottom: 1px solid #eee;
}

.task-item:last-child {
  border-bottom: none;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.task-name {
  font-weight: 500;
  color: #303133;
}

.error-message {
  font-size: 12px;
  color: #f56c6c;
  margin: 5px 0;
}

.task-actions {
  margin-top: 8px;
  text-align: right;
}

.coordinate-inputs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
</style>
