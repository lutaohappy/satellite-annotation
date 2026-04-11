import request from '@/utils/request'

/**
 * 批量上传影像
 */
export function uploadBatch(formData) {
  return request({
    url: '/api/images/upload/batch',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取所有批次列表
 */
export function getBatches() {
  return request({
    url: '/api/images/batches',
    method: 'get'
  })
}

/**
 * 获取指定批次的影像列表
 */
export function getImagesByBatch(batchId) {
  return request({
    url: `/api/images?batchId=${batchId}`,
    method: 'get'
  })
}

/**
 * 保存影像调整参数
 */
export function saveAdjustment(id, params) {
  return request({
    url: `/api/images/${id}/adjust`,
    method: 'post',
    data: params
  })
}

/**
 * 保存调整后的影像
 */
export function saveAdjusted(id, params) {
  return request({
    url: `/api/images/${id}/save-adjusted`,
    method: 'post',
    data: params
  })
}

/**
 * 更新批次名称
 */
export function updateBatch(batchUuid, data) {
  return request({
    url: `/api/images/batches/${batchUuid}`,
    method: 'put',
    data: data
  })
}

/**
 * 删除批次
 */
export function deleteBatch(batchUuid) {
  return request({
    url: `/api/images/batches/${batchUuid}`,
    method: 'delete'
  })
}
