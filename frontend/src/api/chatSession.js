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
 * 生成设备 ID（使用简单的浏览器指纹）
 */
export function generateDeviceId() {
  const navigatorData = [
    navigator.userAgent,
    navigator.language,
    screen.colorDepth,
    screen.width + 'x' + screen.height,
    new Date().getTimezoneOffset()
  ].join('|')

  // 简单哈希
  let hash = 0
  for (let i = 0; i < navigatorData.length; i++) {
    const char = navigatorData.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash
  }
  return 'device_' + Math.abs(hash).toString(36)
}
