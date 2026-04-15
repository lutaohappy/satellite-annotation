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

      <!-- 影像管理 Tab -->
      <el-tab-pane label="影像" name="image">
        <div class="tab-content">
          <!-- 创建占位图项目按钮 -->
          <div class="batch-actions" style="margin-bottom: 12px;">
            <el-button size="small" type="primary" @click="loadBatches">
              <el-icon><Refresh /></el-icon> 刷新批次
            </el-button>
            <el-button size="small" type="success" @click="startCreatePlaceholderProject">
              <el-icon><Plus /></el-icon> 创建占位图项目
            </el-button>
          </div>

          <!-- 批次折叠面板 -->
          <el-collapse v-model="activeBatchIds" class="batch-collapse" accordion>
            <el-collapse-item
              v-for="batch in batches"
              :key="batch.batchUuid"
              :title="formatBatchTitle(batch)"
              :name="batch.batchUuid"
            >
              <template #title>
                <div class="batch-title">
                  <span v-if="editingBatchId === batch.batchUuid" class="batch-edit">
                    <el-input
                      v-model="editingBatchName"
                      size="small"
                      placeholder="输入批次名称"
                      @keyup.enter="confirmBatchNameEdit(batch)"
                      @blur="confirmBatchNameEdit(batch)"
                    />
                  </span>
                  <span v-else class="batch-name">
                    {{ batch.name || '未命名批次' }}
                    <el-tag size="small" type="info" style="margin-left: 5px;">{{ batch.fileCount }} 个文件</el-tag>
                  </span>
                  <div class="batch-title-actions">
                    <el-button
                      link
                      type="primary"
                      size="small"
                      @click.stop="startBatchNameEdit(batch)"
                      title="重命名"
                    >
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      size="small"
                      @click.stop="confirmDeleteBatch(batch)"
                      title="删除批次"
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="success"
                      size="small"
                      @click.stop="uploadToBatch(batch)"
                      title="继续上传"
                    >
                      <el-icon><Upload /></el-icon>
                    </el-button>
                  </div>
                </div>
              </template>
              <div class="batch-images">
                <div
                  v-for="img in getImagesForBatch(batch.batchUuid)"
                  :key="img.id"
                  class="image-item"
                  :class="{ active: imageStore.currentImage?.id === img.id }"
                  @click="selectImage(img)"
                >
                  <div class="image-info">
                    <div class="image-name">{{ img.name }}</div>
                    <div class="image-meta">
                      <span>{{ img.width }}x{{ img.height }}</span>
                      <span>{{ formatFileSize(img.fileSize) }}</span>
                    </div>
                  </div>
                  <div class="image-actions">
                    <el-button size="small" type="success" @click.stop="loadImage(img)">加载</el-button>
                    <el-button size="small" type="danger" @click.stop="deleteImage(img.id)">删除</el-button>
                  </div>
                </div>
              </div>
              <div class="batch-actions">
                <el-button size="small" @click.stop="loadAllBatchImages(batch.batchUuid)">全部加载</el-button>
              </div>
            </el-collapse-item>
          </el-collapse>

          <!-- 无批次时显示上传提示 -->
          <div v-if="batches.length === 0 && !loadingImages" class="empty-tip">
            暂无影像批次，请点击上传按钮添加
          </div>

          <!-- 上传按钮 -->
          <div class="upload-section">
            <el-button type="primary" size="small" :icon="Upload" @click="emit('open-upload')">
              上传影像
            </el-button>
          </div>

          <!-- 影像调整（如果有加载的影像） -->
          <div v-if="imageStore.currentImage" class="image-adjustment-section">
            <el-divider>影像调整</el-divider>

            <!-- 当前影像信息 -->
            <div class="current-image-info">
              <el-icon><Picture /></el-icon>
              <span class="image-name">{{ imageStore.currentImage.name }}</span>
              <el-tag size="small" type="info">编辑中</el-tag>
            </div>

            <div class="adjustment-item">
              <span class="label">亮度</span>
              <el-slider v-model="adjustParams.brightness" :min="0" :max="2" :step="0.01" @input="applyAdjustment" />
              <span class="value">{{ adjustParams.brightness.toFixed(2) }}</span>
            </div>

            <div class="adjustment-item">
              <span class="label">对比度</span>
              <el-slider v-model="adjustParams.contrast" :min="0" :max="2" :step="0.01" @input="applyAdjustment" />
              <span class="value">{{ adjustParams.contrast.toFixed(2) }}</span>
            </div>

            <div class="adjustment-item">
              <span class="label">Gamma</span>
              <el-slider v-model="adjustParams.gamma" :min="0.1" :max="3" :step="0.01" @input="applyAdjustment" />
              <span class="value">{{ adjustParams.gamma.toFixed(2) }}</span>
            </div>

            <div class="adjustment-item">
              <span class="label">透明度</span>
              <el-slider v-model="adjustParams.opacity" :min="0" :max="1" :step="0.01" @input="applyAdjustment" />
              <span class="value">{{ adjustParams.opacity.toFixed(2) }}</span>
            </div>

            <div class="adjustment-actions">
              <el-button size="small" @click="resetAdjustment">重置</el-button>
              <el-button size="small" type="primary" @click="showSaveDialog = true">保存</el-button>
              <el-button size="small" type="success" @click="emit('open-swipe')" :disabled="imageStore.images.length < 2">
                对比
              </el-button>
            </div>
          </div>

          <!-- 保存对话框 -->
          <el-dialog v-model="showSaveDialog" title="保存调整" width="400px">
            <el-radio-group v-model="saveMode" style="width: 100%">
              <el-radio value="overwrite" size="large" border style="width: 100%; margin-bottom: 10px;">
                <div style="display: flex; flex-direction: column; gap: 4px;">
                  <span style="font-weight: 500;">覆盖当前影像</span>
                  <span style="font-size: 12px; color: #999;">保存调整参数到当前影像</span>
                </div>
              </el-radio>
              <el-radio value="new" size="large" border style="width: 100%;">
                <div style="display: flex; flex-direction: column; gap: 4px;">
                  <span style="font-weight: 500;">另存为新影像</span>
                  <span style="font-size: 12px; color: #999;">创建新的影像文件</span>
                </div>
              </el-radio>
            </el-radio-group>
            <el-input v-if="saveMode === 'new'" v-model="newImageName" placeholder="请输入新影像名称" style="margin-top: 15px;" />
            <template #footer>
              <el-button @click="showSaveDialog = false">取消</el-button>
              <el-button type="primary" @click="saveAdjustment" :loading="saving">保存</el-button>
            </template>
          </el-dialog>
        </div>
      </el-tab-pane>

      <!-- 路网管理 Tab -->
      <el-tab-pane label="路网" name="road-network">
        <div class="tab-content">
          <div class="road-network-section">
            <el-button type="primary" size="small" @click="openRoadNetworkDownload" style="width: 100%; margin-bottom: 15px;">
              <el-icon><Plus /></el-icon> 下载路网
            </el-button>

            <!-- 已下载路网列表 -->
            <div v-if="roadNetworks.length > 0" class="road-network-list">
              <div v-for="network in roadNetworks" :key="network.id" class="network-item">
                <div class="network-info">
                  <el-checkbox v-model="network.visible" @change="toggleRoadNetwork(network)" :label="network.name" />
                  <el-tag size="small" type="info">{{ network.totalRoads }} 条道路</el-tag>
                </div>
                <div class="network-actions">
                  <el-button link type="primary" size="small" @click="zoomToRoadNetwork(network)">
                    <el-icon><Location /></el-icon>
                  </el-button>
                  <el-button link type="danger" size="small" @click="deleteRoadNetwork(network.id)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
            <div v-else class="empty-tip">
              暂无路网，点击"下载路网"添加
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Close, Lock, Unlock, Refresh, Picture, Edit, Delete, Plus, Location } from '@element-plus/icons-vue'
import { getLength, getArea } from 'ol/sphere'
import { transform } from 'ol/proj'
import { useImageStore } from '@/stores/image'
import { getImages, deleteImage as deleteImageApi } from '@/api/image'
import { getBatches, getImagesByBatch, saveAdjustment as saveAdjustmentApi, saveAdjusted as saveAdjustedApi, deleteBatch, updateBatch } from '@/api/batch'
import { getRoadNetworks, deleteRoadNetwork as deleteRoadNetworkApi } from '@/api/roadNetwork'

const props = defineProps({
  map: Object,
  vectorSource: Object,
  symbols: Array,
  selectedSymbol: Object
})

const emit = defineEmits([
  'measure-result',
  'symbol-select',
  'symbols-change',
  'open-library',
  'open-upload',
  'open-swipe',
  'image-load',
  'image-adjustment',
  'create-project',
  'create-placeholder-project',
  'open-road-network-download',
  'road-network-toggle',
  'road-network-zoom',
  'road-network-delete'
])

const imageStore = useImageStore()

const activeTab = ref('measure')
const expanded = ref(true)
const position = ref({ x: 10, y: 80 })
const isLocked = ref(false)

// 路网相关
const roadNetworks = ref([])
const roadNetworkLayers = ref({})

// 影像相关
const loadingImages = ref(false)
const adjustParams = ref({
  brightness: 1.0,
  contrast: 1.0,
  gamma: 1.0,
  opacity: 1.0
})

// 保存对话框相关
const showSaveDialog = ref(false)
const saveMode = ref('overwrite')
const newImageName = ref('')
const saving = ref(false)

// 批次编辑相关
const editingBatchId = ref(null)
const editingBatchName = ref('')

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

// ==================== 影像相关方法 ====================

// 批次相关状态
const batches = ref([])
const activeBatchIds = ref([])
const batchImages = ref({})

// 加载批次列表
const loadBatches = async () => {
  loadingImages.value = true
  try {
    const res = await getBatches()
    if (res.code === 200) {
      batches.value = res.data || []
      imageStore.setBatches(res.data || [])
      // 加载每个批次的影像
      for (const batch of batches.value) {
        await loadBatchImages(batch.batchUuid)
      }
    }
  } catch (error) {
    ElMessage.error('加载批次列表失败')
  } finally {
    loadingImages.value = false
  }
}

// 加载指定批次的影像
const loadBatchImages = async (batchId) => {
  try {
    const res = await getImagesByBatch(batchId)
    if (res.code === 200) {
      batchImages.value[batchId] = res.data || []
      imageStore.setImagesByBatch(batchId, res.data || [])
    }
  } catch (error) {
    ElMessage.error('加载批次影像失败')
  }
}

// 获取指定批次的影像
const getImagesForBatch = (batchId) => {
  return batchImages.value[batchId] || []
}

// 加载批次内所有影像
const loadAllBatchImages = (batchId) => {
  const images = getImagesForBatch(batchId)
  if (images.length === 0) {
    ElMessage.warning('该批次没有影像')
    return
  }
  // 逐个加载影像
  images.forEach((img, index) => {
    setTimeout(() => {
      emit('image-load', img)
    }, index * 100)
  })
  ElMessage.success(`正在加载 ${images.length} 个影像`)
}

// 格式化批次标题
const formatBatchTitle = (batch) => {
  const date = new Date(batch.createdAt)
  const dateStr = `${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}`
  return `批次 ${dateStr} - ${batch.fileCount} 个文件`
}

// 加载影像列表（保留原有方法用于兼容）
const loadImagesList = async () => {
  loadingImages.value = true
  try {
    const res = await getImages()
    if (res.code === 200) {
      imageStore.setImages(res.data || [])
    }
  } catch (error) {
    ElMessage.error('加载影像列表失败')
  } finally {
    loadingImages.value = false
  }
}

// 选择影像
const selectImage = (img) => {
  console.log('[MapTools selectImage] 选择影像:', img)
  imageStore.setCurrentImage(img)
}

// 加载影像到地图
const loadImage = (img) => {
  console.log('[MapTools loadImage] 加载影像:', img)
  // 先设置当前影像，确保保存时能获取到
  imageStore.setCurrentImage(img)
  emit('image-load', img)
}

// 删除影像
const deleteImage = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该影像吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteImageApi(id)
    imageStore.removeImage(id)
    ElMessage.success('删除成功')
    // 刷新批次列表以更新文件数量
    await loadBatches()
    // 清除批次影像缓存，确保下次获取时是最新数据
    batchImages.value = {}
    // 如果删除的是当前影像，清除当前影像状态
    if (imageStore.currentImage?.id === id) {
      imageStore.setCurrentImage(null)
      resetAdjustment()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 基于影像创建项目
const createProjectWithImage = (img) => {
  const nameInput = ref('')
  const descInput = ref('')
  const crsInput = ref('EPSG:3857')

  ElMessageBox({
    title: '新建项目',
    message: h('div', { style: 'display: flex; flex-direction: column; gap: 12px; min-width: 300px' }, [
      h('div', { style: 'display: flex; flex-direction: column; gap: 4px' }, [
        h('label', { style: 'font-size: 14px; color: #606266' }, '项目名称'),
        h('el-input', {
          modelValue: nameInput.value,
          'onUpdate:modelValue': (val) => { nameInput.value = val },
          placeholder: '请输入项目名称'
        })
      ]),
      h('div', { style: 'display: flex; flex-direction: column; gap: 4px' }, [
        h('label', { style: 'font-size: 14px; color: #606266' }, '项目描述'),
        h('el-input', {
          modelValue: descInput.value,
          'onUpdate:modelValue': (val) => { descInput.value = val },
          type: 'textarea',
          rows: 3,
          placeholder: '请输入项目描述（可选）'
        })
      ]),
      h('div', { style: 'display: flex; flex-direction: column; gap: 4px' }, [
        h('label', { style: 'font-size: 14px; color: #606266' }, '坐标系'),
        h('el-select', {
          modelValue: crsInput.value,
          'onUpdate:modelValue': (val) => { crsInput.value = val },
          placeholder: '请选择坐标系',
          style: 'width: 100%'
        }, [
          h('el-option', { label: 'WGS84 / Pseudo-Mercator (EPSG:3857)', value: 'EPSG:3857' }),
          h('el-option', { label: 'WGS84 (EPSG:4326)', value: 'EPSG:4326' }),
          h('el-option', { label: 'CGCS2000 (EPSG:4490)', value: 'EPSG:4490' })
        ])
      ])
    ]),
    showCancelButton: true,
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    customClass: 'project-create-dialog',
    beforeClose: async (action, instance, done) => {
      if (action === 'confirm') {
        if (!nameInput.value || nameInput.value.trim() === '') {
          ElMessage.warning('请输入项目名称')
          return
        }
        try {
          const token = localStorage.getItem('token')
          const response = await fetch('/api/projects', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
              name: nameInput.value.trim(),
              description: descInput.value?.trim() || '',
              crs: crsInput.value
            })
          })
          const result = await response.json()
          if (result.code === 200) {
            ElMessage.success('项目创建成功，正在加载影像...')
            emit('create-project', {
              ...result.data,
              imageId: img.id
            })
            done()
          } else {
            ElMessage.error(result.message || '创建项目失败')
          }
        } catch (error) {
          ElMessage.error('创建项目失败：' + error.message)
        }
      } else {
        done()
      }
    }
  })
}

// 开始创建占位图项目
const startCreatePlaceholderProject = () => {
  ElMessageBox.prompt('请输入项目名称', '创建占位图项目', {
    confirmButtonText: '下一步',
    cancelButtonText: '取消',
    inputPlaceholder: '项目名称',
    inputPattern: /.+/,
    inputErrorMessage: '请输入项目名称'
  }).then(async ({ value }) => {
    try {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/projects', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          name: value,
          description: '',
          crs: 'EPSG:3857',
          method: 'draw'
        })
      })
      const result = await response.json()
      if (result.code === 200) {
        ElMessage.success('请在地图上绘制矩形范围，双击完成')
        emit('create-placeholder-project', result.data)
      } else {
        ElMessage.error(result.message || '创建项目失败')
      }
    } catch (error) {
      ElMessage.error('创建项目失败：' + error.message)
    }
  }).catch(() => {})
}

// ==================== 批次管理方法 ====================

// 开始编辑批次名称
const startBatchNameEdit = (batch) => {
  editingBatchId.value = batch.batchUuid
  editingBatchName.value = batch.name || ''
}

// 确认编辑批次名称
const confirmBatchNameEdit = async (batch) => {
  if (editingBatchId.value !== batch.batchUuid) return

  try {
    const res = await updateBatch(batch.batchUuid, { name: editingBatchName.value })
    if (res.code === 200) {
      ElMessage.success('批次名称已更新')
      // 更新本地批次数据
      const batchIndex = batches.value.findIndex(b => b.batchUuid === batch.batchUuid)
      if (batchIndex >= 0) {
        batches.value[batchIndex].name = editingBatchName.value
      }
    } else {
      ElMessage.error('更新失败：' + (res.message || '未知错误'))
    }
  } catch (error) {
    ElMessage.error('更新失败：' + (error.message || '网络错误'))
  } finally {
    editingBatchId.value = null
    editingBatchName.value = ''
  }
}

// 确认删除批次
const confirmDeleteBatch = async (batch) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除批次"${batch.name || '未命名'}"吗？这将删除该批次下的所有 ${batch.fileCount} 个影像文件，操作不可恢复！`,
      '警告',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const res = await deleteBatch(batch.batchUuid)
    if (res.code === 200) {
      ElMessage.success('批次已删除')
      // 从本地列表中移除
      batches.value = batches.value.filter(b => b.batchUuid !== batch.batchUuid)
      // 清除该批次的影像缓存
      imageStore.clearBatchImages(batch.batchUuid)
    } else {
      ElMessage.error('删除失败：' + (res.message || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + (error.message || '网络错误'))
    }
  }
}

// 上传到指定批次
const uploadToBatch = (batch) => {
  // 打开通用上传对话框，传递批次信息
  emit('open-upload', { batchUuid: batch.batchUuid, batchName: batch.name })
}

// 应用影像调整（实时预览）
const applyAdjustment = () => {
  emit('image-adjustment', adjustParams.value)
}

// 重置调整
const resetAdjustment = () => {
  adjustParams.value = {
    brightness: 1.0,
    contrast: 1.0,
    gamma: 1.0,
    opacity: 1.0
  }
  applyAdjustment()
}

// 保存调整
const saveAdjustment = async () => {
  if (!imageStore.currentImage) {
    console.error('[saveAdjustment] 没有当前影像')
    return
  }

  console.log('[saveAdjustment] 开始保存')
  console.log('[saveAdjustment] 保存模式:', saveMode.value)
  console.log('[saveAdjustment] 调整参数:', adjustParams.value)
  console.log('[saveAdjustment] 当前影像:', imageStore.currentImage)

  saving.value = true
  try {
    if (saveMode.value === 'overwrite') {
      // 保存调整到当前影像（覆盖原文件）
      console.log('[saveAdjustment] 调用 saveAdjustmentApi')
      const res = await saveAdjustmentApi(imageStore.currentImage.id, {
        brightness: adjustParams.value.brightness,
        contrast: adjustParams.value.contrast,
        gamma: adjustParams.value.gamma
      })
      console.log('[saveAdjustment] 保存响应:', res)
      if (res.code === 200) {
        ElMessage.success('影像已保存')
        showSaveDialog.value = false
        // 重置调整参数
        resetAdjustment()
        // 重新加载影像以显示调整后的效果
        emit('image-load', imageStore.currentImage)
      } else {
        ElMessage.error('保存失败：' + (res.message || '未知错误'))
      }
    } else {
      // 另存为新影像
      const name = newImageName.value || `Adjusted_${imageStore.currentImage.name}`
      console.log('[saveAdjustment] 另存为新影像:', name)
      console.log('[saveAdjustment] 调用 saveAdjustedApi')
      const res = await saveAdjustedApi(
        imageStore.currentImage.id,
        {
          brightness: adjustParams.value.brightness,
          contrast: adjustParams.value.contrast,
          gamma: adjustParams.value.gamma,
          newName: name
        }
      )
      console.log('[saveAdjustment] 保存响应:', res)
      if (res.code === 200) {
        ElMessage.success('新影像已保存')
        showSaveDialog.value = false
        newImageName.value = ''
        // 重置调整参数
        resetAdjustment()
        // 延迟刷新批次列表，确保数据库已提交
        setTimeout(async () => {
          console.log('[saveAdjustment] 开始刷新批次列表')
          await loadBatches()
          console.log('[saveAdjustment] 批次列表刷新完成')
          // 刷新后再加载新影像
          if (res.data && res.data.id) {
            console.log('[saveAdjustment] 加载新影像:', res.data.id)
            emit('image-load', res.data)
          }
        }, 500)
      } else {
        ElMessage.error('保存失败：' + (res.message || '未知错误'))
      }
    }
  } catch (error) {
    console.error('[saveAdjustment] 错误:', error)
    ElMessage.error('保存失败：' + (error.message || '网络错误'))
  } finally {
    saving.value = false
  }
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

// 监听 tab 切换
watch(activeTab, (newTab) => {
  if (newTab === 'image') {
    loadBatches()
  } else if (newTab === 'road-network') {
    loadRoadNetworks()
  }
})

// ==================== 路网管理方法 ====================

// 加载路网列表
const loadRoadNetworks = async () => {
  try {
    const res = await getRoadNetworks()
    if (res.code === 200) {
      roadNetworks.value = (res.data || []).map(n => ({
        ...n,
        visible: false
      }))
    }
  } catch (error) {
    console.error('加载路网列表失败:', error)
  }
}

// 打开路网下载对话框
const openRoadNetworkDownload = () => {
  emit('open-road-network-download')
}

// 切换路网显示
const toggleRoadNetwork = async (network) => {
  emit('road-network-toggle', network)
}

// 定位到路网
const zoomToRoadNetwork = (network) => {
  emit('road-network-zoom', network)
}

// 删除路网
const deleteRoadNetwork = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该路网吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteRoadNetworkApi(id)
    ElMessage.success('删除成功')
    emit('road-network-delete', id)
    loadRoadNetworks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + error.message)
    }
  }
}

// 暴露方法
defineExpose({
  handleDrawEvent,
  loadBatches
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

/* 影像管理样式 */
.image-list {
  max-height: 200px;
  overflow-y: auto;
  margin-bottom: 12px;
}

.image-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 8px;
  background: #f5f7fa;
}

.image-item:hover {
  background: #ecf5ff;
  border-color: #409eff;
}

.image-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.image-info {
  flex: 1;
  min-width: 0;
}

.image-name {
  font-size: 14px;
  color: #333;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.image-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  display: flex;
  gap: 10px;
}

.image-actions {
  display: flex;
  gap: 6px;
  margin-left: 10px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 20px;
  font-size: 14px;
}

.image-adjustment-section {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #eee;
}

.adjustment-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;
}

.adjustment-item .label {
  width: 50px;
  font-size: 14px;
  color: #666;
}

.adjustment-item .value {
  width: 50px;
  text-align: right;
  font-size: 14px;
  color: #409eff;
}

.adjustment-item :deep(.el-slider) {
  flex: 1;
}

.adjustment-item :deep(.el-slider__runway) {
  height: 6px;
}

.adjustment-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 15px;
}

/* 批次折叠面板样式 */
.batch-collapse {
  margin-bottom: 12px;
}

.batch-collapse :deep(.el-collapse-item__header) {
  font-weight: 500;
  font-size: 14px;
  background: #f5f7fa;
  padding: 10px 15px;
}

.batch-collapse :deep(.el-collapse-item__content) {
  padding: 10px 15px;
}

.batch-images {
  margin-bottom: 10px;
}

.batch-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #eee;
}

/* 批次标题样式 */
.batch-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.batch-title-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.batch-title-actions .el-button {
  padding: 4px;
  min-width: auto;
}

.batch-edit {
  flex: 1;
}

.batch-edit :deep(.el-input) {
  max-width: 200px;
}
</style>
