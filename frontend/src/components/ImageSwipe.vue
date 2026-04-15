<template>
  <el-dialog
    v-model="dialogVisible"
    title="影像对比"
    width="95%"
    :fullscreen="true"
    class="image-swipe-dialog"
  >
    <div class="swipe-container" ref="swipeContainerRef">
      <div ref="swipeMapRef" class="swipe-map"></div>
      <div
        class="swipe-bar"
        :style="{ left: swipePosition + '%' }"
        @mousedown="startDrag"
      >
        <div class="swipe-handle">
          <el-icon><ArrowLeft /><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <div class="swipe-controls">
      <el-form :inline="true" size="default">
        <el-form-item label="左图">
          <el-select
            v-model="leftImageId"
            @change="loadSwipeImages"
            placeholder="选择左图"
            style="width: 200px"
          >
            <el-option
              v-for="img in imageStore.images"
              :key="img.id"
              :label="img.name"
              :value="img.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="右图">
          <el-select
            v-model="rightImageId"
            @change="loadSwipeImages"
            placeholder="选择右图"
            style="width: 200px"
          >
            <el-option
              v-for="img in imageStore.images"
              :key="img.id"
              :label="img.name"
              :value="img.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="syncView = !syncView" :type="syncView ? 'primary' : ''">
            {{ syncView ? '已联动' : '联动视图' }}
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetView">复位视图</el-button>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import Map from 'ol/Map'
import View from 'ol/View'
import ImageLayer from 'ol/layer/Image'
import Static from 'ol/source/ImageStatic'
import { useImageStore } from '@/stores/image'
import { getImages, getImage, getImageFileUrl } from '@/api/image'
import { getCenter } from 'ol/extent'

const props = defineProps({
  modelValue: Boolean
})

const emit = defineEmits(['update:modelValue'])

const imageStore = useImageStore()
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const swipeContainerRef = ref(null)
const swipeMapRef = ref(null)
const swipePosition = ref(50)
const leftImageId = ref(null)
const rightImageId = ref(null)
const syncView = ref(true)

let swipeMap = null
let leftLayer = null
let rightLayer = null
let isDragging = ref(false)

// Watch for dialog visibility
watch(dialogVisible, async (val) => {
  if (val) {
    await nextTick()
    await loadImagesList()
    initSwipeMap()
  } else {
    cleanupMap()
  }
})

// Load images list
const loadImagesList = async () => {
  try {
    const res = await getImages()
    if (res.code === 200) {
      imageStore.setImages(res.data || [])
    }
  } catch (error) {
    ElMessage.error('加载影像列表失败')
  }
}

// Initialize swipe map
const initSwipeMap = () => {
  if (!swipeMapRef.value) return

  if (swipeMap) {
    swipeMap.setTarget(null)
  }

  swipeMap = new Map({
    target: swipeMapRef.value,
    layers: [],
    view: new View({
      center: [12800000, 4800000],
      zoom: 10
    })
  })

  // Add pointer move handler for swipe effect
  swipeMap.on('pointermove', (e) => {
    if (e.dragging) return

    const pixel = e.pixel
    const size = swipeMap.getSize()
    if (size && size[0] > 0) {
      swipePosition.value = Math.max(0, Math.min(100, (pixel[0] / size[0]) * 100))
      updateSwipeClip()
    }
  })
}

// Load swipe images
const loadSwipeImages = async () => {
  if (!leftImageId.value || !rightImageId.value) return

  try {
    const [leftRes, rightRes] = await Promise.all([
      getImage(leftImageId.value),
      getImage(rightImageId.value)
    ])

    if (leftRes.code !== 200 || rightRes.code !== 200) {
      ElMessage.error('加载影像失败')
      return
    }

    const leftImg = leftRes.data
    const rightImg = rightRes.data

    // Remove existing layers
    if (leftLayer) swipeMap.removeLayer(leftLayer)
    if (rightLayer) swipeMap.removeLayer(rightLayer)

    // Create left layer (full visible)
    leftLayer = new ImageLayer({
      source: new Static({
        url: getImageFileUrl(leftImg.id),
        crossOrigin: 'anonymous',
        imageExtent: [leftImg.minX, leftImg.minY, leftImg.maxX, leftImg.maxY],
        projection: 'EPSG:3857'
      }),
      zIndex: 1
    })

    // Create right layer (clipped)
    rightLayer = new ImageLayer({
      source: new Static({
        url: getImageFileUrl(rightImg.id),
        crossOrigin: 'anonymous',
        imageExtent: [rightImg.minX, rightImg.minY, rightImg.maxX, rightImg.maxY],
        projection: 'EPSG:3857'
      }),
      zIndex: 2
    })

    swipeMap.addLayer(leftLayer)
    swipeMap.addLayer(rightLayer)

    updateSwipeClip()

    // Zoom to combined extent
    const extent = [
      Math.min(leftImg.minX, rightImg.minX),
      Math.min(leftImg.minY, rightImg.minY),
      Math.max(leftImg.maxX, rightImg.maxX),
      Math.max(leftImg.maxY, rightImg.maxY)
    ]

    if (extent[0] && extent[1]) {
      swipeMap.getView().fit(extent, { padding: [50, 50, 50, 50], duration: 500 })
    }

    ElMessage.success('影像加载成功')

  } catch (error) {
    ElMessage.error('加载影像失败：' + error.message)
  }
}

// Update swipe clip effect
const updateSwipeClip = () => {
  if (!rightLayer) return

  const clipPath = `inset(0 ${100 - swipePosition.value}% 0 0)`
  const element = rightLayer.getElement()
  if (element) {
    element.style.clipPath = clipPath
  }
}

// Drag handlers
const startDrag = (e) => {
  isDragging.value = true
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', stopDrag)
}

const onDrag = (e) => {
  if (!isDragging.value || !swipeContainerRef.value) return

  const rect = swipeContainerRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const width = rect.width
  swipePosition.value = Math.max(0, Math.min(100, (x / width) * 100))
  updateSwipeClip()
}

const stopDrag = () => {
  isDragging.value = false
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', stopDrag)
}

// Reset view
const resetView = () => {
  if (leftLayer && rightLayer) {
    const leftSource = leftLayer.getSource()
    const extent = leftSource.getImageExtent()
    if (extent) {
      swipeMap.getView().fit(extent, { padding: [50, 50, 50, 50], duration: 500 })
    }
  }
}

// Cleanup
const cleanupMap = () => {
  if (swipeMap) {
    swipeMap.setTarget(null)
    swipeMap = null
  }
  leftLayer = null
  rightLayer = null
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', stopDrag)
}

onUnmounted(() => {
  cleanupMap()
})
</script>

<style scoped>
.image-swipe-dialog :deep(.el-dialog__body) {
  padding: 0;
  display: flex;
  flex-direction: column;
}

.swipe-container {
  position: relative;
  height: 70vh;
  overflow: hidden;
  background: #000;
}

.swipe-map {
  width: 100%;
  height: 100%;
}

.swipe-bar {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 4px;
  background: white;
  box-shadow: 0 0 10px rgba(0,0,0,0.8);
  cursor: ew-resize;
  z-index: 1000;
  transition: background 0.2s;
}

.swipe-bar:hover {
  background: #60a5fa;
}

.swipe-handle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: white;
  border-radius: 50%;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 12px rgba(0,0,0,0.4);
  color: #333;
}

.swipe-handle .el-icon {
  font-size: 20px;
}

.swipe-controls {
  padding: 15px 20px;
  background: #f5f7fa;
  border-top: 1px solid #e4e7ed;
}

.swipe-controls :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 20px;
}
</style>
