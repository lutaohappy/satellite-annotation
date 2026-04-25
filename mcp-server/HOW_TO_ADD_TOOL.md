# 如何开发新的 MCP 工具

## 1. 创建工具类

实现 `McpToolProvider` 接口：

```java
package com.annotation.mcpserver.tool;

import com.annotation.mcpserver.dto.McpTool;
import com.annotation.mcpserver.tool.McpToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 你的工具描述
 */
@Slf4j
@Component
public class YourTool implements McpToolProvider {

    @Override
    public McpTool.ToolDefinition getDefinition() {
        return McpTool.ToolDefinition.builder()
            .name("your_tool_name")  // 工具名称（唯一）
            .description("工具描述")
            .inputSchema(McpTool.InputSchema.builder()
                .type("object")
                .properties(Map.of(
                    "param1", McpTool.PropertySchema.builder()
                        .type("string")
                        .description("参数 1 描述")
                        .build(),
                    "param2", McpTool.PropertySchema.builder()
                        .type("number")
                        .description("参数 2 描述")
                        .build()
                ))
                .required(java.util.List.of("param1"))  // 必填参数
                .build()
            )
            .build();
    }

    @Override
    public McpTool.ToolResult execute(Map<String, Object> args) {
        // 获取参数
        String param1 = (String) args.get("param1");
        Number param2 = (Number) args.get("param2");

        log.info("Executing tool with param1={}, param2={}", param1, param2);

        // 执行你的逻辑
        Object result = doYourLogic(param1, param2);

        // 返回结果
        return McpTool.ToolResult.builder()
            .isSuccess(true)
            .content(Map.of(
                "result", result,
                "message", "执行成功"
            ))
            .build();
    }

    private Object doYourLogic(String param1, Number param2) {
        // 你的业务逻辑
        return "result";
    }
}
```

## 2. 工具定义说明

### 基本属性
- `name`: 工具名称（唯一标识符）
- `description`: 工具描述（**AI 会看到这个描述来决定是否使用工具**，描述要清晰明确）

### 输入 Schema
支持的类型：
- `string`: 字符串
- `number`: 数字（整数或浮点数）
- `boolean`: 布尔值
- `object`: 对象
- `array`: 数组

### 返回结果
```java
McpTool.ToolResult.builder()
    .isSuccess(true)  // 是否成功
    .content(...)     // 返回内容（任意对象）
    .error("错误信息") // 失败时的错误信息
    .resources(...)   // 可选的资源列表
    .build();
```

## 3. 自动注册

只要类上有 `@Component` 注解，Spring 会自动扫描并注册到 MCP 服务器。

## 4. 配置启用/禁用

在 `application.yml` 中配置：

```yaml
mcp:
  server:
    enabled-tools:
      - your_tool_name
      - echo
```

## 5. 测试工具

### HTTP 请求测试

```bash
# 列出所有工具
curl http://localhost:4000/api/mcp/message -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }'

# 调用工具
curl http://localhost:4000/api/mcp/message -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "your_tool_name",
      "arguments": {
        "param1": "value1",
        "param2": 123
      }
    }
  }'
```

## 6. 示例参考

参考以下示例工具：
- `EchoTool.java` - 回声工具
- `GetCurrentTimeTool.java` - 获取当前时间工具

### 数据库查询工具示例

- `RoadNetworkListTool.java` - 查询路网数据列表
- `ChatSessionListTool.java` - 查询对话会话列表

---

## 核心经验：将 MCP 工具集成到 Ollama AI 助手

### 完整流程

```
用户提问 → 前端获取 MCP 工具列表 → 发送给 Ollama /api/chat
  → Ollama 判断是否需要调用工具
    → 需要：执行 MCP 工具 → 把结果喂回 Ollama → 生成回答
    → 不需要：直接生成回答
```

### 第 1 步：从 MCP 服务器获取工具定义

```javascript
// 调用 MCP tools/list 获取工具列表
const response = await fetch('/api/mcp/message', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    jsonrpc: '2.0', id: 'defs', method: 'tools/list', params: {}
  })
})
const data = await response.json()

// 转换为 Ollama 工具格式
const tools = data.result.tools.map(tool => ({
  type: 'function',
  function: {
    name: tool.name,
    description: tool.description,
    parameters: tool.inputSchema
  }
}))
```

**关键点**：`tool.inputSchema` 的结构与 Ollama 的 `parameters` 格式兼容，都是 JSON Schema。

### 第 2 步：将工具定义发送给 Ollama /api/chat

```javascript
const response = await fetch('/ollama/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    model: 'gemma4:26b',
    messages: [{ role: 'user', content: '帮我查一下有多少路网' }],
    tools: tools,      // 工具定义
    stream: false      // 工具调用时建议用非流式
  })
})
const data = await response.json()

// 检查是否触发工具调用
const content = data.message.content        // 可能为空（完全工具调用）或有文本
const toolCalls = data.message.tool_calls   // 工具调用列表
```

**关键点**：
- Ollama 的 `/api/chat` 端点（不是 `/generate`）支持工具调用
- 工具调用响应中，`content` 可能为空字符串，工具调用在 `tool_calls` 数组中
- 超时时间需要足够长（gemma4 约需 10-30 秒），建议 120 秒

### 第 3 步：执行 MCP 工具调用

```javascript
for (const toolCall of toolCalls) {
  const toolName = toolCall.function.name
  const args = typeof toolCall.function.arguments === 'string'
    ? JSON.parse(toolCall.function.arguments)
    : toolCall.function.arguments || {}

  // 调用 MCP tools/call
  const result = await fetch('/api/mcp/message', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      jsonrpc: '2.0',
      id: `call-${toolName}`,
      method: 'tools/call',
      params: { name: toolName, arguments: args }
    })
  })
  const resultData = await result.json()
  const toolResultText = JSON.stringify(resultData.result.content)

  // 将工具结果添加到消息列表
  messages.push({
    role: 'assistant',
    content: content,
    tool_calls: toolCalls
  })
  messages.push({
    role: 'tool',
    content: toolResultText
  })
}
```

**关键点**：
- 工具结果必须是文本字符串，Ollama 不接收结构化 JSON
- 如果 MCP 返回的是对象，用 `JSON.stringify()` 转为文本
- `role: 'tool'` 的消息内容不能是嵌套的 JSON-RPC 结构，只需要 `result.content` 部分

### 第 4 步：将工具结果喂回 Ollama 获取最终回答

```javascript
// 再次发送请求，注意 tools 参数为空或不传
const finalResponse = await fetch('/ollama/chat', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    model: 'gemma4:26b',
    messages: messages,  // 现在包含 tool 结果
    stream: false
  })
})
const finalData = await finalResponse.json()
const finalAnswer = finalData.message.content  // Ollama 生成的自然语言回答
```

**关键点**：第二次请求**不传 `tools` 参数**（或传空数组），否则 Ollama 可能再次触发工具调用形成死循环。

### 关键经验总结

1. **工具描述要清晰明确**：AI 完全依靠 `description` 来决定是否使用工具。描述应包含"查询什么数据"、"参数含义"、"返回什么信息"。

2. **MCP 返回的内容要扁平**：工具的 `content` 字段应该是可直接给 AI 的数据结构，不要包含 JSON-RPC 包装（id、jsonrpc 等）。

3. **Ollama 使用 `/api/chat` 而非 `/generate`**：`/generate` 是纯文本补全，不支持工具调用；`/api/chat` 支持 tools 参数。

4. **处理超时**：工具调用需要两次 Ollama 请求 + 一次 MCP 调用，总时间可能较长。首次请求（检测工具调用）约 10-30 秒，第二次（生成最终回答）约 5-15 秒。

5. **工具结果转文本**：`role: 'tool'` 的消息内容必须是字符串。复杂数据结构用 `JSON.stringify()` 转为文本。

6. **消息历史管理**：在前后端对话中，不要把 UI 状态文本（如"正在查询..."）混入发送给 Ollama 的消息中。

### 部署注意事项

- MCP 工具类放在 `backend/src/main/java/com/annotation/mcpserver/tool/` 目录下
- 添加 `@Component` 注解即可自动注册
- 在 `application.yml` 的 `enabled-tools` 中启用新工具
- Spring Security 需要放行 `/api/mcp/**` 路径
