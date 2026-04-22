<template>
  <div class="map-container">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button-group>
        <el-tooltip content="放大"><el-button @click="zoomIn">+</el-button></el-tooltip>
        <el-tooltip content="缩小"><el-button @click="zoomOut">-</el-button></el-tooltip>
        <el-tooltip content="全图"><el-button @click="zoomToExtent">全局</el-button></el-tooltip>
      </el-button-group>

      <el-divider direction="vertical" />

      <el-button-group>
        <el-tooltip content="点标注"><el-button :type="drawType==='Point' && !selectedSymbol ?'primary':''" @click="drawPoint">点</el-button></el-tooltip>
        <el-tooltip content="线标注"><el-button :type="drawType==='LineString'?'primary':''" @click="setDrawType('LineString')">线</el-button></el-tooltip>
        <el-tooltip content="面标注"><el-button :type="drawType==='Polygon'?'primary':''" @click="setDrawType('Polygon')">面</el-button></el-tooltip>
      </el-button-group>

      <el-divider direction="vertical" />

      <el-button-group>
        <el-tooltip content="绘制圆形"><el-button :type="drawType==='Circle'?'primary':''" @click="setDrawType('Circle')">圆</el-button></el-tooltip>
        <el-tooltip content="绘制矩形"><el-button :type="drawType==='Rectangle'?'primary':''" @click="setDrawType('Rectangle')">矩形</el-button></el-tooltip>
      </el-button-group>

      <el-divider direction="vertical" />

      <el-button-group>
        <el-tooltip content="选择要素"><el-button :type="editMode==='select'?'warning':''" @click="setEditMode('select')">选择</el-button></el-tooltip>
        <el-tooltip content="删除要素"><el-button :type="editMode==='delete'?'danger':''" @click="setEditMode('delete')">删除</el-button></el-tooltip>
        <el-tooltip content="空闲状态"><el-button :type="!editMode && !drawType ?'success':''" @click="clearToIdle">空闲</el-button></el-tooltip>
      </el-button-group>

      <el-divider direction="vertical" />

      <!-- 颜色选择器 -->
      <el-tooltip content="修改选中要素颜色">
        <div class="color-picker-wrapper">
          <el-color-picker v-model="selectedColor" :predefine="predefineColors" @change="changeFeatureColor" :disabled="!selectedFeature" size="small" />
        </div>
      </el-tooltip>

      <el-button @click="clearAll" type="danger">清除全部</el-button>

      <!-- 符号库编辑按钮 -->
      <el-button @click="openSymbolLibrary">符号库</el-button>

      <el-divider direction="vertical" />

      <!-- 影像功能 -->
      <el-tooltip content="上传影像">
        <el-button @click="openImageUpload">
          <el-icon><Picture /></el-icon>
        </el-button>
      </el-tooltip>

      <el-divider direction="vertical" />

      <!-- 项目管理 -->
      <el-button-group>
        <el-tooltip content="新建项目"><el-button @click="openNewProjectDialog">新建项目</el-button></el-tooltip>
        <el-tooltip content="打开项目"><el-button @click="openProjectListDialog">打开项目</el-button></el-tooltip>
        <el-tooltip content="保存当前标注"><el-button @click="saveCurrentProject">保存标注</el-button></el-tooltip>
        <el-tooltip content="导出 Shapefile"><el-button @click="exportShapefile">导出 SHP</el-button></el-tooltip>
      </el-button-group>

      <div class="user-info">
        <span>{{ userStore.username }}</span>
        <el-button link type="primary" @click="handleLogout">退出</el-button>
      </div>
    </div>

    <!-- 地图容器 -->
    <div ref="baseMapRef" class="map"></div>

    <!-- 工具面板组件（量测 + 符号库 + 影像） -->
    <MapTools
      ref="mapToolsRef"
      v-if="mapInitialized"
      :map="map"
      :vector-source="vectorSource"
      :symbols="symbols"
      :selected-symbol="selectedSymbol"
      @measure-result="handleMeasureResult"
      @symbol-select="handleToolSymbolSelect"
      @symbols-change="handleSymbolsChange"
      @open-upload="openImageUpload"
      @open-swipe="openImageSwipe"
      @image-load="handleImageLoad"
      @image-adjustment="handleImageAdjustment"
      @create-project="handleCreateProjectFromTool"
      @create-placeholder-project="handleCreatePlaceholderProject"
      @open-road-network-download="showRoadNetworkDialog = true"
      @truck-analysis-select="handleTruckAnalysisSelect"
      @truck-analysis-locate="handleTruckAnalysisLocate"
      @truck-analysis-hide="handleTruckAnalysisHide"
    />

    <!-- 状态栏 -->
    <div class="status-bar">
      <span>坐标：{{ coordinate }}</span>
      <el-divider direction="vertical" />
      <span>缩放级别：{{ zoomLevel }}</span>
      <el-divider direction="vertical" />
      <span v-if="statusMessage">{{ statusMessage }}</span>
      <span class="version-info">{{ BUILD_VERSION }}</span>
    </div>

    <!-- 符号编辑对话框 -->
    <SymbolEditDialog
      v-model="symbolEditDialogVisible"
      :initial-symbols="symbols"
      @symbols-change="handleSymbolsChange"
    />

    <!-- 项目管理对话框 -->
    <ProjectDialog
      v-model="projectDialogVisible"
      :mode="projectDialogMode"
      :current-project="currentProject"
      @create="handleCreateProject"
      @open="handleOpenProject"
      @close="projectDialogVisible = false"
    />

    <!-- 右键菜单 -->
    <div
      v-if="contextMenuVisible"
      class="context-menu"
      :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
      @click.stop
    >
      <div class="context-menu-item" @click="copyFeature">
        <el-icon><Connection /></el-icon>
        <span>复制</span>
      </div>
      <div class="context-menu-item" @click="pasteFeature" :class="{ disabled: !copiedFeature }">
        <el-icon><DocumentCopy /></el-icon>
        <span>粘贴</span>
      </div>
    </div>

    <!-- 影像上传对话框 -->
    <ImageUpload
      v-model="imageUploadVisible"
      :projectId="currentProject?.id"
      :batch-uuid="uploadBatchUuid"
      :batch-name="uploadBatchName"
      @uploaded="handleImageUploaded"
    />

    <!-- 影像对比对话框 -->
    <ImageSwipe v-model="imageSwipeVisible" />

    <!-- 路网下载对话框 - 全屏模式 -->
    <el-dialog
      v-model="showRoadNetworkDialog"
      title="路网数据下载"
      width="90%"
      top="5vh"
      :close-on-click-modal="false"
      class="road-network-dialog"
      append-to-body
    >
      <RoadNetworkDownloader
        :map="map"
        @draw-start="handleRoadNetworkDrawStart"
        @download-complete="handleRoadNetworkDownloadComplete"
      />
    </el-dialog>

    <!-- 货车分析面板 -->
    <TruckAnalysisPanel
      v-if="showTruckAnalysisPanel"
      :map="map"
      @select-start="handleTruckSelectStart"
      @select-end="handleTruckSelectEnd"
      @show-route="handleShowRoute"
      @locate-point="handleLocatePoint"
    />
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useImageStore } from '@/stores/image'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, DocumentCopy, Picture } from '@element-plus/icons-vue'
import MapTools from '@/components/MapTools.vue'
import SymbolEditDialog from '@/components/SymbolEditDialog.vue'
import ProjectDialog from '@/components/ProjectDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import ImageSwipe from '@/components/ImageSwipe.vue'
import RoadNetworkDownloader from '@/components/RoadNetworkDownloader.vue'
import TruckAnalysisPanel from '@/components/TruckAnalysisPanel.vue'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import ImageLayer from 'ol/layer/Image'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import Static from 'ol/source/ImageStatic'
import Feature from 'ol/Feature'
import { fromLonLat, toLonLat } from 'ol/proj'
import MousePosition from 'ol/control/MousePosition'
import { Draw, Modify, Select, Translate } from 'ol/interaction'
import XYZ from 'ol/source/XYZ'
import { Style, Icon, Stroke, Fill, Circle, Text } from 'ol/style'
import { Circle as CircleGeom, Polygon, Point, LineString } from 'ol/geom'
import { fromExtent } from 'ol/geom/Polygon'
import GeoJSON from 'ol/format/GeoJSON'
import Overlay from 'ol/Overlay'
import { getImages, getImage, getImageFileUrl, getImagePreviewUrl, createPlaceholder } from '@/api/image'
import { getRoadNetworks } from '@/api/roadNetwork'
import { Location, Delete, Refresh } from '@element-plus/icons-vue'

// 版本号
const BUILD_VERSION = 'v3.0 (20260421-47-路段表格优化)'
console.log('[MapView] 当前版本:', BUILD_VERSION)
console.log('%c [Map] 当前版本:', 'background: #f00; color: #fff; font-size: 16px;', BUILD_VERSION)
console.log('%c [Map] 双层地图架构：底层 (底图 + 影像) + 上层 (矢量标注)', 'background: #00f; color: #fff; font-size: 14px;')

const router = useRouter()
const userStore = useUserStore()
const mapRef = ref(null)
const baseMapRef = ref(null)  // 地图容器

let map = null  // 地图引用
let vectorSource = null
let vectorLayer = null
let tileLayer = null  // OSM 底图图层
let draw = null
let modify = null
let select = null
let translate = null

const drawType = ref(null)
const coordinate = ref('0, 0')
const zoomLevel = ref(0)
const statusMessage = ref('')
const mapInitialized = ref(false)
const isMeasureMode = ref(false)
const measureCallback = ref(null)

// 符号相关
const selectedSymbol = ref(null)
const symbolEditDialogVisible = ref(false)

// MapTools 引用
const mapToolsRef = ref(null)

// 货车分析相关
const showRoadNetworkDialog = ref(false)
const showTruckAnalysisPanel = ref(false)
const truckAnalysisSelectMode = ref(null) // 'select-start' or 'select-end'
let roadNetworkDrawInteraction = null

// 路网图层相关
const roadNetworks = ref([]) // 已下载的路网列表

// 项目管理相关
const projectDialogVisible = ref(false)
const projectDialogMode = ref('create') // 'create' or 'open'
const currentProject = ref(null) // 当前打开的项目
const allAnnotations = ref([]) // 当前地图上的所有标注

// 编辑相关
const editMode = ref(null) // 'select', 'delete', 'modify'
const selectedFeature = ref(null)
const originalFeatureStyle = ref(null) // 保存原始样式
const selectedColor = ref('#409eff')
const predefineColors = ref([
  '#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399',
  '#ff0000', '#00ff00', '#0000ff', '#ffff00', '#00ffff', '#ff00ff'
])

// 复制/粘贴相关
const copiedFeature = ref(null) // 复制的要素
const contextMenuVisible = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })
const contextMenuFeature = ref(null) // 右键点击的要素

// 符号数据 - 从 localStorage 读取已保存的符号
const defaultSymbols = [
  {
    id: 1,
    name: '雷达站',
    category: 'military',
    content: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><circle cx="50" cy="50" r="40" fill="none" stroke="red" stroke-width="3"/><circle cx="50" cy="50" r="25" fill="none" stroke="red" stroke-width="2"/><circle cx="50" cy="50" r="10" fill="red"/><line x1="50" y1="10" x2="50" y2="90" stroke="red" stroke-width="2"/><line x1="10" y1="50" x2="90" y2="50" stroke="red" stroke-width="2"/></svg>',
    fillColor: '#ff0000',
    strokeColor: '#ff0000',
    size: 48
  },
  {
    id: 2,
    name: '机场',
    category: 'transport',
    content: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><path d="M50 15 L60 40 L85 40 L65 50 L75 75 L50 60 L25 75 L35 50 L15 40 L40 40 Z" fill="#3399ff" stroke="#0066cc" stroke-width="2"/></svg>',
    fillColor: '#3399ff',
    strokeColor: '#0066cc',
    size: 48
  },
  {
    id: 3,
    name: '医院',
    category: 'landmark',
    content: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><rect x="35" y="10" width="30" height="80" fill="#ff4444"/><rect x="10" y="35" width="80" height="30" fill="#ff4444"/><rect x="40" y="20" width="20" height="60" fill="#ffffff"/><rect x="20" y="40" width="60" height="20" fill="#ffffff"/></svg>',
    fillColor: '#ff4444',
    strokeColor: '#cc0000',
    size: 48
  },
  {
    id: 4,
    name: '森林',
    category: 'nature',
    content: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><polygon points="50,10 70,50 60,50 75,80 25,80 40,50 30,50" fill="#228B22" stroke="#006400" stroke-width="2"/><rect x="45" y="80" width="10" height="15" fill="#8B4513"/></svg>',
    fillColor: '#228B22',
    strokeColor: '#006400',
    size: 48
  },
  {
    id: 5,
    name: '学校',
    category: 'landmark',
    content: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><path d="M10 40 L50 15 L90 40 L50 65 Z" fill="#ffcc00" stroke="#cc9900" stroke-width="2"/><rect x="25" y="65" width="50" height="30" fill="#ffcc00" stroke="#cc9900" stroke-width="2"/></svg>',
    fillColor: '#ffcc00',
    strokeColor: '#cc9900',
    size: 48
  },
  {
    id: 6,
    name: '加油站',
    category: 'transport',
    content: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><rect x="20" y="30" width="50" height="50" fill="#ff6600" stroke="#cc5500" stroke-width="2"/><text x="30" y="65" fill="white" font-size="20" font-weight="bold">GAS</text></svg>',
    fillColor: '#ff6600',
    strokeColor: '#cc5500',
    size: 48
  }
]

// 从 localStorage 读取已保存的符号
const loadSymbolsFromStorage = () => {
  try {
    const saved = localStorage.getItem('satellite_annotation_symbols')
    if (saved) {
      const savedSymbols = JSON.parse(saved)
      // 合并默认符号和保存的符号（去重）
      const defaultIds = defaultSymbols.map(s => s.id)
      const customSymbols = savedSymbols.filter(s => !defaultIds.includes(s.id))
      return [...defaultSymbols, ...customSymbols]
    }
  } catch (e) {
    console.error('加载符号失败:', e)
  }
  return [...defaultSymbols]
}

// 保存符号到 localStorage
const saveSymbolsToStorage = (symbolsData) => {
  try {
    localStorage.setItem('satellite_annotation_symbols', JSON.stringify(symbolsData))
  } catch (e) {
    console.error('保存符号失败:', e)
  }
}

const symbols = ref(loadSymbolsFromStorage())

// 影像相关
const imageStore = useImageStore()
const imageUploadVisible = ref(false)
const imageSwipeVisible = ref(false)
const imageLayer = ref(null)

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const initMap = () => {
  console.log('[InitMap] 开始初始化单层地图架构')

  // 创建 XYZ 瓦片图层（使用 OSM 作为测试）
  tileLayer = new TileLayer({
    source: new XYZ({
      url: 'https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      crossOrigin: 'anonymous'
    }),
    visible: true,
    zIndex: 0
  })

  // 空影像图层（后续加载 GeoTIFF 时设置 source）
  imageLayer.value = new ImageLayer({
    source: null,
    visible: false,
    zIndex: 1
  })

  // 鼠标位置显示
  const mousePositionControl = new MousePosition({
    coordinateFormat: function (coordinate) {
      return coordinate[0].toFixed(2) + ', ' + coordinate[1].toFixed(2)
    },
    projection: 'EPSG:3857',
    className: 'mouse-position'
  })

  // 创建视图
  const mapView = new View({
    center: fromLonLat([116.4, 39.9]),
    zoom: 10,
    minZoom: 2,
    maxZoom: 18
  })

  // ========== 单层地图：所有图层都在一个 Map 中 ==========
  vectorSource = new VectorSource()
  vectorLayer = new VectorLayer({
    source: vectorSource,
    style: (feature) => {
      const featureStyle = feature.getStyle()
      if (featureStyle) {
        return featureStyle
      }
      return getDefaultStyle()
    },
    renderMode: 'hybrid',
    zIndex: 2  // 关键：矢量图层始终在最上面
  })

  // 创建地图 - 所有图层都在一个 Map 中
  map = new Map({
    target: baseMapRef.value,
    layers: [tileLayer, imageLayer.value, vectorLayer],  // 按顺序添加
    view: mapView,
    controls: [mousePositionControl],
    interactions: []
  })

  // 监听视图变化（用于状态栏）
  mapView.on('change:center', updateStatus)
  mapView.on('change:resolution', updateStatus)

  // ========== 交互添加到地图 ==========
  console.log('[Select init] 创建 Select 交互，版本:', BUILD_VERSION)
  select = new Select({
    style: null,
    // 排除货车分析的标记点（起点/终点）
    filter: (feature) => {
      const pointType = feature.get('pointType')
      if (pointType === 'start' || pointType === 'end') {
        return false  // 不选择起点/终点标记
      }
      return true
    }
  })
  map.addInteraction(select)

  // 监听右键点击事件
  map.on('contextmenu', (e) => {
    e.preventDefault()
    contextMenuVisible.value = false

    const clickedFeature = map.forEachFeatureAtPixel(e.pixel, (feature) => feature)

    contextMenuPosition.value = {
      x: e.originalEvent.clientX,
      y: e.originalEvent.clientY
    }

    if (copiedFeature.value) {
      if (clickedFeature) {
        contextMenuFeature.value = clickedFeature
      }
      contextMenuVisible.value = true
    } else if (clickedFeature) {
      contextMenuFeature.value = clickedFeature
      contextMenuVisible.value = true
    }
  })

  map.on('click', () => {
    contextMenuVisible.value = false
  })

  // 货车分析选点处理（旧流程：从 TruckAnalysisPanel 来）
  map.on('click', (e) => {
    console.log('[MapClick] truckAnalysisSelectMode:', truckAnalysisSelectMode.value)
    console.log('[MapClick] truckAnalysisCallback:', truckAnalysisCallback)

    // 货车分析 - 选择起点（旧流程）
    if (truckAnalysisSelectMode.value === 'select-start') {
      const coord = e.coordinate
      const lonLat = [coord[0], coord[1]]
      console.log('[MapClick] 选择起点，坐标:', coord)
      if (truckAnalysisCallback) {
        truckAnalysisCallback(coord[1], coord[0])
      }
      truckAnalysisSelectMode.value = null
      ElMessage.success('起点已选择')
      return
    }

    // 货车分析 - 选择终点（旧流程）
    if (truckAnalysisSelectMode.value === 'select-end') {
      const coord = e.coordinate
      console.log('[MapClick] 选择终点，坐标:', coord)
      if (truckAnalysisCallback) {
        truckAnalysisCallback(coord[1], coord[0])
      }
      truckAnalysisSelectMode.value = null
      ElMessage.success('终点已选择')
      return
    }

    // 货车分析选点（新流程：从 MapTools 来）
    if (truckAnalysisSelectCallback && truckAnalysisSelectStep) {
      const coord = e.coordinate
      // 将墨卡托坐标转换为经纬度
      const lonLat = toLonLat(coord)
      const lat = lonLat[1]
      const lon = lonLat[0]

      if (truckAnalysisSelectStep === 'start') {
        // 选择起点
        truckAnalysisSelectCallback(lat, lon, 'start')
        truckAnalysisSelectStep = 'end'
        ElMessage.success('起点已选择，请选择终点')
      } else if (truckAnalysisSelectStep === 'end') {
        // 选择终点
        truckAnalysisSelectCallback(lat, lon, 'end')
        truckAnalysisSelectCallback = null
        truckAnalysisSelectStep = null
        ElMessage.success('终点已选择，请点击"计算分析"')
      }
      return
    }

    if (editMode.value !== 'delete') return

    const clickedFeature = map.forEachFeatureAtPixel(e.pixel, (feature) => feature)
    if (clickedFeature) {
      console.log('[Delete] 删除要素:', clickedFeature)
      vectorSource.removeFeature(clickedFeature)
      if (vectorLayer) {
        vectorLayer.changed()
      }
      statusMessage.value = '已删除要素'
    }
  })

  // 监听选择变化 - 手动处理符号高亮
  select.on('select', (e) => {
    const selected = e.selected || []
    const deselected = e.deselected || []

    console.log('[Select] === select 事件开始 ===')
    console.log('[Select] selected:', selected.length, 'deselected:', deselected.length)
    console.log('[Select] 版本:', BUILD_VERSION)

    // 恢复被取消选择的要素的原始样式
    deselected.forEach(feature => {
      // 优先使用保存的 originalStyle
      const savedStyle = feature.get('originalStyle')
      if (savedStyle) {
        console.log('[Select deselect] 恢复要素原始样式')
        feature.setStyle(savedStyle)
        feature.set('originalStyle', null)
        // 触发矢量图层重绘
        if (vectorLayer) {
          vectorLayer.changed()
        }
      }
    })

    if (selected.length > 0) {
      const feature = selected[0]
      selectedFeature.value = feature

      // 检查是否是符号 - 使用多种方法判断
      const isSymbolFeature = feature.get('isSymbol') === true
      const geomType = feature.getGeometry()?.getType()

      // 从自定义属性获取保存的符号样式
      const savedSymbolStyle = feature.get('symbolStyle')
      console.log('[Select] feature:', feature)
      console.log('[Select] feature keys:', feature.getKeys())
      console.log('[Select] savedSymbolStyle:', savedSymbolStyle)
      console.log('[Select] feature.get("isSymbol"):', feature.get('isSymbol'), 'geomType:', geomType)

      if (isSymbolFeature && savedSymbolStyle) {
        // 符号 - 保存原始样式并设置高亮样式（外框圆圈 + 原始符号）
        console.log('[Select] 符号要素 - 设置符号外框样式')
        feature.set('originalStyle', savedSymbolStyle)
        feature.set('isSymbol', true)
        // 创建包含原始符号和外框的样式数组
        const highlightStyles = []
        // 先画外框
        highlightStyles.push(new Style({
          image: new Circle({
            radius: 25,
            stroke: new Stroke({ color: '#ff0000', width: 3, lineDash: [5, 5] }),
            fill: null
          })
        }))
        // 再画原始符号
        highlightStyles.push(new Style({
          image: savedSymbolStyle.getImage()
        }))
        console.log('[Select] highlightStyles:', highlightStyles)
        console.log('[Select] 符号 - 设置样式前，feature style:', feature.getStyle())
        feature.setStyle(highlightStyles)
        console.log('[Select] 符号 - 设置样式后，feature style:', feature.getStyle())
      } else {
        // 普通点线面 - 手动设置高亮样式
        console.log('[Select] 普通点线面 - 设置高亮样式')
        feature.set('isSymbol', false)
        // 保存原始样式 - 如果要素自己没有样式，使用默认样式
        let originalStyle = feature.getStyle()
        if (!originalStyle) {
          originalStyle = getDefaultStyle()
        }
        console.log('[Select] 普通点线面 - 原始样式:', originalStyle)
        feature.set('originalStyle', originalStyle)
        // 获取原始样式的颜色
        const styleArray = Array.isArray(originalStyle) ? originalStyle : [originalStyle]
        const fill = styleArray[0]?.getFill?.()
        if (fill) {
          selectedColor.value = fill.getColor()
        }
        // 设置高亮样式
        const highlightStyle = new Style({
          fill: new Fill({ color: 'rgba(255, 0, 0, 0.3)' }),
          stroke: new Stroke({ color: '#ff0000', width: 3, lineDash: [5, 5] }),
          image: new Circle({
            radius: 8,
            fill: new Fill({ color: '#ff0000' }),
            stroke: new Stroke({ color: '#fff', width: 2 })
          })
        })
        console.log('[Select] 普通点线面 - 设置高亮样式:', highlightStyle)
        feature.setStyle(highlightStyle)
        console.log('[Select] 普通点线面 - 设置样式后，feature style:', feature.getStyle())
      }

      statusMessage.value = '已选中要素，可拖动顶点修改形状'
    } else {
      selectedFeature.value = null
      statusMessage.value = ''
    }
    console.log('[Select] === select 事件结束 ===')
  })

  // 添加修改交互 - 使用 features 集合，只修改选中的要素
  // Modify 本身就支持顶点编辑和整体移动
  modify = new Modify({
    features: select.getFeatures(),
    style: new Style({
      image: new Circle({
        radius: 5,
        fill: new Fill({ color: '#ff0000' }),
        stroke: new Stroke({ color: '#fff', width: 2 })
      })
    }),
    hitTolerance: 10
  })
  map.addInteraction(modify)

  // 添加平移交互 - 用于整体移动
  translate = new Translate({
    features: select.getFeatures(),
    hitTolerance: 5
  })
  map.addInteraction(translate)

  // 用于保存拖动状态
  let isOnVertex = false
  let draggedVertexIndex = -1 // 对于矩形：0=左下，1=右下，2=右上，3=左上
  let draggedDirection = null // 对于圆形：'east', 'south', 'west', 'north'
  let originalFeatureCoords = null // 保存原始要素坐标
  let originalCircleCenter = null // 保存原始圆心
  let pendingConstraining = false // 等待进行约束编辑（在 modifystart 中确认）

  // 检查点是否在顶点附近，并记录被拖动的顶点
  const checkVertex = (feature, pixel) => {
    const geometry = feature.getGeometry()
    if (!geometry) return false

    const geomType = feature.get('geomType')
    console.log('[CheckVertex] geomType:', geomType)

    const actualType = geometry.getType()
    let coordinates = []

    if (actualType === 'Polygon') {
      coordinates = geometry.getCoordinates()[0]
    } else if (actualType === 'LineString') {
      coordinates = geometry.getCoordinates()
    } else if (actualType === 'Point') {
      const coord = geometry.getCoordinates()
      coordinates = [coord]
    }

    console.log('[CheckVertex] coordinates:', coordinates)

    // 对于矩形和圆，只检查 4 个关键点
    if (geomType === 'Rectangle') {
      console.log('[CheckVertex] Checking Rectangle vertices')
      // 矩形：按顺序检查 4 个角点
      const rectPoints = [coordinates[0], coordinates[1], coordinates[2], coordinates[3]]
      for (let i = 0; i < 4; i++) {
        const vertexPixel = map.getPixelFromCoordinate(rectPoints[i])
        if (vertexPixel) {
          const dx = pixel[0] - vertexPixel[0]
          const dy = pixel[1] - vertexPixel[1]
          const dist = Math.sqrt(dx * dx + dy * dy)
          console.log('[CheckVertex] Rect point', i, 'dist:', dist)
          if (dist < 15) {
            draggedVertexIndex = i
            console.log('[CheckVertex] Rectangle vertex detected, index:', i)
            return true
          }
        }
      }
    } else if (geomType === 'Circle') {
      console.log('[CheckVertex] Checking Circle vertices')
      // 圆：检查 4 个关键点（东、南、西、北）
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity
      coordinates.forEach(coord => {
        minX = Math.min(minX, coord[0])
        maxX = Math.max(maxX, coord[0])
        minY = Math.min(minY, coord[1])
        maxY = Math.max(maxY, coord[1])
      })
      const centerX = (minX + maxX) / 2
      const centerY = (minY + maxY) / 2
      const circlePoints = [
        { index: 0, coord: [maxX, centerY], dir: 'east' },
        { index: 8, coord: [centerX, maxY], dir: 'south' },
        { index: 16, coord: [minX, centerY], dir: 'west' },
        { index: 24, coord: [centerX, minY], dir: 'north' }
      ]
      for (const pt of circlePoints) {
        const vertexPixel = map.getPixelFromCoordinate(pt.coord)
        if (vertexPixel) {
          const dx = pixel[0] - vertexPixel[0]
          const dy = pixel[1] - vertexPixel[1]
          const dist = Math.sqrt(dx * dx + dy * dy)
          console.log('[CheckVertex] Circle point', pt.dir, 'dist:', dist)
          if (dist < 15) {
            draggedVertexIndex = pt.index
            draggedDirection = pt.dir
            originalCircleCenter = [centerX, centerY]
            console.log('[CheckVertex] Circle vertex detected, dir:', pt.dir, 'center:', originalCircleCenter)
            return true
          }
        }
      }
    } else {
      // 普通点线面：检查所有顶点
      console.log('[CheckVertex] Checking normal feature vertices')
      for (let i = 0; i < coordinates.length; i++) {
        const vertexPixel = map.getPixelFromCoordinate(coordinates[i])
        if (vertexPixel) {
          const dx = pixel[0] - vertexPixel[0]
          const dy = pixel[1] - vertexPixel[1]
          const dist = Math.sqrt(dx * dx + dy * dy)
          if (dist < 15) {
            console.log('[CheckVertex] Normal vertex detected, index:', i)
            return true
          }
        }
      }
    }

    console.log('[CheckVertex] No vertex detected')
    return false
  }

  // 监听指针按下事件，判断是平移还是编辑
  map.on('pointerdown', (e) => {
    if (editMode.value !== 'select') return

    const feature = select.getFeatures().getArray()[0]
    console.log('[PointerDown] feature:', feature)
    if (!feature) return

    // 检查是否在顶点上按下
    isOnVertex = checkVertex(feature, e.pixel)
    console.log('[PointerDown] isOnVertex:', isOnVertex)
    console.log('[PointerDown] draggedVertexIndex after checkVertex:', draggedVertexIndex)

    // 保存原始坐标，用于约束计算
    if (isOnVertex) {
      const geometry = feature.getGeometry()
      if (geometry) {
        originalFeatureCoords = geometry.getCoordinates()
        pendingConstraining = true
        console.log('[PointerDown] Saved originalFeatureCoords:', originalFeatureCoords)
        console.log('[PointerDown] pendingConstraining:', pendingConstraining)
        console.log('[PointerDown] Final state - draggedVertexIndex:', draggedVertexIndex, 'draggedDirection:', draggedDirection)
      }
    }

    // 如果在顶点上，禁用 translate（让 modify 工作）
    // 如果不在顶点上，启用 translate（整体移动）
    translate.setActive(!isOnVertex)
  })

  // 在 modify 开始时确认约束编辑状态
  modify.on('modifystart', (e) => {
    console.log('[ModifyStart] modifystart triggered')
    console.log('[ModifyStart] pendingConstraining:', pendingConstraining)
    console.log('[ModifyStart] draggedVertexIndex:', draggedVertexIndex)

    // 如果之前有 pending 的约束编辑，在这里确认
    if (pendingConstraining && draggedVertexIndex >= 0) {
      const feature = e.features.getArray()[0]
      if (feature) {
        const geomType = feature.get('geomType')
        console.log('[ModifyStart] Confirming constraining for:', geomType)
      }
    }
  })

  // 拖动结束后重置状态
  map.on('pointerup', () => {
    console.log('[PointerUp] pointerup triggered')
    console.log('[PointerUp] pendingConstraining:', pendingConstraining)
    console.log('[PointerUp] draggedVertexIndex:', draggedVertexIndex)
    // 不立即重置状态，等待 modifyend 处理完成后再重置
  })

  // 应用形状约束
  const applyShapeConstraints = (features) => {
    console.log('[ShapeConstraint] applyShapeConstraints called, features count:', features.length)
    console.log('[ShapeConstraint] draggedVertexIndex:', draggedVertexIndex, 'draggedDirection:', draggedDirection)
    console.log('[ShapeConstraint] originalFeatureCoords:', originalFeatureCoords)
    console.log('[ShapeConstraint] originalCircleCenter:', originalCircleCenter)
    console.log('[ShapeConstraint] pendingConstraining:', pendingConstraining)

    features.forEach(feature => {
      const geomType = feature.get('geomType')
      console.log('[ShapeConstraint] feature geomType:', geomType)

      if (geomType !== 'Rectangle' && geomType !== 'Circle') {
        console.log('[ShapeConstraint] Not rectangle or circle, skipping')
        return
      }

      const geometry = feature.getGeometry()
      if (!geometry) {
        console.log('[ShapeConstraint] No geometry, skipping')
        return
      }

      if (geomType === 'Rectangle') {
        console.log('[ShapeConstraint] Processing Rectangle constraint')
        const currentCoords = geometry.getCoordinates()[0]
        console.log('[ShapeConstraint] Rectangle currentCoords:', currentCoords)
        if (!currentCoords || currentCoords.length < 5) {
          console.log('[ShapeConstraint] Invalid coords, skipping')
          return
        }

        // 使用保存的拖动顶点索引
        if (draggedVertexIndex >= 0 && draggedVertexIndex < 4 && originalFeatureCoords) {
          console.log('[ShapeConstraint] Applying rectangle constraint with draggedVertexIndex:', draggedVertexIndex)
          // 当前被拖动的点位置（从当前坐标获取）
          const draggedCoord = currentCoords[draggedVertexIndex]
          console.log('[ShapeConstraint] draggedCoord:', draggedCoord)

          // 对角点固定
          const oppositeIndex = (draggedVertexIndex + 2) % 4
          const fixedPoint = originalFeatureCoords[0][oppositeIndex]
          console.log('[ShapeConstraint] oppositeIndex:', oppositeIndex, 'fixedPoint:', fixedPoint)

          // 计算新的矩形：拖动点与对角点形成 bounding box
          let minX = Math.min(draggedCoord[0], fixedPoint[0])
          let maxX = Math.max(draggedCoord[0], fixedPoint[0])
          let minY = Math.min(draggedCoord[1], fixedPoint[1])
          let maxY = Math.max(draggedCoord[1], fixedPoint[1])

          // 确保矩形有一定大小
          if (maxX - minX < 1) maxX = minX + 1
          if (maxY - minY < 1) maxY = minY + 1

          const newCoords = [
            [minX, minY], // 左下
            [maxX, minY], // 右下
            [maxX, maxY], // 右上
            [minX, maxY], // 左上
            [minX, minY]  // 闭合
          ]
          console.log('[ShapeConstraint] newCoords:', newCoords)
          geometry.setCoordinates([newCoords])
          geometry.changed()
          console.log('[ShapeConstraint] Rectangle constraint applied')
        } else {
          console.log('[ShapeConstraint] Missing draggedVertexIndex or originalFeatureCoords')
        }
      } else if (geomType === 'Circle') {
        console.log('[ShapeConstraint] Processing Circle constraint')
        // 圆形约束：保持圆心固定，只改变半径
        if (originalCircleCenter && draggedDirection) {
          const [centerX, centerY] = originalCircleCenter
          const currentCoords = geometry.getCoordinates()[0]
          console.log('[ShapeConstraint] Circle center:', [centerX, centerY], 'draggedDirection:', draggedDirection)
          if (!currentCoords || currentCoords.length < 4) {
            console.log('[ShapeConstraint] Invalid coords, skipping')
            return
          }

          // 根据拖动方向获取当前半径
          let radius
          const keyIndices = { east: 0, south: 8, west: 16, north: 24 }
          const keyIndex = keyIndices[draggedDirection]
          if (keyIndex !== undefined && currentCoords[keyIndex]) {
            const pt = currentCoords[keyIndex]
            const dx = pt[0] - centerX
            const dy = pt[1] - centerY
            radius = Math.sqrt(dx * dx + dy * dy)
            console.log('[ShapeConstraint] radius from keyPoint:', radius)
          } else {
            // 默认半径
            radius = 10
            console.log('[ShapeConstraint] using default radius:', radius)
          }

          // 确保半径为正
          if (radius < 1) radius = 1

          // 用 32 个点重新生成圆形
          const angles = []
          for (let i = 0; i < 32; i++) {
            const angle = (i / 32) * Math.PI * 2
            angles.push([
              centerX + Math.cos(angle) * radius,
              centerY + Math.sin(angle) * radius
            ])
          }
          angles.push(angles[0]) // 闭合
          geometry.setCoordinates([angles])
          geometry.changed()
          console.log('[ShapeConstraint] Circle constraint applied')
        } else {
          console.log('[ShapeConstraint] Missing originalCircleCenter or draggedDirection')
        }
      }
    })
    // 注意：不在这里重置状态，因为 modify 会多次触发
    // 只在 modifyend 中重置状态
  }

  // 监听修改结束事件，处理矩形和圆的几何约束
  modify.on('modifyend', (e) => {
    console.log('[ModifyEnd] modifyend triggered')
    applyShapeConstraints(e.features)
    // 在 modifyend 完成后重置状态
    console.log('[ModifyEnd] Resetting state after modifyend')
    draggedVertexIndex = -1
    draggedDirection = null
    originalFeatureCoords = null
    originalCircleCenter = null
    pendingConstraining = false
  })

  // 在修改过程中也应用约束（实时约束）
  modify.on('modify', (e) => {
    applyShapeConstraints(e.features)
  })

  // 监听指针移动事件，更新鼠标样式
  modify.on('active', (e) => {
    if (mapRef.value) {
      if (e.active) {
        mapRef.value.style.cursor = 'move'
      }
    }
  })

  // 监听地图鼠标移动，动态更新鼠标样式
  map.on('pointermove', (e) => {
    if (!mapRef.value) return

    const hasFeature = map.hasFeatureAtPixel(e.pixel)

    // 直接设置 canvas 容器的 cursor
    const canvasContainer = map.getTargetElement()
    if (canvasContainer) {
      if (editMode.value === 'select' || editMode.value === 'delete') {
        canvasContainer.style.cursor = hasFeature ? 'pointer' : 'default'
      } else if (drawType.value) {
        canvasContainer.style.cursor = 'crosshair'
      } else {
        canvasContainer.style.cursor = 'default'
      }
    }
  })

  // 地图初始化完成
  mapInitialized.value = true
  statusMessage.value = '地图加载完成，可选择符号进行标注'
}

const updateStatus = () => {
  const center = map.getView().getCenter()
  coordinate.value = center ? `${center[0].toFixed(2)}, ${center[1].toFixed(2)}` : '0, 0'
  zoomLevel.value = map.getView().getZoom().toFixed(2)
}

const clearDraw = () => {
  if (draw) {
    // 移除双击事件监听
    if (draw.dblClickKey && map) {
      map.un('dblclick', draw.dblClickKey.handleDblClick)
    }
    map.removeInteraction(draw)
    draw = null
  }
  drawType.value = null
  if (mapRef.value) {
    mapRef.value.style.cursor = 'default'
  }
  statusMessage.value = '已取消标注工具'
}

// 切换到空闲状态（取消所有编辑和绘制状态）
const clearToIdle = () => {
  // 清除绘制状态
  clearDraw()
  // 清除编辑模式
  editMode.value = null
  // 清除量测模式
  isMeasureMode.value = false
  measureCallback.value = null
  // 清除选择
  if (select) {
    select.getFeatures().clear()
  }
  selectedFeature.value = null
  // 清除符号选择
  selectedSymbol.value = null
  if (mapRef.value) {
    mapRef.value.style.cursor = 'default'
  }
  statusMessage.value = '已切换到空闲状态'
}

// 检查点是否在多边形内部（用于判断是否拖动图形内部）
const isPointInPolygon = (point, coords) => {
  const x = point[0], y = point[1]
  let inside = false
  for (let i = 0, j = coords.length - 1; i < coords.length; j = i++) {
    const xi = coords[i][0], yi = coords[i][1]
    const xj = coords[j][0], yj = coords[j][1]
    const intersect = ((yi > y) !== (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
    if (intersect) inside = !inside
  }
  return inside
}

// 创建符号样式
const createSymbolStyle = (symbol) => {
  if (!symbol) return null

  // 使用 Blob URL 方式
  const svgBlob = new Blob([symbol.content], { type: 'image/svg+xml' })
  const svgUrl = URL.createObjectURL(svgBlob)

  const icon = new Icon({
    src: svgUrl,
    scale: 0.5
  })

  return new Style({
    image: icon
  })
}

// 获取当前样式函数（用于绘制时的样式）
const getStyleFunction = () => {
  // 只有选择符号且绘制点时，才使用符号样式
  if (selectedSymbol.value && drawType.value === 'Point') {
    return createSymbolStyle(selectedSymbol.value)
  }
  return getDefaultStyle()
}

// 获取默认样式
const getDefaultStyle = () => {
  return new Style({
    fill: new Fill({
      color: 'rgba(64, 158, 255, 0.5)'
    }),
    stroke: new Stroke({
      color: '#409eff',
      width: 2
    }),
    image: new Circle({
      radius: 7,
      fill: new Fill({
        color: '#409eff'
      })
    })
  })
}

// 创建矩形绘制函数
const createRectangleFunction = () => {
  return (coordinates, geometry) => {
    if (!geometry) {
      geometry = new Polygon([])
    }
    const start = coordinates[0]
    const end = coordinates[1]
    const minX = Math.min(start[0], end[0])
    const maxX = Math.max(start[0], end[0])
    const minY = Math.min(start[1], end[1])
    const maxY = Math.max(start[1], end[1])
    const polygon = fromExtent([minX, minY, maxX, maxY])
    geometry.setCoordinates(polygon.getCoordinates())
    return geometry
  }
}

// 创建圆形绘制函数 - 将圆转换为多边形存储
const createCircleFunction = () => {
  return (coordinates, geometry) => {
    if (!geometry) {
      geometry = new Polygon([])
    }
    const start = coordinates[0]
    const end = coordinates[1]
    const centerX = start[0]
    const centerY = start[1]
    const dx = end[0] - start[0]
    const dy = end[1] - start[1]
    const radius = Math.sqrt(dx * dx + dy * dy)

    // 用 32 个点近似圆形
    const angles = []
    for (let i = 0; i < 32; i++) {
      const angle = (i / 32) * Math.PI * 2
      angles.push([
        centerX + Math.cos(angle) * radius,
        centerY + Math.sin(angle) * radius
      ])
    }
    angles.push(angles[0]) // 闭合

    geometry.setCoordinates([angles])
    return geometry
  }
}

// 绘制普通点（清除符号选择）
const drawPoint = () => {
  selectedSymbol.value = null
  setDrawType('Point')
}

const setDrawType = (type, callback = null) => {
  // 清除选择并恢复样式
  if (select) {
    const features = select.getFeatures()
    features.forEach(feature => {
      const savedStyle = feature.get('originalStyle') || feature.get('symbolStyle')
      if (savedStyle) {
        feature.setStyle(savedStyle)
        feature.set('originalStyle', null)
      }
    })
    features.clear()
  }
  selectedFeature.value = null

  clearDraw()

  if (drawType.value === type) {
    drawType.value = null
    statusMessage.value = '已取消标注工具'
    return
  }

  // 如果绘制的是线或面，清除符号选择（点绘制保留符号选择）
  if (type !== 'Point') {
    selectedSymbol.value = null
  }

  // 清除编辑模式（选择/删除）
  editMode.value = null
  if (select) select.setActive(false)
  if (modify) modify.setActive(false)
  if (translate) translate.setActive(false)

  drawType.value = type
  measureCallback.value = callback

  // 圆和矩形使用 Circle 绘制类型，但通过 geometryFunction 转换为 Polygon
  const drawTypeMapping = {
    'Circle': 'Circle',
    'Rectangle': 'Circle',
    'Point': 'Point',
    'LineString': 'LineString',
    'Polygon': 'Polygon'
  }

  draw = new Draw({
    source: vectorSource,
    type: drawTypeMapping[type] || type,
    geometryFunction: type === 'Rectangle' ? createRectangleFunction() : (type === 'Circle' ? createCircleFunction() : undefined),
    style: getStyleFunction()
  })

  // 绘制开始
  draw.on('drawstart', (event) => {
    if (callback) {
      callback('start', event)
    }
  })

  // 绘制结束
  draw.on('drawend', (event) => {
    // 检查是否处于正确的绘制状态
    if (drawType.value !== type) {
      return
    }

    // 移除双击事件监听
    if (draw.dblClickKey && map) {
      map.un('dblclick', draw.dblClickKey.handleDblClick)
    }

    const feature = event.feature
    console.log('[Draw] drawend 事件，版本:', BUILD_VERSION)
    console.log('[Draw] feature:', feature)
    console.log('[Draw] feature keys:', feature.getKeys())

    // 路网区域绘制处理
    if (truckAnalysisSelectMode.value === 'draw-area' && roadNetworkDrawCallback) {
      const geom = feature.getGeometry()
      if (geom) {
        const extent = geom.getExtent()
        // 将 EPSG:3857 坐标转换为 WGS84 经纬度
        const minLonLat = toLonLat([extent[0], extent[1]])
        const maxLonLat = toLonLat([extent[2], extent[3]])
        // callback 接收 bbox [minLon, minLat, maxLon, maxLat]
        roadNetworkDrawCallback([minLonLat[0], minLonLat[1], maxLonLat[0], maxLonLat[1]])
      }
      // 移除绘制的要素
      vectorSource.removeFeature(feature)
      // 清除绘制工具
      clearDraw()
      // 重新打开对话框
      showRoadNetworkDialog.value = true
      ElMessage.success('区域已选择')
      return
    }

    // 只有符号标注才设置样式，普通点线面使用默认样式
    if (selectedSymbol.value && drawType.value === 'Point' && !isMeasureMode.value) {
      const symbolStyle = createSymbolStyle(selectedSymbol.value)
      feature.setStyle(symbolStyle)
      // 标记为符号要素，并保存原始样式到自定义属性
      feature.set('isSymbol', true)
      feature.set('symbolStyle', symbolStyle)
      feature.set('geomType', 'Point')
      feature.set('symbolId', selectedSymbol.value.id)
      feature.set('symbolName', selectedSymbol.value.name)
      console.log('[Draw] 创建符号要素，symbolStyle:', symbolStyle)
      console.log('[Draw] 创建符号要素后，feature keys:', feature.getKeys())
      if (vectorLayer) {
        vectorLayer.changed()
      }
    } else {
      feature.set('isSymbol', false)
      // 保存几何类型
      feature.set('geomType', drawType.value)
      console.log('[Draw] 创建普通要素，type:', drawType.value)
    }

    if (callback) {
      callback('end', event)
    }

    // 量测模式下，绘制结束后立即移除交互
    if (isMeasureMode.value) {
      map.removeInteraction(draw)
      draw = null
      drawType.value = null

      setTimeout(() => {
        isMeasureMode.value = false
        measureCallback.value = null
        statusMessage.value = '量测完成'
      }, 50)
    }

    // 绘制项目区域模式
    if (isDrawingProjectArea.value && currentProject.value) {
      // 创建占位图
      createPlaceholderImageFromDrawnArea(feature)
      // 重置标志
      isDrawingProjectArea.value = false
      // 清除绘制工具
      map.removeInteraction(draw)
      draw = null
      drawType.value = null
    }
  })

  map.addInteraction(draw)

  // 设置绘制时的鼠标样式
  if (mapRef.value) {
    mapRef.value.style.cursor = 'crosshair'
  }

  // 添加双击结束绘制的事件处理 - 仅用于多边形和线
  // 圆和矩形使用 Circle 类型，单击即可结束，不需要双击
  if (type === 'Polygon' || type === 'LineString') {
    const handleDblClick = (e) => {
      if (draw && drawType.value === type) {
        draw.finishDrawing()
        e.stopPropagation()
      }
    }

    map.on('dblclick', handleDblClick)

    // 存储事件监听器 key 以便后续移除
    const dblClickKey = { handleDblClick }
    draw.dblClickKey = dblClickKey
  }

  const typeNames = { Point: '点', LineString: '线', Polygon: '面', Circle: '圆', Rectangle: '矩形' }
  // 圆和矩形使用 Circle 类型绘制，单击结束；线和面需要双击结束
  const finishTip = (type === 'Point' || type === 'Circle' || type === 'Rectangle') ? '单击结束' : '双击结束'
  statusMessage.value = `正在绘制${typeNames[type] || '标注'}，${finishTip}`
}

const zoomIn = () => {
  const view = map.getView()
  view.animate({ zoom: view.getZoom() + 1, duration: 250 })
}

const zoomOut = () => {
  const view = map.getView()
  view.animate({ zoom: view.getZoom() - 1, duration: 250 })
}

const zoomToExtent = () => {
  map.getView().animate({
    center: fromLonLat([116.4, 39.9]),
    zoom: 10,
    duration: 500
  })
}

const clearAll = () => {
  vectorSource.clear()
  statusMessage.value = '已清除所有标注'
}

// 复制要素
const copyFeature = () => {
  if (contextMenuFeature.value) {
    const original = contextMenuFeature.value

    // 保存原始样式引用（如果要素有 originalStyle）
    const originalStyle = original.get('originalStyle')

    // 克隆要素数据
    copiedFeature.value = {
      geomType: original.get('geomType'),
      isSymbol: original.get('isSymbol'),
      symbolId: original.get('symbolId'),
      symbolName: original.get('symbolName'),
      symbolStyle: original.get('symbolStyle'),
      geometry: original.getGeometry().clone()
    }

    ElMessage.success('已复制要素，右键点击地图任意位置粘贴')
    contextMenuVisible.value = false

    // 清除选择状态，让要素不再处于激活状态
    if (select) {
      // 先恢复要素样式
      if (originalStyle) {
        original.setStyle(originalStyle)
      } else {
        original.setStyle(getDefaultStyle())
      }
      original.set('originalStyle', null)
      // 清除选择
      select.getFeatures().clear()
    }
    selectedFeature.value = null

    // 触发图层重绘
    if (vectorLayer) {
      vectorLayer.changed()
    }
  }
}

// 粘贴要素（在菜单位置）
const pasteFeature = () => {
  if (!copiedFeature.value) {
    ElMessage.warning('没有复制的要素')
    return
  }

  try {
    const { geomType, isSymbol, symbolId, symbolName, symbolStyle, geometry } = copiedFeature.value

    // 克隆几何体
    const newGeometry = geometry.clone()
    const coords = newGeometry.getCoordinates()

    // 获取右键菜单打开时的位置坐标
    const clickCoord = map.getCoordinateFromPixel([contextMenuPosition.value.x, contextMenuPosition.value.y])

    // 计算原始几何的中心点
    let centerCoord
    if (geometry.getType() === 'Point') {
      centerCoord = coords
    } else {
      // 计算边界框中心
      const extent = geometry.getExtent()
      centerCoord = [(extent[0] + extent[2]) / 2, (extent[1] + extent[3]) / 2]
    }

    // 计算偏移量
    const offsetX = clickCoord[0] - centerCoord[0]
    const offsetY = clickCoord[1] - centerCoord[1]

    // 应用偏移
    let newCoords
    if (Array.isArray(coords[0])) {
      // Polygon 或 LineString - 偏移每个点
      newCoords = coords.map(ring => Array.isArray(ring)
        ? ring.map(c => [c[0] + offsetX, c[1] + offsetY])
        : [ring[0] + offsetX, ring[1] + offsetY]
      )
    } else {
      // Point - 直接使用点击位置
      newCoords = clickCoord
    }
    newGeometry.setCoordinates(newCoords)

    // 创建新要素
    const newFeature = new Feature({
      geomType,
      isSymbol,
      symbolId,
      symbolName
    })
    newFeature.setGeometry(newGeometry)

    // 设置样式
    if (symbolStyle) {
      newFeature.setStyle(symbolStyle.clone())
      newFeature.set('symbolStyle', symbolStyle.clone())
    }

    // 添加到地图
    vectorSource.addFeature(newFeature)

    ElMessage.success('已粘贴要素')
    contextMenuVisible.value = false
  } catch (error) {
    console.error('粘贴失败:', error)
    ElMessage.error('粘贴失败：' + error.message)
  }
}

// 处理量测结果
const handleMeasureResult = (data) => {
  if (!data) {
    isMeasureMode.value = false
    measureCallback.value = null
    return
  }

  if (data.drawType) {
    isMeasureMode.value = true
    setDrawType(data.drawType, data.callback)
  }
}

// 符号相关方法

// 选择符号并开始标注（新方法 - 直接平铺）
const selectSymbolAndDraw = (symbol) => {
  // 如果已选择该符号，则取消
  if (selectedSymbol.value?.id === symbol.id && drawType.value === 'Point') {
    clearDraw()
    return
  }

  // 恢复当前选中要素的原始样式
  if (select && selectedFeature.value) {
    const savedStyle = selectedFeature.value.get('originalStyle') || selectedFeature.value.get('symbolStyle')
    if (savedStyle) {
      selectedFeature.value.setStyle(savedStyle)
      selectedFeature.value.set('originalStyle', null)
    }
  }

  selectedSymbol.value = symbol
  statusMessage.value = `已选择符号：${symbol.name}，单击地图放置标注`

  // 清除选择状态
  if (select) {
    select.getFeatures().clear()
  }
  selectedFeature.value = null
  editMode.value = null

  setDrawType('Point')
}

// 设置编辑模式
const setEditMode = (mode) => {
  // 清除当前的绘制工具
  clearDraw()

  // 如果点击的是当前已激活的模式，则关闭
  if (editMode.value === mode) {
    editMode.value = null
    modify.setActive(false)
    translate.setActive(false)
    select.setActive(false)

    // 恢复所有选中要素的原始样式
    const features = select.getFeatures()
    features.forEach(feature => {
      const savedStyle = feature.get('originalStyle') || feature.get('symbolStyle')
      if (savedStyle) {
        feature.setStyle(savedStyle)
        feature.set('originalStyle', null)
      }
    })
    features.clear()

    selectedFeature.value = null
    if (mapRef.value) {
      mapRef.value.style.cursor = 'default'
    }
    statusMessage.value = ''
    return
  }

  editMode.value = mode

  if (mode === 'select') {
    select.getFeatures().clear()
    selectedFeature.value = null

    select.setActive(true)
    modify.setActive(true)
    translate.setActive(true)
    if (mapRef.value) {
      mapRef.value.style.cursor = 'default'
    }
    statusMessage.value = '选择模式：点击要素选中，拖动顶点修改形状（矩形/圆可整体移动）'
  } else if (mode === 'delete') {
    // 禁用修改交互
    modify.setActive(false)
    translate.setActive(false)
    select.setActive(true)
    select.getFeatures().clear()
    selectedFeature.value = null
    if (mapRef.value) {
      mapRef.value.style.cursor = 'default'
    }
    statusMessage.value = '删除模式：点击要素删除'
  }
}

// 修改要素颜色
const changeFeatureColor = (color) => {
  if (!selectedFeature.value) return

  const feature = selectedFeature.value
  const geomType = feature.getGeometry().getType()
  const style = feature.getStyle()

  if (style) {
    // 符号标注（有 image 且是 Icon）
    if (style.getImage && typeof style.getImage === 'function' && style.getImage() instanceof Icon) {
      // 保持符号样式，只修改可能的 fill
      feature.setStyle(new Style({
        image: style.getImage(),
        fill: new Fill({
          color: color.replace(')', ', 0.5)').replace('rgb', 'rgba')
        })
      }))
    } else {
      // 普通点线面
      feature.setStyle(new Style({
        fill: new Fill({
          color: geomType === 'Polygon' ? color.replace(')', ', 0.5)').replace('rgb', 'rgba') : color
        }),
        stroke: new Stroke({
          color: color,
          width: 2
        }),
        image: new Circle({
          radius: 7,
          fill: new Fill({ color: color })
        })
      }))
    }
  }

  // 触发重绘
  if (vectorLayer) {
    vectorLayer.changed()
  }
  statusMessage.value = '已修改颜色'
}

const openSymbolLibrary = () => {
  symbolEditDialogVisible.value = true
}

const handleSymbolsChange = (newSymbols) => {
  symbols.value = newSymbols
  // 保存到 localStorage
  saveSymbolsToStorage(newSymbols)
}

// 处理工具面板的符号选择
const handleToolSymbolSelect = (symbol) => {
  if (symbol) {
    selectSymbolAndDraw(symbol)
  }
}

// ==================== 项目管理相关方法 ====================

// 打开新建项目对话框
const openNewProjectDialog = () => {
  projectDialogMode.value = 'create'
  projectDialogVisible.value = true
}

// 打开项目列表对话框
const openProjectListDialog = () => {
  projectDialogMode.value = 'open'
  projectDialogVisible.value = true
}
const getCurrentAnnotations = () => {
  const features = vectorSource.getFeatures()
  const annotations = []

  features.forEach(feature => {
    const geomType = feature.get('geomType')
    const isSymbol = feature.get('isSymbol')
    const symbolStyle = feature.get('symbolStyle')
    const symbolId = feature.get('symbolId')
    const symbolName = feature.get('symbolName')

    // 获取几何信息
    const geometry = feature.getGeometry()
    if (!geometry) return

    // 数据已经是 EPSG:3857，直接获取坐标
    const coordinates = geometry.getCoordinates()

    // 构建 GeoJSON 要素
    const featureData = {
      type: 'Feature',
      geometry: {
        type: geometry.getType(),
        coordinates: coordinates
      },
      properties: {
        type: geomType,
        isSymbol: isSymbol,
        geomType: geomType
      }
    }

    // 如果是符号，保存符号信息
    if (isSymbol && symbolId) {
      featureData.properties.symbolId = symbolId
      featureData.properties.symbolName = symbolName
      featureData.properties.category = 'symbol'
    }

    annotations.push(featureData)
  })

  return annotations
}

// 加载标注到地图
const loadAnnotationsToMap = (annotations) => {
  console.log('[LoadAnnotations] Loading annotations:', annotations)
  console.log('[LoadAnnotations] annotations length:', annotations.length)

  // 清空当前地图
  vectorSource.clear()

  annotations.forEach((annData, index) => {
    let { geometry, properties } = annData

    // 解析 JSON 字符串（后端返回的是字符串）
    if (typeof geometry === 'string') {
      geometry = JSON.parse(geometry)
    }
    if (typeof properties === 'string') {
      properties = JSON.parse(properties)
    }

    console.log(`[LoadAnnotations] Processing annotation ${index}:`, annData)
    console.log(`[LoadAnnotations] geometry.type:`, geometry.type)
    console.log(`[LoadAnnotations] geometry.coordinates:`, geometry.coordinates)

    // 创建要素
    const feature = new Feature({
      geomType: properties.geomType || properties.type,
      isSymbol: properties.isSymbol || false,
      symbolId: properties.symbolId,
      symbolName: properties.symbolName
    })
    console.log('[LoadAnnotations] Created feature:', feature)

    // 创建几何 - 数据已经是 EPSG:3857，不需要转换
    let geom
    if (geometry.type === 'Point') {
      console.log('[LoadAnnotations] Point coords:', geometry.coordinates)
      geom = new Point(geometry.coordinates)
    } else if (geometry.type === 'LineString') {
      console.log('[LoadAnnotations] LineString coords:', geometry.coordinates)
      geom = new LineString(geometry.coordinates)
    } else if (geometry.type === 'Polygon') {
      console.log('[LoadAnnotations] Polygon coords:', geometry.coordinates)
      geom = new Polygon(geometry.coordinates)
    } else if (geometry.type === 'Circle') {
      // Circle 用 Polygon 近似
      console.log('[LoadAnnotations] Circle coords:', geometry.coordinates)
      geom = new Polygon(geometry.coordinates)
    } else if (geometry.type === 'Rectangle') {
      // Rectangle 用 Polygon 近似
      console.log('[LoadAnnotations] Rectangle coords:', geometry.coordinates)
      geom = new Polygon(geometry.coordinates)
    }

    console.log('[LoadAnnotations] Created geom:', geom)
    feature.setGeometry(geom)

    // 设置样式 - 如果不是符号，使用默认样式
    if (properties.isSymbol && properties.symbolId) {
      // 尝试通过 symbolId 查找符号（可能是数字 ID 或字符串 ID）
      let symbol = symbols.value.find(s => s.id === properties.symbolId)
      // 如果找不到，尝试通过 symbolName 查找
      if (!symbol && properties.symbolName) {
        symbol = symbols.value.find(s => s.name === properties.symbolName)
      }
      if (symbol) {
        const symbolStyle = createSymbolStyle(symbol)
        feature.setStyle(symbolStyle)
        feature.set('symbolStyle', symbolStyle)
        console.log('[LoadAnnotations] Set symbol style for feature')
      } else {
        console.log('[LoadAnnotations] Symbol not found, using default style')
        feature.setStyle(getDefaultStyle())
      }
    } else {
      console.log('[LoadAnnotations] Not a symbol, using default style')
      feature.setStyle(getDefaultStyle())
    }

    console.log('[LoadAnnotations] Feature style:', feature.getStyle())
    vectorSource.addFeature(feature)
    console.log('[LoadAnnotations] Added feature to vectorSource')
  })

  console.log('[LoadAnnotations] Final vectorSource features count:', vectorSource.getFeatures().length)

  // 强制重绘
  map.renderSync()
  vectorLayer.changed()

  console.log('[LoadAnnotations] 已强制重绘地图')

  // 缩放到标注范围
  if (vectorSource.getFeatures().length > 0) {
    const extent = vectorSource.getExtent()
    if (extent && extent[0] !== Infinity) {
      console.log('[LoadAnnotations] 缩放到标注范围:', extent)
      // 强制立即缩放，不使用动画
      map.getView().fit(extent, { duration: 0, padding: [50, 50, 50, 50] })
      // 触发图层重绘
      vectorLayer.changed()
    }
  }

  statusMessage.value = `已加载 ${annotations.length} 个标注`
}

// 创建项目
const handleCreateProject = async (projectData) => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch('/api/projects', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(projectData)
    })

    const result = await response.json()

    if (result.code === 200) {
      console.log('[Create Project] 开始清理当前地图和标注')

      // 设置新项目
      currentProject.value = result.data
      console.log('[Create Project] 新项目已设置:', currentProject.value)

      // 1. 清底图模块 - 清理底层地图
      console.log('[Create Project] 清理前底层地图图层数量:', map.getLayers().getLength())
      console.log('[Create Project] 清理前所有图层:', map.getLayers().getArray().map(l => l.constructor.name))

      // 1.1 移除底层地图所有图层
      const allLayers = map.getLayers().getArray().slice()
      allLayers.forEach(layer => {
        map.removeLayer(layer)
        console.log('[Create Project] 移除图层:', layer.constructor.name)
      })
      tileLayer = null
      imageLayer.value = null

      // 1.2 添加 OSM 底图
      tileLayer = new TileLayer({
        source: new XYZ({
          url: 'https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png',
          crossOrigin: 'anonymous'
        })
      })
      map.addLayer(tileLayer)
      console.log('[Create Project] OSM 底图已添加到底层地图')

      // 1.3 创建空影像图层
      imageLayer.value = new ImageLayer({
        source: null,
        visible: false,
        zIndex: 1
      })
      map.addLayer(imageLayer.value)
      console.log('[Create Project] 空影像图层已添加到底层地图')

      // 1.4 重新创建矢量图层
      vectorSource = new VectorSource()
      vectorLayer = new VectorLayer({
        source: vectorSource,
        style: (feature) => {
          const featureStyle = feature.getStyle()
          if (featureStyle) {
            return featureStyle
          }
          return getDefaultStyle()
        },
        zIndex: 999  // 最高优先级，确保矢量在最上层
      })
      map.addLayer(vectorLayer)
      console.log('[Create Project] 矢量图层已重新创建并添加')

      // 1.5 强制重绘
      map.render()
      console.log('[Create Project] map.render() 已调用')
      console.log('[Create Project] 清理后底层地图图层数量:', map.getLayers().getLength())
      console.log('[Create Project] 清理后所有图层:', map.getLayers().getArray().map(l => l.constructor.name))
      imageStore.setCurrentImage(null)
      currentProject.value.baseImageId = null
      console.log('[Create Project] imageStore 和 baseImageId 已清空')

      // 2. 清矢量模块 - 清理上层矢量地图
      vectorSource.clear()
      console.log('[Create Project] 矢量标注已清空')

      ElMessage.success('项目创建成功')
      projectDialogVisible.value = false

      // 根据创建方式处理
      if (projectData.method === 'image' && projectData.imageId) {
        // 方式一：已选择影像，直接加载
        console.log('[Create Project] 已选择影像，加载影像:', projectData.imageId)
        await loadBaseImage(projectData.imageId)
      } else if (projectData.method === 'draw') {
        // 方式二：需要绘制项目区域
        console.log('[Create Project] 需要绘制项目区域')
        ElMessage.info('请在地图上使用面标注工具绘制项目范围，绘制完成后点击保存按钮')
        // 自动激活面标注工具
        setDrawType('Polygon')
      } else {
        // 旧方式：创建占位影像
        await createPlaceholderImage()
      }

      // 自动保存当前标注（新项目没有标注）
      await saveCurrentAnnotations()
    } else {
      ElMessage.error(result.message || '创建项目失败')
    }
  } catch (error) {
    console.error('创建项目失败:', error)
    ElMessage.error('创建项目失败：' + error.message)
  }
}

// 创建占位影像（旧方式，兼容用）
const createPlaceholderImage = async () => {
  try {
    const placeholderToken = localStorage.getItem('token')

    // 计算当前视图范围作为占位影像范围
    const view = map.getView()
    const center = view.getCenter()
    const resolution = view.getResolution()
    const size = map.getSize()

    if (center && resolution && size) {
      const widthInMeters = resolution * size[0]
      const heightInMeters = resolution * size[1]

      const minX = center[0] - widthInMeters / 2
      const minY = center[1] - heightInMeters / 2
      const maxX = center[0] + widthInMeters / 2
      const maxY = center[1] + heightInMeters / 2

      console.log('[Create Project] 创建占位影像，范围:', { minX, minY, maxX, maxY })

      const placeholderResponse = await fetch('/api/images/create-placeholder', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${placeholderToken}`
        },
        body: JSON.stringify({
          minX, minY, maxX, maxY,
          projectId: currentProject.value.id
        })
      })

      const placeholderResult = await placeholderResponse.json()

      if (placeholderResult.code === 200) {
        const placeholderImage = placeholderResult.data
        console.log('[Create Project] 占位影像创建成功:', placeholderImage)

        // 更新项目的 baseImageId
        await fetch(`/api/projects/${currentProject.value.id}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${placeholderToken}`
          },
          body: JSON.stringify({
            baseImageId: placeholderImage.id
          })
        })

        // 加载占位影像
        await loadBaseImage(placeholderImage.id)

        console.log('[Create Project] 占位影像已加载')
      } else {
        console.error('[Create Project] 创建占位影像失败:', placeholderResult.message)
        await updateProjectBaseImageId(null)
      }
    } else {
      console.warn('[Create Project] 无法计算视图范围，将 baseImageId 设为 null')
      await updateProjectBaseImageId(null)
    }
  } catch (error) {
    console.error('[Create Project] 创建占位影像异常:', error)
    await updateProjectBaseImageId(null)
  }
}

// 打开项目
const handleOpenProject = async (project) => {
  try {
    const token = localStorage.getItem('token')
    console.log('[Open Project] token:', token ? token.substring(0, 20) + '...' : 'null')
    console.log('[Open Project] project id:', project.id)
    const response = await fetch(`/api/projects/${project.id}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    })

    console.log('[Open Project] response status:', response.status)

    const result = await response.json()
    console.log('[Open Project] result:', JSON.stringify(result, null, 2))

    if (result.code === 200) {
      console.log('[Open Project] 开始清理旧项目图层')

      // 清除底层地图所有图层
      const allLayers = map.getLayers().getArray().slice()
      allLayers.forEach(layer => {
        map.removeLayer(layer)
        console.log('[Open Project] 移除旧图层:', layer.constructor.name)
      })

      // 重置图层
      tileLayer = null
      imageLayer.value = null
      vectorSource.clear()
      console.log('[Open Project] 旧图层已清空')

      currentProject.value = result.data.project
      console.log('[Open Project] currentProject set to:', currentProject.value)

      // 1. 添加 OSM 底图到底层地图
      tileLayer = new TileLayer({
        source: new XYZ({
          url: 'https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png',
          crossOrigin: 'anonymous'
        })
      })
      map.addLayer(tileLayer)
      console.log('[Open Project] OSM 底图已添加到底层地图')

      // 2. 创建影像图层（所有项目都有影像图层，保持统一流程）
      imageLayer.value = new ImageLayer({
        source: null,
        visible: false,
        zIndex: 1
      })
      map.addLayer(imageLayer.value)
      console.log('[Open Project] 空影像图层已添加到底层地图')

      // 3. 重新创建并添加矢量图层
      vectorSource = new VectorSource()
      vectorLayer = new VectorLayer({
        source: vectorSource,
        style: (feature) => {
          const featureStyle = feature.getStyle()
          if (featureStyle) {
            return featureStyle
          }
          return getDefaultStyle()
        },
        zIndex: 999  // 最高优先级，确保矢量在最上层
      })
      map.addLayer(vectorLayer)
      console.log('[Open Project] 矢量图层已重新创建并添加')

      // 重新添加交互（因为矢量图层已重新创建）
      console.log('[Open Project] 重新创建交互')

      // 移除旧交互
      if (select) map.removeInteraction(select)
      if (modify) map.removeInteraction(modify)
      if (translate) map.removeInteraction(translate)
      if (draw) map.removeInteraction(draw)

      // 重新创建交互
      select = new Select({ style: null })
      map.addInteraction(select)

      modify = new Modify({
        features: select.getFeatures(),
        style: new Style({
          image: new Circle({
            radius: 5,
            fill: new Fill({ color: '#ff0000' }),
            stroke: new Stroke({ color: '#fff', width: 2 })
          })
        }),
        hitTolerance: 10
      })
      map.addInteraction(modify)

      translate = new Translate({
        features: select.getFeatures(),
        hitTolerance: 5
      })
      map.addInteraction(translate)

      console.log('[Open Project] 交互已重新创建')

      // 先加载标注
      const hasAnnotations = result.data.annotations && result.data.annotations.length > 0
      if (hasAnnotations) {
        console.log('[Open Project] Loading', result.data.annotations.length, 'annotations')
        loadAnnotationsToMap(result.data.annotations)
      } else {
        console.log('[Open Project] No annotations to load')
        vectorSource.clear()
      }

      // 加载底图影像（所有项目都调用，保持统一流程）
      if (result.data.project.baseImageId) {
        console.log('[Open Project] 加载底图影像 ID:', result.data.project.baseImageId)
        await loadBaseImage(result.data.project.baseImageId)
      }

      ElMessage.success('项目加载成功')
      projectDialogVisible.value = false
    } else {
      ElMessage.error(result.message || '加载项目失败')
    }
  } catch (error) {
    console.error('加载项目失败:', error)
    ElMessage.error('加载项目失败：' + error.message)
  }
}

// 保存当前项目
const saveCurrentProject = () => {
  if (!currentProject.value) {
    // 没有打开的项目，弹出新建项目对话框
    projectDialogMode.value = 'create'
    projectDialogVisible.value = true
    return
  }
  // 有打开的项目，直接保存
  saveCurrentAnnotations()
}

// 更新项目的 baseImageId
const updateProjectBaseImageId = async (baseImageId) => {
  if (!currentProject.value?.id) return

  try {
    const token = localStorage.getItem('token')
    await fetch(`/api/projects/${currentProject.value.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        baseImageId
      })
    })
    console.log('[Update Project] baseImageId 已更新为:', baseImageId)
  } catch (error) {
    console.error('[Update Project] 更新 baseImageId 失败:', error)
  }
}

// 保存当前标注到项目
const saveCurrentAnnotations = async () => {
  if (!currentProject.value) {
    ElMessage.error('没有打开的项目')
    return
  }

  try {
    // 详细调试日志
    console.log('[Save Annotations] ====== 开始保存 ======')
    console.log('[Save Annotations] currentProject:', currentProject.value)
    console.log('[Save Annotations] imageStore:', imageStore)
    console.log('[Save Annotations] imageStore.currentImage:', imageStore.currentImage)
    console.log('[Save Annotations] imageStore.currentImage?.id:', imageStore.currentImage?.id)
    console.log('[Save Annotations] imageLayer:', imageLayer.value)

    // 检查是否有加载的影像（通过 imageLayer 判断）
    const hasImageLayer = imageLayer.value !== null
    console.log('[Save Annotations] hasImageLayer:', hasImageLayer)

    // 如果没有影像且有标注，可能是绘制项目区域模式，创建占位图
    const hasAnnotations = vectorSource.getFeatures().length > 0
    const needsPlaceholder = !hasImageLayer && hasAnnotations && !currentProject.value.baseImageId

    if (needsPlaceholder) {
      console.log('[Save Annotations] 绘制区域模式，创建占位影像')
      await createPlaceholderImageFromAnnotations()
    }

    if (imageStore.currentImage && imageStore.currentImage.id) {
      console.log('[Save Annotations] 更新项目底图影像 ID:', imageStore.currentImage.id)
      const token = localStorage.getItem('token')
      const updateResponse = await fetch(`/api/projects/${currentProject.value.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          baseImageId: imageStore.currentImage.id.toString()
        })
      })
      const updateResult = await updateResponse.json()
      console.log('[Save Annotations] 更新底图结果:', updateResult)
      // 更新当前项目的 baseImageId
      currentProject.value.baseImageId = imageStore.currentImage.id
    } else {
      console.log('[Save Annotations] 没有当前影像，将 baseImageId 设为 null')
      // 没有影像时，将项目的 baseImageId 设为 null 并保存到数据库
      currentProject.value.baseImageId = null
      // 可选：更新数据库中的项目记录
      try {
        const token = localStorage.getItem('token')
        await fetch(`/api/projects/${currentProject.value.id}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({
            baseImageId: null
          })
        })
        console.log('[Save Annotations] 已将数据库中的 baseImageId 设为 null')
      } catch (error) {
        console.error('[Save Annotations] 更新 baseImageId 失败:', error)
      }
    }

    const annotations = getCurrentAnnotations()
    const token = localStorage.getItem('token')

    console.log('[Save Annotations] token:', token ? token.substring(0, 20) + '...' : 'null')
    console.log('[Save Annotations] project id:', currentProject.value?.id)
    console.log('[Save Annotations] annotations count:', annotations.length)

    const response = await fetch(`/api/projects/${currentProject.value.id}/annotations`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(annotations)
    })

    const result = await response.json()

    if (result.code === 200) {
      ElMessage.success(result.data || '标注已保存')
    } else {
      ElMessage.error(result.message || '保存标注失败')
    }
  } catch (error) {
    console.error('保存标注失败:', error)
    ElMessage.error('保存标注失败：' + error.message)
  }
}

// 根据标注范围创建占位影像
const createPlaceholderImageFromAnnotations = async () => {
  try {
    const features = vectorSource.getFeatures()
    if (features.length === 0) return

    // 计算所有标注的范围
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
    features.forEach(feature => {
      const geom = feature.getGeometry()
      const extent = geom.getExtent()
      minX = Math.min(minX, extent[0])
      minY = Math.min(minY, extent[1])
      maxX = Math.max(maxX, extent[2])
      maxY = Math.max(maxY, extent[3])
    })

    // 添加 10% 的边距
    const paddingX = (maxX - minX) * 0.1
    const paddingY = (maxY - minY) * 0.1
    minX -= paddingX
    minY -= paddingY
    maxX += paddingX
    maxY += paddingY

    console.log('[Create Placeholder] 根据标注范围创建占位影像:', { minX, minY, maxX, maxY })

    const placeholderToken = localStorage.getItem('token')
    const placeholderResponse = await fetch('/api/images/create-placeholder', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${placeholderToken}`
      },
      body: JSON.stringify({
        minX, minY, maxX, maxY,
        projectId: currentProject.value.id
      })
    })

    const placeholderResult = await placeholderResponse.json()

    if (placeholderResult.code === 200) {
      const placeholderImage = placeholderResult.data
      console.log('[Create Placeholder] 占位影像创建成功:', placeholderImage)

      // 更新项目的 baseImageId
      await fetch(`/api/projects/${currentProject.value.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${placeholderToken}`
        },
        body: JSON.stringify({
          baseImageId: placeholderImage.id
        })
      })

      // 设置 imageStore 的当前影像
      imageStore.setCurrentImage(placeholderImage)

      // 加载占位影像
      await loadBaseImage(placeholderImage.id)

      console.log('[Create Placeholder] 占位影像已加载')
      ElMessage.success('项目范围已创建，现在可以加载同名影像继续标绘')
    } else {
      console.error('[Create Placeholder] 创建占位影像失败:', placeholderResult.message)
      ElMessage.warning('创建项目范围失败，请重试')
    }
  } catch (error) {
    console.error('[Create Placeholder] 创建占位影像异常:', error)
    ElMessage.error('创建项目范围失败：' + error.message)
  }
}

// 根据绘制的区域创建占位影像
const createPlaceholderImageFromDrawnArea = async (feature) => {
  try {
    const geom = feature.getGeometry()
    const extent = geom.getExtent()
    const [minX, minY, maxX, maxY] = extent

    console.log('[Create Placeholder from Draw] 绘制范围:', { minX, minY, maxX, maxY })

    // 先移除绘制的矩形要素
    vectorSource.removeFeature(feature)
    console.log('[Create Placeholder from Draw] 矩形框已移除')

    const placeholderToken = localStorage.getItem('token')
    const placeholderResponse = await fetch('/api/images/create-placeholder', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${placeholderToken}`
      },
      body: JSON.stringify({
        minX, minY, maxX, maxY,
        projectId: currentProject.value.id
      })
    })

    const placeholderResult = await placeholderResponse.json()

    if (placeholderResult.code === 200) {
      const placeholderImage = placeholderResult.data
      console.log('[Create Placeholder from Draw] 占位影像创建成功:', placeholderImage)

      // 更新项目的 baseImageId
      await fetch(`/api/projects/${currentProject.value.id}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${placeholderToken}`
        },
        body: JSON.stringify({
          baseImageId: placeholderImage.id
        })
      })

      // 设置 imageStore 的当前影像
      imageStore.setCurrentImage(placeholderImage)

      // 加载占位影像（此时矩形框已移除，不会被保存）
      await loadBaseImage(placeholderImage.id)

      console.log('[Create Placeholder from Draw] 占位影像已加载')
      ElMessage.success('项目范围已创建，现在可以加载影像继续标绘')
    } else {
      console.error('[Create Placeholder from Draw] 创建占位影像失败:', placeholderResult.message)
      ElMessage.warning('创建项目范围失败，请重试')
    }
  } catch (error) {
    console.error('[Create Placeholder from Draw] 创建占位影像异常:', error)
    ElMessage.error('创建项目范围失败：' + error.message)
  }
}

// 导出 Shapefile
const exportShapefile = () => {
  if (!currentProject.value) {
    ElMessage.warning('请先创建或打开一个项目')
    return
  }

  const token = localStorage.getItem('token')
  const url = `/api/projects/${currentProject.value.id}/export/shapefile`

  // 创建下载链接
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `project_${currentProject.value.id}_shapefile.zip`)

  // 需要携带 token
  // 使用 fetch 下载
  fetch(url, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`
    }
  })
    .then(response => {
      if (!response.ok) {
        throw new Error('导出失败')
      }
      return response.blob()
    })
    .then(blob => {
      const url = window.URL.createObjectURL(blob)
      link.href = url
      link.download = `project_${currentProject.value.id}_shapefile.zip`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    })
    .catch(error => {
      console.error('导出失败:', error)
      ElMessage.error('导出失败：' + error.message)
    })
}

// 导出 GeoJSON
const exportGeoJSON = async () => {
  if (!currentProject.value) {
    ElMessage.warning('请先创建或打开一个项目')
    return
  }

  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/projects/${currentProject.value.id}/export/geojson`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    const result = await response.json()

    if (result.code === 200) {
      // 下载 GeoJSON 文件
      const blob = new Blob([JSON.stringify(result.data, null, 2)], { type: 'application/json' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `project_${currentProject.value.id}.geojson`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
      ElMessage.success('导出 GeoJSON 成功')
    } else {
      ElMessage.error(result.message || '导出 GeoJSON 失败')
    }
  } catch (error) {
    console.error('导出 GeoJSON 失败:', error)
    ElMessage.error('导出 GeoJSON 失败：' + error.message)
  }
}

// ==================== 影像功能相关方法 ====================

// 批次上传相关状态
const uploadBatchUuid = ref(null)
const uploadBatchName = ref('')

// 打开上传对话框
const openImageUpload = (batchInfo) => {
  // batchInfo 可选，包含 { batchUuid, batchName }
  if (batchInfo) {
    uploadBatchUuid.value = batchInfo.batchUuid
    uploadBatchName.value = batchInfo.batchName || ''
  } else {
    uploadBatchUuid.value = null
    uploadBatchName.value = ''
  }
  imageUploadVisible.value = true
}

// 打开对比对话框
const openImageSwipe = () => {
  if (imageStore.images.length < 2) {
    ElMessage.warning('至少需要两个影像才能进行对比')
    return
  }
  imageSwipeVisible.value = true
}

// 处理影像上传成功
const handleImageUploaded = async (uploadResult) => {
  try {
    console.log('[Upload] 上传结果:', uploadResult)
    // uploadResult 包含 { batchUuid, fileCount, images: [...] }
    const images = uploadResult.images || []
    if (images.length === 0) {
      ElMessage.error('上传成功，但没有影像文件')
      return
    }

    // 添加所有影像到 store
    images.forEach(img => imageStore.addImage(img))

    // 刷新批次列表
    if (mapToolsRef.value && mapToolsRef.value.loadBatches) {
      await mapToolsRef.value.loadBatches()
      console.log('[Upload] 批次列表已刷新')
    }

    ElMessage.success(`上传成功，共 ${uploadResult.fileCount} 个文件`)
  } catch (error) {
    console.error('[Upload] 加载影像失败:', error)
    ElMessage.error('加载影像失败：' + error.message)
  }
}

// ==================== 路网下载相关处理函数 ====================

// 开始绘制路网区域
const handleRoadNetworkDrawStart = (callback) => {
  truckAnalysisSelectMode.value = 'draw-area'
  roadNetworkDrawCallback = callback

  // 关闭对话框，让用户可以在地图上操作
  showRoadNetworkDialog.value = false

  // 创建矩形绘制交互
  clearDraw() // 先清除之前的绘制工具
  setDrawType('Rectangle')

  ElMessage.info('请在地图上拖动鼠标框选区域，单击完成绘制')
}

// 路网下载完成
const handleRoadNetworkDownloadComplete = (result) => {
  ElMessage.success('路网数据下载成功')
  showRoadNetworkDialog.value = false

  // 刷新 MapTools 中的路网列表
  if (mapToolsRef.value && mapToolsRef.value.loadRoadNetworks) {
    mapToolsRef.value.loadRoadNetworks()
  }
}

// 加载路网列表
const loadRoadNetworks = async () => {
  // 检查是否登录，未登录时不加载
  const token = localStorage.getItem('token')
  if (!token) {
    return
  }
  try {
    const res = await getRoadNetworks()
    if (res.code === 200) {
      roadNetworks.value = res.data.map(n => ({
        ...n,
        visible: false // 默认不显示
      }))
    }
  } catch (error) {
    console.error('加载路网列表失败:', error)
  }
}

// 货车分析 - 选择起点
const handleTruckSelectStart = (callback) => {
  console.log('[TruckAnalysis] handleTruckSelectStart called')
  truckAnalysisSelectMode.value = 'select-start'
  truckAnalysisCallback = callback
  console.log('[TruckAnalysis] Mode set to:', truckAnalysisSelectMode.value)
  console.log('[TruckAnalysis] Callback set:', callback)
  ElMessage.info('请在地图上点击选择起点')
}

// 货车分析 - 选择终点
const handleTruckSelectEnd = (callback) => {
  console.log('[TruckAnalysis] handleTruckSelectEnd called')
  truckAnalysisSelectMode.value = 'select-end'
  truckAnalysisCallback = callback
  console.log('[TruckAnalysis] Mode set to:', truckAnalysisSelectMode.value)
  console.log('[TruckAnalysis] Callback set:', callback)
  ElMessage.info('请在地图上点击选择终点')
}

// 显示路线
const handleShowRoute = (result) => {
  console.log('[MapView] handleShowRoute called')
  console.log('[MapView] 分析结果:', result)

  // 清除之前的路线和转弯点图层（但保留起点终点标记）
  clearTruckAnalysisLayersOnly()

  if (result.routeGeoJson) {
    // 在地图上显示路线 GeoJSON
    console.log('[MapView] Route GeoJSON length:', result.routeGeoJson.length)
    console.log('[MapView] Route GeoJSON preview:', result.routeGeoJson.substring(0, 300))
    loadRouteGeoJSON(result.routeGeoJson)
  } else {
    console.warn('[MapView] No routeGeoJson in result')
  }

  // 显示转弯点
  if (result.turnPoints && result.turnPoints.length > 0) {
    console.log('[MapView] Turn points count:', result.turnPoints.length)
    console.log('[MapView] First turn point:', result.turnPoints[0])
    loadTurnPoints(result.turnPoints)
  } else {
    console.log('[MapView] No turnPoints in result')
  }

  // 显示禁行点
  if (result.violations && result.violations.length > 0) {
    console.log('[MapView] Violations count:', result.violations.length)
    console.log('[MapView] First violation:', result.violations[0])
    loadViolations(result.violations)
  } else {
    console.log('[MapView] No violations in result')
  }

  // 加载起点和终点标记（从 form 获取）
  const formStartLat = window.truckAnalysisForm?.startLat
  const formStartLon = window.truckAnalysisForm?.startLon
  const formEndLat = window.truckAnalysisForm?.endLat
  const formEndLon = window.truckAnalysisForm?.endLon

  if (formStartLat && formStartLon && formEndLat && formEndLon) {
    loadStartEndMarkers(formStartLat, formStartLon, formEndLat, formEndLon)
  }
}

// 清除货车分析相关图层（保留起点终点标记）
const clearTruckAnalysisLayersOnly = () => {
  if (truckAnalysisRouteLayer) {
    map.removeLayer(truckAnalysisRouteLayer)
    truckAnalysisRouteLayer = null
  }
  if (truckAnalysisTurnPointsLayer) {
    map.removeLayer(truckAnalysisTurnPointsLayer)
    truckAnalysisTurnPointsLayer = null
  }
  if (truckAnalysisViolationPointsLayer) {
    map.removeLayer(truckAnalysisViolationPointsLayer)
    truckAnalysisViolationPointsLayer = null
  }
}

// 清除货车分析相关图层（包括起点终点标记）
const clearTruckAnalysisLayers = () => {
  clearTruckAnalysisLayersOnly()
  if (truckAnalysisStartEndLayer) {
    map.removeLayer(truckAnalysisStartEndLayer)
    truckAnalysisStartEndLayer = null
  }
}

// 加载起点和终点标记
const loadStartEndMarkers = (startLat, startLon, endLat, endLon) => {
  const features = []

  // 起点标记 - S（绿色）
  const startCoordinate = fromLonLat([startLon, startLat])
  const startFeature = new Feature({
    geometry: new Point(startCoordinate),
    type: 'start'
  })
  startFeature.setStyle(new Style({
    image: new Circle({
      radius: 10,
      fill: new Fill({ color: '#67c23a' }),  // 绿色
      stroke: new Stroke({ color: '#fff', width: 3 })
    }),
    text: new Text({
      text: 'S',
      font: 'bold 16px Arial',
      fill: new Fill({ color: '#fff' }),
      stroke: new Stroke({ color: '#67c23a', width: 3 }),
      offsetY: 0
    }),
    zIndex: 100
  }))
  features.push(startFeature)

  // 终点标记 - E（红色）
  const endCoordinate = fromLonLat([endLon, endLat])
  const endFeature = new Feature({
    geometry: new Point(endCoordinate),
    type: 'end'
  })
  endFeature.setStyle(new Style({
    image: new Circle({
      radius: 10,
      fill: new Fill({ color: '#f56c6c' }),  // 红色
      stroke: new Stroke({ color: '#fff', width: 3 })
    }),
    text: new Text({
      text: 'E',
      font: 'bold 16px Arial',
      fill: new Fill({ color: '#fff' }),
      stroke: new Stroke({ color: '#f56c6c', width: 3 }),
      offsetY: 0
    }),
    zIndex: 100
  }))
  features.push(endFeature)

  const source = new VectorSource({ features })

  truckAnalysisStartEndLayer = new VectorLayer({
    source,
    zIndex: 100
  })

  map.addLayer(truckAnalysisStartEndLayer)
}

// 起点终点图层引用
let truckAnalysisStartEndLayer = null

// 加载路线 GeoJSON
const loadRouteGeoJSON = (geojsonString) => {
  try {
    const geojson = JSON.parse(geojsonString)
    console.log('[TruckAnalysis] Parsed geojson:', geojson)

    // 如果是 LineString 几何体，需要转换为 Feature
    let features
    if (geojson.type === 'LineString' && geojson.coordinates) {
      // 将 LineString 几何体转换为 Feature
      const lineStringFeature = new GeoJSON().readFeature(geojson, {
        featureProjection: 'EPSG:3857'
      })
      features = [lineStringFeature]
    } else {
      // 已经是 Feature 或 FeatureCollection
      features = new GeoJSON().readFeatures(geojson, {
        featureProjection: 'EPSG:3857'
      })
    }

    const source = new VectorSource({ features })

    truckAnalysisRouteLayer = new VectorLayer({
      source,
      style: new Style({
        stroke: new Stroke({
          color: '#000000',  // 黑色路线
          width: 6
        })
      }),
      zIndex: 100
    })

    map.addLayer(truckAnalysisRouteLayer)

    // 自动缩放到路线范围 - 使用更宽松的缩放
    const extent = source.getExtent()
    if (extent && extent[0] !== Infinity) {
      // 扩大范围以便更好地查看
      const expandedExtent = [
        extent[0] - (extent[2] - extent[0]) * 0.2,
        extent[1] - (extent[3] - extent[1]) * 0.2,
        extent[2] + (extent[2] - extent[0]) * 0.2,
        extent[3] + (extent[3] - extent[1]) * 0.2
      ]
      map.getView().fit(expandedExtent, { duration: 500, padding: [50, 50, 50, 50], maxZoom: 13 })
    }
  } catch (error) {
    console.error('[TruckAnalysis] 加载路线失败:', error)
  }
}

// 加载转弯点
const loadTurnPoints = (turnPoints) => {
  const features = []

  turnPoints.forEach(point => {
    const coordinate = fromLonLat([point.lon, point.lat])

    // 判断是否急转弯
    const minRadius = truck.value?.wheelbase / 0.574
    const isSharp = point.isSharpTurn || (point.turnRadius && point.turnRadius < minRadius)
    const color = isSharp ? '#f56c6c' : '#409eff'

    // 创建转弯点符号（合并点和文字标注）
    const pointFeature = new Feature({
      geometry: new Point(coordinate),
      turnPoint: point
    })

    const styleOptions = {
      image: new Circle({
        radius: isSharp ? 8 : 6,
        fill: new Fill({ color }),
        stroke: new Stroke({ color: '#fff', width: 2 })
      })
    }

    // 添加文字标注（显示转弯半径）
    if (point.turnRadius) {
      styleOptions.text = new Text({
        text: `${point.turnRadius.toFixed(1)}m`,
        font: 'bold 12px Arial',
        fill: new Fill({ color: isSharp ? '#f56c6c' : '#409eff' }),
        stroke: new Stroke({ color: '#fff', width: 3 }),
        offsetY: -15,
        textAlign: 'center'
      })
    }

    pointFeature.setStyle(new Style(styleOptions))
    features.push(pointFeature)
  })

  const source = new VectorSource({ features })

  truckAnalysisTurnPointsLayer = new VectorLayer({
    source,
    zIndex: 101
  })

  map.addLayer(truckAnalysisTurnPointsLayer)

  // 添加点击 popup 显示详情
  setupTurnPointPopup()
}

// 加载禁行点
const loadViolations = (violations) => {
  const features = []

  violations.forEach((point, index) => {
    const coordinate = fromLonLat([point.lon, point.lat])

    // 创建禁行点要素
    const feature = new Feature({
      geometry: new Point(coordinate),
      violation: point,
      violationIndex: index
    })

    // 根据违规类型使用不同样式
    let styleConfig = {
      image: new Circle({
        radius: 10,
        fill: new Fill({ color: '#f56c6c' }),
        stroke: new Stroke({ color: '#fff', width: 2 })
      }),
      zIndex: 102
    }

    // 根据违规类型设置不同颜色
    if (point.reason === '转弯半径不足') {
      styleConfig = {
        image: new Circle({
          radius: 10,
          fill: new Fill({ color: '#e6a23c' }),
          stroke: new Stroke({ color: '#fff', width: 2 })
        }),
        zIndex: 102
      }
    } else if (point.reason === '轮渡路段') {
      styleConfig = {
        image: new Circle({
          radius: 10,
          fill: new Fill({ color: '#909399' }),
          stroke: new Stroke({ color: '#fff', width: 2 })
        }),
        zIndex: 102
      }
    }

    feature.setStyle(new Style(styleConfig))
    features.push(feature)

    // 添加禁行标志符号（X 标记）
    const xFeature = new Feature({
      geometry: new Point(coordinate),
      violation: point
    })

    // 使用文字 X 作为禁行标志
    xFeature.setStyle(new Style({
      text: new Text({
        text: '⛔',
        font: '20px Arial',
        fill: new Fill({ color: '#f56c6c' }),
        offsetY: 0
      }),
      zIndex: 103
    }))

    features.push(xFeature)
  })

  const source = new VectorSource({ features })

  // 创建禁行点图层
  const violationsLayer = new VectorLayer({
    source,
    zIndex: 102
  })
  violationsLayer.set('type', 'violations')

  map.addLayer(violationsLayer)

  // 存储图层引用以便后续清除
  if (!truckAnalysisViolationPointsLayer) {
    truckAnalysisViolationPointsLayer = violationsLayer
  }

  // 添加点击 popup 显示详情
  setupViolationPopup()
}

// 禁行点图层引用
let truckAnalysisViolationPointsLayer = null

// 设置转弯点 Popup
const setupTurnPointPopup = () => {
  // 移除旧的点击监听
  if (turnPointClickHandler) {
    map.un('click', turnPointClickHandler)
  }

  turnPointClickHandler = (e) => {
    const feature = map.forEachFeatureAtPixel(e.pixel, (f) => f)
    if (feature && feature.get('turnPoint')) {
      const point = feature.get('turnPoint')
      const coord = e.coordinate

      // 创建 popup 内容
      const overlay = createTurnPointOverlay(coord, point)
      showOverlay(overlay)
    } else if (feature && feature.get('violation')) {
      // 点击的是禁行点
      const point = feature.get('violation')
      const coord = e.coordinate

      const overlay = createViolationOverlay(coord, point)
      showOverlay(overlay)
    }
  }

  map.on('click', turnPointClickHandler)
}

// 设置禁行点 Popup
const setupViolationPopup = () => {
  // 与转弯点共用同一个点击监听器
  // 在 setupTurnPointPopup 中已经处理
}

// 创建禁行点 overlay
const createViolationOverlay = (coordinate, point) => {
  const content = `
    <div style="padding: 10px; min-width: 220px;">
      <h4 style="margin: 0 0 10px; color: #f56c6c;">${point.reason}</h4>
      <div style="font-size: 13px; line-height: 1.8;">
        <div><strong>详情:</strong> ${point.detail}</div>
        ${point.turnAngle ? `<div><strong>转弯角度:</strong> ${point.turnAngle.toFixed(1)}°</div>` : ''}
        ${point.turnRadius ? `<div><strong>转弯半径:</strong> ${point.turnRadius.toFixed(1)}m</div>` : ''}
        <div style="margin-top: 10px; color: #909399; font-size: 12px;">
          位置：${point.lat.toFixed(6)}, ${point.lon.toFixed(6)}
        </div>
      </div>
    </div>
  `

  return new Overlay({
    position: coordinate,
    positioning: 'bottom-center',
    element: createOverlayElement(content),
    offset: [0, -10]
  })
}

// 创建转弯点 overlay
const createTurnPointOverlay = (coordinate, point) => {
  const content = `
    <div style="padding: 10px; min-width: 200px;">
      <h4 style="margin: 0 0 10px; color: #303133;">转弯点 #${point.sequence}</h4>
      <div style="font-size: 13px; line-height: 1.8;">
        <div><strong>转向:</strong> ${point.instruction}</div>
        <div><strong>角度:</strong> <span style="color: ${point.isSharpTurn ? '#f56c6c' : '#409eff'}; font-weight: bold;">${point.turnAngle?.toFixed(1)}°</span></div>
        <div><strong>半径:</strong> ${point.turnRadius ? point.turnRadius.toFixed(1) + 'm' : 'N/A'}</div>
        <div><strong>进入方位:</strong> ${point.bearingBefore?.toFixed(0)}°</div>
        <div><strong>离开方位:</strong> ${point.bearingAfter?.toFixed(0)}°</div>
      </div>
    </div>
  `

  return new Overlay({
    position: coordinate,
    positioning: 'bottom-center',
    element: createOverlayElement(content),
    offset: [0, -10]
  })
}

// 创建 overlay DOM 元素
const createOverlayElement = (content) => {
  const el = document.createElement('div')
  el.className = 'turn-point-popup'
  el.innerHTML = content
  el.style.cssText = `
    background: white;
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.2);
    padding: 0;
    margin: 0;
  `
  return el
}

// 显示 overlay
const showOverlay = (overlay) => {
  if (currentOverlay) {
    map.removeOverlay(currentOverlay)
  }
  map.addOverlay(overlay)
  currentOverlay = overlay
}

// 定位禁行点
const handleLocatePoint = (point) => {
  if (map && point.lat && point.lon) {
    map.getView().animate({
      center: fromLonLat([point.lon, point.lat]),
      zoom: 16,
      duration: 500
    })
  }
}

// 货车分析选点处理（新流程：从 MapTools 来）
let truckAnalysisSelectCallback = null
let truckAnalysisSelectStep = null  // 'start' or 'end'

const handleTruckAnalysisSelect = (callback) => {
  truckAnalysisSelectCallback = callback
  truckAnalysisSelectStep = 'start'
  ElMessage.info('请在地图上点击选择起点')
}

const handleTruckAnalysisLocate = (result) => {
  console.log('[TruckAnalysis] handleTruckAnalysisLocate called')
  console.log('[TruckAnalysis] Result:', result)

  // 后端返回格式：{code: 200, message: 'success', data: AnalysisResultDTO}
  // 需要解包 data
  const analysisResult = result.data || result

  console.log('[TruckAnalysis] Unwrapped result:', analysisResult)
  console.log('[TruckAnalysis] routeGeoJson:', analysisResult.routeGeoJson)
  console.log('[TruckAnalysis] turnPoints:', analysisResult.turnPoints)

  handleShowRoute(analysisResult)
}

const handleTruckAnalysisHide = () => {
  console.log('[TruckAnalysis] handleTruckAnalysisHide called')
  clearTruckAnalysisLayers()
  ElMessage.info('路线已隐藏')
}

// 更新 map click 事件中的货车分析处理逻辑
// 在已有的 map click 事件中，需要添加对新流程的支持

let roadNetworkDrawCallback = null
let truckAnalysisCallback = null

// 货车分析图层相关
let truckAnalysisRouteLayer = null
let truckAnalysisTurnPointsLayer = null
let turnPointClickHandler = null
let currentOverlay = null

// 货车参数（用于计算最小转弯半径）
const truck = ref({
  wheelbase: 8
})

// 处理影像加载
const handleImageLoad = (image) => {
  loadGeoTIFF(image.id)
}

// 从工具栏创建项目（基于影像）
const handleCreateProjectFromTool = (projectData) => {
  console.log('[handleCreateProjectFromTool] projectData:', projectData)
  // 项目已创建，现在加载影像
  if (projectData.baseImageId || projectData.imageId) {
    const imageId = projectData.baseImageId || projectData.imageId
    console.log('[handleCreateProjectFromTool] 加载影像:', imageId)
    // 先设置当前项目
    currentProject.value = projectData
    // 检查图层是否已初始化，如果没有，等待一下
    if (!imageLayer.value || !vectorLayer) {
      console.log('[handleCreateProjectFromTool] 图层未初始化，等待...')
      setTimeout(() => {
        loadBaseImage(imageId).then(() => {
          console.log('[handleCreateProjectFromTool] 影像加载完成')
          ElMessage.success('项目已创建，影像已加载')
        }).catch(err => {
          console.error('[handleCreateProjectFromTool] 影像加载失败:', err)
          ElMessage.error('项目创建成功，但影像加载失败')
        })
      }, 500)
    } else {
      // 加载影像
      loadBaseImage(imageId).then(() => {
        console.log('[handleCreateProjectFromTool] 影像加载完成')
        ElMessage.success('项目已创建，影像已加载')
      }).catch(err => {
        console.error('[handleCreateProjectFromTool] 影像加载失败:', err)
        ElMessage.error('项目创建成功，但影像加载失败')
      })
    }
  }
}

// 创建占位图项目 - 进入绘制矩形状态
const handleCreatePlaceholderProject = (projectData) => {
  console.log('[handleCreatePlaceholderProject] 开始创建占位图项目')

  // 先清空当前地图状态
  if (map) {
    console.log('[handleCreatePlaceholderProject] 清空当前地图状态')
    const allLayers = map.getLayers().getArray().slice()
    allLayers.forEach(layer => {
      map.removeLayer(layer)
    })
  }
  tileLayer = null
  imageLayer.value = null
  vectorSource = new VectorSource()
  vectorLayer = null
  currentProject.value = null

  // 设置新项目
  currentProject.value = projectData
  console.log('[handleCreatePlaceholderProject] 新项目已设置:', projectData)

  // 重新初始化地图图层
  if (map) {
    // 添加 OSM 底图
    tileLayer = new TileLayer({
      source: new XYZ({
        url: 'https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png',
        crossOrigin: 'anonymous'
      })
    })
    map.addLayer(tileLayer)

    // 创建空影像图层
    imageLayer.value = new ImageLayer({
      source: null,
      visible: false,
      zIndex: 1
    })
    map.addLayer(imageLayer.value)

    // 创建矢量图层
    vectorSource = new VectorSource()
    vectorLayer = new VectorLayer({
      source: vectorSource,
      style: (feature) => {
        const featureStyle = feature.getStyle()
        if (featureStyle) return featureStyle
        return getDefaultStyle()
      },
      zIndex: 999
    })
    map.addLayer(vectorLayer)
  }

  ElMessage.info('请在地图上绘制矩形定义项目范围，双击完成绘制')
  // 激活矩形绘制工具
  setDrawType('Rectangle')
  // 设置一个标志，表示正在绘制项目范围
  isDrawingProjectArea.value = true
}

// 是否正在绘制项目区域
const isDrawingProjectArea = ref(false)

// 处理影像调整（实时预览）
const handleImageAdjustment = (params) => {
  if (!imageLayer.value) return

  // 实时预览时使用 CSS filter（仅用于预览，不保存）
  const filter = `brightness(${params.brightness}) contrast(${params.contrast})`

  // 获取当前影像图层的 canvas 元素
  const canvas = document.querySelector('.ol-layer canvas')
  if (canvas) {
    canvas.style.filter = filter
  }

  // 应用透明度
  imageLayer.value.setOpacity(params.opacity)
  imageLayer.value.changed()

  imageStore.updateAdjustment(params)
}

// 确保矢量图层在最上层
// 注意：使用双层地图架构后，矢量地图本身就是独立的上层，不需要此函数
const ensureVectorLayerOnTop = () => {
  // 双层地图架构下，矢量地图天然在最上层
  // 只需要触发重绘
  if (vectorLayer) {
    vectorLayer.changed()
  }
  console.log('[ensureVectorLayerOnTop] 双层地图架构，矢量天然在最上层')
}

// 加载 GeoTIFF 影像到地图
const loadGeoTIFF = async (imageId) => {
  try {
    console.log('[loadGeoTIFF] 开始加载影像:', imageId)
    const res = await getImage(imageId)
    if (res.code !== 200) {
      ElMessage.error('加载影像失败')
      return
    }

    const image = res.data
    console.log('[loadGeoTIFF] 影像信息:', {
      id: image.id,
      name: image.name,
      filePath: image.filePath,
      fileSize: image.fileSize,
      width: image.width,
      height: image.height
    })
    imageStore.setCurrentImage(image)

    // 计算影像范围
    const extent = [
      image.minX || 0, image.minY || 0,
      image.maxX || 0, image.maxY || 0
    ]

    // 如果范围为 0，使用默认范围
    if (extent[0] === 0 && extent[1] === 0 && extent[2] === 0 && extent[3] === 0) {
      ElMessage.warning('影像范围信息缺失，使用默认范围')
    }

    // 关键修复：保存所有矢量要素
    console.log('[loadGeoTIFF] 保存矢量要素，准备加载影像')
    const features = vectorSource.getFeatures()
    const featuresData = []
    features.forEach(f => {
      const geom = f.getGeometry()
      featuresData.push({
        geometry: geom.clone(),
        properties: f.getProperties(),
        style: f.getStyle()
      })
    })
    console.log('[loadGeoTIFF] 保存了', featuresData.length, '个矢量要素')

    // 清空矢量图层
    vectorSource.clear()
    console.log('[loadGeoTIFF] 矢量图层已清空')

    // 创建新的影像 source
    const timestamp = new Date().getTime()
    const newSource = new Static({
      url: `${getImagePreviewUrl(image.id)}?t=${timestamp}`,
      crossOrigin: 'anonymous',
      imageExtent: extent,
      projection: 'EPSG:3857'
    })

    // 关键修复：检查地图上现有的影像图层数量
    const allLayers = map.getLayers().getArray()
    console.log('[loadGeoTIFF] 加载前图层数量:', allLayers.length)
    console.log('[loadGeoTIFF] 加载前图层:', allLayers.map((l, i) => `${i}: ${l.constructor.name}`))

    // 移除所有 ImageLayer 类型的图层
    const imageLayers = allLayers.filter(l => l.constructor.name === 'ImageLayer' || l.constructor.name === 'rf')
    console.log('[loadGeoTIFF] 要移除的影像图层数量:', imageLayers.length)
    imageLayers.forEach(layer => {
      map.removeLayer(layer)
      console.log('[loadGeoTIFF] 已移除影像图层')
    })

    // 关键修复：先移除矢量图层，确保影像加载后矢量在最上层
    console.log('[loadGeoTIFF] 移除矢量图层，准备加载影像')
    map.removeLayer(vectorLayer)

    // 创建新的影像图层
    const newImageLayer = new ImageLayer({
      source: newSource,
      visible: true,
      zIndex: 1
    })

    // 缩放到影像范围
    if (extent[0] !== 0 || extent[1] !== 0 || extent[2] !== 0 || extent[3] !== 0) {
      map.getView().fit(extent, { duration: 500, padding: [50, 50, 50, 50] })
    }

    // 添加影像图层
    map.addLayer(newImageLayer)
    imageLayer.value = newImageLayer

    console.log('[loadGeoTIFF] 影像图层已重新创建')

    // 关键修复：设置最高 zIndex 并重新添加矢量图层到最上层
    vectorLayer.setZIndex(999)
    map.addLayer(vectorLayer)

    // 调试：检查图层顺序
    const layersAfterReorder = map.getLayers().getArray()
    console.log('[loadGeoTIFF] 重排后图层顺序:', layersAfterReorder.map((l, i) => `${i}: ${l.constructor.name}`))
    console.log('[loadGeoTIFF] 矢量图层已重新添加，zIndex=999')

    // 等待影像渲染完成后再添加矢量
    await new Promise(resolve => setTimeout(resolve, 100))

    // 重新添加所有矢量要素
    console.log('[loadGeoTIFF] 开始重新添加矢量要素')
    featuresData.forEach((data, idx) => {
      const feature = new Feature({
        geomType: data.properties.geomType,
        isSymbol: data.properties.isSymbol,
        symbolId: data.properties.symbolId,
        symbolName: data.properties.symbolName
      })
      feature.setGeometry(data.geometry)
      if (data.style) {
        feature.setStyle(data.style)
        console.log('[loadGeoTIFF] 要素', idx, '样式已恢复')
      } else {
        console.log('[loadGeoTIFF] 要素', idx, '无样式，使用默认')
      }
      vectorSource.addFeature(feature)
    })

    console.log('[loadGeoTIFF] 矢量要素已重新添加，共', vectorSource.getFeatures().length, '个')

    // 强制重绘 - 关键修复：先隐藏再显示，确保 OpenLayers 完全重绘
    vectorLayer.setVisible(false)
    await new Promise(resolve => setTimeout(resolve, 50))
    vectorLayer.setVisible(true)
    vectorLayer.changed()
    map.renderSync()

    console.log('[loadGeoTIFF] 已强制重绘，矢量图层在最上层')

    // 最终检查图层
    const finalLayers = map.getLayers().getArray()
    console.log('[loadGeoTIFF] 最终图层顺序:', finalLayers.map((l, i) => `${i}: ${l.constructor.name}`))

    // 重置调整参数
    imageStore.resetAdjustment()
    console.log('[loadGeoTIFF] 影像加载完成，调整参数已重置')

    ElMessage.success('影像加载成功')
  } catch (error) {
    console.error('加载影像失败:', error)
    ElMessage.error('加载影像失败：' + error.message)
  }
}

// 加载透明占位影像（用于初始化影像图层，防止覆盖矢量）
const loadPlaceholderImage = async () => {
  console.log('[loadPlaceholderImage] 加载透明占位影像')

  // 设置一个空的影像 source（不显示任何内容）
  // 关键：这确保影像图层已初始化，后续加载真实影像时不会覆盖矢量
  const placeholderSource = new Static({
    url: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',  // 1x1 透明 PNG
    crossOrigin: 'anonymous',
    imageExtent: [-20037508.34, -20037508.34, 20037508.34, 20037508.34],  // 全球范围
    projection: 'EPSG:3857'
  })

  imageLayer.value.setSource(placeholderSource)
  imageLayer.value.setZIndex(1)
  imageLayer.value.setVisible(false)  // 隐藏，不显示

  console.log('[loadPlaceholderImage] 透明占位影像已加载')
}

// 加载基于标注范围的透明占位影像（后端创建真实文件）
const loadPlaceholderImageForAnnotations = async (annotations) => {
  console.log('[loadPlaceholderImageForAnnotations] 基于标注范围加载透明占位影像（后端创建）')

  // 1. 计算所有标注的外接矩形
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity

  annotations.forEach(ann => {
    let geometry = ann.geometry
    if (typeof geometry === 'string') {
      geometry = JSON.parse(geometry)
    }
    const coords = geometry.coordinates

    if (geometry.type === 'Point') {
      minX = Math.min(minX, coords[0])
      minY = Math.min(minY, coords[1])
      maxX = Math.max(maxX, coords[0])
      maxY = Math.max(maxY, coords[1])
    } else if (geometry.type === 'LineString') {
      coords.forEach(coord => {
        minX = Math.min(minX, coord[0])
        minY = Math.min(minY, coord[1])
        maxX = Math.max(maxX, coord[0])
        maxY = Math.max(maxY, coord[1])
      })
    } else if (geometry.type === 'Polygon' || geometry.type === 'Rectangle' || geometry.type === 'Circle') {
      // Polygon 坐标是嵌套数组，取第一个环
      const ring = coords[0]
      ring.forEach(coord => {
        minX = Math.min(minX, coord[0])
        minY = Math.min(minY, coord[1])
        maxX = Math.max(maxX, coord[0])
        maxY = Math.max(maxY, coord[1])
      })
    }
  })

  // 添加 10% 的边距
  const paddingX = (maxX - minX) * 0.1 || 1000
  const paddingY = (maxY - minY) * 0.1 || 1000
  const extent = [minX - paddingX, minY - paddingY, maxX + paddingX, maxY + paddingY]

  console.log('[loadPlaceholderImageForAnnotations] 标注范围:', { minX, minY, maxX, maxY })
  console.log('[loadPlaceholderImageForAnnotations] 扩展范围:', extent)

  try {
    // 2. 调用后端 API 创建透明占位影像
    const token = localStorage.getItem('token')
    const response = await fetch('/api/images/create-placeholder', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        minX: minX - paddingX,
        minY: minY - paddingY,
        maxX: maxX + paddingX,
        maxY: maxY + paddingY,
        projectId: currentProject.value?.id
      })
    })

    const result = await response.json()

    if (result.code !== 200) {
      console.error('[loadPlaceholderImageForAnnotations] 创建占位影像失败:', result.message)
      ElMessage.error('创建占位影像失败：' + (result.message || '未知错误'))
      return
    }

    const placeholderImage = result.data
    console.log('[loadPlaceholderImageForAnnotations] 占位影像创建成功:', placeholderImage)

    // 关键修复：保存所有矢量要素（在移除图层之前）
    console.log('[loadPlaceholderImageForAnnotations] 保存矢量要素，准备加载占位影像')
    const features = vectorSource.getFeatures()
    const featuresData = []
    features.forEach(f => {
      const geom = f.getGeometry()
      featuresData.push({
        geometry: geom.clone(),
        properties: f.getProperties(),
        style: f.getStyle()
      })
    })
    console.log('[loadPlaceholderImageForAnnotations] 保存了', featuresData.length, '个矢量要素')

    // 关键修复：先清空矢量图层，避免影像加载时覆盖
    vectorSource.clear()
    console.log('[loadPlaceholderImageForAnnotations] 矢量图层已清空')

    // 3. 使用创建的占位影像加载地图
    const newSource = new Static({
      url: `${getImagePreviewUrl(placeholderImage.id)}?t=${new Date().getTime()}`,
      crossOrigin: 'anonymous',
      imageExtent: extent,
      projection: 'EPSG:3857'
    })

    // 移除旧图层，创建新图层
    const oldImageLayer = imageLayer.value
    map.removeLayer(oldImageLayer)

    const newImageLayer = new ImageLayer({
      source: newSource,
      visible: false  // 保持隐藏，仅用于初始化图层结构
    })
    map.addLayer(newImageLayer)
    imageLayer.value = newImageLayer

    console.log('[loadPlaceholderImageForAnnotations] 占位影像图层已重新创建')

    // 关键修复：移除矢量图层，然后重新添加，确保它在最上面
    map.removeLayer(vectorLayer)
    map.addLayer(vectorLayer)

    // 调试：检查图层顺序
    const placeholderLayers = map.getLayers().getArray()
    console.log('[loadPlaceholderImageForAnnotations] 当前图层顺序:', placeholderLayers.map((l, i) => `${i}: ${l.constructor.name}`))
    console.log('[loadPlaceholderImageForAnnotations] 矢量图层已移动到最上层')

    // 等待影像渲染完成后再添加矢量
    await new Promise(resolve => setTimeout(resolve, 100))

    // 重新添加所有矢量要素
    console.log('[loadPlaceholderImageForAnnotations] 开始重新添加矢量要素')
    featuresData.forEach(data => {
      const feature = new Feature({
        geomType: data.properties.geomType,
        isSymbol: data.properties.isSymbol,
        symbolId: data.properties.symbolId,
        symbolName: data.properties.symbolName
      })
      feature.setGeometry(data.geometry)
      if (data.style) {
        feature.setStyle(data.style)
      }
      vectorSource.addFeature(feature)
    })

    console.log('[loadPlaceholderImageForAnnotations] 矢量要素已重新添加，共', vectorSource.getFeatures().length, '个')

    // 4. 设置为当前影像
    imageStore.setCurrentImage(placeholderImage)

    // 5. 强制重绘
    vectorLayer.changed()
    map.renderSync()

    console.log('[loadPlaceholderImageForAnnotations] 透明占位影像已加载，ID:', placeholderImage.id)
    ElMessage.success('占位影像已加载')

  } catch (error) {
    console.error('[loadPlaceholderImageForAnnotations] 创建占位影像异常:', error)
    ElMessage.error('创建占位影像失败：' + error.message)
  }
}

// 加载底图影像（不重置调整参数，不清除当前影像）
const loadBaseImage = async (imageId) => {
  try {
    console.log('[loadBaseImage] 开始加载底图影像:', imageId)
    const res = await getImage(imageId)
    if (res.code !== 200) {
      ElMessage.error('加载底图影像失败')
      return
    }

    const image = res.data
    console.log('[loadBaseImage] 影像信息:', image)

    // 计算影像范围
    const extent = [
      image.minX || 0, image.minY || 0,
      image.maxX || 0, image.maxY || 0
    ]

    // 如果范围为 0，使用默认范围
    if (extent[0] === 0 && extent[1] === 0 && extent[2] === 0 && extent[3] === 0) {
      console.log('[loadBaseImage] 影像范围信息缺失，使用默认范围')
    }

    // 创建新的影像 source
    const timestamp = new Date().getTime()
    const newSource = new Static({
      url: `${getImagePreviewUrl(image.id)}?t=${timestamp}`,
      crossOrigin: 'anonymous',
      imageExtent: extent,
      projection: 'EPSG:3857'
    })

    // 移除旧影像图层
    console.log('[loadBaseImage] 移除旧影像图层')
    map.removeLayer(imageLayer.value)

    // 关键修复：先移除矢量图层，等影像图层添加后再添加，确保渲染顺序正确
    console.log('[loadBaseImage] 移除矢量图层，准备加载影像')
    map.removeLayer(vectorLayer)

    // 创建新的影像图层
    const newImageLayer = new ImageLayer({
      source: newSource,
      visible: true,
      zIndex: 1
    })
    map.addLayer(newImageLayer)
    imageLayer.value = newImageLayer

    console.log('[loadBaseImage] 影像图层已重新创建')

    // 缩放到影像范围
    if (extent[0] !== 0 || extent[1] !== 0 || extent[2] !== 0 || extent[3] !== 0) {
      map.getView().fit(extent, { duration: 500, padding: [50, 50, 50, 50] })
    }

    // 关键修复：设置最高 zIndex 并添加矢量图层到最上层
    vectorLayer.setZIndex(999)
    map.addLayer(vectorLayer)

    // 调试：检查图层顺序
    const baseLayers = map.getLayers().getArray()
    console.log('[loadBaseImage] 当前图层顺序:', baseLayers.map((l, i) => `${i}: ${l.constructor.name}`))
    console.log('[loadBaseImage] 矢量图层已移动到最上层，zIndex=999')

    // 关键修复：保存所有矢量要素，清空后重新添加，强制 OpenLayers 完全重绘
    console.log('[loadBaseImage] 保存矢量要素，准备重绘')
    const features = vectorSource.getFeatures()
    const featuresData = []
    features.forEach(f => {
      const geom = f.getGeometry()
      featuresData.push({
        geometry: geom.clone(),
        properties: f.getProperties(),
        style: f.getStyle()
      })
    })

    // 清空并重新添加
    vectorSource.clear()
    console.log('[loadBaseImage] 矢量图层已清空，重新添加', featuresData.length, '个要素')

    // 等待影像渲染完成后再添加矢量
    await new Promise(resolve => setTimeout(resolve, 100))

    // 重新添加所有要素
    featuresData.forEach(data => {
      const feature = new Feature({
        geomType: data.properties.geomType,
        isSymbol: data.properties.isSymbol,
        symbolId: data.properties.symbolId,
        symbolName: data.properties.symbolName
      })
      feature.setGeometry(data.geometry)
      if (data.style) {
        feature.setStyle(data.style)
      }
      vectorSource.addFeature(feature)
    })

    console.log('[loadBaseImage] 矢量要素已重新添加')

    // 强制重绘
    vectorLayer.changed()
    map.renderSync()

    console.log('[loadBaseImage] 已强制重绘，矢量图层 zIndex=999')

    // 设置为当前影像
    imageStore.setCurrentImage(image)

    console.log('[loadBaseImage] 底图影像加载完成')
    ElMessage.success('底图影像加载成功')
  } catch (error) {
    console.error('加载底图影像失败:', error)
    ElMessage.error('加载底图影像失败：' + error.message)
  }
}

// 加载影像列表（用于对比功能）
const loadImagesList = async () => {
  try {
    const res = await getImages()
    if (res.code === 200) {
      imageStore.setImages(res.data || [])
    }
  } catch (error) {
    console.error('加载影像列表失败:', error)
  }
}

onMounted(() => {
  initMap()
  // 加载路网列表
  loadRoadNetworks()
  // 不再自动加载影像列表，改为在 MapTools 中需要时再加载
  // loadImagesList()
})

onUnmounted(() => {
  if (map) {
    map.setTarget(null)
  }
})
</script>

<style scoped>
.map-container {
  width: 100%;
  height: 100%;
  position: relative;
}

.map {
  width: 100%;
  height: 100%;
}

.map canvas {
  cursor: inherit !important;
}

.toolbar {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1000;
  background: white;
  padding: 10px;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* 符号按钮样式 */
.symbol-btn {
  padding: 4px 8px;
}

.symbol-btn svg {
  width: 20px;
  height: 20px;
  min-width: 20px;
  min-height: 20px;
}

/* 颜色选择器样式 */
.color-picker-wrapper {
  display: flex;
  align-items: center;
}

.user-info {
  position: absolute;
  top: 10px;
  right: 10px;
  background: white;
  padding: 10px 20px;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  gap: 10px;
}

.map {
  width: 100%;
  height: 100%;
}

.map canvas {
  cursor: inherit !important;
}

/* 确保矢量图层的 canvas 在最上层 */
.map canvas.ol-vector-layer {
  z-index: 100 !important;
  position: relative !important;
}

.status-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 8px 15px;
  font-size: 12px;
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.version-info {
  margin-left: auto;
  opacity: 0.8;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
}

.mouse-position {
  bottom: 30px;
  right: 10px;
  position: absolute;
  background: rgba(255, 255, 255, 0.8);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
}

/* 符号按钮 SVG 样式 */
.el-button svg {
  display: inline-block;
  vertical-align: middle;
}

/* 右键菜单样式 */
.context-menu {
  position: fixed;
  z-index: 9999;
  background: white;
  border-radius: 6px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  padding: 6px 0;
  min-width: 120px;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  color: #333;
}

.context-menu-item:hover {
  background: #f5f7fa;
  color: #409eff;
}

.context-menu-item.disabled {
  color: #ccc;
  cursor: not-allowed;
}

.context-menu-item.disabled:hover {
  background: transparent;
}

/* 双层地图架构样式 */
.base-map {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
}

.vector-map {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
  background: transparent !important;
}

/* 确保矢量地图的所有 canvas 背景透明 */
.vector-map canvas {
  background: transparent !important;
}

/* 确保矢量地图的图层容器也透明 */
.vector-map .ol-layer {
  background: transparent !important;
}

/* 矢量地图的交互点样式 */
.vector-map .ol-pointer-event {
  cursor: inherit;
}

/* 路网下载对话框全屏样式 */
.road-network-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}

@media screen and (min-width: 1200px) {
  .road-network-dialog {
    width: 80%;
  }
}
</style>
