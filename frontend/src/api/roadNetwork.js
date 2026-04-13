import request from '@/utils/request'

/**
 * 下载路网数据
 */
export function downloadRoadNetwork(params) {
  return request({
    url: '/api/road-networks/download',
    method: 'post',
    data: params
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
