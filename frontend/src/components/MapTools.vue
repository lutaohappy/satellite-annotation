<template>
  <div class="map-tools" :class="{ locked: isLocked }" :style="{ left: position.x + 'px', top: position.y + 'px' }">
    <div class="tools-header" @mousedown="startDrag">
      <span class="title">工具</span>
      <div class="header-actions">
        <el-tooltip :content="isLocked ? '解锁' : '锁定'">
          <el-button link :type="isLocked ? 'warning' : 'info'" size="small" @click.stop="toggleLock">
            <el-icon><Lock v-if="isLocked" /><Unlock v-else /></el-icon>
          </el-button>
        </el-tooltip>
        <el-button link type="primary" size="small" @click.stop="toggleExpand">
          {{ expanded ? '收起' : '展开' }}
        </el-button>
        <el-button link type="danger" size="small" @click.stop="close">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <el-tabs v-if="expanded" v-model="activeTab" class="tools-tabs" stretch>
      <!-- 量测工具 Tab -->
      <el-tab-pane label="量测" name="measure">
        <div class="tab-content">
          <el-radio-group v-model="measureMode" @change="toggleMeasure" size="small">
            <el-radio-button value="distance">距离</el-radio-button>
            <el-radio-button value="area">面积</el-radio-button>
            <el-radio-button value="coordinate">坐标</el-radio-button>
          </el-radio-group>

          <div v-if="measureResult" class="measure-result">
            <div class="result-title">{{ resultTitle }}</div>
            <div class="result-value">{{ measureResult }}</div>
          </div>

          <div class="measure-tips">
            <el-alert v-if="measureMode" type="info" :closable="false" show-icon>
              <template #title>
                <div v-if="measureMode === 'coordinate'">点击地图获取坐标</div>
                <div v-else-if="measureMode === 'distance'">画线测量距离，双击结束</div>
                <div v-else>画面测量面积，双击结束</div>
              </template>
            </el-alert>
          </div>
        </div>
      </el-tab-pane>

      <!-- 符号库 Tab -->
      <el-tab-pane label="符号库" name="symbols">
        <div class="tab-content">
          <!-- 分类筛选 -->
          <div class="filter-bar">
            <el-select v-model="selectedCategory" size="small" placeholder="全部分类" clearable @change="filterSymbols">
              <el-option label="全部" value="" />
              <el-option label="军事符号" value="military" />
              <el-option label="地标建筑" value="landmark" />
              <el-option label="交通设施" value="transport" />
              <el-option label="自然资源" value="nature" />
              <el-option label="自定义" value="custom" />
            </el-select>
            <el-input
              v-model="searchKeyword"
              size="small"
              placeholder="搜索符号..."
              clearable
              @input="filterSymbols"
              class="search-input"
            />
          </div>

          <!-- 上传按钮 -->
          <div class="upload-section">
            <el-upload
              :auto-upload="false"
              :on-change="handleFileChange"
              :multiple="true"
              accept=".svg"
              class="upload-btn"
            >
              <el-button type="primary" size="small" :icon="Upload">
                批量上传 SVG
              </el-button>
            </el-upload>
          </div>

          <!-- 符号网格 -->
          <div class="symbol-grid">
            <div
              v-for="symbol in filteredSymbols"
              :key="symbol.id"
              class="symbol-item"
              :class="{ active: selectedSymbol?.id === symbol.id }"
              @click="selectSymbol(symbol)"
            >
              <div class="symbol-preview" v-html="symbol.content"></div>
              <span class="symbol-name">{{ symbol.name }}</span>
            </div>
          </div>

          <!-- 分页信息 -->
          <div class="pagination-info">
            共 {{ filteredSymbols.length }} 个符号
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Close, Lock, Unlock } from '@element-plus/icons-vue'
import { getLength, getArea } from 'ol/sphere'
import { transform } from 'ol/proj'

const props = defineProps({
  map: Object,
  vectorSource: Object,
  symbols: Array,
  selectedSymbol: Object
})

const emit = defineEmits(['measure-result', 'symbol-select', 'symbols-change', 'open-library'])

const activeTab = ref('measure')
const expanded = ref(true)
const position = ref({ x: 10, y: 80 })
const isLocked = ref(false) // 锁定状态

// 切换锁定状态
const toggleLock = () => {
  isLocked.value = !isLocked.value
  ElMessage.success(isLocked.value ? '已锁定，无法拖动' : '已解锁，可以拖动')
}

// 量测相关
const measureMode = ref(null)
const measureResult = ref('')
const currentSketch = ref(null)
const changeListenerKey = ref(null)

const resultTitle = computed(() => {
  if (measureMode.value === 'distance') return '距离'
  if (measureMode.value === 'area') return '面积'
  if (measureMode.value === 'coordinate') return '坐标'
  return ''
})

const clearMeasure = () => {
  measureResult.value = ''
  measureMode.value = null
  removeSketchListener()
  if (props.vectorSource) {
    const features = props.vectorSource.getFeatures()
    if (features.length > 0) {
      props.vectorSource.removeFeature(features[features.length - 1])
    }
  }
  emit('measure-result', null)
}

const removeSketchListener = () => {
  if (currentSketch.value && changeListenerKey.value) {
    currentSketch.value.getGeometry().un('change', changeListenerKey.value)
  }
  currentSketch.value = null
  changeListenerKey.value = null
}

const toggleMeasure = () => {
  if (!measureMode.value) {
    removeSketchListener()
    emit('measure-result', null)
    measureResult.value = ''
    return
  }

  let drawType = 'Point'
  if (measureMode.value === 'distance') drawType = 'LineString'
  if (measureMode.value === 'area') drawType = 'Polygon'

  emit('measure-result', {
    mode: measureMode.value,
    drawType: drawType,
    callback: handleDrawEvent
  })
}

const handleDrawEvent = (type, event) => {
  if (type === 'start') {
    currentSketch.value = event.feature
    const geom = currentSketch.value.getGeometry()
    changeListenerKey.value = geom.on('change', () => {
      updateMeasureResult(geom)
    })
    updateMeasureResult(geom)
  } else if (type === 'end') {
    const geom = event.feature.getGeometry()
    updateMeasureResult(geom)
    removeSketchListener()
  }
}

const updateMeasureResult = (geom) => {
  if (!geom) return
  const type = geom.getType()
  if (type === 'LineString') {
    const length = getLength(geom)
    measureResult.value = formatDistance(length)
  } else if (type === 'Polygon') {
    const area = getArea(geom)
    measureResult.value = formatArea(area)
  } else if (type === 'Point') {
    const coord = geom.getCoordinates()
    measureResult.value = formatCoordinate(coord)
  }
  emit('measure-result', {
    mode: measureMode.value,
    result: measureResult.value
  })
}

const formatDistance = (meters) => {
  if (!meters || isNaN(meters)) return '0 m'
  if (meters > 1000) {
    return (meters / 1000).toFixed(2) + ' km'
  }
  return meters.toFixed(2) + ' m'
}

const formatArea = (sqMeters) => {
  if (!sqMeters || isNaN(sqMeters)) return '0 m²'
  if (sqMeters > 1000000) {
    return (sqMeters / 1000000).toFixed(2) + ' km²'
  } else if (sqMeters > 10000) {
    return (sqMeters / 10000).toFixed(2) + ' 公顷'
  }
  return sqMeters.toFixed(2) + ' m²'
}

const formatCoordinate = (coord) => {
  if (!coord || coord.length < 2) return '无效坐标'
  const wgs84 = transform(coord, 'EPSG:3857', 'EPSG:4326')
  return `经度：${wgs84[0].toFixed(6)}°, 纬度：${wgs84[1].toFixed(6)}°`
}

// 符号相关
const selectedCategory = ref('')
const searchKeyword = ref('')
const filteredSymbols = ref([])

const filterSymbols = () => {
  let result = [...(props.symbols || [])]
  if (selectedCategory.value) {
    result = result.filter(s => s.category === selectedCategory.value)
  }
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(s => s.name.toLowerCase().includes(keyword))
  }
  filteredSymbols.value = result
}

watch(() => props.symbols, () => {
  filterSymbols()
}, { deep: true })

filterSymbols()

const toggleExpand = () => {
  expanded.value = !expanded.value
}

const close = () => {
  expanded.value = false
}

const selectSymbol = (symbol) => {
  emit('symbol-select', symbol)
}

const handleFileChange = async (file) => {
  const files = Array.isArray(file) ? file : [file]
  for (const f of files) {
    try {
      const content = await readFileAsText(f.raw || f)
      const name = f.name.replace('.svg', '')
      const newSymbol = {
        id: Date.now() + Math.random(),
        name: name,
        category: 'custom',
        content: content,
        fillColor: '#ff0000',
        strokeColor: '#000000',
        size: 48
      }
      const newSymbols = [...(props.symbols || []), newSymbol]
      emit('symbols-change', newSymbols)
      ElMessage.success(`已添加符号：${name}`)
    } catch (error) {
      ElMessage.error(`读取文件失败：${f.name}`)
    }
  }
}

const readFileAsText = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.onerror = () => reject(e)
    reader.readAsText(file)
  })
}

// 拖拽功能
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

const startDrag = (e) => {
  // 锁定状态下禁止拖动
  if (isLocked.value) {
    ElMessage.warning('工具面板已锁定，解锁后才能拖动')
    return
  }
  isDragging.value = true
  dragOffset.value = {
    x: e.clientX - position.value.x,
    y: e.clientY - position.value.y
  }
}

const onDrag = (e) => {
  if (!isDragging.value) return
  position.value = {
    x: e.clientX - dragOffset.value.x,
    y: e.clientY - dragOffset.value.y
  }
}

const stopDrag = () => {
  isDragging.value = false
}

watch(isDragging, (val) => {
  if (val) {
    window.addEventListener('mousemove', onDrag)
    window.addEventListener('mouseup', stopDrag)
  } else {
    window.removeEventListener('mousemove', onDrag)
    window.removeEventListener('mouseup', stopDrag)
  }
})

defineExpose({
  handleDrawEvent
})
</script>

<style scoped>
.map-tools {
  position: fixed;
  z-index: 2000;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  min-width: 320px;
  overflow: hidden;
  transition: all 0.3s;
}

.map-tools.locked {
  opacity: 0.9;
}

.map-tools.locked .tools-header {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  cursor: default;
}

.tools-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  cursor: move;
  user-select: none;
}

.tools-header .title {
  font-weight: bold;
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.header-actions .el-button {
  color: white;
}

.tools-tabs {
  padding: 0;
}

.tools-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  background: #f5f7fa;
}

.tools-tabs :deep(.el-tabs__content) {
  padding: 12px;
  max-height: 450px;
  overflow-y: auto;
}

.tab-content {
  padding: 0;
}

/* 量测工具样式 */
.measure-result {
  margin-top: 15px;
  text-align: center;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}

.result-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.result-value {
  font-size: 22px;
  font-weight: bold;
  color: #409eff;
}

.measure-tips {
  margin-top: 10px;
}

:deep(.el-radio-group) {
  width: 100%;
}

:deep(.el-radio-button) {
  flex: 1;
}

:deep(.el-radio-button__inner) {
  width: 100%;
}

/* 符号库样式 */
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.search-input {
  flex: 1;
}

.upload-section {
  margin-bottom: 12px;
}

.symbol-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(70px, 1fr));
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  padding: 4px;
}

.symbol-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;
}

.symbol-item:hover {
  background: #f0f9ff;
  border-color: #409eff;
}

.symbol-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.symbol-preview {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.symbol-preview :deep(svg) {
  max-width: 100%;
  max-height: 100%;
}

.symbol-name {
  font-size: 10px;
  color: #666;
  margin-top: 4px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.pagination-info {
  text-align: center;
  font-size: 12px;
  color: #999;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid #eee;
}
</style>
