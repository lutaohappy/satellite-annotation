const OLLAMA_BASE_URL = '/ollama'
const MCP_BASE_URL = '/api/mcp'

/**
 * 从 MCP 服务器获取工具定义
 * @returns {Promise<Array>} Ollama 格式的工具列表
 */
async function fetchMCPTools() {
  try {
    const response = await fetch(`${MCP_BASE_URL}/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jsonrpc: '2.0',
        id: 'tool-defs',
        method: 'tools/list',
        params: {}
      })
    })
    if (!response.ok) {
      console.warn('[Ollama] 获取 MCP 工具列表失败:', response.status)
      return []
    }
    const data = await response.json()
    const mcpTools = data.result?.tools || []

    // 转换为 Ollama 工具格式
    return mcpTools.map(tool => ({
      type: 'function',
      function: {
        name: tool.name,
        description: tool.description,
        parameters: tool.inputSchema || { type: 'object', properties: {} }
      }
    }))
  } catch (error) {
    console.warn('[Ollama] 获取 MCP 工具列表失败:', error.message)
    return []
  }
}

/**
 * 调用 MCP 工具
 * @param {string} toolName - 工具名称
 * @param {Object} args - 工具参数
 * @returns {Promise<string>} 工具执行结果（文本）
 */
async function callMCPTool(toolName, args) {
  const response = await fetch(`${MCP_BASE_URL}/message`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: `call-${toolName}`,
      method: 'tools/call',
      params: { name: toolName, arguments: args || {} }
    })
  })
  if (!response.ok) {
    throw new Error(`MCP 工具调用失败: ${response.status}`)
  }
  const data = await response.json()
  const content = data.result?.content
  if (content) {
    return typeof content === 'string' ? content : JSON.stringify(content, null, 2)
  }
  return JSON.stringify(data.result || {}, null, 2)
}

/**
 * 调用 Ollama chat API（支持工具调用）
 * @param {Array} messages - 对话消息列表 [{role, content}]
 * @param {Array} tools - Ollama 格式的工具列表
 * @param {string} model - 模型名称
 * @param {function} onChunk - 流式响应回调函数（仅最终文本响应时触发）
 * @returns {Promise<string>} - 完整响应文本
 */
async function chatWithOllama(messages, tools, model = 'gemma4:26b', onChunk = null) {
  const response = await fetch(`${OLLAMA_BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model,
      messages,
      tools,
      stream: false
    })
  })
  if (!response.ok) {
    throw new Error(`Ollama chat 请求失败: HTTP ${response.status}`)
  }
  const data = await response.json()
  const message = data.message || {}
  const content = message.content || ''
  const toolCalls = message.tool_calls || []

  return { content, toolCalls }
}

/**
 * 带工具调用的聊天主函数
 * 流程：获取工具 → 发送到 Ollama → 如有工具调用则执行 → 把结果喂回 Ollama → 返回最终回答
 *
 * @param {Array} messages - 对话消息列表 [{role, content}]
 * @param {string} model - 模型名称（默认 gemma4:26b）
 * @param {function} onChunk - 流式响应回调函数
 * @param {function} onToolCall - 工具调用进度回调 (toolName) => void
 * @returns {Promise<string>} - 完整响应文本
 */
export async function chatWithTools(messages, model = 'gemma4:26b', onChunk = null, onToolCall = null) {
  // 1. 获取 MCP 工具定义
  const tools = await fetchMCPTools()
  console.log('[Chat] MCP 工具数量:', tools.length)

  // 2. 发送到 Ollama 进行对话
  let response = await chatWithOllama(messages, tools, model, onChunk)
  console.log('[Chat] Ollama 响应:', response)

  // 3. 如果 Ollama 要求调用工具
  if (response.toolCalls && response.toolCalls.length > 0) {
    // 逐个调用工具
    for (const toolCall of response.toolCalls) {
      const fn = toolCall.function
      const toolName = fn?.name
      const args = typeof fn?.arguments === 'string'
        ? JSON.parse(fn.arguments)
        : fn?.arguments || {}

      console.log('[Chat] 调用工具:', toolName, args)
      if (onToolCall) onToolCall(toolName)

      const toolResult = await callMCPTool(toolName, args)
      console.log('[Chat] 工具结果:', toolResult.substring(0, 200))

      // 把工具结果添加到消息列表
      messages.push({ role: 'assistant', content: response.content })
      messages.push({
        role: 'tool',
        content: toolResult
      })

      // 再次发送到 Ollama 获取最终回答
      response = await chatWithOllama(messages, [], model, onChunk)
    }
  }

  // 4. 返回最终文本（支持流式）
  const finalContent = response.content || ''
  if (onChunk) {
    // 流式效果：逐字发送
    const chunkSize = 10
    for (let i = 0; i < finalContent.length; i += chunkSize) {
      onChunk(finalContent.slice(i, i + chunkSize), false)
      await new Promise(r => setTimeout(r, 30))
    }
    onChunk('', true)
  }

  return finalContent
}

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
            // 只要有 response 或 done 标志就调用回调
            if (json.response || json.done) {
              if (json.response) {
                fullResponse += json.response
              }
              onChunk(json.response || '', json.done)
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
          // 只要有 response 或 done 标志就调用回调
          if (json.response || json.done) {
            if (json.response) {
              fullResponse += json.response
            }
            onChunk(json.response || '', json.done)
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
