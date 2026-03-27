<template>
  <el-dialog
    v-model="dialogVisible"
    title="符号库管理"
    width="1000px"
    :close-on-click-modal="false"
  >
    <div class="edit-content">
      <el-button type="primary" size="small" @click="showUploadForm = true">
        <el-icon><Upload /></el-icon> 上传符号
      </el-button>

      <!-- 上传表单 -->
      <el-form v-if="showUploadForm" :model="uploadForm" class="upload-form" inline>
        <el-form-item label="名称">
          <el-input v-model="uploadForm.name" placeholder="符号名称" size="small" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="uploadForm.category" size="small">
            <el-option label="军事符号" value="military" />
            <el-option label="地标建筑" value="landmark" />
            <el-option label="交通设施" value="transport" />
            <el-option label="自然资源" value="nature" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="SVG">
          <el-upload
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="1"
            accept=".svg"
          >
            <el-button size="small">选择文件</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" @click="handleUpload" :disabled="!svgContent">
            添加
          </el-button>
          <el-button size="small" @click="showUploadForm = false">取消</el-button>
        </el-form-item>
      </el-form>

      <!-- 符号列表 -->
      <el-table :data="symbols" style="width: 100%" max-height="400">
        <el-table-column label="预览" width="80">
          <template #default="{ row }">
            <div class="table-symbol-preview" v-html="row.content"></div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column prop="category" label="分类" width="100">
          <template #default="{ row }">
            {{ getCategoryName(row.category) }}
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="80" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="editExistingSymbol(row)">
              编辑
            </el-button>
            <el-button link type="primary" size="small" @click="copySymbol(row)">
              复制
            </el-button>
            <el-button link type="danger" size="small" @click="deleteSymbol(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 符号编辑详情对话框 -->
    <el-dialog
      v-model="editDetailVisible"
      title="编辑符号详情"
      width="500px"
      append-to-body
    >
      <el-form :model="editDetailForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="editDetailForm.name" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editDetailForm.category">
            <el-option label="军事符号" value="military" />
            <el-option label="地标建筑" value="landmark" />
            <el-option label="交通设施" value="transport" />
            <el-option label="自然资源" value="nature" />
            <el-option label="自定义" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="填充色">
          <el-color-picker v-model="editDetailForm.fillColor" />
        </el-form-item>
        <el-form-item label="大小">
          <el-slider v-model="editDetailForm.size" :min="16" :max="128" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDetailVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSymbolEdit">保存</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: Boolean,
  initialSymbols: Array // 从父组件传入的初始符号数据
})

const emit = defineEmits(['update:modelValue', 'symbols-change'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 符号数据 - 使用 computed 保持与父组件同步
const symbols = computed({
  get: () => props.initialSymbols || defaultSymbols,
  set: (val) => emit('symbols-change', val)
})

// 默认符号（当没有传入 initialSymbols 时使用）
const defaultSymbols = ref([
  {
    id: 1,
    name: '雷达站',
    category: 'military',
    content: '<svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="40" fill="none" stroke="red" stroke-width="3"/><circle cx="50" cy="50" r="25" fill="none" stroke="red" stroke-width="2"/><circle cx="50" cy="50" r="10" fill="red"/><line x1="50" y1="10" x2="50" y2="90" stroke="red" stroke-width="2"/><line x1="10" y1="50" x2="90" y2="50" stroke="red" stroke-width="2"/></svg>',
    fillColor: '#ff0000',
    strokeColor: '#ff0000',
    size: 48
  },
  {
    id: 2,
    name: '机场',
    category: 'transport',
    content: '<svg viewBox="0 0 100 100"><path d="M50 15 L60 40 L85 40 L65 50 L75 75 L50 60 L25 75 L35 50 L15 40 L40 40 Z" fill="#3399ff" stroke="#0066cc" stroke-width="2"/></svg>',
    fillColor: '#3399ff',
    strokeColor: '#0066cc',
    size: 48
  },
  {
    id: 3,
    name: '医院',
    category: 'landmark',
    content: '<svg viewBox="0 0 100 100"><rect x="35" y="10" width="30" height="80" fill="#ff4444"/><rect x="10" y="35" width="80" height="30" fill="#ff4444"/><rect x="40" y="20" width="20" height="60" fill="#ffffff"/><rect x="20" y="40" width="60" height="20" fill="#ffffff"/></svg>',
    fillColor: '#ff4444',
    strokeColor: '#cc0000',
    size: 48
  },
  {
    id: 4,
    name: '森林',
    category: 'nature',
    content: '<svg viewBox="0 0 100 100"><polygon points="50,10 70,50 60,50 75,80 25,80 40,50 30,50" fill="#228B22" stroke="#006400" stroke-width="2"/><rect x="45" y="80" width="10" height="15" fill="#8B4513"/></svg>',
    fillColor: '#228B22',
    strokeColor: '#006400',
    size: 48
  },
  {
    id: 5,
    name: '学校',
    category: 'landmark',
    content: '<svg viewBox="0 0 100 100"><path d="M10 40 L50 15 L90 40 L50 65 Z" fill="#ffcc00" stroke="#cc9900" stroke-width="2"/><rect x="25" y="65" width="50" height="30" fill="#ffcc00" stroke="#cc9900" stroke-width="2"/></svg>',
    fillColor: '#ffcc00',
    strokeColor: '#cc9900',
    size: 48
  },
  {
    id: 6,
    name: '加油站',
    category: 'transport',
    content: '<svg viewBox="0 0 100 100"><rect x="20" y="30" width="50" height="50" fill="#ff6600" stroke="#cc5500" stroke-width="2"/><text x="30" y="65" fill="white" font-size="20" font-weight="bold">GAS</text></svg>',
    fillColor: '#ff6600',
    strokeColor: '#cc5500',
    size: 48
  }
])

const showUploadForm = ref(false)
const svgContent = ref(null)
const editDetailVisible = ref(false)

const uploadForm = ref({
  name: '',
  category: 'custom'
})

const editDetailForm = ref({
  id: null,
  name: '',
  category: '',
  fillColor: '#ff0000',
  size: 48
})

// 处理文件选择
const handleFileChange = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    svgContent.value = e.target.result
  }
  reader.readAsText(file.raw)
}

// 上传符号
const handleUpload = () => {
  if (!uploadForm.value.name || !svgContent.value) {
    ElMessage.warning('请填写符号名称并上传 SVG 文件')
    return
  }

  const newSymbol = {
    id: Date.now(),
    name: uploadForm.value.name,
    category: uploadForm.value.category,
    content: svgContent.value,
    fillColor: '#ff0000',
    strokeColor: '#000000',
    size: 48
  }

  // 使用展开运算符创建新数组，触发 computed 的 set
  symbols.value = [...symbols.value, newSymbol]
  ElMessage.success('符号添加成功')
  showUploadForm.value = false
  uploadForm.value = { name: '', category: 'custom' }
  svgContent.value = null
}

// 编辑现有符号
const editExistingSymbol = (symbol) => {
  editDetailForm.value = {
    id: symbol.id,
    name: symbol.name,
    category: symbol.category,
    fillColor: symbol.fillColor,
    size: symbol.size
  }
  editDetailVisible.value = true
}

// 保存符号编辑
const saveSymbolEdit = () => {
  const symbol = symbols.value.find(s => s.id === editDetailForm.value.id)
  if (symbol) {
    symbol.name = editDetailForm.value.name
    symbol.category = editDetailForm.value.category
    symbol.fillColor = editDetailForm.value.fillColor
    symbol.size = editDetailForm.value.size
    // 触发更新
    symbols.value = [...symbols.value]
    ElMessage.success('符号修改成功')
    editDetailVisible.value = false
  }
}

// 复制符号
const copySymbol = (symbol) => {
  const newSymbol = {
    id: Date.now(),
    name: symbol.name + ' (复制)',
    category: symbol.category,
    content: symbol.content,
    fillColor: symbol.fillColor,
    strokeColor: symbol.strokeColor,
    size: symbol.size
  }

  symbols.value = [...symbols.value, newSymbol]
  ElMessage.success('符号复制成功')
}

// 删除符号
const deleteSymbol = (symbol) => {
  symbols.value = symbols.value.filter(s => s.id !== symbol.id)
  ElMessage.success('符号已删除')
}

// 获取分类名称
const getCategoryName = (category) => {
  const names = {
    military: '军事',
    landmark: '地标',
    transport: '交通',
    nature: '自然',
    custom: '自定义'
  }
  return names[category] || category
}
</script>

<style scoped>
.edit-content {
  padding: 10px 0;
}

.upload-form {
  margin-bottom: 15px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.table-symbol-preview {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.table-symbol-preview :deep(svg) {
  max-width: 100%;
  max-height: 100%;
}
</style>
