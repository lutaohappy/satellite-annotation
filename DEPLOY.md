# 卫星影像标注系统 - 部署配置文档

## 一、服务器信息

### 1.1 远程服务器
| 项目 | 值 |
|------|-----|
| IP 地址 | `192.168.200.77` |
| SSH 账号 | `root` |
| SSH 密码 | `box@gridknow` |
| **部署目录** | `/var/www/satellite-annotation/` |
| Web 服务器 | Nginx |
| 访问路径 | `http://192.168.200.77/gis/` |

**注意**：部署目录是 `/var/www/satellite-annotation/`，不是 `/opt/html/`！

### 1.2 本地项目结构
```
/Users/taolu/Downloads/test/satellite-annotation/
├── frontend/                    # 前端项目
│   ├── dist/                    # 构建输出目录（部署时复制此目录）
│   └── ...
├── backend/                     # 后端项目
│   └── src/main/java/...        # Java 源代码
└── deploy/                      # 部署脚本
    ├── deploy-frontend.sh       # 前端部署脚本
    └── nginx-cache-fix.conf     # Nginx 缓存配置
```

## 二、部署流程

### 2.1 前端部署（标准流程）

```bash
# 1. 构建前端
cd /Users/taolu/Downloads/test/satellite-annotation/frontend
npm run build

# 2. 部署到服务器（在项目根目录执行）
cd /Users/taolu/Downloads/test/satellite-annotation
bash deploy/deploy-frontend.sh
# 输入密码：box@gridknow

# 3. 重启 Nginx（SSH 到服务器执行）
ssh root@192.168.200.77
# 输入密码：box@gridknow
nginx -s reload
```

### 2.2 一键部署脚本（推荐）

```bash
cd /Users/taolu/Downloads/test/satellite-annotation

# 构建前端
npm run build --prefix frontend

# 上传文件（注意：部署目录是 /var/www/satellite-annotation/）
sshpass -p 'box@gridknow' scp -r frontend/dist/* root@192.168.200.77:/var/www/satellite-annotation/

# 重启 Nginx
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "nginx -s reload"
```

### 2.3 部署步骤记录（从版本 12 到 13）

```bash
# 1. 修改版本号
# 编辑 frontend/src/views/MapView.vue
# BUILD_VERSION = '20260409-13-双地图架构'

# 2. 构建前端
npm run build --prefix frontend

# 3. 上传到服务器（正确目录！）
sshpass -p 'box@gridknow' scp -r frontend/dist/* root@192.168.200.77:/var/www/satellite-annotation/

# 4. 验证文件已上传
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "ls -la /var/www/satellite-annotation/assets/"

# 5. 重启 Nginx
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "nginx -s reload"

# 6. 浏览器强制刷新：Ctrl+Shift+R 或 Cmd+Shift+R
```

## 三、Nginx 配置

### 3.1 配置文件位置
```
/etc/nginx/nginx.conf
# 或
/etc/nginx/conf.d/default.conf
```

### 3.2 关键配置项

```nginx
location /gis/ {
    alias /opt/html/;
    try_files $uri $uri/ /index.html;
    
    # 缓存控制（重要！）
    add_header Cache-Control "no-cache, no-store, must-revalidate";
    add_header Pragma "no-cache";
    add_header Expires "0";
}

location /api/ {
    proxy_pass http://localhost:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

### 3.3 缓存问题修复
如果遇到浏览器缓存旧文件的问题，检查：
1. Nginx 配置中有无 `Cache-Control` 头
2. `index.html` 中的 meta 标签是否有缓存控制
3. 前端构建文件的 hash 是否更新

## 四、常见问题

### 4.1 浏览器缓存问题
**症状**：构建了新版本，但浏览器仍加载旧文件

**解决方案**：
1. 强制刷新：`Ctrl+Shift+R` (Windows) 或 `Cmd+Shift+R` (Mac)
2. 清除浏览器缓存
3. 检查 Nginx 缓存配置
4. 确保构建文件的 hash 已更新（Vite 会自动处理）

### 4.2 SSH 连接失败
```bash
# 检查 SSH 连接
ssh root@192.168.200.77

# 如果密码错误，检查：
# 1. 密码是否正确（见上方服务器信息）
# 2. 服务器是否在线：ping 192.168.200.77
# 3. SSH 服务是否运行
```

### 4.3 Nginx 重启失败
```bash
# 检查 Nginx 配置
nginx -t

# 查看错误日志
tail -f /var/log/nginx/error.log

# 重启 Nginx
systemctl restart nginx
# 或
service nginx restart
# 或
nginx -s reload
```

### 4.4 前端访问 404
1. 检查文件是否上传成功：
   ```bash
   ssh root@192.168.200.77 "ls -la /opt/html/"
   ```
2. 检查 Nginx 配置中的 `alias` 路径
3. 检查 `index.html` 中的资源路径是否正确

## 五、版本管理

### 5.1 版本号格式
```
YYYYMMDD-XX-描述
例：20260409-13-双地图架构
```

### 5.2 版本记录
| 版本 | 日期 | 说明 |
|------|------|------|
| 20260409-01 | 2026-04-09 | 初始版本 |
| 20260409-12 | 2026-04-09 | 双地图架构 |
| 20260409-13 | 2026-04-09 | 移除透明占位影像 |
| 20260410-14 | 2026-04-10 | 矢量图层修复测试（移除 hybrid 渲染模式，使用 zIndex） |
| 20260410-15 | 2026-04-10 | 统一处理流程（移除有无影像的分支逻辑） |
| 20260410-16 | 2026-04-10 | 修复透明占位影像 bug（改为白色不透明影像） |
| 20260410-17 | 2026-04-10 | 新项目创建白色占位影像，修复矢量图层缺失 |
| 20260410-18 | 2026-04-10 | 修复图层渲染顺序（先移除矢量，加载影像后再添加） |
| 20260410-19 | 2026-04-10 | 统一 loadGeoTIFF 逻辑 |
| 20260410-20 | 2026-04-10 | 修复重复添加 vectorLayer 错误 |
| 20260410-21 | 2026-04-10 | 使用 GeoTools 创建真正的 GeoTIFF 占位影像 |
| 20260410-22 | 2026-04-11 | 修复 loadGeoTIFF 先移除再加矢量图层 |
| 20260410-23 | 2026-04-11 | 修复矢量样式恢复，添加 setVisible 重绘 |
| 20260410-24 | 2026-04-11 | 添加操作提示，说明正确工作流程 |
| 20260410-25 | 2026-04-11 | 新建项目流程优化：支持选择影像或绘制项目区域创建 |
| 20260410-26 | 2026-04-11 | 简化新建项目流程：影像列表可直接新建项目，新增创建占位图项目按钮 |
| 20260410-27 | 2026-04-11 | 修复 loadProjects is not defined 错误 |
| 20260410-28 | 2026-04-11 | 修复占位图项目创建：清空前项目数据，绘制完成后移除矩形框 |
| 20260410-21 | 2026-04-10 | 使用 GeoTools 创建真正的 GeoTIFF 占位影像 |
| 20260410-22 | 2026-04-11 | 修复 loadGeoTIFF 先移除再加矢量图层 |
| 20260410-23 | 2026-04-11 | 修复矢量样式恢复，添加 setVisible 重绘 |
| 20260410-24 | 2026-04-11 | 添加操作提示，说明正确工作流程 |
| 20260410-25 | 2026-04-11 | 新建项目流程优化：支持选择影像或绘制项目区域创建 |
| 20260410-26 | 2026-04-11 | 简化新建项目流程：影像列表可直接新建项目，新增创建占位图项目按钮 |
| 20260410-21 | 2026-04-10 | 使用 GeoTools 创建真正的 GeoTIFF 占位影像 |
| 20260410-22 | 2026-04-11 | 修复 loadGeoTIFF 先移除再加矢量图层 |
| 20260410-23 | 2026-04-11 | 修复矢量样式恢复，添加 setVisible 重绘 |
| 20260410-24 | 2026-04-11 | 添加操作提示，说明正确工作流程 |
| 20260410-25 | 2026-04-11 | 新建项目流程优化：支持选择影像或绘制项目区域创建 |
| 20260410-21 | 2026-04-10 | 使用 GeoTools 创建真正的 GeoTIFF 占位影像 |
| 20260410-22 | 2026-04-11 | 修复 loadGeoTIFF 先移除再加矢量图层 |
| 20260410-23 | 2026-04-11 | 修复矢量样式恢复，添加 setVisible 重绘 |
| 20260410-24 | 2026-04-11 | 添加操作提示，说明正确工作流程 |

### 5.3 查看当前部署版本
在浏览器控制台执行：
```javascript
console.log(BUILD_VERSION)
```

## 六、后端服务

### 6.1 启动后端
```bash
cd /Users/taolu/Downloads/test/satellite-annotation/backend
mvn spring-boot:run
```

### 6.2 后端端口
- 默认端口：`8080`
- API 路径：`/api/`

### 6.3 数据库配置
```
# 数据库连接信息（根据实际环境配置）
spring.datasource.url=jdbc:mysql://localhost:3306/satellite_annotation
spring.datasource.username=root
spring.datasource.password=xxx
```

## 七、检查清单

### 部署前检查
- [ ] 前端代码已修改并保存
- [ ] 版本号已更新
- [ ] `npm run build` 执行成功
- [ ] `dist/` 目录已生成新文件

### 部署后检查
- [ ] 文件已上传到服务器（`ls -la /opt/html/`）
- [ ] Nginx 已重启（`nginx -s reload`）
- [ ] 浏览器访问正常
- [ ] 控制台显示正确版本号
- [ ] 功能测试通过

## 八、重要文件清单

| 文件 | 用途 |
|------|------|
| `deploy/deploy-frontend.sh` | 前端部署脚本 |
| `deploy/nginx-cache-fix.conf` | Nginx 缓存配置 |
| `frontend/src/views/MapView.vue` | 主地图视图（核心逻辑） |
| `frontend/dist/` | 构建输出目录 |
| `backend/src/main/java/.../ImageController.java` | 影像控制器 |

## 九、快速命令参考

```bash
# 构建前端
npm run build --prefix frontend

# 部署到服务器
sshpass -p 'box@gridknow' scp -r frontend/dist/* root@192.168.200.77:/opt/html/

# 重启 Nginx
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "nginx -s reload"

# 查看服务器文件
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "ls -la /opt/html/assets/"

# 重启后端
cd backend && mvn spring-boot:run
```
