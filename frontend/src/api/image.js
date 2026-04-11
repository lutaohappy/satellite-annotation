import request from '@/utils/request'

/**
 * 上传卫星影像
 */
export function uploadImage(formData) {
  return request({
    url: '/api/images/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取影像列表
 */
export function getImages(projectId) {
  return request({
    url: projectId ? `/api/images?projectId=${projectId}` : '/api/images',
    method: 'get'
  })
}

/**
 * 获取影像详情
 */
export function getImage(id) {
  return request({
    url: `/api/images/${id}`,
    method: 'get'
  })
}

/**
 * 删除影像
 */
export function deleteImage(id) {
  return request({
    url: `/api/images/${id}`,
    method: 'delete'
  })
}

/**
 * 获取影像文件 URL
 */
export function getImageFileUrl(id) {
  return `/api/images/${id}/file`
}

/**
 * 获取影像预览图 URL（PNG 格式）
 */
export function getImagePreviewUrl(id) {
  return `/api/images/${id}/preview`
}

/**
 * 保存影像调整（覆盖原文件）
 */
export function saveAdjustment(id, params) {
  return request({
    url: `/api/images/${id}/adjust`,
    method: 'post',
    data: params
  })
}

/**
 * 保存调整后的影像为新文件
 */
export function saveAdjusted(id, params) {
  return request({
    url: `/api/images/${id}/save-adjusted`,
    method: 'post',
    data: params
  })
}

/**
 * 创建透明占位影像（用于仅有矢量标注的项目）
 */
export function createPlaceholder(params) {
  return request({
    url: '/api/images/create-placeholder',
    method: 'post',
    data: params
  })
}
