# 前端部署脚本

## 服务器信息
- 主机：192.168.200.77
- 账号：root
- 密码：box@gridknow

## 常见问题 Fix

### Fix: 浏览器缓存导致版本不更新

**问题现象**：部署后浏览器仍显示旧版本号，出现 `ERR_ABORTED 404 (Not Found)` 错误

**原因**：
1. Nginx 配置中 `location /gis/` 使用 `alias /opt/html/`，而部署脚本将文件部署到 `/opt/html/gis/`
2. 浏览器缓存了旧版 `index.html`，引用了旧哈希值的 JS/CSS 文件（如 `index-Bxz3U7-c.js`）

**解决方案**：
```bash
# 正确部署命令 - 同时更新两个目录
sshpass -p 'box@gridknow' scp -r dist/* root@192.168.200.77:/opt/html/
sshpass -p 'box@gridknow' scp -r dist/* root@192.168.200.77:/opt/html/gis/
```

**验证命令**：
```bash
# 检查主目录文件
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "cat /opt/html/index.html | grep 'app-version'"

# 检查 gis 目录文件
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "cat /opt/html/gis/index.html | grep 'app-version'"
```

**浏览器端清除缓存方法**：
1. `Ctrl+Shift+R` (强制刷新)
2. F12 开发者工具 → Network → 勾选 "Disable cache"
3. `Ctrl+Shift+Delete` 清除浏览器缓存

### Fix: MapTools.vue 货车分析缺少菜单按钮

**问题现象**：MapTools.vue 的货车分析 tab 只有"打点选择起点终点"和"清空记录"按钮，缺少"查看历史"和"保存结果"菜单

**原因**：MapTools.vue 的货车分析功能未完整实现历史记录的查看和保存功能

**解决方案**：
1. 添加 `showHistoryDialog`、`showSavedListDialog` 等状态变量
2. 添加"查看历史"按钮，调用 `getAnalysisHistory` API 显示历史分析记录
3. 添加历史对话框和已保存列表对话框
4. 实现 `loadHistoryResult`、`loadSavedResult`、`deleteSaved` 等方法

**修改文件**：
- `frontend/src/components/MapTools.vue` - 添加历史菜单按钮和相关方法

## 前端部署命令

```bash
# 1. 构建前端
cd /Users/taolu/Downloads/test/satellite-annotation/frontend
npm run build

# 2. 上传到服务器（两个目录都要更新，避免缓存问题）
sshpass -p 'box@gridknow' scp -r dist/* root@192.168.200.77:/opt/html/
sshpass -p 'box@gridknow' scp -r dist/* root@192.168.200.77:/opt/html/gis/

# 3. 验证部署
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "cat /opt/html/index.html | grep 'app-version'"
```

## 验证部署

```bash
# 检查服务器文件版本
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "cat /opt/html/gis/index.html | grep 'MapView-'"

# 检查 nginx 返回
curl -s http://192.168.200.77/gis/ | grep 'MapView-'
```

## 一键部署脚本

```bash
#!/bin/bash
# deploy-frontend.sh - 前端一键部署

cd "$(dirname "$0")/frontend"

echo "Building frontend..."
npm run build

echo "Uploading to server (both directories to avoid cache issues)..."
sshpass -p 'box@gridknow' scp -r dist/* root@192.168.200.77:/opt/html/
sshpass -p 'box@gridknow' scp -r dist/* root@192.168.200.77:/opt/html/gis/

echo "Verifying deployment..."
sshpass -p 'box@gridknow' ssh root@192.168.200.77 "cat /opt/html/index.html | grep 'app-version'"

echo "Done!"
echo "Please refresh browser with Ctrl+Shift+R"
```

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v3.0 (20260421-43-路段详情对话框) | 2026-04-21 | 添加路段详情对话框，显示端点坐标、路段名称、属性信息（转弯半径、限高等） |
| v3.0 (20260421-42-货车分析历史菜单) | 2026-04-21 | Git 发布版本：完整货车分析功能，支持查看历史、保存分析记录 |
| v2.9 (20260421-41-转弯点修复) | 2026-04-21 | 修复转弯点解析和显示问题 |
| v2.9 (20260421-40-货车参数恢复) | 2026-04-21 | 加载历史时恢复货车参数 |
| v2.9 (20260420-39-自动保存记录) | 2026-04-20 | 分析完成后自动保存记录到数据库 |
| v2.9 (20260420-38-转弯点入库) | 2026-04-20 | 添加 turnPoints 字段到数据库，支持转弯点持久化 |
| v2.9 (20260420-37-货车分析显示隐藏) | 2026-04-20 | 添加路线显示/隐藏功能，支持重算和删除记录 |
| v2.9 (20260420-36-货车分析坐标修复) | 2026-04-20 | 修复货车分析坐标转换问题（墨卡托→经纬度），起点终点标记改为 S/E 字母 |
| v2.9 (20260420-35-货车分析修复) | 2026-04-20 | 修复货车分析起点/终点显示问题，添加调试日志 |
| v2.9 (20260417-34-货车分析 Tab 重构) | 2026-04-17 | 重构货车分析功能为 Tab 面板，支持记录管理，显示转弯角度和半径 |
| v2.9 (20260417-33-路网刷新修复) | 2026-04-17 | 修复路网工具面板列表刷新问题，修复取消勾选路网不消失问题 |
