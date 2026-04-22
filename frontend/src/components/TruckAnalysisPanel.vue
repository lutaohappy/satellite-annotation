<template>
  <el-card class="truck-analysis-panel">
    <template #header>
      <div class="card-header">
        <span>货车通过性分析</span>
        <div class="header-actions">
          <el-button type="success" size="small" @click="saveResult" :disabled="!analysisResult">
            <el-icon><Download /></el-icon> 保存结果
          </el-button>
          <el-button type="info" size="small" @click="showSavedList = true">
            <el-icon><Folder /></el-icon> 加载历史
          </el-button>
          <el-button type="primary" size="small" @click="showHistory = !showHistory">
            {{ showHistory ? '隐藏历史' : '查看历史' }}
          </el-button>
        </div>
      </div>
    </template>

    <!-- 分析表单 -->
    <el-form :model="form" label-width="100px" label-position="left">
      <!-- 起点终点 -->
      <el-form-item label="起点 S" required>
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

      <el-form-item label="终点 E" required>
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

      <el-row :gutter="10">
        <el-col :span="12">
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
        </el-col>
        <el-col :span="12">
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
        </el-col>
      </el-row>

      <el-row :gutter="10">
        <el-col :span="12">
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
        </el-col>
        <el-col :span="12">
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
        </el-col>
      </el-row>

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
          最小转弯半径：{{ minTurningRadius.toFixed(1) }}m (公式：R = L / sin(35°))
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
        <div class="info-actions">
          <el-button type="primary" size="small" @click="showRoadSegmentsDialog = true">
            <el-icon><Document /></el-icon> 路段详情
          </el-button>
        </div>
      </div>

      <!-- 转弯点列表 -->
      <div v-if="analysisResult.turnPoints && analysisResult.turnPoints.length > 0" class="turn-points">
        <h4 class="turn-points-title">转弯点列表 ({{ analysisResult.turnPoints.length }} 个)</h4>
        <el-table :data="analysisResult.turnPoints" size="small" :max-height="300">
          <el-table-column prop="sequence" label="序号" width="60" />
          <el-table-column prop="instruction" label="转向说明" />
          <el-table-column label="角度" width="80">
            <template #default="scope">
              <span :class="{ 'sharp-turn': scope.row.isSharpTurn }">{{ scope.row.turnAngle?.toFixed(1) }}°</span>
            </template>
          </el-table-column>
          <el-table-column label="半径" width="90">
            <template #default="scope">
              <span :class="{ 'sharp-turn': scope.row.turnRadius && scope.row.turnRadius < minTurningRadius }">
                {{ scope.row.turnRadius?.toFixed(1) }}m
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="scope">
              <el-button
                type="primary"
                size="small"
                @click="locateTurnPoint(scope.row)"
              >
                定位
              </el-button>
            </template>
          </el-table-column>
        </el-table>
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

    <!-- 路段详情对话框 -->
    <el-dialog v-model="showRoadSegmentsDialog" title="路段详情" width="900px">
      <el-table :data="analysisResult.roadSegments || []" style="width: 100%" max-height="500">
        <el-table-column prop="sequence" label="序号" width="60" />
        <el-table-column prop="name" label="路段名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="起点坐标" width="130">
          <template #default="scope">
            <span v-if="scope.row.startLat && scope.row.startLon">
              {{ scope.row.startLat.toFixed(4) }}, {{ scope.row.startLon.toFixed(4) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="终点坐标" width="130">
          <template #default="scope">
            <span v-if="scope.row.endLat && scope.row.endLon">
              {{ scope.row.endLat.toFixed(4) }}, {{ scope.row.endLon.toFixed(4) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="距离/用时" width="100">
          <template #default="scope">
            <div v-if="scope.row.distance">
              {{ (scope.row.distance / 1000).toFixed(2) }}km
              <br>
              <span style="font-size: 11px; color: #999;">{{ Math.round(scope.row.duration || 0) }}s</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="instruction" label="导航指示" min-width="180" show-overflow-tooltip />
        <el-table-column label="属性信息" width="180">
          <template #default="scope">
            <div class="segment-properties">
              <span v-if="scope.row.turnRadius" :class="{ 'sharp-turn': scope.row.turnRadius < minTurningRadius }">
                转弯半径：{{ scope.row.turnRadius.toFixed(1) }}m
              </span>
              <span v-if="scope.row.restrictions" class="restriction">
                {{ scope.row.restrictions }}
              </span>
              <span v-if="scope.row.roadMode === 'ferry'" class="restriction">
                轮渡路段
              </span>
              <span v-if="scope.row.bearingBefore">
                方位：{{ Math.round(scope.row.bearingBefore) }}°→{{ Math.round(scope.row.bearingAfter) }}°
              </span>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showRoadSegmentsDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 分析历史对话框 -->
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

    <!-- 已保存 GeoJSON 列表对话框 -->
    <el-dialog v-model="showSavedList" title="已保存的分析结果" width="700px">
      <el-button type="primary" size="small" @click="loadSavedList" style="margin-bottom: 10px">
        <el-icon><Refresh /></el-icon> 刷新列表
      </el-button>
      <el-table :data="savedList" style="width: 100%" max-height="400" v-loading="loadingSaved">
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="startPoint" label="起点" width="100" />
        <el-table-column prop="endPoint" label="终点" width="100" />
        <el-table-column prop="createdAt" label="保存时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button
              type="primary"
              size="small"
              @click="loadSavedResult(scope.row)"
            >
              加载
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="deleteSaved(scope.row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Folder, Refresh, Document } from '@element-plus/icons-vue'
import { analyzeTruck, getAnalysisHistory, saveAnalysisRecord, getSavedAnalysisList, deleteSavedAnalysis } from '@/api/truckAnalysis'
import { getRoadNetworks } from '@/api/roadNetwork'

const props = defineProps({
  map: Object
})

const emit = defineEmits(['select-start', 'select-end', 'show-route', 'locate-point', 'save-complete'])

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
const showSavedList = ref(false)
const showRoadSegmentsDialog = ref(false)
const history = ref([])
const savedList = ref([])
const loadingHistory = ref(false)
const loadingSaved = ref(false)

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
    console.log('[TruckAnalysisPanel] 开始执行分析')

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

    console.log('[TruckAnalysisPanel] 请求参数:', params)

    // 将表单数据传递到全局，以便 MapView 显示起点终点标记
    window.truckAnalysisForm = {
      startLat: form.value.startLat,
      startLon: form.value.startLon,
      endLat: form.value.endLat,
      endLon: form.value.endLon,
      startPoint: form.value.startPoint,
      endPoint: form.value.endPoint
    }

    const result = await analyzeTruck(params)
    console.log('[TruckAnalysisPanel] 分析结果:', result)

    // 解包响应数据（如果后端返回的是 ApiResponse 格式）
    const analysisData = result.data || result
    console.log('[TruckAnalysisPanel] 解包后的数据:', analysisData)

    analysisResult.value = analysisData

    // 显示结果消息
    const msg = analysisData.isPassable
      ? '路线可通过，无禁行限制'
      : `发现 ${analysisData.violations?.length || 0} 处禁行点`
    ElMessage.success(msg)

    // 在地图上显示路线
    emit('show-route', analysisData)

    // 自动保存分析记录
    autoSaveRecord(analysisData)

  } catch (error) {
    console.error('[TruckAnalysisPanel] 分析失败:', error)
    ElMessage.error('分析失败：' + (error.message || '未知错误'))
  } finally {
    analyzing.value = false
  }
}

// 自动保存分析记录
const autoSaveRecord = async (analysisData) => {
  try {
    const data = {
      name: `${form.value.startPoint} -> ${form.value.endPoint} (${new Date().toLocaleString()})`,
      startPoint: form.value.startPoint,
      endPoint: form.value.endPoint,
      startLat: form.value.startLat,
      startLon: form.value.startLon,
      endLat: form.value.endLat,
      endLon: form.value.endLon,
      truckParams: {
        length: truck.value.length,
        width: truck.value.width,
        height: truck.value.height,
        weight: truck.value.weight,
        axleWeight: truck.value.weight / 2,
        wheelbase: truck.value.wheelbase
      },
      routeGeoJson: analysisData.routeGeoJson,
      turnPoints: analysisData.turnPoints,
      violations: analysisData.violations,
      roadSegments: analysisData.roadSegments,  // 添加路段数据
      totalDistance: analysisData.totalDistance,
      estimatedTime: analysisData.estimatedTime
    }

    const result = await saveAnalysisRecord(data)
    console.log('[TruckAnalysisPanel] 自动保存记录:', result)
    // 不显示提示，避免打扰用户
  } catch (error) {
    console.error('[TruckAnalysisPanel] 自动保存失败:', error)
    // 静默失败，不影响主流程
  }
}

// 保存分析结果
const saveResult = async () => {
  if (!analysisResult.value) {
    ElMessage.warning('没有可保存的分析结果')
    return
  }

  try {
    const data = {
      name: `${form.value.startPoint} -> ${form.value.endPoint}`,
      startPoint: form.value.startPoint,
      endPoint: form.value.endPoint,
      startLat: form.value.startLat,
      startLon: form.value.startLon,
      endLat: form.value.endLat,
      endLon: form.value.endLon,
      truckParams: truck.value,
      routeGeoJson: analysisResult.value.routeGeoJson,
      turnPoints: analysisResult.value.turnPoints,
      violations: analysisResult.value.violations,
      roadSegments: analysisResult.value.roadSegments,  // 添加路段数据
      totalDistance: analysisResult.value.totalDistance,
      estimatedTime: analysisResult.value.estimatedTime
    }

    const result = await saveAnalysisResult(data)
    console.log('[TruckAnalysisPanel] 保存结果:', result)

    ElMessage.success('分析结果已保存')
    emit('save-complete', result)

  } catch (error) {
    console.error('[TruckAnalysisPanel] 保存失败:', error)
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  }
}

// 加载已保存列表
const loadSavedList = async () => {
  loadingSaved.value = true
  try {
    const result = await getSavedAnalysisList()
    console.log('[TruckAnalysisPanel] 已保存列表:', result)
    savedList.value = (result.data || result) || []
  } catch (error) {
    console.error('[TruckAnalysisPanel] 加载列表失败:', error)
    ElMessage.error('加载列表失败：' + (error.message || '未知错误'))
  } finally {
    loadingSaved.value = false
  }
}

// 加载已保存的结果
const loadSavedResult = async (item) => {
  try {
    const result = await getSavedAnalysisList()
    const savedItems = (result.data || result) || []
    const savedItem = savedItems.find(s => s.id === item.id)

    if (savedItem) {
      // 恢复表单数据
      form.value.startLat = savedItem.startLat
      form.value.startLon = savedItem.startLon
      form.value.endLat = savedItem.endLat
      form.value.endLon = savedItem.endLon
      form.value.startPoint = savedItem.startPoint
      form.value.endPoint = savedItem.endPoint

      // 恢复货车参数
      if (savedItem.truckParams) {
        truck.value = savedItem.truckParams
      }

      // 保存表单数据到全局，以便 MapView 显示起点终点标记
      window.truckAnalysisForm = {
        startLat: savedItem.startLat,
        startLon: savedItem.startLon,
        endLat: savedItem.endLat,
        endLon: savedItem.endLon,
        startPoint: savedItem.startPoint,
        endPoint: savedItem.endPoint
      }

      // 恢复分析结果
      analysisResult.value = {
        isPassable: savedItem.isPassable,
        routeGeoJson: savedItem.routeGeoJson,
        turnPoints: savedItem.turnPoints,
        violations: savedItem.violations,
        totalDistance: savedItem.totalDistance,
        estimatedTime: savedItem.estimatedTime
      }

      // 在地图上显示
      emit('show-route', analysisResult.value)

      showSavedList.value = false
      ElMessage.success('已加载保存的分析结果')
    }
  } catch (error) {
    console.error('[TruckAnalysisPanel] 加载结果失败:', error)
    ElMessage.error('加载失败：' + (error.message || '未知错误'))
  }
}

// 删除已保存的结果
const deleteSaved = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这条保存的分析结果吗？', '确认删除', {
      type: 'warning'
    })

    await deleteSavedAnalysis(id)
    ElMessage.success('删除成功')
    loadSavedList()  // 刷新列表
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[TruckAnalysisPanel] 删除失败:', error)
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }
}

// 定位禁行点
const locateViolation = (point) => {
  emit('locate-point', { lat: point.lat, lon: point.lon })
}

// 定位转弯点
const locateTurnPoint = (point) => {
  emit('locate-point', { lat: point.lat, lon: point.lon })
}

// 计算最小转弯半径（基于轴距）
const minTurningRadius = computed(() => {
  // R = wheelbase / sin(35°) ≈ wheelbase / 0.574
  return truck.value.wheelbase / 0.574
})

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
  // 解析禁行点和转弯点
  let violations = []
  let turnPoints = []

  try {
    if (record.violationPoints) {
      violations = JSON.parse(record.violationPoints)
    }
  } catch (e) {
    console.error('[TruckAnalysisPanel] 解析禁行点失败:', e)
  }

  try {
    if (record.turnPoints) {
      turnPoints = JSON.parse(record.turnPoints)
    }
  } catch (e) {
    console.error('[TruckAnalysisPanel] 解析转弯点失败:', e)
  }

  analysisResult.value = {
    isPassable: record.isPassable,
    routeGeoJson: record.routeGeoJson,
    violations: violations,
    turnPoints: turnPoints,
    totalDistance: null,
    estimatedTime: null
  }

  // 恢复货车参数
  if (record.truckLength) {
    truck.value.length = record.truckLength
    truck.value.width = record.truckWidth
    truck.value.height = record.truckHeight
    truck.value.weight = record.truckWeight
    truck.value.wheelbase = record.wheelbase
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
  flex-wrap: wrap;
  gap: 8px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  align-items: center;
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

.info-actions {
  margin-left: auto;
}

.turn-points {
  margin-top: 15px;
}

.turn-points-title {
  margin: 0 0 10px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.sharp-turn {
  color: #f56c6c;
  font-weight: 600;
}

.segment-properties {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.segment-properties .sharp-turn {
  color: #f56c6c;
  font-weight: 600;
}

.segment-properties .restriction {
  color: #f56c6c;
  font-weight: 500;
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
