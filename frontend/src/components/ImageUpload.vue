<template>
  <el-dialog
    v-model="dialogVisible"
    title="上传卫星影像"
    width="600px"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="影像文件" required>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          multiple
          accept=".tif,.tiff,.tfw"
          drag
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            拖拽文件到此处或<em>点击选择</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持批量上传 GeoTIFF 文件 (.tif, .tiff) 和世界文件 (.tfw)<br/>
              <strong>提示：</strong>同名 .tfw 文件将自动与 .tif/.tiff 文件匹配
            </div>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="所属项目">
        <el-input v-model="form.projectId" type="number" placeholder="可选" />
      </el-form-item>
    </el-form>

    <!-- 文件列表 -->
    <div v-if="tifFiles.length > 0 || tfwFiles.length > 0" class="file-list">
      <el-card shadow="never" class="file-card">
        <template #header>
          <div class="card-header">
            <span><el-icon><Document /></el-icon> TIFF 文件 ({{ tifFiles.length }})</span>
            <el-tag size="small" type="success">必传</el-tag>
          </div>
        </template>
        <ul class="file-ul">
          <li v-for="(file, idx) in tifFiles" :key="idx" class="file-li">
            <el-icon><Document /></el-icon>
            <span :title="file.name">{{ file.name }}</span>
            <span v-if="getMatchedTfw(file.name)" class="matched">
              <el-icon><CircleCheck /></el-icon> 已匹配 TFW
            </span>
            <span v-else class="unmatched">未匹配 TFW</span>
          </li>
        </ul>
      </el-card>

      <el-card shadow="never" class="file-card" v-if="tfwFiles.length > 0">
        <template #header>
          <div class="card-header">
            <span><el-icon><Document /></el-icon> TFW 文件 ({{ tfwFiles.length }})</span>
            <el-tag size="small" type="info">可选</el-tag>
          </div>
        </template>
        <ul class="file-ul">
          <li v-for="(file, idx) in tfwFiles" :key="idx" class="file-li">
            <el-icon><Document /></el-icon>
            <span :title="file.name">{{ file.name }}</span>
          </li>
        </ul>
      </el-card>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        @click="handleUpload"
        :loading="uploading"
        :disabled="tifFiles.length === 0"
      >
        上传 {{ tifFiles.length }} 个影像文件{{ tfwFiles.length > 0 ? ` + ${tfwFiles.length} 个 TFW 文件` : '' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Document, CircleCheck } from '@element-plus/icons-vue'
import { uploadBatch } from '@/api/batch'

const props = defineProps({
  modelValue: Boolean,
  projectId: Number,
  // 支持传递已有的批次信息，如果为空则创建新批次
  batchUuid: String,
  batchName: String
})

const emit = defineEmits(['update:modelValue', 'uploaded'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const uploadRef = ref(null)

const form = ref({
  projectId: ''
})

const tifFiles = ref([])  // TIFF 文件列表
const tfwFiles = ref([])  // TFW 文件列表
const uploading = ref(false)

// 处理文件选择
const handleFileChange = (file, fileList) => {
  tifFiles.value = []
  tfwFiles.value = []

  fileList.forEach(f => {
    const name = f.raw?.name || f.name
    if (name.toLowerCase().endsWith('.tif') || name.toLowerCase().endsWith('.tiff')) {
      tifFiles.value.push(f.raw || f)
    } else if (name.toLowerCase().endsWith('.tfw')) {
      tfwFiles.value.push(f.raw || f)
    }
  })
}

const handleFileRemove = (file, fileList) => {
  tifFiles.value = []
  tfwFiles.value = []

  fileList.forEach(f => {
    const name = f.raw?.name || f.name
    if (name.toLowerCase().endsWith('.tif') || name.toLowerCase().endsWith('.tiff')) {
      tifFiles.value.push(f.raw || f)
    } else if (name.toLowerCase().endsWith('.tfw')) {
      tfwFiles.value.push(f.raw || f)
    }
  })
}

// 获取与 TIFF 文件匹配的 TFW 文件
const getMatchedTfw = (tifName) => {
  const baseName = tifName.replace(/\.(tif|tiff)$/i, '')
  return tfwFiles.value.find(f => f.name === baseName + '.tfw')
}

const handleUpload = async () => {
  if (tifFiles.value.length === 0) {
    ElMessage.warning('请至少选择一个 TIFF 文件')
    return
  }

  uploading.value = true
  const formData = new FormData()

  // 添加所有 TIFF 文件
  tifFiles.value.forEach(file => {
    formData.append('files', file)
  })

  // 添加所有 TFW 文件
  tfwFiles.value.forEach(file => {
    formData.append('tfwFiles', file)
  })

  // 添加项目 ID
  if (props.projectId) {
    formData.append('projectId', props.projectId)
  } else if (form.value.projectId) {
    formData.append('projectId', parseInt(form.value.projectId))
  }

  // 添加批次信息
  if (props.batchUuid) {
    formData.append('batchUuid', props.batchUuid)
  }
  // 如果有批次名称（新批次或重命名），也传递
  if (props.batchName) {
    formData.append('batchName', props.batchName)
  }

  try {
    const res = await uploadBatch(formData)
    if (res.code === 200) {
      ElMessage.success(`上传成功，共 ${res.data.fileCount} 个文件`)
      emit('uploaded', res.data)
      dialogVisible.value = false
      tifFiles.value = []
      tfwFiles.value = []
      form.value.projectId = ''
      if (uploadRef.value) uploadRef.value.clearFiles()
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch (error) {
    ElMessage.error('上传失败：' + (error.message || '网络错误'))
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.el-upload {
  width: 100%;
}

.file-list {
  margin-top: 15px;
}

.file-card {
  margin-bottom: 10px;
}

.file-card :deep(.el-card__header) {
  padding: 10px 15px;
  background: #f5f7fa;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header .el-icon {
  margin-right: 5px;
  vertical-align: middle;
}

.file-ul {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 150px;
  overflow-y: auto;
}

.file-li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid #eee;
  font-size: 13px;
}

.file-li:last-child {
  border-bottom: none;
}

.file-li .el-icon {
  color: #409eff;
}

.file-li span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-li .matched {
  color: #67c23a;
  font-size: 12px;
  margin-left: auto;
}

.file-li .matched .el-icon {
  vertical-align: middle;
}

.file-li .unmatched {
  color: #e6a23c;
  font-size: 12px;
  margin-left: auto;
}
</style>
