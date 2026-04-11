import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useImageStore = defineStore('image', () => {
  const images = ref([])
  const batches = ref([])
  const imagesByBatch = ref({})
  const currentImage = ref(null)
  const adjustmentParams = ref({
    brightness: 1.0,
    contrast: 1.0,
    gamma: 1.0,
    opacity: 1.0
  })
  const imageLayers = ref([])

  function setImages(list) {
    images.value = list
  }

  function addImage(image) {
    images.value.push(image)
  }

  function removeImage(id) {
    images.value = images.value.filter(img => img.id !== id)
  }

  function setCurrentImage(image) {
    currentImage.value = image
  }

  function updateAdjustment(params) {
    adjustmentParams.value = { ...adjustmentParams.value, ...params }
  }

  function addImageLayer(layer) {
    imageLayers.value.push(layer)
  }

  function removeImageLayer(layerId) {
    imageLayers.value = imageLayers.value.filter(l => l.id !== layerId)
  }

  function resetAdjustment() {
    adjustmentParams.value = {
      brightness: 1.0,
      contrast: 1.0,
      gamma: 1.0,
      opacity: 1.0
    }
  }

  // 批次相关方法
  function setBatches(list) {
    batches.value = list
  }

  function addBatch(batch) {
    batches.value.push(batch)
  }

  function setImagesByBatch(batchId, list) {
    imagesByBatch.value[batchId] = list
  }

  function getImagesByBatch(batchId) {
    return imagesByBatch.value[batchId] || []
  }

  function clearBatchImages(batchId) {
    delete imagesByBatch.value[batchId]
  }

  return {
    images,
    batches,
    imagesByBatch,
    currentImage,
    adjustmentParams,
    imageLayers,
    setImages,
    addImage,
    removeImage,
    setCurrentImage,
    updateAdjustment,
    addImageLayer,
    removeImageLayer,
    resetAdjustment,
    setBatches,
    addBatch,
    setImagesByBatch,
    getImagesByBatch,
    clearBatchImages
  }
})
