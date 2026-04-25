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
- `description`: 工具描述（AI 会看到这个描述来决定是否使用工具）

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
