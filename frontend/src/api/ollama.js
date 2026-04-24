import axios from 'axios'

// Ollama API 基础配置
const ollamaAPI = axios.create({
  baseURL: 'http://192.168.1.97:11434/api',
  timeout: 120000  // LLM 响应时间较长，设置 2 分钟超时
})

/**
 * 调用 Ollama generate API
 * @param {string} prompt - 用户输入的提示词
 * @param {string} model - 模型名称（默认 qwen2.5）
 * @param {function} onChunk - 流式响应回调函数
 * @returns {Promise<string>} - 完整响应文本
 */
export async function generateResponse(prompt, model = 'qwen2.5', onChunk = null) {
  try {
    const response = await ollamaAPI.post('/generate', {
      model: model,
      prompt: prompt,
      stream: !!onChunk  // 如果有回调函数，启用流式响应
    }, {
      responseType: onChunk ? 'text' : 'json'
    })

    if (onChunk && typeof response.data === 'string') {
      // 流式响应：解析 NDJSON 格式
      const lines = response.data.split('\n').filter(line => line.trim())
      let fullResponse = ''

      for (const line of lines) {
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

      return fullResponse
    } else {
      // 非流式响应
      return response.data.response || ''
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
    const response = await ollamaAPI.get('/tags')
    return response.data?.models || []
  } catch (error) {
    console.error('[Ollama] 连接测试失败:', error)
    throw error
  }
}

export default ollamaAPI
