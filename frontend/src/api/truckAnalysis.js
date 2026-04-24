import request from '@/utils/request'

/**
 * 执行货车通过性分析
 */
export function analyzeTruck(params) {
  console.log('[TruckAnalysis API] 发送分析请求:', params)
  return request({
    url: '/api/truck-analysis/analyze',
    method: 'post',
    data: params
  }).then(response => {
    console.log('[TruckAnalysis API] 收到响应:', response)
    return response
  }).catch(error => {
    console.error('[TruckAnalysis API] 请求失败:', error)
    throw error
  })
}

/**
 * 获取分析历史
 */
export function getAnalysisHistory() {
  console.log('[TruckAnalysis API] 获取分析历史')
  return request({
    url: '/api/truck-analysis/history',
    method: 'get'
  })
}

/**
 * 获取分析详情
 */
export function getAnalysisDetail(id) {
  console.log('[TruckAnalysis API] 获取分析详情:', id)
  return request({
    url: `/api/truck-analysis/${id}`,
    method: 'get'
  })
}

/**
 * 保存分析结果为 GeoJSON
 */
export function saveAnalysisResult(data) {
  console.log('[TruckAnalysis API] 保存分析结果:', data)
  return request({
    url: '/api/truck-analysis/save-geojson',
    method: 'post',
    data
  })
}

/**
 * 加载已保存的 GeoJSON 分析结果
 */
export function loadSavedAnalysis(id) {
  console.log('[TruckAnalysis API] 加载已保存的分析:', id)
  return request({
    url: `/api/truck-analysis/geojson/${id}`,
    method: 'get'
  })
}

/**
 * 保存分析记录
 */
export function saveAnalysisRecord(data) {
  console.log('[TruckAnalysis API] 保存分析记录:', data)
  return request({
    url: '/api/truck-analysis/save-record',
    method: 'post',
    data
  })
}

/**
 * 获取已保存的分析列表
 */
export function getSavedAnalysisList() {
  console.log('[TruckAnalysis API] 获取已保存分析列表')
  return request({
    url: '/api/truck-analysis/saved-list',
    method: 'get'
  })
}

/**
 * 删除已保存的分析
 */
export function deleteSavedAnalysis(id) {
  console.log('[TruckAnalysis API] 删除已保存的分析:', id)
  return request({
    url: `/api/truck-analysis/geojson/${id}`,
    method: 'delete'
  })
}

/**
 * 获取已保存的分析详情
 */
export function getSavedAnalysisDetail(id) {
  console.log('[TruckAnalysis API] 获取已保存的分析详情:', id)
  return request({
    url: `/api/truck-analysis/saved/${id}`,
    method: 'get'
  })
}
