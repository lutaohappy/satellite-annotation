# 前端部署更新问题记录

## 问题现象
前端代码修改后，刷新浏览器仍然显示旧版本：
- JS 文件名不变（如 `index-BJHoJZrx.js` 应该是 `index-DblN8tVg.js`）
- 按钮文字、功能不更新
- 出现 404 错误：`GET /gis/assets/index-旧文件名.js 404 (Not Found)`

## 根本原因
Nginx 配置使用两层目录结构：
```
/opt/html/          # 根目录
/opt/html/gis/      # 前端构建目录
```

Nginx 配置：
```nginx
location /gis/ {
    alias /opt/html/;
    index index.html;
}
```

问题：`/opt/html/index.html` 是旧版本，nginx 返回的是这个旧文件，而不是 `/opt/html/gis/index.html`。

## 解决方案

### 方法 1：同时更新两个 index.html（推荐）
```bash
# 构建前端
cd frontend && npm run build

# 上传到服务器
scp -r dist/* root@192.168.200.77:/opt/html/gis/

# 同时复制 index.html 到 /opt/html/
scp dist/index.html root@192.168.200.77:/opt/html/index.html

# 重启 nginx
ssh root@192.168.200.77 "kill -HUP $(pgrep -f 'nginx: master')"
```

### 方法 2：修改 nginx 配置（更彻底）
修改 `/etc/nginx/nginx.conf`：
```nginx
location /gis/ {
    alias /opt/html/gis/;
    index index.html;
    try_files $uri $uri/ /gis/index.html;
}
```

然后只部署到 `/opt/html/gis/` 即可。

### 方法 3：清理旧文件
```bash
ssh root@192.168.200.77 "rm -rf /opt/html/gis/* && rm -f /opt/html/index.html"
scp -r dist/* root@192.168.200.77:/opt/html/gis/
scp dist/index.html root@192.168.200.77:/opt/html/
```

## 验证部署成功
```bash
# 检查服务器文件
ssh root@192.168.200.77 "cat /opt/html/gis/index.html | grep 'index-'"

# 检查 nginx 返回
curl -s http://192.168.200.77/gis/ | grep 'index-'

# 两者应该显示相同的 JS 文件名
```

## 浏览器缓存问题
即使服务器文件正确，浏览器可能缓存旧 HTML。解决方法：

1. **硬刷新**：Cmd+Shift+R (Mac) 或 Ctrl+Shift+F5 (Windows)
2. **无痕模式**：Cmd+Shift+N (Mac) 或 Ctrl+Shift+N (Windows)
3. **清除缓存**：Cmd+Shift+Delete → 勾选"缓存的图片和文件"
4. **开发工具禁用缓存**：F12 → Network → 勾选"Disable cache"

## 部署脚本（推荐保存）
```bash
#!/bin/bash
# deploy.sh - 前端部署脚本

cd "$(dirname "$0")/frontend"

echo "Building frontend..."
npm run build

echo "Uploading to server..."
scp -r dist/* root@192.168.200.77:/opt/html/gis/
scp dist/index.html root@192.168.200.77:/opt/html/index.html

echo "Reloading nginx..."
ssh root@192.168.200.77 "kill -HUP $(pgrep -f 'nginx: master')"

echo "Done!"
echo "Verify: curl -s http://192.168.200.77/gis/ | grep 'index-'"
```

---

# JWT 认证问题修复（2026-04-17）

## 问题现象
点击下载路网数据按钮，立即跳转回登录页面，Console 显示 `hasToken: false`

## 根本原因
1. Nginx 没有转发 `Authorization` header 到后端
2. Spring Security 的 `@AuthenticationPrincipal User` 无法解析自定义 `User` 实体类

## 解决方案

### 1. 修改 Nginx 配置
在 `/etc/nginx/nginx.conf` 的 `location /api/` 块中添加：
```nginx
proxy_set_header Authorization $http_authorization;
```

### 2. 修改后端 JWT 认证
创建 `UserDetailsImpl.java` 包装 `User` 实体，修改 `UserDetailsServiceImpl.java` 返回 `UserDetailsImpl`，修改 `JwtAuthenticationFilter.java` 将 `User` 实体设置到 `SecurityContext`。

## 验证
登录成功后，API 请求应该能正确获取当前用户信息，不再跳转到登录页。

---

# 任务列表功能增强（2026-04-17）

## 新增功能
1. 任务列表显示完整信息：
   - 任务名称、状态标签
   - 区域、创建时间、完成时间
   - 下载进度条（显示百分比）
   - 操作按钮（取消/重试/下载/查看结果）

2. 后台异步下载：
   - 创建任务后立即返回，后台异步执行
   - 前端轮询任务状态（每 2 秒）
   - 进度实时更新（10% → 50% → 80% → 100%）

3. 文件下载：
   -  completed 状态的任务可下载 GeoJSON 文件
   - 下载的文件以任务名称命名

## API 变更
- `GET /api/road-network-tasks/{id}/download` - 下载任务结果文件
