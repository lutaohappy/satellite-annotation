<template>
  <div
    v-if="visible"
    class="symbol-palette"
    :style="{ left: position.x + 'px', top: position.y + 'px' }"
  >
    <div class="palette-header" @mousedown="startDrag">
      <span class="title">符号库</span>
      <div class="header-actions">
        <el-button link type="primary" size="small" @click="toggleExpand">
          {{ expanded ? '收起' : '展开' }}
        </el-button>
        <el-button link type="primary" size="small" @click="openSymbolLibrary">
          管理
        </el-button>
        <el-button link type="danger" size="small" @click="close">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <div v-if="expanded" class="palette-content">
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
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Close } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: Boolean,
  symbols: Array,
  selectedSymbol: Object
})

const emit = defineEmits(['update:modelValue', 'symbol-select', 'symbols-change'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const expanded = ref(true)
const position = ref({ x: 10, y: 80 }) // 初始位置在左上角

const selectedCategory = ref('')
const searchKeyword = ref('')
const filteredSymbols = ref([])

// 初始化过滤后的符号列表
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

// 初始化
filterSymbols()

const toggleExpand = () => {
  expanded.value = !expanded.value
}

const close = () => {
  visible.value = false
}

const openSymbolLibrary = () => {
  emit('symbol-select', null) // 清除选择
  emit('open-library')
}

const selectSymbol = (symbol) => {
  emit('symbol-select', symbol)
}

// 处理文件上传
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

// 拖拽移动功能
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

const startDrag = (e) => {
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

// 监听全局鼠标事件
watch(isDragging, (val) => {
  if (val) {
    window.addEventListener('mousemove', onDrag)
    window.addEventListener('mouseup', stopDrag)
  } else {
    window.removeEventListener('mousemove', onDrag)
    window.removeEventListener('mouseup', stopDrag)
  }
})
</script>

<style scoped>
.symbol-palette {
  position: fixed;
  z-index: 2000;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  min-width: 200px;
  max-width: 400px;
  overflow: hidden;
}

.palette-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  cursor: move;
  user-select: none;
}

.palette-header .title {
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

.palette-content {
  padding: 12px;
  max-height: 500px;
  overflow-y: auto;
}

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
  max-height: 400px;
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
