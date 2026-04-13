import request from '@/utils/request'

/**
 * 执行货车通过性分析
 */
export function analyzeTruck(params) {
  return request({
    url: '/api/truck-analysis/analyze',
    method: 'post',
    data: params
  })
}

/**
 * 获取分析历史
 */
export function getAnalysisHistory() {
  return request({
    url: '/api/truck-analysis/history',
    method: 'get'
  })
}

/**
 * 获取分析详情
 */
export function getAnalysisDetail(id) {
  return request({
    url: `/api/truck-analysis/${id}`,
    method: 'get'
  })
}
