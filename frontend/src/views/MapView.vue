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

      <el-divider direction="vertical" />

      <el-button @click="clearAll" type="danger">清除全部</el-button>

      <!-- 符号库编辑按钮 -->
      <el-button @click="openSymbolLibrary">符号库编辑</el-button>

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
    <div ref="mapRef" class="map"></div>

    <!-- 工具面板组件（量测 + 符号库） -->
    <MapTools
      v-if="mapInitialized"
      :map="map"
      :vector-source="vectorSource"
      :symbols="symbols"
      :selected-symbol="selectedSymbol"
      @measure-result="handleMeasureResult"
      @symbol-select="handleToolSymbolSelect"
      @symbols-change="handleSymbolsChange"
    />

    <!-- 状态栏 -->
    <div class="status-bar">
      <span>坐标：{{ coordinate }}</span>
      <el-divider direction="vertical" />
      <span>缩放级别：{{ zoomLevel }}</span>
      <el-divider direction="vertical" />
      <span v-if="statusMessage">{{ statusMessage }}</span>
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
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, DocumentCopy } from '@element-plus/icons-vue'
import MapTools from '@/components/MapTools.vue'
import SymbolEditDialog from '@/components/SymbolEditDialog.vue'
import ProjectDialog from '@/components/ProjectDialog.vue'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import Feature from 'ol/Feature'
import { fromLonLat } from 'ol/proj'
import MousePosition from 'ol/control/MousePosition'
import { Draw, Modify, Select, Translate } from 'ol/interaction'
import XYZ from 'ol/source/XYZ'
import { Style, Icon, Stroke, Fill, Circle } from 'ol/style'
import { Circle as CircleGeom, Polygon, Point, LineString } from 'ol/geom'
import { fromExtent } from 'ol/geom/Polygon'

// 版本号（用于调试）
const BUILD_VERSION = '20260401-1930'
console.log('%c [Map] 当前版本:', 'background: #f00; color: #fff; font-size: 16px;', BUILD_VERSION)
console.log('%c [Map] 如果看到这个日志，说明加载的是新代码！', 'background: #0f0; color: #000; font-size: 14px;')

const router = useRouter()
const userStore = useUserStore()
const mapRef = ref(null)

let map = null
let vectorSource = null
let vectorLayer = null
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

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const initMap = () => {
  // 创建矢量图层用于标注
  vectorSource = new VectorSource()
  vectorLayer = new VectorLayer({
    source: vectorSource,
    style: (feature) => {
      // 如果要素自己有样式，使用要素样式
      const featureStyle = feature.getStyle()
      if (featureStyle) {
        return featureStyle
      }
      // 否则使用默认样式
      return getDefaultStyle()
    }
  })

  // 创建 XYZ 瓦片图层（使用 OSM 作为测试）
  const tileLayer = new TileLayer({
    source: new XYZ({
      url: 'https://{a-c}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      crossOrigin: 'anonymous'
    }),
    visible: true
  })

  // 鼠标位置显示
  const mousePositionControl = new MousePosition({
    coordinateFormat: function (coordinate) {
      return coordinate[0].toFixed(2) + ', ' + coordinate[1].toFixed(2)
    },
    projection: 'EPSG:3857',
    className: 'mouse-position'
  })

  // 创建地图
  map = new Map({
    target: mapRef.value,
    layers: [tileLayer, vectorLayer],
    view: new View({
      center: fromLonLat([116.4, 39.9]), // 北京
      zoom: 10,
      minZoom: 2,
      maxZoom: 18
    }),
    controls: [mousePositionControl]
  })

  // 监听视图变化
  map.getView().on('change:center', updateStatus)
  map.getView().on('change:resolution', updateStatus)

  // 添加选择交互 - 完全不在 style 函数中处理，避免递归
  console.log('[Select init] 创建 Select 交互，版本:', BUILD_VERSION.value)
  select = new Select({
    // 不设置 style 函数，完全在 select 事件中手动处理
    style: null
  })

  map.addInteraction(select)

  // 监听右键点击事件，显示上下文菜单
  map.on('contextmenu', (e) => {
    e.preventDefault()

    // 隐藏菜单
    contextMenuVisible.value = false

    // 获取点击位置的要素
    const clickedFeature = map.forEachFeatureAtPixel(e.pixel, (feature) => {
      return feature
    })

    // 设置菜单位置
    contextMenuPosition.value = {
      x: e.originalEvent.clientX,
      y: e.originalEvent.clientY
    }

    // 如果已复制要素，任意位置右键都显示菜单（包含粘贴选项）
    if (copiedFeature.value) {
      // 如果点击到要素，也设置成可复制的要素
      if (clickedFeature) {
        contextMenuFeature.value = clickedFeature
      }
      contextMenuVisible.value = true
    } else if (clickedFeature) {
      // 没有复制要素时，只有点击到要素才显示菜单（只有复制选项）
      contextMenuFeature.value = clickedFeature
      contextMenuVisible.value = true
    }
  })

  // 点击地图时关闭上下文菜单
  map.on('click', () => {
    contextMenuVisible.value = false
  })

  // 监听点击事件，实现删除功能
  map.on('click', (e) => {
    // 只有在删除模式下才执行删除
    if (editMode.value !== 'delete') return

    const clickedFeature = map.forEachFeatureAtPixel(e.pixel, (feature) => {
      return feature
    })

    if (clickedFeature) {
      console.log('[Delete] 删除要素:', clickedFeature)
      vectorSource.removeFeature(clickedFeature)
      // 触发重绘
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
    console.log('[Select] 版本:', BUILD_VERSION.value)

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
    console.log('[Draw] drawend 事件，版本:', BUILD_VERSION.value)
    console.log('[Draw] feature:', feature)
    console.log('[Draw] feature keys:', feature.getKeys())

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
      currentProject.value = result.data
      ElMessage.success('项目创建成功')
      projectDialogVisible.value = false
      // 自动保存当前标注
      await saveCurrentAnnotations()
    } else {
      ElMessage.error(result.message || '创建项目失败')
    }
  } catch (error) {
    console.error('创建项目失败:', error)
    ElMessage.error('创建项目失败：' + error.message)
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
      currentProject.value = result.data.project
      console.log('[Open Project] currentProject set to:', currentProject.value)
      // 加载标注
      if (result.data.annotations && result.data.annotations.length > 0) {
        console.log('[Open Project] Loading', result.data.annotations.length, 'annotations')
        loadAnnotationsToMap(result.data.annotations)
      } else {
        console.log('[Open Project] No annotations to load')
        vectorSource.clear()
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

// 保存当前标注到项目
const saveCurrentAnnotations = async () => {
  if (!currentProject.value) {
    ElMessage.error('没有打开的项目')
    return
  }

  try {
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

onMounted(() => {
  initMap()
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
</style>
