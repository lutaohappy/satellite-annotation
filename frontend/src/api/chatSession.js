const API_BASE = '/api/chat-sessions'

/**
 * 获取会话列表
 */
export async function getChatSessions(deviceId) {
  const response = await fetch(API_BASE, {
    method: 'GET',
    headers: {
      'X-Device-Id': deviceId
    }
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`)
  }
  const data = await response.json()
  return data.data || []
}

/**
 * 保存会话到服务器
 */
export async function saveChatSession(session, deviceId) {
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Device-Id': deviceId
    },
    body: JSON.stringify(session)
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`)
  }
  const data = await response.json()
  return data.data
}

/**
 * 删除会话
 */
export async function deleteChatSession(sessionId, deviceId) {
  const response = await fetch(`${API_BASE}/${sessionId}`, {
    method: 'DELETE',
    headers: {
      'X-Device-Id': deviceId
    }
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`)
  }
  const data = await response.json()
  return data.success
}

/**
 * 生成设备 ID
 * 使用固定的共享 ID，让所有用户/设备共享同一份会话数据
 *
 * 注意：这意味着所有访问者看到的是同一份会话列表
 * 如果需要按用户隔离，需要添加用户登录系统
 */
export function generateDeviceId() {
  // 使用固定的共享设备 ID
  return 'shared_device'
}
