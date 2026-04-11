<template>
  <div class="image-adjustment" v-if="visible">
    <el-card shadow="hover" class="adjustment-card">
      <template #header>
        <div class="card-header">
          <span>影像调整</span>
          <el-button size="small" @click="resetAdjustment">重置</el-button>
        </div>
      </template>

      <div class="adjustment-item">
        <span class="label">亮度</span>
        <el-slider
          v-model="params.brightness"
          :min="0"
          :max="2"
          :step="0.01"
          @change="applyAdjustment"
        />
        <span class="value">{{ params.brightness.toFixed(2) }}</span>
      </div>

      <div class="adjustment-item">
        <span class="label">对比度</span>
        <el-slider
          v-model="params.contrast"
          :min="0"
          :max="2"
          :step="0.01"
          @change="applyAdjustment"
        />
        <span class="value">{{ params.contrast.toFixed(2) }}</span>
      </div>

      <div class="adjustment-item">
        <span class="label">Gamma</span>
        <el-slider
          v-model="params.gamma"
          :min="0.1"
          :max="3"
          :step="0.01"
          @change="applyAdjustment"
        />
        <span class="value">{{ params.gamma.toFixed(2) }}</span>
      </div>

      <div class="adjustment-item">
        <span class="label">透明度</span>
        <el-slider
          v-model="params.opacity"
          :min="0"
          :max="1"
          :step="0.01"
          @change="applyOpacity"
        />
        <span class="value">{{ params.opacity.toFixed(2) }}</span>
      </div>

      <div class="adjustment-item" v-if="imageStore.currentImage">
        <span class="label">当前影像</span>
        <span class="image-name">{{ imageStore.currentImage.name }}</span>
      </div>

      <!-- 保存调整按钮 -->
      <div class="adjustment-actions">
        <el-button
          type="primary"
          size="small"
          @click="saveAdjustment('current')"
          :disabled="!imageStore.currentImage"
        >
          保存调整
        </el-button>
        <el-button
          type="success"
          size="small"
          @click="showSaveAsDialog = true"
          :disabled="!imageStore.currentImage"
        >
          另存为新影像
        </el-button>
      </div>
    </el-card>

    <!-- 另存为对话框 -->
    <el-dialog
      v-model="showSaveAsDialog"
      title="另存为新影像"
      width="400px"
    >
      <el-form :model="saveAsForm" label-width="80px">
        <el-form-item label="影像名称">
          <el-input
            v-model="saveAsForm.name"
            placeholder="请输入新影像名称"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSaveAsDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAsNewImage" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, computed, watch, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useImageStore } from '@/stores/image'
import { saveAdjustment, saveAdjusted } from '@/api/batch'

const props = defineProps({
  modelValue: Boolean
})

const emit = defineEmits(['update:modelValue', 'adjustment-change'])

const imageStore = useImageStore()

const visible = computed(() => props.modelValue)

const params = reactive({
  brightness: 1.0,
  contrast: 1.0,
  gamma: 1.0,
  opacity: 1.0
})

const showSaveAsDialog = ref(false)
const saving = ref(false)
const saveAsForm = ref({
  name: ''
})

// Watch for current image changes and reset adjustments
watch(() => imageStore.currentImage, (newImage) => {
  if (newImage) {
    resetAdjustment()
  }
})

const applyAdjustment = () => {
  emit('adjustment-change', {
    brightness: params.brightness,
    contrast: params.contrast,
    gamma: params.gamma,
    opacity: params.opacity
  })
}

const applyOpacity = () => {
  emit('adjustment-change', {
    opacity: params.opacity
  })
}

const resetAdjustment = () => {
  params.brightness = 1.0
  params.contrast = 1.0
  params.gamma = 1.0
  params.opacity = 1.0
  imageStore.resetAdjustment()
  applyAdjustment()
}

// 保存调整到当前影像
const saveAdjustmentToImage = async (mode) => {
  if (!imageStore.currentImage) return

  saving.value = true
  try {
    if (mode === 'current') {
      // 仅保存调整参数
      const res = await saveAdjustment(imageStore.currentImage.id, {
        brightness: params.brightness,
        contrast: params.contrast,
        gamma: params.gamma
      })
      if (res.code === 200) {
        ElMessage.success('调整参数已保存')
      } else {
        ElMessage.error('保存失败：' + (res.message || '未知错误'))
      }
    } else if (mode === 'new') {
      // 保存为新影像
      const res = await saveAdjusted(
        imageStore.currentImage.id,
        {
          brightness: params.brightness,
          contrast: params.contrast,
          gamma: params.gamma,
          asNew: true
        },
        saveAsForm.value.name || `Adjusted_${imageStore.currentImage.name}`
      )
      if (res.code === 200) {
        ElMessage.success('新影像已保存')
        showSaveAsDialog.value = false
        saveAsForm.value.name = ''
        emit('image-saved', res.data)
      } else {
        ElMessage.error('保存失败：' + (res.message || '未知错误'))
      }
    }
  } catch (error) {
    ElMessage.error('保存失败：' + (error.message || '网络错误'))
  } finally {
    saving.value = false
  }
}

const saveAdjustment = (mode) => {
  saveAdjustmentToImage(mode)
}

const saveAsNewImage = () => {
  saveAdjustmentToImage('new')
}
</script>

<style scoped>
.image-adjustment {
  position: absolute;
  top: 80px;
  right: 10px;
  z-index: 1000;
}

.adjustment-card {
  width: 300px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.adjustment-item .image-name {
  flex: 1;
  font-size: 14px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px solid #eee;
}
</style>
