const OLLAMA_BASE_URL = '/ollama'

/**
 * 调用 Ollama generate API（使用 Nginx 代理）
 * @param {string} prompt - 用户输入的提示词
 * @param {string} model - 模型名称（默认 gemma4:26b）
 * @param {function} onChunk - 流式响应回调函数
 * @returns {Promise<string>} - 完整响应文本
 */
export async function generateResponse(prompt, model = 'gemma4:26b', onChunk = null) {
  try {
    const response = await fetch(`${OLLAMA_BASE_URL}/generate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model: model,
        prompt: prompt,
        stream: !!onChunk
      })
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    if (onChunk) {
      // 流式响应：解析 NDJSON 格式
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      let fullResponse = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        // 按行分割并解析
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''  // 保留不完整的行

        for (const line of lines) {
          if (!line.trim()) continue
          try {
            const json = JSON.parse(line)
            if (json.response) {
              fullResponse += json.response
              onChunk(json.response, json.done)
            }
          } catch (e) {
            console.warn('[Ollama] 解析响应失败:', line)
          }
        }
      }

      // 处理剩余的 buffer
      if (buffer.trim()) {
        try {
          const json = JSON.parse(buffer)
          if (json.response) {
            fullResponse += json.response
            onChunk(json.response, json.done)
          }
        } catch (e) {
          console.warn('[Ollama] 解析最终 buffer 失败:', buffer)
        }
      }

      return fullResponse
    } else {
      // 非流式响应
      const data = await response.json()
      return data.response || ''
    }
  } catch (error) {
    console.error('[Ollama] API 调用失败:', error)
    throw error
  }
}

/**
 * 测试 Ollama 服务连接
 */
export async function testConnection() {
  try {
    const response = await fetch(`${OLLAMA_BASE_URL}/tags`)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    return data.models || []
  } catch (error) {
    console.error('[Ollama] 连接测试失败:', error)
    throw error
  }
}
