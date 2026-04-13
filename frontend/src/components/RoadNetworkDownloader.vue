<template>
  <el-card class="road-network-downloader">
    <template #header>
      <div class="card-header">
        <span>路网数据下载</span>
      </div>
    </template>

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
          title="请在地图上绘制矩形区域"
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
          :loading="drawing"
          style="margin-top: 10px"
        >
          {{ drawing ? '绘制中，请在地图上框选区域' : '在地图上框选区域' }}
        </el-button>
      </el-form-item>

      <!-- 下载按钮 -->
      <el-form-item>
        <el-button
          type="primary"
          @click="handleDownload"
          :loading="downloading"
          :disabled="!canDownload"
        >
          下载路网数据
        </el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 下载进度 -->
    <div v-if="downloading" class="download-progress">
      <el-progress
        :indeterminate="true"
        :duration="2"
        :stroke-width="20"
      />
      <p class="progress-text">正在从 OpenStreetMap 下载路网数据...</p>
      <p class="progress-tip">提示：首次下载可能需要较长时间，请耐心等待</p>
    </div>
  </el-card>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadRoadNetwork } from '@/api/roadNetwork'

const props = defineProps({
  map: Object  // 地图实例
})

const emit = defineEmits(['draw-start', 'draw-end', 'download-complete'])

const form = ref({
  name: '',
  region: '',
  minLat: '',
  minLon: '',
  maxLat: '',
  maxLon: ''
})

const drawing = ref(false)
const downloading = ref(false)
let drawInteraction = null

const canDownload = computed(() => {
  return form.value.name &&
         form.value.minLat &&
         form.value.minLon &&
         form.value.maxLat &&
         form.value.maxLon
})

// 开始绘制
const startDraw = () => {
  if (!props.map) {
    ElMessage.warning('地图未初始化')
    return
  }

  drawing.value = true
  emit('draw-start', (bbox) => {
    // 接收绘制完成的边界框
    form.value.minLat = bbox[1]
    form.value.minLon = bbox[0]
    form.value.maxLat = bbox[3]
    form.value.maxLon = bbox[2]
    drawing.value = false
    emit('draw-end')
    ElMessage.success('区域已选择')
  })
}

// 下载路网数据
const handleDownload = async () => {
  if (!canDownload.value) {
    ElMessage.warning('请先填写完整信息并选择区域')
    return
  }

  try {
    downloading.value = true

    const params = {
      name: form.value.name,
      region: form.value.region,
      minLat: parseFloat(form.value.minLat),
      minLon: parseFloat(form.value.minLon),
      maxLat: parseFloat(form.value.maxLat),
      maxLon: parseFloat(form.value.maxLon)
    }

    const result = await downloadRoadNetwork(params)

    ElMessage.success('路网数据下载成功')
    emit('download-complete', result)

  } catch (error) {
    ElMessage.error('下载失败：' + error.message)
  } finally {
    downloading.value = false
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
  emit('draw-end')
}
</script>

<style scoped>
.road-network-downloader {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.coordinate-inputs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.download-progress {
  margin-top: 20px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
  text-align: center;
}

.progress-text {
  margin: 10px 0 5px;
  color: #606266;
  font-weight: 500;
}

.progress-tip {
  margin: 5px 0;
  color: #909399;
  font-size: 12px;
}
</style>
