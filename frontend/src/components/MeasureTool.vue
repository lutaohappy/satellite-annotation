<template>
  <div class="measure-tool">
    <el-card class="measure-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>量测工具</span>
          <el-button size="small" @click="clearMeasure">清除</el-button>
        </div>
      </template>

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
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { getLength, getArea } from 'ol/sphere'
import { transform } from 'ol/proj'

const props = defineProps({
  map: Object,
  vectorSource: Object,
  drawInteraction: Object,
  setDrawInteraction: Function
})

const emit = defineEmits(['measure-result'])

const measureMode = ref(null)
const measureResult = ref('')
const currentSketch = ref(null)
const changeListenerKey = ref(null)

// 结果标题
const resultTitle = computed(() => {
  if (measureMode.value === 'distance') return '距离'
  if (measureMode.value === 'area') return '面积'
  if (measureMode.value === 'coordinate') return '坐标'
  return ''
})

// 清除量测
const clearMeasure = () => {
  measureResult.value = ''
  measureMode.value = null
  removeSketchListener()
  if (props.vectorSource) {
    // 清除最后一个要素（当前绘制的草图）
    const features = props.vectorSource.getFeatures()
    if (features.length > 0) {
      props.vectorSource.removeFeature(features[features.length - 1])
    }
  }
  emit('measure-result', null)
}

// 移除草图监听
const removeSketchListener = () => {
  if (currentSketch.value && changeListenerKey.value) {
    currentSketch.value.getGeometry().un('change', changeListenerKey.value)
  }
  currentSketch.value = null
  changeListenerKey.value = null
}

// 切换量测模式
const toggleMeasure = () => {
  if (!measureMode.value) {
    removeSketchListener()
    emit('measure-result', null)
    measureResult.value = ''
    return
  }

  // 通知父组件开始对应类型的绘制
  let drawType = 'Point'
  if (measureMode.value === 'distance') drawType = 'LineString'
  if (measureMode.value === 'area') drawType = 'Polygon'

  emit('measure-result', {
    mode: measureMode.value,
    drawType: drawType,
    callback: handleDrawEvent
  })
}

// 处理绘制事件
const handleDrawEvent = (type, event) => {
  if (type === 'start') {
    currentSketch.value = event.feature
    const geom = currentSketch.value.getGeometry()

    // 监听几何体变化
    changeListenerKey.value = geom.on('change', () => {
      updateMeasureResult(geom)
    })

    // 初始计算一次
    updateMeasureResult(geom)
  } else if (type === 'end') {
    const geom = event.feature.getGeometry()
    updateMeasureResult(geom)
    removeSketchListener()
  }
}

// 更新量测结果
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

// 格式化距离
const formatDistance = (meters) => {
  if (!meters || isNaN(meters)) return '0 m'
  if (meters > 1000) {
    return (meters / 1000).toFixed(2) + ' km'
  }
  return meters.toFixed(2) + ' m'
}

// 格式化面积
const formatArea = (sqMeters) => {
  if (!sqMeters || isNaN(sqMeters)) return '0 m²'
  if (sqMeters > 1000000) {
    return (sqMeters / 1000000).toFixed(2) + ' km²'
  } else if (sqMeters > 10000) {
    return (sqMeters / 10000).toFixed(2) + ' 公顷'
  }
  return sqMeters.toFixed(2) + ' m²'
}

// 格式化坐标
const formatCoordinate = (coord) => {
  if (!coord || coord.length < 2) return '无效坐标'
  const wgs84 = transform(coord, 'EPSG:3857', 'EPSG:4326')
  return `经度：${wgs84[0].toFixed(6)}°, 纬度：${wgs84[1].toFixed(6)}°`
}

// 暴露方法给父组件
defineExpose({
  handleDrawEvent
})
</script>

<style scoped>
.measure-tool {
  position: absolute;
  top: 60px;
  left: 10px;
  z-index: 1000;
}

.measure-card {
  width: 280px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

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
</style>
