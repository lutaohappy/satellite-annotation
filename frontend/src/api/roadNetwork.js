import request from '@/utils/request'

/**
 * 下载路网数据（异步任务模式）
 */
export function downloadRoadNetwork(params) {
  return request({
    url: '/api/road-networks/download',
    method: 'post',
    data: params
  })
}

/**
 * 创建下载任务
 */
export function createDownloadTask(data) {
  return request({
    url: '/api/road-network-tasks',
    method: 'post',
    data
  })
}

/**
 * 获取用户任务列表
 */
export function getTaskList() {
  return request({
    url: '/api/road-network-tasks',
    method: 'get'
  })
}

/**
 * 获取任务详情
 */
export function getTaskDetail(id) {
  return request({
    url: `/api/road-network-tasks/${id}`,
    method: 'get'
  })
}

/**
 * 重试失败任务
 */
export function retryTask(id) {
  return request({
    url: `/api/road-network-tasks/${id}/retry`,
    method: 'post'
  })
}

/**
 * 取消任务
 */
export function cancelTask(id) {
  return request({
    url: `/api/road-network-tasks/${id}/cancel`,
    method: 'post'
  })
}

/**
 * 获取路网列表
 */
export function getRoadNetworks() {
  return request({
    url: '/api/road-networks',
    method: 'get'
  })
}

/**
 * 获取路网详情
 */
export function getRoadNetworkDetail(id) {
  return request({
    url: `/api/road-networks/${id}`,
    method: 'get'
  })
}

/**
 * 按区域查询路网
 */
export function getRoadNetworksByRegion(region) {
  return request({
    url: `/api/road-networks/region/${region}`,
    method: 'get'
  })
}

/**
 * 删除路网
 */
export function deleteRoadNetwork(id) {
  return request({
    url: `/api/road-networks/${id}`,
    method: 'delete'
  })
}
