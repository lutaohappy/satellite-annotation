const OLLAMA_BASE_URL = '/ollama'
const MCP_BASE_URL = '/api/mcp'

/**
 * 带超时的 fetch
 */
async function fetchWithTimeout(url, options = {}, timeoutMs = 120000) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(url, { ...options, signal: controller.signal })
    return response
  } finally {
    clearTimeout(timer)
  }
}

/**
 * 从 MCP 服务器获取工具定义（导出供外部使用）
 * @returns {Promise<Array>} [{name, displayName, description}, ...]
 */
export async function fetchMCPToolsList() {
  try {
    const response = await fetchWithTimeout(`${MCP_BASE_URL}/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jsonrpc: '2.0',
        id: 'tool-defs-ui',
        method: 'tools/list',
        params: {}
      })
    }, 10000)
    if (!response.ok) return []
    const data = await response.json()
    const mcpTools = data.result?.tools || []

    return mcpTools.map(tool => ({
      name: tool.name,
      displayName: tool.name.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()),
      description: tool.description
    }))
  } catch (error) {
    console.warn('[Ollama] UI 获取工具列表失败:', error.message)
    return []
  }
}

/**
 * 从 MCP 服务器获取工具定义
 * @returns {Promise<Array>} Ollama 格式的工具列表
 */
async function fetchMCPTools() {
  try {
    console.log('[Ollama] 正在获取 MCP 工具列表...')
    const response = await fetchWithTimeout(`${MCP_BASE_URL}/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jsonrpc: '2.0',
        id: 'tool-defs',
        method: 'tools/list',
        params: {}
      })
    }, 10000)
    if (!response.ok) {
      console.error('[Ollama] MCP 工具列表请求失败:', response.status, response.statusText)
      return []
    }
    const data = await response.json()
    const mcpTools = data.result?.tools || []

    const tools = mcpTools.map(tool => ({
      type: 'function',
      function: {
        name: tool.name,
        description: tool.description,
        parameters: tool.inputSchema || { type: 'object', properties: {} }
      }
    }))
    console.log('[Ollama] 获取到 MCP 工具:', tools.map(t => t.function.name))
    return tools
  } catch (error) {
    console.error('[Ollama] 获取 MCP 工具列表失败:', error.message, error)
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
  console.log('[Ollama] 调用 MCP 工具:', toolName, args)
  const response = await fetchWithTimeout(`${MCP_BASE_URL}/message`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: `call-${toolName}`,
      method: 'tools/call',
      params: { name: toolName, arguments: args || {} }
    })
  }, 15000)
  if (!response.ok) {
    throw new Error(`MCP 工具调用失败: HTTP ${response.status}`)
  }
  const data = await response.json()
  console.log('[Ollama] MCP 工具结果:', JSON.stringify(data.result, null, 2))
  const content = data.result?.content
  if (content) {
    return typeof content === 'string' ? content : JSON.stringify(content, null, 2)
  }
  return JSON.stringify(data.result || {}, null, 2)
}

/**
 * 调用 Ollama chat API（支持工具调用）
 */
async function chatWithOllama(messages, tools, model = 'gemma4:26b') {
  console.log('[Ollama] 发送 chat 请求:', {
    model,
    messageCount: messages.length,
    toolCount: tools?.length || 0,
    messages: JSON.stringify(messages, null, 2)
  })

  const requestBody = {
    model,
    messages,
    stream: false
  }
  if (tools && tools.length > 0) {
    requestBody.tools = tools
  }

  const response = await fetchWithTimeout(`${OLLAMA_BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(requestBody)
  }, 120000)

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(`Ollama chat 请求失败: HTTP ${response.status} ${text.substring(0, 200)}`)
  }
  const data = await response.json()
  const message = data.message || {}
  const content = message.content || ''
  const toolCalls = message.tool_calls || []

  console.log('[Ollama] 收到响应:', {
    content: content ? content.substring(0, 200) : '(empty)',
    toolCallCount: toolCalls.length,
    toolCalls: JSON.stringify(toolCalls, null, 2)
  })

  return { content, toolCalls }
}

/**
 * 带工具调用的聊天主函数
 *
 * 流程：获取工具 → 发送到 Ollama → 如有工具调用则执行 → 把结果喂回 Ollama → 返回最终回答
 *
 * @param {Array} messages - 对话消息列表 [{role, content}]
 * @param {string} model - 模型名称（默认 gemma4:26b）
 * @param {function} onChunk - 最终文本的流式显示回调
 * @param {function} onToolCall - 工具调用进度回调 (toolName) => void
 * @returns {Promise<string>} - 完整响应文本
 */
export async function chatWithTools(messages, model = 'gemma4:26b', onChunk = null, onToolCall = null) {
  console.log('[Chat] ===== 开始工具调用流程 =====')
  console.log('[Chat] 输入消息:', JSON.stringify(messages, null, 2))

  // 1. 获取 MCP 工具定义
  const tools = await fetchMCPTools()
  console.log('[Chat] MCP 工具数量:', tools.length)

  if (tools.length === 0) {
    console.warn('[Chat] 没有可用的 MCP 工具，降级为普通对话')
    return ''
  }

  // 2. 发送到 Ollama 进行对话
  console.log('[Chat] 发送消息到 Ollama，等待模型响应（可能需要较长时间）...')
  let response = await chatWithOllama(messages, tools, model)
  console.log('[Chat] Ollama 返回:', { content: response.content, toolCalls: response.toolCalls.length })

  // 3. 如果 Ollama 要求调用工具
  if (response.toolCalls && response.toolCalls.length > 0) {
    console.log('[Chat] 检测到', response.toolCalls.length, '个工具调用')

    // 逐个调用工具
    for (const toolCall of response.toolCalls) {
      const fn = toolCall.function
      const toolName = fn?.name
      const args = typeof fn?.arguments === 'string'
        ? JSON.parse(fn.arguments)
        : (fn?.arguments || {})

      console.log('[Chat] >>> 执行工具调用:', toolName, JSON.stringify(args))
      if (onToolCall) onToolCall(toolName)

      const toolResult = await callMCPTool(toolName, args)
      console.log('[Chat] <<< 工具结果 (前300字符):', toolResult.substring(0, 300))

      // 添加 assistant 消息（包含 tool_calls）
      messages.push({
        role: 'assistant',
        content: response.content,
        tool_calls: response.toolCalls
      })
      // 添加 tool 结果消息
      messages.push({
        role: 'tool',
        content: toolResult
      })

      console.log('[Chat] 将工具结果发送回 Ollama...')
      // 再次发送到 Ollama 获取最终回答（不带 tools）
      response = await chatWithOllama(messages, [], model)
      console.log('[Chat] Ollama 最终回答:', response.content?.substring(0, 200) || '(empty)')
    }
  } else {
    console.log('[Chat] Ollama 未触发工具调用，直接返回文本')
  }

  // 4. 返回最终文本
  const finalContent = response.content || ''
  console.log('[Chat] ===== 工具调用流程结束，最终回答长度:', finalContent.length, '=====')

  if (onChunk && finalContent) {
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
