<template>
  <el-card class="truck-analysis-panel">
    <template #header>
      <div class="card-header">
        <span>货车通过性分析</span>
        <el-button type="primary" size="small" @click="showHistory = !showHistory">
          {{ showHistory ? '隐藏历史' : '查看历史' }}
        </el-button>
      </div>
    </template>

    <!-- 分析表单 -->
    <el-form :model="form" label-width="90px" label-position="left">
      <!-- 起点终点 -->
      <el-form-item label="起点" required>
        <el-input
          v-model="form.startPoint"
          placeholder="点击地图选择起点或输入坐标"
          readonly
          @click="selectStartPoint"
        >
          <template #append>
            <el-button @click.stop="selectStartPoint">选择</el-button>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="终点" required>
        <el-input
          v-model="form.endPoint"
          placeholder="点击地图选择终点或输入坐标"
          readonly
          @click="selectEndPoint"
        >
          <template #append>
            <el-button @click.stop="selectEndPoint">选择</el-button>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="路网数据">
        <el-select
          v-model="form.roadNetworkId"
          placeholder="选择路网数据（可选）"
          style="width: 100%"
          clearable
        >
          <el-option
            v-for="network in roadNetworks"
            :key="network.id"
            :label="network.name"
            :value="network.id"
          />
        </el-select>
      </el-form-item>

      <!-- 货车参数 -->
      <el-divider>货车参数</el-divider>

      <el-form-item label="车长 (m)">
        <el-input-number
          v-model="truck.length"
          :min="0"
          :max="100"
          :precision="2"
          :step="0.5"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="车宽 (m)">
        <el-input-number
          v-model="truck.width"
          :min="0"
          :max="10"
          :precision="2"
          :step="0.1"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="车高 (m)">
        <el-input-number
          v-model="truck.height"
          :min="0"
          :max="10"
          :precision="2"
          :step="0.1"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="总重 (吨)">
        <el-input-number
          v-model="truck.weight"
          :min="0"
          :max="500"
          :precision="2"
          :step="1"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="轴距 (m)">
        <el-input-number
          v-model="truck.wheelbase"
          :min="0"
          :max="50"
          :precision="2"
          :step="0.5"
          style="width: 100%"
        />
        <div class="form-tip">
          用于计算最小转弯半径
        </div>
      </el-form-item>

      <!-- 分析按钮 -->
      <el-form-item>
        <el-button
          type="primary"
          @click="handleAnalyze"
          :loading="analyzing"
          :disabled="!canAnalyze"
          style="width: 100%"
        >
          开始分析
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 分析结果 -->
    <div v-if="analysisResult" class="analysis-result">
      <el-divider>分析结果</el-divider>

      <el-alert
        :type="analysisResult.isPassable ? 'success' : 'error'"
        :title="analysisResult.isPassable ? '可以通过' : '存在禁行路段'"
        :closable="false"
        show-icon
      />

      <!-- 路线信息 -->
      <div class="route-info">
        <div class="info-item">
          <span class="label">总距离：</span>
          <span class="value">{{ analysisResult.totalDistance?.toFixed(2) }} km</span>
        </div>
        <div class="info-item">
          <span class="label">预计时间：</span>
          <span class="value">{{ analysisResult.estimatedTime?.toFixed(1) }} 分钟</span>
        </div>
      </div>

      <!-- 禁行点列表 -->
      <div v-if="analysisResult.violations && analysisResult.violations.length > 0" class="violations">
        <h4 class="violations-title">禁行点列表 ({{ analysisResult.violations.length }} 处)</h4>
        <el-table :data="analysisResult.violations" size="small" :max-height="300">
          <el-table-column prop="reason" label="原因" width="120" />
          <el-table-column prop="detail" label="详情" show-overflow-tooltip />
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="scope">
              <el-button
                type="primary"
                size="small"
                @click="locateViolation(scope.row)"
              >
                定位
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 分析历史 -->
    <el-dialog v-model="showHistory" title="分析历史" width="700px">
      <el-table :data="history" style="width: 100%" max-height="400" v-loading="loadingHistory">
        <el-table-column prop="requestName" label="名称" />
        <el-table-column prop="startPoint" label="起点" width="100" />
        <el-table-column prop="endPoint" label="终点" width="100" />
        <el-table-column label="结果" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.isPassable ? 'success' : 'danger'" size="small">
              {{ scope.row.isPassable ? '可通过' : '禁行' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button
              type="primary"
              size="small"
              @click="loadHistoryResult(scope.row)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { analyzeTruck, getAnalysisHistory } from '@/api/truckAnalysis'
import { getRoadNetworks } from '@/api/roadNetwork'

const props = defineProps({
  map: Object
})

const emit = defineEmits(['select-start', 'select-end', 'show-route', 'locate-point'])

const form = ref({
  startPoint: '',
  endPoint: '',
  roadNetworkId: null,
  startLat: null,
  startLon: null,
  endLat: null,
  endLon: null
})

const truck = ref({
  length: 15,
  width: 2.5,
  height: 3.5,
  weight: 20,
  wheelbase: 8
})

const roadNetworks = ref([])
const analysisResult = ref(null)
const analyzing = ref(false)
const showHistory = ref(false)
const history = ref([])
const loadingHistory = ref(false)

const canAnalyze = computed(() => {
  return form.value.startLat && form.value.startLon &&
         form.value.endLat && form.value.endLon
})

// 加载路网数据
const loadRoadNetworks = async () => {
  try {
    const result = await getRoadNetworks()
    roadNetworks.value = result || []
  } catch (error) {
    console.error('加载路网数据失败:', error)
  }
}

// 选择起点
const selectStartPoint = () => {
  emit('select-start', (lat, lon) => {
    form.value.startLat = lat
    form.value.startLon = lon
    form.value.startPoint = `${lat.toFixed(4)}, ${lon.toFixed(4)}`
  })
}

// 选择终点
const selectEndPoint = () => {
  emit('select-end', (lat, lon) => {
    form.value.endLat = lat
    form.value.endLon = lon
    form.value.endPoint = `${lat.toFixed(4)}, ${lon.toFixed(4)}`
  })
}

// 执行分析
const handleAnalyze = async () => {
  if (!canAnalyze.value) {
    ElMessage.warning('请选择起点和终点')
    return
  }

  try {
    analyzing.value = true

    const params = {
      requestName: `${form.value.startPoint} -> ${form.value.endPoint}`,
      startLat: form.value.startLat,
      startLon: form.value.startLon,
      endLat: form.value.endLat,
      endLon: form.value.endLon,
      roadNetworkId: form.value.roadNetworkId,
      truck: {
        length: truck.value.length,
        width: truck.value.width,
        height: truck.value.height,
        weight: truck.value.weight,
        axleWeight: truck.value.weight / 2,  // 简化估算
        wheelbase: truck.value.wheelbase
      }
    }

    const result = await analyzeTruck(params)

    analysisResult.value = result
    ElMessage.success(result.isPassable ? '路线可通过' : '发现禁行路段')

    // 在地图上显示路线
    emit('show-route', result)

  } catch (error) {
    ElMessage.error('分析失败：' + error.message)
  } finally {
    analyzing.value = false
  }
}

// 定位禁行点
const locateViolation = (point) => {
  emit('locate-point', { lat: point.lat, lon: point.lon })
}

// 加载历史
const loadHistory = async () => {
  loadingHistory.value = true
  try {
    const result = await getAnalysisHistory()
    history.value = result || []
  } catch (error) {
    ElMessage.error('加载历史失败：' + error.message)
  } finally {
    loadingHistory.value = false
  }
}

// 加载历史结果
const loadHistoryResult = (record) => {
  analysisResult.value = {
    isPassable: record.isPassable,
    routeGeoJson: record.routeGeoJson,
    violations: JSON.parse(record.violationPoints || '[]'),
    totalDistance: null,
    estimatedTime: null
  }
  emit('show-route', analysisResult.value)
  showHistory.value = false
}

onMounted(() => {
  loadRoadNetworks()
})
</script>

<style scoped>
.truck-analysis-panel {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.analysis-result {
  margin-top: 20px;
}

.route-info {
  margin: 15px 0;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}

.info-item {
  margin: 5px 0;
}

.info-item .label {
  color: #606266;
  font-weight: 500;
}

.info-item .value {
  color: #409eff;
  font-weight: 600;
}

.violations {
  margin-top: 15px;
}

.violations-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}
</style>
