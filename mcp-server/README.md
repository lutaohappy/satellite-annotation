# MCP Server - Model Context Protocol 服务器

## 简介

这是一个基于 Spring Boot 实现的 MCP（Model Context Protocol）服务器，为卫星影像标注系统提供标准化的工具调用接口。

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                    MCP Client                           │
│              (Claude, AI Assistant, etc.)               │
└─────────────────────┬───────────────────────────────────┘
                      │ HTTP POST /api/mcp/message
                      ▼
┌─────────────────────────────────────────────────────────┐
│                  MCP Controller                         │
│              /api/mcp/message                           │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                MCP Server Service                       │
│  - initialize                                           │
│  - tools/list                                           │
│  - tools/call                                           │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                 McpToolRegistry                         │
│           (工具注册表 - 管理所有工具)                    │
└─────────┬─────────────┬─────────────┬───────────────────┘
          │             │             │
          ▼             ▼             ▼
    ┌─────────┐   ┌─────────┐   ┌─────────
    │  Tool 1 │   │  Tool 2 │   │  Tool N │
    │  (Echo) │   │ (Time)  │   │  (...)  │
    └─────────┘   └─────────   └─────────┘
```

## 目录结构

```
mcp-server/
├── src/main/java/com/annotation/mcpserver/
│   ├── config/
│   │   ├── McpServerProperties.java   # 服务器配置
│   │   └── McpAutoConfig.java         # 自动配置
│   ├── controller/
│   │   └── McpController.java         # HTTP 端点
│   ├── service/
│   │   └── McpServerService.java      # 核心服务
│   ├── registry/
│   │   └── McpToolRegistry.java       # 工具注册表
│   ├── tool/
│   │   ├── McpToolProvider.java       # 工具接口
│   │   └── example/                   # 示例工具
│   │       ├── EchoTool.java
│   │       └── GetCurrentTimeTool.java
│   └── dto/
│       ├── McpMessage.java            # MCP 消息
│       └── McpTool.java               # 工具 DTO
└── HOW_TO_ADD_TOOL.md                 # 开发指南
```

## 支持的 MCP 方法

| 方法 | 说明 | 示例 |
|------|------|------|
| `initialize` | 初始化连接 | 客户端连接时调用 |
| `tools/list` | 获取可用工具列表 | AI 查询可用工具 |
| `tools/call` | 调用指定工具 | AI 执行工具 |

## API 端点

### POST /api/mcp/message

处理所有 MCP 协议请求。

**请求格式:**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/list",
  "params": {}
}
```

**响应格式:**
```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "tools": [...]
  }
}
```

### GET /api/mcp/health

健康检查端点。

## 配置说明

在 `application.yml` 中添加：

```yaml
mcp:
  server:
    name: satellite-annotation-mcp
    version: 1.0.0
    description: 卫星影像标注系统 MCP 服务器
    
    # 启用的工具列表（留空表示启用所有）
    enabled-tools:
      - echo
      - get_current_time
```

## 添加工具

参考 [HOW_TO_ADD_TOOL.md](./HOW_TO_ADD_TOOL.md)

## 测试

```bash
# 健康检查
curl http://localhost:4000/api/mcp/health

# 列出工具
curl http://localhost:4000/api/mcp/message -X POST \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"tools/list","params":{}}'

# 调用回声工具
curl http://localhost:4000/api/mcp/message -X POST \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/call","params":{"name":"echo","arguments":{"message":"Hello"}}}'
```

## 集成到现有项目

1. 将 `mcp-server` 目录复制到项目中
2. 在父 `pom.xml` 中添加模块引用
3. 在启动类上添加 `@ComponentScan` 扫描 MCP 包
4. 配置 `application.yml`

## 版本

- MCP 协议版本：2025-03-26
- 服务器版本：1.0.0
- Java 版本：17+
- Spring Boot：3.2.0
