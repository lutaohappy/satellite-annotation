<template>
  <div class="symbol-selector">
    <el-button
      :type="selectedSymbol ? 'primary' : 'default'"
      @click="openLibrary"
    >
      {{ selectedSymbol ? selectedSymbol.name : '选择符号' }}
    </el-button>

    <SymbolLibrary
      v-model="dialogVisible"
      @select="handleSelect"
      @close="handleClose"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SymbolLibrary from './SymbolLibrary.vue'

const props = defineProps({
  modelValue: Object
})

const emit = defineEmits(['update:modelValue', 'change', 'start-draw'])

const dialogVisible = ref(false)
const selectedSymbol = ref(props.modelValue)

const openLibrary = () => {
  dialogVisible.value = true
}

const handleSelect = (symbol) => {
  selectedSymbol.value = symbol
  emit('update:modelValue', symbol)
  emit('change', symbol)
  // 通知父组件开始标注
  emit('start-draw', symbol)
}

const handleClose = () => {
  // 关闭回调
}
</script>

<style scoped>
.symbol-selector {
  display: inline-block;
}
</style>
