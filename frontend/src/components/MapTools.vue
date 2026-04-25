<template>
  <div class="map-tools" :class="{ locked: isLocked }" :style="{ left: position.x + 'px', top: position.y + 'px' }">
    <div class="tools-header" @mousedown="startDrag">
      <span class="title">工具</span>
      <div class="header-actions">
        <el-tooltip :content="isLocked ? '解锁' : '锁定'">
          <el-button link :type="isLocked ? 'warning' : 'info'" size="small" @click.stop="toggleLock">
            <el-icon><Lock v-if="isLocked" /><Unlock v-else /></el-icon>
          </el-button>
        </el-tooltip>
        <el-button link type="primary" size="small" @click.stop="toggleExpand">
          {{ expanded ? '收起' : '展开' }}
        </el-button>
        <el-button link type="danger" size="small" @click.stop="close">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <el-tabs v-if="expanded" v-model="activeTab" class="tools-tabs" stretch>
      <!-- 量测工具 Tab -->
      <el-tab-pane label="量测" name="measure">
        <div class="tab-content">
          <el-radio-group v-model="measureMode" @change="toggleMeasure" size="small">
            <el-radio-button value="distance">距离</el-radio-button>
            <el-radio-button value="area">面积</el-radio-button>
            <el-radio-button value="coordinate">坐标</el-radio-button>
          </el-radio-group>

          <div v-if="measureResult" class="measure-result">
            <div class="result-title">{{ resultTitle }}</div>
            <div class="result-value">{{ measureResult }}</div>
          </div>

          <div class="measure-tips">
            <el-alert v-if="measureMode" type="info" :closable="false" show-icon>
              <template #title>
                <div v-if="measureMode === 'coordinate'">点击地图获取坐标</div>
                <div v-else-if="measureMode === 'distance'">画线测量距离，双击结束</div>
                <div v-else>画面测量面积，双击结束</div>
              </template>
            </el-alert>
          </div>
        </div>
      </el-tab-pane>

      <!-- 符号库 Tab -->
      <el-tab-pane label="符号库" name="symbols">
        <div class="tab-content">
          <!-- 分类筛选 -->
          <div class="filter-bar">
            <el-select v-model="selectedCategory" size="small" placeholder="全部分类" clearable @change="filterSymbols">
              <el-option label="全部" value="" />
              <el-option label="军事符号" value="military" />
              <el-option label="地标建筑" value="landmark" />
              <el-option label="交通设施" value="transport" />
              <el-option label="自然资源" value="nature" />
              <el-option label="自定义" value="custom" />
            </el-select>
            <el-input
              v-model="searchKeyword"
              size="small"
              placeholder="搜索符号..."
              clearable
              @input="filterSymbols"
              class="search-input"
            />
          </div>

          <!-- 上传按钮 -->
          <div class="upload-section">
            <el-upload
              :auto-upload="false"
              :on-change="handleFileChange"
              :multiple="true"
              accept=".svg"
              class="upload-btn"
            >
              <el-button type="primary" size="small" :icon="Upload">
                批量上传 SVG
              </el-button>
            </el-upload>
          </div>

          <!-- 符号网格 -->
          <div class="symbol-grid">
            <div
              v-for="symbol in filteredSymbols"
              :key="symbol.id"
              class="symbol-item"
              :class="{ active: selectedSymbol?.id === symbol.id }"
              @click="selectSymbol(symbol)"
            >
              <div class="symbol-preview" v-html="symbol.content"></div>
              <span class="symbol-name">{{ symbol.name }}</span>
            </div>
          </div>

          <!-- 分页信息 -->
          <div class="pagination-info">
            共 {{ filteredSymbols.length }} 个符号
          </div>
        </div>
      </el-tab-pane>

      <!-- 影像管理 Tab -->
      <el-tab-pane label="影像" name="image">
        <div class="tab-content">
          <!-- 创建占位图项目按钮 -->
          <div class="batch-actions" style="margin-bottom: 12px;">
            <el-button size="small" type="primary" @click="loadBatches">
              <el-icon><Refresh /></el-icon> 刷新批次
            </el-button>
            <el-button size="small" type="success" @click="startCreatePlaceholderProject">
              <el-icon><Plus /></el-icon> 创建占位图项目
            </el-button>
          </div>

          <!-- 批次折叠面板 -->
          <el-collapse v-model="activeBatchIds" class="batch-collapse" accordion>
            <el-collapse-item
              v-for="batch in batches"
              :key="batch.batchUuid"
              :title="formatBatchTitle(batch)"
              :name="batch.batchUuid"
            >
              <template #title>
                <div class="batch-title">
                  <span v-if="editingBatchId === batch.batchUuid" class="batch-edit">
                    <el-input
                      v-model="editingBatchName"
                      size="small"
                      placeholder="输入批次名称"
                      @keyup.enter="confirmBatchNameEdit(batch)"
                      @blur="confirmBatchNameEdit(batch)"
                    />
                  </span>
                  <span v-else class="batch-name">
                    {{ batch.name || '未命名批次' }}
                    <el-tag size="small" type="info" style="margin-left: 5px;">{{ batch.fileCount }} 个文件</el-tag>
                  </span>
                  <div class="batch-title-actions">
                    <el-button
                      link
                      type="primary"
                      size="small"
                      @click.stop="startBatchNameEdit(batch)"
                      title="重命名"
                    >
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      size="small"
                      @click.stop="confirmDeleteBatch(batch)"
                      title="删除批次"
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="success"
                      size="small"
                      @click.stop="uploadToBatch(batch)"
                      title="继续上传"
                    >
                      <el-icon><Upload /></el-icon>
                    </el-button>
                  </div>
                </div>
              </template>
              <div class="batch-images">
                <div
                  v-for="img in getImagesForBatch(batch.batchUuid)"
                  :key="img.id"
                  class="image-item"
                  :class="{ active: imageStore.currentImage?.id === img.id }"
                  @click="selectImage(img)"
                >
                  <div class="image-info">
                    <div class="image-name">{{ img.name }}</div>
                    <div class="image-meta">
                      <span>{{ img.width }}x{{ img.height }}</span>
                      <span>{{ formatFileSize(img.fileSize) }}</span>
                    </div>
                  </div>
                  <div class="image-actions">
                    <el-button size="small" type="success" @click.stop="loadImage(img)">加载</el-button>
                    <el-button size="small" type="danger" @click.stop="deleteImage(img.id)">删除</el-button>
                  </div>
                </div>
              </div>
              <div class="batch-actions">
                <el-button size="small" @click.stop="loadAllBatchImages(batch.batchUuid)">全部加载</el-button>
              </div>
            </el-collapse-item>
          </el-collapse>

          <!-- 无批次时显示上传提示 -->
          <div v-if="batches.length === 0 && !loadingImages" class="empty-tip">
            暂无影像批次，请点击上传按钮添加
          </div>

          <!-- 上传按钮 -->
          <div class="upload-section">
            <el-button type="primary" size="small" :icon="Upload" @click="emit('open-upload')">
              上传影像
            </el-button>
          </div>

          <!-- 影像调整（如果有加载的影像） -->
          <div v-if="imageStore.currentImage" class="image-adjustment-section">
            <el-divider>影像调整</el-divider>

            <!-- 当前影像信息 -->
            <div class="current-image-info">
              <el-icon><Picture /></el-icon>
              <span class="image-name">{{ imageStore.currentImage.name }}</span>
              <el-tag size="small" type="info">编辑中</el-tag>
            </div>

            <div class="adjustment-item">
              <span class="label">亮度</span>
              <el-slider v-model="adjustParams.brightness" :min="0" :max="2" :step="0.01" @input="applyAdjustment" />
              <span class="value">{{ adjustParams.brightness.toFixed(2) }}</span>
            </div>

            <div class="adjustment-item">
              <span class="label">对比度</span>
              <el-slider v-model="adjustParams.contrast" :min="0" :max="2" :step="0.01" @input="applyAdjustment" />
              <span class="value">{{ adjustParams.contrast.toFixed(2) }}</span>
            </div>

            <div class="adjustment-item">
              <span class="label">Gamma</span>
              <el-slider v-model="adjustParams.gamma" :min="0.1" :max="3" :step="0.01" @input="applyAdjustment" />
              <span class="value">{{ adjustParams.gamma.toFixed(2) }}</span>
            </div>

            <div class="adjustment-item">
              <span class="label">透明度</span>
              <el-slider v-model="adjustParams.opacity" :min="0" :max="1" :step="0.01" @input="applyAdjustment" />
              <span class="value">{{ adjustParams.opacity.toFixed(2) }}</span>
            </div>

            <div class="adjustment-actions">
              <el-button size="small" @click="resetAdjustment">重置</el-button>
              <el-button size="small" type="primary" @click="showSaveDialog = true">保存</el-button>
              <el-button size="small" type="success" @click="emit('open-swipe')" :disabled="imageStore.images.length < 2">
                对比
              </el-button>
            </div>
          </div>

          <!-- 保存对话框 -->
          <el-dialog v-model="showSaveDialog" title="保存调整" width="400px">
            <el-radio-group v-model="saveMode" style="width: 100%">
              <el-radio value="overwrite" size="large" border style="width: 100%; margin-bottom: 10px;">
                <div style="display: flex; flex-direction: column; gap: 4px;">
                  <span style="font-weight: 500;">覆盖当前影像</span>
                  <span style="font-size: 12px; color: #999;">保存调整参数到当前影像</span>
                </div>
              </el-radio>
              <el-radio value="new" size="large" border style="width: 100%;">
                <div style="display: flex; flex-direction: column; gap: 4px;">
                  <span style="font-weight: 500;">另存为新影像</span>
                  <span style="font-size: 12px; color: #999;">创建新的影像文件</span>
                </div>
              </el-radio>
            </el-radio-group>
            <el-input v-if="saveMode === 'new'" v-model="newImageName" placeholder="请输入新影像名称" style="margin-top: 15px;" />
            <template #footer>
              <el-button @click="showSaveDialog = false">取消</el-button>
              <el-button type="primary" @click="saveAdjustment" :loading="saving">保存</el-button>
            </template>
          </el-dialog>
        </div>
      </el-tab-pane>

      <!-- 路网管理 Tab -->
      <el-tab-pane label="路网" name="road-network">
        <div class="tab-content">
          <div class="road-network-section">
            <div class="road-network-header">
              <el-button type="primary" size="small" @click="openRoadNetworkDownload" style="flex: 1;">
                <el-icon><Plus /></el-icon> 下载路网
              </el-button>
              <el-button type="info" size="small" @click="loadRoadNetworks" style="margin-left: 8px;">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>

            <!-- 已下载路网列表 -->
            <div v-if="roadNetworks.length > 0" class="road-network-list">
              <div v-for="network in roadNetworks" :key="network.id" class="network-item">
                <div class="network-info">
                  <el-checkbox
                    :model-value="network.visible"
                    @update:model-value="(val) => { network.visible = val; toggleRoadNetwork(network, val); }"
                    :label="network.name"
                  />
                  <el-tag size="small" type="info">{{ network.totalRoads }} 条道路</el-tag>
                </div>
                <div class="network-actions">
                  <el-button link type="primary" size="small" @click="zoomToRoadNetwork(network)">
                    <el-icon><Location /></el-icon>
                  </el-button>
                  <el-button link type="danger" size="small" @click="deleteRoadNetwork(network.id)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
            <div v-else class="empty-tip">
              暂无路网，点击"下载路网"添加
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- AI Chat Tab -->
      <el-tab-pane label="AI 助手" name="ai-chat">
        <div class="tab-content ai-chat-content">
          <div class="chat-container">
            <!-- 会话管理栏 -->
            <div class="chat-session-bar">
              <div class="session-selector">
                <el-select v-model="currentSessionId" placeholder="选择会话" size="small" @change="switchSession" style="width: 200px">
                  <el-option
                    v-for="session in chatSessions"
                    :key="session.id"
                    :label="session.title"
                    :value="session.id"
                  >
                    <span>{{ session.title }}</span>
                    <span class="session-time">{{ session.updatedAt }}</span>
                  </el-option>
                </el-select>
              </div>
              <div class="session-actions">
                <el-button size="small" @click="createNewSession" title="新建会话">
                  <el-icon><Plus /></el-icon>
                </el-button>
                <el-button size="small" :disabled="chatSessions.length <= 1" @click="deleteCurrentSession" title="删除当前会话" type="danger">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <!-- 聊天消息列表 -->
            <div class="chat-messages" ref="chatMessagesRef">
              <div
                v-for="(msg, index) in chatMessages"
                :key="index"
                class="chat-message"
                :class="msg.role"
              >
                <div class="message-avatar">
                  <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                  <el-icon v-else><Cpu /></el-icon>
                </div>
                <div class="message-content">
                  <div class="message-text">{{ msg.content }}</div>
                  <div class="message-time">{{ msg.time }}</div>
                </div>
              </div>
              <!-- 加载中显示 -->
              <div v-if="chatLoading" class="chat-message ai">
                <div class="message-avatar">
                  <el-icon><Cpu /></el-icon>
                </div>
                <div class="message-content">
                  <div class="message-text typing">
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                  </div>
                </div>
              </div>
            </div>
            <!-- 输入区域 -->
            <div class="chat-input-area">
              <el-input
                v-model="chatInput"
                type="textarea"
                :rows="3"
                placeholder="输入问题，按 Enter 发送（Shift+Enter 换行）..."
                @keydown.enter.exact="handleChatSend"
                :disabled="chatLoading"
                class="chat-input"
              />
              <div class="chat-input-actions">
                <el-button
                  type="primary"
                  size="small"
                  @click="handleChatSend"
                  :disabled="!chatInput.trim() || chatLoading"
                  :loading="chatLoading"
                >
                  发送
                </el-button>
                <el-button
                  size="small"
                  @click="clearChatHistory"
                  :disabled="chatMessages.length === 0"
                >
                  清空
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 货车分析 Tab -->
      <el-tab-pane label="货车分析" name="truck-analysis">
        <div class="tab-content">
          <div class="truck-analysis-section">
            <!-- 分析 ID 显示 -->
            <div v-if="currentAnalysisId" class="analysis-id-bar">
              <span class="id-label">分析 ID:</span>
              <span class="id-value">{{ currentAnalysisId }}</span>
              <el-tag v-if="currentAnalyzingRecord?.saved" type="success" size="small">已保存</el-tag>
            </div>

            <!-- 记录管理菜单 -->
            <div class="record-menu">
              <el-button type="primary" size="small" @click="createAnalysis">
                <el-icon><Plus /></el-icon> 创建分析
              </el-button>
              <el-button type="success" size="small" @click="showSavedList">
                <el-icon><Folder /></el-icon> 历史记录
              </el-button>
              <el-button type="danger" size="small" @click="clearAnalysis">
                <el-icon><Delete /></el-icon> 清空
              </el-button>
            </div>

            <!-- 编辑操作区（创建分析后显示） -->
            <div v-if="currentAnalysisId" class="edit-section">
              <!-- 操作按钮 -->
              <div class="analysis-actions">
                <el-button type="info" size="small" @click="startSelectPoints" :disabled="selectMode">
                  <el-icon><Location /></el-icon> 打点选择起点终点
                </el-button>
                <el-button type="warning" size="small" @click="saveCurrentAnalysis" :disabled="!currentAnalyzingRecord || !currentAnalyzingRecord.result || currentAnalyzingRecord.saved">
                  <el-icon><Download /></el-icon> {{ currentAnalyzingRecord?.saved ? '已保存' : '保存结果' }}
                </el-button>
              </div>

              <!-- 货车参数 -->
              <el-divider>货车参数</el-divider>
              <el-form label-width="70px" label-position="left" size="small">
                <el-row :gutter="10">
                  <el-col :span="12">
                    <el-form-item label="车长 (m)">
                      <el-input-number v-model="truckParams.length" :min="0" :max="100" :precision="2" :step="0.5" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="车宽 (m)">
                      <el-input-number v-model="truckParams.width" :min="0" :max="10" :precision="2" :step="0.1" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="10">
                  <el-col :span="12">
                    <el-form-item label="车高 (m)">
                      <el-input-number v-model="truckParams.height" :min="0" :max="10" :precision="2" :step="0.1" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="总重 (吨)">
                      <el-input-number v-model="truckParams.weight" :min="0" :max="500" :precision="2" :step="1" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="轴距 (m)">
                  <el-input-number v-model="truckParams.wheelbase" :min="0" :max="50" :precision="2" :step="0.5" style="width: 100%" />
                  <div class="form-tip">最小转弯半径：{{ minTurningRadius.toFixed(1) }}m (公式：R = L / sin(35°))</div>
                </el-form-item>
              </el-form>

              <!-- 状态提示 -->
              <el-alert
                v-if="selectMode"
                title="请在地图上点击选择起点，然后再选择终点"
                type="info"
                :closable="false"
                show-icon
                style="margin: 10px 0"
              />

              <!-- 分析记录列表 -->
              <div v-if="analysisRecords.length > 0" class="analysis-list">
                <div v-for="record in analysisRecords" :key="record.id" class="analysis-record">
                  <div class="record-header">
                    <span class="record-name">记录 #{{ record.id }}</span>
                    <el-tag :type="record.status === 'analyzed' ? 'success' : 'info'" size="small">
                      {{ record.status === 'analyzed' ? '已分析' : '待分析' }}
                    </el-tag>
                  </div>
                  <div class="record-points">
                    <div class="point-item">
                      <el-icon><CirclePlus /></el-icon> 起点：{{ record.startPoint || '未选择' }}
                    </div>
                    <div class="point-item">
                      <el-icon><CirclePlus /></el-icon> 终点：{{ record.endPoint || '未选择' }}
                    </div>
                  </div>
                  <div class="record-actions">
                    <el-button
                      v-if="record.startLat && record.startLon && record.endLat && record.endLon && record.status !== 'analyzed'"
                      type="primary"
                      size="small"
                      @click="analyzeRoute(record)"
                      :loading="record.analyzing"
                    >
                      计算分析
                    </el-button>
                    <el-button
                      v-if="record.status === 'analyzed'"
                      :type="record.visible ? 'warning' : 'success'"
                      size="small"
                      @click="toggleResult(record)"
                    >
                      {{ record.visible ? '隐藏' : '显示' }}
                    </el-button>
                    <el-button
                      v-if="record.status === 'analyzed'"
                      type="primary"
                      size="small"
                      @click="analyzeRoute(record)"
                      :loading="record.analyzing"
                    >
                      重算
                    </el-button>
                    <el-button
                      type="danger"
                      size="small"
                      @click="deleteRecord(record.id)"
                    >
                      删除
                    </el-button>
                  </div>
                </div>
              </div>
              <div v-else class="empty-tip">
                暂无分析记录，请点击"打点选择起点终点"添加
              </div>
            </div>
            <!-- 查看历史对话框 -->
            <el-dialog v-model="showHistoryDialog" title="分析历史" width="700px">
              <div v-if="analysisHistory.length === 0 && !loadingHistory" class="empty-tip">
                暂无分析历史记录
              </div>
              <el-table v-else :data="analysisHistory" style="width: 100%" max-height="400" v-loading="loadingHistory">
                <el-table-column prop="requestName" label="名称" />
                <el-table-column prop="startPoint" label="起点" width="100" />
                <el-table-column prop="endPoint" label="终点" width="100" />
                <el-table-column label="结果" width="80">
                  <template #default="scope">
                    <el-tag :type="scope.row.isPassable ? 'success' : 'danger'" size="small">
                      {{ scope.row.isPassable ? '可通过' : '禁行' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="时间" width="160" />
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="scope">
                    <el-button
                      type="primary"
                      size="small"
                      @click="loadHistoryResult(scope.row)"
                    >
                      加载
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-dialog>

            <!-- 已保存列表对话框 -->
            <el-dialog v-model="showSavedListDialog" title="已保存的分析" width="700px">
              <el-button type="primary" size="small" @click="loadSavedList" style="margin-bottom: 10px">
                <el-icon><Refresh /></el-icon> 刷新列表
              </el-button>
              <div v-if="savedAnalysisList.length === 0 && !loadingSaved" class="empty-tip">
                暂无已保存的分析记录
              </div>
              <el-table v-else :data="savedAnalysisList" style="width: 100%" max-height="400" v-loading="loadingSaved">
                <el-table-column prop="name" label="名称" />
                <el-table-column prop="startPoint" label="起点" width="100" />
                <el-table-column prop="endPoint" label="终点" width="100" />
                <el-table-column prop="createdAt" label="保存时间" width="160" />
                <el-table-column label="操作" width="250" fixed="right">
                  <template #default="scope">
                    <el-button
                      type="primary"
                      size="small"
                      @click="viewSavedDetail(scope.row)"
                    >
                      详情
                    </el-button>
                    <el-button
                      type="success"
                      size="small"
                      @click="loadSavedResult(scope.row)"
                    >
                      加载
                    </el-button>
                    <el-button
                      type="danger"
                      size="small"
                      @click="deleteSaved(scope.row.id)"
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-dialog>

            <!-- 已保存分析详情对话框 -->
            <el-dialog v-model="showSavedDetailDialog" title="分析详情【MapTools 组件-V3 测试版】" width="900px" class="analysis-detail-dialog">
              <div v-if="currentSavedDetail" class="detail-content">
                <!-- 测试标记 -->
                <el-alert type="success" title="【MapTools 组件】这是新的路段详情对话框 - 20260422" :closable="false" show-icon style="margin-bottom: 15px;" />

                <!-- 基本信息 -->
                <div class="detail-section">
                  <h4 class="detail-title">基本信息</h4>
                  <div class="info-grid">
                    <div class="info-item"><span class="label">名称：</span><span>{{ currentSavedDetail.name }}</span></div>
                    <div class="info-item"><span class="label">起点：</span><span>{{ currentSavedDetail.startPoint }}</span></div>
                    <div class="info-item"><span class="label">终点：</span><span>{{ currentSavedDetail.endPoint }}</span></div>
                    <div class="info-item"><span class="label">时间：</span><span>{{ currentSavedDetail.createdAt }}</span></div>
                  </div>
                </div>

                <!-- 货车参数 -->
                <div class="detail-section">
                  <h4 class="detail-title">货车参数</h4>
                  <div class="info-grid">
                    <div class="info-item"><span class="label">车长：</span><span>{{ currentSavedDetail.truckParams?.length }}m</span></div>
                    <div class="info-item"><span class="label">车宽：</span><span>{{ currentSavedDetail.truckParams?.width }}m</span></div>
                    <div class="info-item"><span class="label">车高：</span><span>{{ currentSavedDetail.truckParams?.height }}m</span></div>
                    <div class="info-item"><span class="label">总重：</span><span>{{ currentSavedDetail.truckParams?.weight }}吨</span></div>
                    <div class="info-item"><span class="label">轴距：</span><span>{{ currentSavedDetail.truckParams?.wheelbase }}m</span></div>
                  </div>
                </div>

                <!-- 分析结果 -->
                <div class="detail-section">
                  <el-alert
                    :type="currentSavedDetail.isPassable ? 'success' : 'error'"
                    :title="currentSavedDetail.isPassable ? '可以通过' : '存在禁行路段'"
                    :closable="false"
                    show-icon
                  />
                </div>

                <!-- 路段详情列表 -->
                <div v-if="currentSavedDetail.roadSegments && currentSavedDetail.roadSegments.length > 0" class="detail-section">
                  <h4 class="detail-title">路段详情列表 ({{ currentSavedDetail.roadSegments.length }} 段)</h4>
                  <el-table :data="mergedRoadSegments" size="small" :max-height="500" stripe class="road-segment-table">
                    <el-table-column label="路段名称" width="120">
                      <template #default="scope">
                        <span class="segment-name-cell">{{ scope.row.name || '无名路段' }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="起止坐标" min-width="220">
                      <template #default="scope">
                        <div class="coord-cell">
                          <div class="coord-start">起：{{ scope.row.startLat?.toFixed(6) || '-' }}, {{ scope.row.startLon?.toFixed(6) || '-' }}</div>
                          <div class="coord-end">止：{{ scope.row.endLat?.toFixed(6) || '-' }}, {{ scope.row.endLon?.toFixed(6) || '-' }}</div>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="转弯半径&角度" width="130">
                      <template #default="scope">
                        <div v-if="scope.row.turnPoint && scope.row.turnPoint.turnRadius" class="turn-info">
                          {{ scope.row.turnPoint.turnRadius.toFixed(1) }}m / {{ scope.row.turnPoint.turnAngle?.toFixed(1) || '0' }}°
                        </div>
                        <span v-else class="text-muted">直行</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="通过属性" min-width="120">
                      <template #default="scope">
                        <div v-if="scope.row.violation" class="violation-reason">
                          <el-tag type="danger" size="small">{{ scope.row.violation.reason }}</el-tag>
                        </div>
                        <el-tag v-else type="success" size="small">可通过</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="导航属性" min-width="120">
                      <template #default="scope">
                        <div class="nav-instruction-cell">{{ scope.row.instruction || '-' }}</div>
                      </template>
                    </el-table-column>
                    <el-table-column label="路长" width="90">
                      <template #default="scope">
                        <span class="distance-cell">{{ ((scope.row.distance || 0) / 1000).toFixed(2) }}km</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <!-- 统计摘要 -->
                <div class="detail-section">
                  <h4 class="detail-title">统计摘要</h4>
                  <div class="summary-cards">
                    <el-card shadow="hover" class="summary-card">
                      <div class="card-label">总路段数</div>
                      <div class="card-value">{{ currentSavedDetail.roadSegments?.length || 0 }} 段</div>
                    </el-card>
                    <el-card shadow="hover" class="summary-card">
                      <div class="card-label">转弯点数</div>
                      <div class="card-value">{{ currentSavedDetail.turnPoints?.length || 0 }} 个</div>
                    </el-card>
                    <el-card shadow="hover" class="summary-card">
                      <div class="card-label">禁行点数</div>
                      <div class="card-value" :class="{ 'text-danger': (currentSavedDetail.violations?.length || 0) > 0 }">
                        {{ currentSavedDetail.violations?.length || 0 }} 处
                      </div>
                    </el-card>
                    <el-card shadow="hover" class="summary-card">
                      <div class="card-label">总距离</div>
                      <div class="card-value">{{ (currentSavedDetail.totalDistance || 0).toFixed(2) }} km</div>
                    </el-card>
                    <el-card shadow="hover" class="summary-card">
                      <div class="card-label">预计时间</div>
                      <div class="card-value">{{ Math.round(currentSavedDetail.estimatedTime || 0) }} 分钟</div>
                    </el-card>
                    <el-card shadow="hover" class="summary-card">
                      <div class="card-label">状态</div>
                      <div class="card-value" :class="currentSavedDetail.isPassable ? 'text-success' : 'text-danger'">
                        {{ currentSavedDetail.isPassable ? '可通过' : '存在禁行' }}
                      </div>
                    </el-card>
                  </div>
                </div>
              </div>
              <template #footer>
                <el-button @click="showSavedDetailDialog = false">关闭</el-button>
                <el-button type="primary" @click="reanalyzeFromDetail">用当前参数重算</el-button>
              </template>
            </el-dialog>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Close, Lock, Unlock, Refresh, Picture, Edit, Delete, Plus, Location, CirclePlus, Folder, User, Cpu } from '@element-plus/icons-vue'
import { getLength, getArea } from 'ol/sphere'
import { transform, fromLonLat } from 'ol/proj'
import { useImageStore } from '@/stores/image'
import { getImages, deleteImage as deleteImageApi } from '@/api/image'
import { getBatches, getImagesByBatch, saveAdjustment as saveAdjustmentApi, saveAdjusted as saveAdjustedApi, deleteBatch, updateBatch } from '@/api/batch'
import { getRoadNetworks, deleteRoadNetwork as deleteRoadNetworkApi } from '@/api/roadNetwork'
import { analyzeTruck, getAnalysisHistory, saveAnalysisRecord, getSavedAnalysisList, getSavedAnalysisDetail } from '@/api/truckAnalysis'
import VectorSource from 'ol/source/Vector'
import VectorLayer from 'ol/layer/Vector'
import GeoJSON from 'ol/format/GeoJSON'
import { Style, Stroke, Fill, Circle, Text } from 'ol/style'
import Feature from 'ol/Feature'
import Point from 'ol/geom/Point'

const props = defineProps({
  map: Object,
  vectorSource: Object,
  symbols: Array,
  selectedSymbol: Object
})

const emit = defineEmits([
  'measure-result',
  'symbol-select',
  'symbols-change',
  'open-library',
  'open-upload',
  'open-swipe',
  'image-load',
  'image-adjustment',
  'create-project',
  'create-placeholder-project',
  'open-road-network-download',
  'road-network-delete',
  'truck-analysis-select',
  'truck-analysis-locate',
  'truck-analysis-hide'
])

const imageStore = useImageStore()

const activeTab = ref('measure')
const expanded = ref(true)
const position = ref({ x: 10, y: 80 })
const isLocked = ref(false)

// 路网相关
const roadNetworks = ref([])
const roadNetworkLayers = ref({})

// 货车分析相关
const currentAnalysisId = ref(null)  // 当前分析 UUID
const analysisRecords = ref([])
const selectMode = ref(false)
const selectPointCallback = ref(null)
const currentRecordId = ref(null)
let truckAnalysisPointsLayer = null
const truckParams = ref({
  length: 15,
  width: 2.5,
  height: 3.5,
  weight: 20,
  wheelbase: 8
})
const currentAnalyzingRecord = ref(null)  // 当前正在分析的记录

// 历史分析相关
const showHistoryDialog = ref(false)
const showSavedListDialog = ref(false)
const showSavedDetailDialog = ref(false)
const analysisHistory = ref([])
const savedAnalysisList = ref([])
const currentSavedDetail = ref(null)
const loadingHistory = ref(false)
const loadingSaved = ref(false)

const minTurningRadius = computed(() => {
  return truckParams.value.wheelbase / 0.574
})

const hasAnalysisRecords = computed(() => analysisRecords.value.length > 0)

// AI Chat 相关
const chatSessions = ref([])
const currentSessionId = ref('')
const chatMessages = ref([])
const chatInput = ref('')
const chatLoading = ref(false)
const chatMessagesRef = ref(null)
const chatContextMemory = ref({}) // 存储每个会话的上下文

// 本地存储键名
const CHAT_STORAGE_KEY = 'ai_chat_sessions'

// 合并路段、转弯点、禁行点数据
const mergedRoadSegments = computed(() => {
  if (!currentSavedDetail.value || !currentSavedDetail.value.roadSegments) {
    return []
  }

  const roadSegments = currentSavedDetail.value.roadSegments || []
  const turnPoints = currentSavedDetail.value.turnPoints || []
  const violations = currentSavedDetail.value.violations || []

  return roadSegments.map((segment, index) => {
    // 查找该路段终点附近的转弯点
    let turnPoint = null
    if (segment.endLat && segment.endLon) {
      turnPoint = turnPoints.find(tp => {
        if (!tp || !tp.lat || !tp.lon) return false
        const dist = Math.abs(tp.lat - segment.endLat) + Math.abs(tp.lon - segment.endLon)
        return dist < 0.001  // 约 100 米范围内
      })
    }

    // 查找该路段起点附近的禁行点
    let violation = null
    if (segment.startLat && segment.startLon) {
      violation = violations.find(v => {
        if (!v || !v.lat || !v.lon) return false
        const dist = Math.abs(v.lat - segment.startLat) + Math.abs(v.lon - segment.startLon)
        return dist < 0.001  // 约 100 米范围内
      })
    }

    return {
      ...segment,
      turnPoint,
      violation
    }
  })
})

// 影像相关
const loadingImages = ref(false)
const adjustParams = ref({
  brightness: 1.0,
  contrast: 1.0,
  gamma: 1.0,
  opacity: 1.0
})

// 保存对话框相关
const showSaveDialog = ref(false)
const saveMode = ref('overwrite')
const newImageName = ref('')
const saving = ref(false)

// 批次编辑相关
const editingBatchId = ref(null)
const editingBatchName = ref('')

// 切换锁定状态
const toggleLock = () => {
  isLocked.value = !isLocked.value
  ElMessage.success(isLocked.value ? '已锁定，无法拖动' : '已解锁，可以拖动')
}

// 量测相关
const measureMode = ref(null)
const measureResult = ref('')
const currentSketch = ref(null)
const changeListenerKey = ref(null)

const resultTitle = computed(() => {
  if (measureMode.value === 'distance') return '距离'
  if (measureMode.value === 'area') return '面积'
  if (measureMode.value === 'coordinate') return '坐标'
  return ''
})


const clearMeasure = () => {
  measureResult.value = ''
  measureMode.value = null
  removeSketchListener()
  if (props.vectorSource) {
    const features = props.vectorSource.getFeatures()
    if (features.length > 0) {
      props.vectorSource.removeFeature(features[features.length - 1])
    }
  }
  emit('measure-result', null)
}

const removeSketchListener = () => {
  if (currentSketch.value && changeListenerKey.value) {
    currentSketch.value.getGeometry().un('change', changeListenerKey.value)
  }
  currentSketch.value = null
  changeListenerKey.value = null
}

const toggleMeasure = () => {
  if (!measureMode.value) {
    removeSketchListener()
    emit('measure-result', null)
    measureResult.value = ''
    return
  }

  let drawType = 'Point'
  if (measureMode.value === 'distance') drawType = 'LineString'
  if (measureMode.value === 'area') drawType = 'Polygon'

  emit('measure-result', {
    mode: measureMode.value,
    drawType: drawType,
    callback: handleDrawEvent
  })
}

const handleDrawEvent = (type, event) => {
  if (type === 'start') {
    currentSketch.value = event.feature
    const geom = currentSketch.value.getGeometry()
    changeListenerKey.value = geom.on('change', () => {
      updateMeasureResult(geom)
    })
    updateMeasureResult(geom)
  } else if (type === 'end') {
    const geom = event.feature.getGeometry()
    updateMeasureResult(geom)
    removeSketchListener()
  }
}

const updateMeasureResult = (geom) => {
  if (!geom) return
  const type = geom.getType()
  if (type === 'LineString') {
    const length = getLength(geom)
    measureResult.value = formatDistance(length)
  } else if (type === 'Polygon') {
    const area = getArea(geom)
    measureResult.value = formatArea(area)
  } else if (type === 'Point') {
    const coord = geom.getCoordinates()
    measureResult.value = formatCoordinate(coord)
  }
  emit('measure-result', {
    mode: measureMode.value,
    result: measureResult.value
  })
}

const formatDistance = (meters) => {
  if (!meters || isNaN(meters)) return '0 m'
  if (meters > 1000) {
    return (meters / 1000).toFixed(2) + ' km'
  }
  return meters.toFixed(2) + ' m'
}

const formatArea = (sqMeters) => {
  if (!sqMeters || isNaN(sqMeters)) return '0 m²'
  if (sqMeters > 1000000) {
    return (sqMeters / 1000000).toFixed(2) + ' km²'
  } else if (sqMeters > 10000) {
    return (sqMeters / 10000).toFixed(2) + ' 公顷'
  }
  return sqMeters.toFixed(2) + ' m²'
}

const formatCoordinate = (coord) => {
  if (!coord || coord.length < 2) return '无效坐标'
  const wgs84 = transform(coord, 'EPSG:3857', 'EPSG:4326')
  return `经度：${wgs84[0].toFixed(6)}°, 纬度：${wgs84[1].toFixed(6)}°`
}

// 符号相关
const selectedCategory = ref('')
const searchKeyword = ref('')
const filteredSymbols = ref([])

const filterSymbols = () => {
  let result = [...(props.symbols || [])]
  if (selectedCategory.value) {
    result = result.filter(s => s.category === selectedCategory.value)
  }
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(s => s.name.toLowerCase().includes(keyword))
  }
  filteredSymbols.value = result
}

watch(() => props.symbols, () => {
  filterSymbols()
}, { deep: true })

filterSymbols()

const toggleExpand = () => {
  expanded.value = !expanded.value
}

const close = () => {
  expanded.value = false
}

const selectSymbol = (symbol) => {
  emit('symbol-select', symbol)
}

const handleFileChange = async (file) => {
  const files = Array.isArray(file) ? file : [file]
  for (const f of files) {
    try {
      const content = await readFileAsText(f.raw || f)
      const name = f.name.replace('.svg', '')
      const newSymbol = {
        id: Date.now() + Math.random(),
        name: name,
        category: 'custom',
        content: content,
        fillColor: '#ff0000',
        strokeColor: '#000000',
        size: 48
      }
      const newSymbols = [...(props.symbols || []), newSymbol]
      emit('symbols-change', newSymbols)
      ElMessage.success(`已添加符号：${name}`)
    } catch (error) {
      ElMessage.error(`读取文件失败：${f.name}`)
    }
  }
}

const readFileAsText = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target.result)
    reader.onerror = () => reject(e)
    reader.readAsText(file)
  })
}

// 拖拽功能
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

const startDrag = (e) => {
  // 锁定状态下禁止拖动
  if (isLocked.value) {
    ElMessage.warning('工具面板已锁定，解锁后才能拖动')
    return
  }
  isDragging.value = true
  dragOffset.value = {
    x: e.clientX - position.value.x,
    y: e.clientY - position.value.y
  }
}

const onDrag = (e) => {
  if (!isDragging.value) return
  position.value = {
    x: e.clientX - dragOffset.value.x,
    y: e.clientY - dragOffset.value.y
  }
}

const stopDrag = () => {
  isDragging.value = false
}

watch(isDragging, (val) => {
  if (val) {
    window.addEventListener('mousemove', onDrag)
    window.addEventListener('mouseup', stopDrag)
  } else {
    window.removeEventListener('mousemove', onDrag)
    window.removeEventListener('mouseup', stopDrag)
  }
})

// ==================== 影像相关方法 ====================

// 批次相关状态
const batches = ref([])
const activeBatchIds = ref([])
const batchImages = ref({})

// 加载批次列表
const loadBatches = async () => {
  loadingImages.value = true
  try {
    const res = await getBatches()
    if (res.code === 200) {
      batches.value = res.data || []
      imageStore.setBatches(res.data || [])
      // 加载每个批次的影像
      for (const batch of batches.value) {
        await loadBatchImages(batch.batchUuid)
      }
    }
  } catch (error) {
    ElMessage.error('加载批次列表失败')
  } finally {
    loadingImages.value = false
  }
}

// 加载指定批次的影像
const loadBatchImages = async (batchId) => {
  try {
    const res = await getImagesByBatch(batchId)
    if (res.code === 200) {
      batchImages.value[batchId] = res.data || []
      imageStore.setImagesByBatch(batchId, res.data || [])
    }
  } catch (error) {
    ElMessage.error('加载批次影像失败')
  }
}

// 获取指定批次的影像
const getImagesForBatch = (batchId) => {
  return batchImages.value[batchId] || []
}

// 加载批次内所有影像
const loadAllBatchImages = (batchId) => {
  const images = getImagesForBatch(batchId)
  if (images.length === 0) {
    ElMessage.warning('该批次没有影像')
    return
  }
  // 逐个加载影像
  images.forEach((img, index) => {
    setTimeout(() => {
      emit('image-load', img)
    }, index * 100)
  })
  ElMessage.success(`正在加载 ${images.length} 个影像`)
}

// 格式化批次标题
const formatBatchTitle = (batch) => {
  const date = new Date(batch.createdAt)
  const dateStr = `${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}`
  return `批次 ${dateStr} - ${batch.fileCount} 个文件`
}

// 加载影像列表（保留原有方法用于兼容）
const loadImagesList = async () => {
  loadingImages.value = true
  try {
    const res = await getImages()
    if (res.code === 200) {
      imageStore.setImages(res.data || [])
    }
  } catch (error) {
    ElMessage.error('加载影像列表失败')
  } finally {
    loadingImages.value = false
  }
}

// 选择影像
const selectImage = (img) => {
  console.log('[MapTools selectImage] 选择影像:', img)
  imageStore.setCurrentImage(img)
}

// 加载影像到地图
const loadImage = (img) => {
  console.log('[MapTools loadImage] 加载影像:', img)
  // 先设置当前影像，确保保存时能获取到
  imageStore.setCurrentImage(img)
  emit('image-load', img)
}

// 删除影像
const deleteImage = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该影像吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteImageApi(id)
    imageStore.removeImage(id)
    ElMessage.success('删除成功')
    // 刷新批次列表以更新文件数量
    await loadBatches()
    // 清除批次影像缓存，确保下次获取时是最新数据
    batchImages.value = {}
    // 如果删除的是当前影像，清除当前影像状态
    if (imageStore.currentImage?.id === id) {
      imageStore.setCurrentImage(null)
      resetAdjustment()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 基于影像创建项目
const createProjectWithImage = (img) => {
  const nameInput = ref('')
  const descInput = ref('')
  const crsInput = ref('EPSG:3857')

  ElMessageBox({
    title: '新建项目',
    message: h('div', { style: 'display: flex; flex-direction: column; gap: 12px; min-width: 300px' }, [
      h('div', { style: 'display: flex; flex-direction: column; gap: 4px' }, [
        h('label', { style: 'font-size: 14px; color: #606266' }, '项目名称'),
        h('el-input', {
          modelValue: nameInput.value,
          'onUpdate:modelValue': (val) => { nameInput.value = val },
          placeholder: '请输入项目名称'
        })
      ]),
      h('div', { style: 'display: flex; flex-direction: column; gap: 4px' }, [
        h('label', { style: 'font-size: 14px; color: #606266' }, '项目描述'),
        h('el-input', {
          modelValue: descInput.value,
          'onUpdate:modelValue': (val) => { descInput.value = val },
          type: 'textarea',
          rows: 3,
          placeholder: '请输入项目描述（可选）'
        })
      ]),
      h('div', { style: 'display: flex; flex-direction: column; gap: 4px' }, [
        h('label', { style: 'font-size: 14px; color: #606266' }, '坐标系'),
        h('el-select', {
          modelValue: crsInput.value,
          'onUpdate:modelValue': (val) => { crsInput.value = val },
          placeholder: '请选择坐标系',
          style: 'width: 100%'
        }, [
          h('el-option', { label: 'WGS84 / Pseudo-Mercator (EPSG:3857)', value: 'EPSG:3857' }),
          h('el-option', { label: 'WGS84 (EPSG:4326)', value: 'EPSG:4326' }),
          h('el-option', { label: 'CGCS2000 (EPSG:4490)', value: 'EPSG:4490' })
        ])
      ])
    ]),
    showCancelButton: true,
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    customClass: 'project-create-dialog',
    beforeClose: async (action, instance, done) => {
      if (action === 'confirm') {
        if (!nameInput.value || nameInput.value.trim() === '') {
          ElMessage.warning('请输入项目名称')
          return
        }
        try {
          const token = localStorage.getItem('token')
          const response = await fetch('/api/projects', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
              name: nameInput.value.trim(),
              description: descInput.value?.trim() || '',
              crs: crsInput.value
            })
          })
          const result = await response.json()
          if (result.code === 200) {
            ElMessage.success('项目创建成功，正在加载影像...')
            emit('create-project', {
              ...result.data,
              imageId: img.id
            })
            done()
          } else {
            ElMessage.error(result.message || '创建项目失败')
          }
        } catch (error) {
          ElMessage.error('创建项目失败：' + error.message)
        }
      } else {
        done()
      }
    }
  })
}

// 开始创建占位图项目
const startCreatePlaceholderProject = () => {
  ElMessageBox.prompt('请输入项目名称', '创建占位图项目', {
    confirmButtonText: '下一步',
    cancelButtonText: '取消',
    inputPlaceholder: '项目名称',
    inputPattern: /.+/,
    inputErrorMessage: '请输入项目名称'
  }).then(async ({ value }) => {
    try {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/projects', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          name: value,
          description: '',
          crs: 'EPSG:3857',
          method: 'draw'
        })
      })
      const result = await response.json()
      if (result.code === 200) {
        ElMessage.success('请在地图上绘制矩形范围，双击完成')
        emit('create-placeholder-project', result.data)
      } else {
        ElMessage.error(result.message || '创建项目失败')
      }
    } catch (error) {
      ElMessage.error('创建项目失败：' + error.message)
    }
  }).catch(() => {})
}

// ==================== 批次管理方法 ====================

// 开始编辑批次名称
const startBatchNameEdit = (batch) => {
  editingBatchId.value = batch.batchUuid
  editingBatchName.value = batch.name || ''
}

// 确认编辑批次名称
const confirmBatchNameEdit = async (batch) => {
  if (editingBatchId.value !== batch.batchUuid) return

  try {
    const res = await updateBatch(batch.batchUuid, { name: editingBatchName.value })
    if (res.code === 200) {
      ElMessage.success('批次名称已更新')
      // 更新本地批次数据
      const batchIndex = batches.value.findIndex(b => b.batchUuid === batch.batchUuid)
      if (batchIndex >= 0) {
        batches.value[batchIndex].name = editingBatchName.value
      }
    } else {
      ElMessage.error('更新失败：' + (res.message || '未知错误'))
    }
  } catch (error) {
    ElMessage.error('更新失败：' + (error.message || '网络错误'))
  } finally {
    editingBatchId.value = null
    editingBatchName.value = ''
  }
}

// 确认删除批次
const confirmDeleteBatch = async (batch) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除批次"${batch.name || '未命名'}"吗？这将删除该批次下的所有 ${batch.fileCount} 个影像文件，操作不可恢复！`,
      '警告',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const res = await deleteBatch(batch.batchUuid)
    if (res.code === 200) {
      ElMessage.success('批次已删除')
      // 从本地列表中移除
      batches.value = batches.value.filter(b => b.batchUuid !== batch.batchUuid)
      // 清除该批次的影像缓存
      imageStore.clearBatchImages(batch.batchUuid)
    } else {
      ElMessage.error('删除失败：' + (res.message || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + (error.message || '网络错误'))
    }
  }
}

// 上传到指定批次
const uploadToBatch = (batch) => {
  // 打开通用上传对话框，传递批次信息
  emit('open-upload', { batchUuid: batch.batchUuid, batchName: batch.name })
}

// 应用影像调整（实时预览）
const applyAdjustment = () => {
  emit('image-adjustment', adjustParams.value)
}

// 重置调整
const resetAdjustment = () => {
  adjustParams.value = {
    brightness: 1.0,
    contrast: 1.0,
    gamma: 1.0,
    opacity: 1.0
  }
  applyAdjustment()
}

// 保存调整
const saveAdjustment = async () => {
  if (!imageStore.currentImage) {
    console.error('[saveAdjustment] 没有当前影像')
    return
  }

  console.log('[saveAdjustment] 开始保存')
  console.log('[saveAdjustment] 保存模式:', saveMode.value)
  console.log('[saveAdjustment] 调整参数:', adjustParams.value)
  console.log('[saveAdjustment] 当前影像:', imageStore.currentImage)

  saving.value = true
  try {
    if (saveMode.value === 'overwrite') {
      // 保存调整到当前影像（覆盖原文件）
      console.log('[saveAdjustment] 调用 saveAdjustmentApi')
      const res = await saveAdjustmentApi(imageStore.currentImage.id, {
        brightness: adjustParams.value.brightness,
        contrast: adjustParams.value.contrast,
        gamma: adjustParams.value.gamma
      })
      console.log('[saveAdjustment] 保存响应:', res)
      if (res.code === 200) {
        ElMessage.success('影像已保存')
        showSaveDialog.value = false
        // 重置调整参数
        resetAdjustment()
        // 重新加载影像以显示调整后的效果
        emit('image-load', imageStore.currentImage)
      } else {
        ElMessage.error('保存失败：' + (res.message || '未知错误'))
      }
    } else {
      // 另存为新影像
      const name = newImageName.value || `Adjusted_${imageStore.currentImage.name}`
      console.log('[saveAdjustment] 另存为新影像:', name)
      console.log('[saveAdjustment] 调用 saveAdjustedApi')
      const res = await saveAdjustedApi(
        imageStore.currentImage.id,
        {
          brightness: adjustParams.value.brightness,
          contrast: adjustParams.value.contrast,
          gamma: adjustParams.value.gamma,
          newName: name
        }
      )
      console.log('[saveAdjustment] 保存响应:', res)
      if (res.code === 200) {
        ElMessage.success('新影像已保存')
        showSaveDialog.value = false
        newImageName.value = ''
        // 重置调整参数
        resetAdjustment()
        // 延迟刷新批次列表，确保数据库已提交
        setTimeout(async () => {
          console.log('[saveAdjustment] 开始刷新批次列表')
          await loadBatches()
          console.log('[saveAdjustment] 批次列表刷新完成')
          // 刷新后再加载新影像
          if (res.data && res.data.id) {
            console.log('[saveAdjustment] 加载新影像:', res.data.id)
            emit('image-load', res.data)
          }
        }, 500)
      } else {
        ElMessage.error('保存失败：' + (res.message || '未知错误'))
      }
    }
  } catch (error) {
    console.error('[saveAdjustment] 错误:', error)
    ElMessage.error('保存失败：' + (error.message || '网络错误'))
  } finally {
    saving.value = false
  }
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

// 监听 tab 切换
watch(activeTab, (newTab) => {
  if (newTab === 'image') {
    loadBatches()
  } else if (newTab === 'road-network') {
    loadRoadNetworks()
  }
})

// ==================== 路网管理方法 ====================

// 加载路网列表
const loadRoadNetworks = async () => {
  try {
    const res = await getRoadNetworks()
    if (res.code === 200) {
      roadNetworks.value = (res.data || []).map(n => ({
        ...n,
        visible: false
      }))
    }
  } catch (error) {
    console.error('加载路网列表失败:', error)
  }
}

// 打开路网下载对话框
const openRoadNetworkDownload = () => {
  emit('open-road-network-download')
}

// 路网图层管理

// 切换路网显示
const toggleRoadNetwork = async (network, checked) => {
  console.log('[toggleRoadNetwork] network:', network.name, 'checked:', checked)
  if (checked) {
    // 显示路网
    console.log('[toggleRoadNetwork] 加载路网:', network.id)
    await loadRoadNetworkGeoJson(network.id)
  } else {
    // 隐藏路网 - 直接从地图图层列表中查找并移除
    console.log('[toggleRoadNetwork] 移除路网:', network.id)

    // 先尝试从缓存中移除
    const cachedLayer = roadNetworkLayers.value[network.id]
    if (cachedLayer && props.map) {
      const layers = props.map.getLayers().getArray()
      if (layers.includes(cachedLayer)) {
        props.map.removeLayer(cachedLayer)
        console.log('[toggleRoadNetwork] 从缓存移除图层成功')
        delete roadNetworkLayers.value[network.id]
        return
      }
    }

    // 如果缓存中没有或不在地图上，遍历地图所有图层查找
    if (props.map) {
      const layers = props.map.getLayers().getArray()
      for (const layer of layers) {
        if (layer.get('roadNetworkId') === network.id) {
          props.map.removeLayer(layer)
          console.log('[toggleRoadNetwork] 从地图查找并移除图层成功')
          delete roadNetworkLayers.value[network.id]
          return
        }
      }
    }

    // 都没找到，清除缓存
    delete roadNetworkLayers.value[network.id]
    console.log('[toggleRoadNetwork] 未找到图层，已清除缓存')
  }
}

// 加载路网 GeoJSON
const loadRoadNetworkGeoJson = async (networkId) => {
  if (roadNetworkLayers.value[networkId]) {
    return // 已经加载
  }

  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/road-networks/${networkId}/geojson`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    if (!response.ok) {
      if (response.status === 404) {
        ElMessage.warning(`路网 ${networkId} 暂无 GeoJSON 数据`)
      } else if (response.status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        localStorage.removeItem('token')
        window.location.href = '/gis/login'
      } else {
        ElMessage.error(`加载路网失败：${response.status}`)
      }
      return
    }

    const geojson = await response.json()

    // 创建矢量图层
    const source = new VectorSource({
      features: new GeoJSON().readFeatures(geojson, {
        featureProjection: 'EPSG:3857'
      })
    })

    const layer = new VectorLayer({
      source,
      style: new Style({
        stroke: new Stroke({
          color: '#409eff',
          width: 2
        })
      }),
      zIndex: 10
    })
    // 设置标识，便于后续查找
    layer.set('roadNetworkId', networkId)

    props.map.addLayer(layer)
    roadNetworkLayers.value[networkId] = layer

    // 自动缩放到路网范围
    const extent = source.getExtent()
    if (extent && extent[0] !== Infinity && extent[0] !== -Infinity) {
      console.log('[MapTools] 路网 extent:', extent)
      // 检查 extent 是否有效（不能太大也不能太小）
      const width = extent[2] - extent[0]
      const height = extent[3] - extent[1]
      if (width > 0 && height > 0 && width < 10000000 && height < 10000000) {
        props.map.getView().fit(extent, { duration: 500, padding: [50, 50, 50, 50] })
      } else {
        console.log('[MapTools] 路网 extent 范围异常，跳过自动缩放')
      }
    }
  } catch (error) {
    console.error('加载路网 GeoJSON 失败:', error)
    ElMessage.error('加载路网失败：' + error.message)
  }
}

// 定位到路网
const zoomToRoadNetwork = (network) => {
  if (!network.minLon || !network.maxLon) {
    ElMessage.warning('路网范围信息缺失')
    return
  }
  const extent = fromLonLat([network.minLon, network.minLat], 'EPSG:3857')
    .concat(fromLonLat([network.maxLon, network.maxLat], 'EPSG:3857'))
  props.map.getView().fit(extent, { duration: 500, padding: [50, 50, 50, 50] })
}

// 删除路网
const deleteRoadNetwork = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该路网吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteRoadNetworkApi(id)
    ElMessage.success('删除成功')

    // 移除图层
    const layer = roadNetworkLayers.value[id]
    if (layer && props.map) {
      props.map.removeLayer(layer)
      delete roadNetworkLayers.value[id]
    }

    // 刷新列表
    loadRoadNetworks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + error.message)
    }
  }
}

// ==================== 货车分析方法 ====================

// 创建分析（生成 UUID）
const createAnalysis = () => {
  // 如果已有分析会话，先清空
  if (currentAnalysisId.value) {
    clearAnalysis()
  }

  currentAnalysisId.value = `ANALYSIS-${Date.now()}`

  ElMessage.success('已创建分析会话，请点击"打点选择起点终点"')
}

// 开始选择起点终点
const startSelectPoints = () => {
  if (!currentAnalysisId.value) {
    ElMessage.warning('请先点击"创建分析"')
    return
  }

  if (!props.map) {
    ElMessage.warning('地图未初始化')
    return
  }

  // 防止重复调用
  if (selectMode.value) {
    console.log('[MapTools] startSelectPoints: already in select mode, skip')
    return
  }

  // 检查是否有已完成的记录（有起点和终点但未分析）
  const hasPendingRecord = analysisRecords.value.some(r =>
    r.startLat && r.startLon && r.endLat && r.endLon && r.status !== 'analyzed'
  )

  // 如果没有记录或所有记录都已完成，创建新记录
  if (analysisRecords.value.length === 0 || hasPendingRecord) {
    const newRecord = {
      id: Date.now(),
      analysisId: currentAnalysisId.value,
      startLat: null,
      startLon: null,
      startPoint: '',
      endLat: null,
      endLon: null,
      endPoint: '',
      status: 'pending',
      analyzing: false,
      result: null,
      visible: false,
      saved: false,
      dbId: null  // 数据库记录 ID
    }
    analysisRecords.value.push(newRecord)
    currentRecordId.value = newRecord.id
    currentAnalyzingRecord.value = newRecord
  } else {
    // 使用第一个未完成的记录
    const pendingRecord = analysisRecords.value.find(r =>
      !(r.startLat && r.startLon && r.endLat && r.endLon)
    )
    if (pendingRecord) {
      currentRecordId.value = pendingRecord.id
      currentAnalyzingRecord.value = pendingRecord
    }
  }

  selectMode.value = true

  ElMessage.info('请在地图上点击选择起点')

  // 通知父组件进入选点模式
  emit('truck-analysis-select', (lat, lon, type) => {
    handlePointSelected(lat, lon, type)
  })
}

// 处理选点
const handlePointSelected = (lat, lon, type) => {
  console.log('[MapTools] handlePointSelected called: type=', type, 'lat=', lat, 'lon=', lon)
  console.log('[MapTools] handlePointSelected: currentRecordId=', currentRecordId.value)
  console.log('[MapTools] handlePointSelected: analysisRecords count=', analysisRecords.value.length)

  const record = analysisRecords.value.find(r => r.id === currentRecordId.value)
  console.log('[MapTools] handlePointSelected: found record=', record ? record.id : 'null')

  if (!record) return

  if (type === 'start') {
    record.startLat = lat
    record.startLon = lon
    record.startPoint = `${lat.toFixed(4)}, ${lon.toFixed(4)}`
    console.log('[MapTools] handlePointSelected: start point set, record=', record)
    showTruckAnalysisPoint(lat, lon, 'start')
    ElMessage.success('起点已选择，请选择终点')
  } else if (type === 'end') {
    record.endLat = lat
    record.endLon = lon
    record.endPoint = `${lat.toFixed(4)}, ${lon.toFixed(4)}`
    console.log('[MapTools] handlePointSelected: end point set, record=', record)
    selectMode.value = false
    // 不清空 currentRecordId，保持对当前记录的引用
    showTruckAnalysisPoint(lat, lon, 'end')
    ElMessage.success('终点已选择，请点击"计算分析"')
  }
}

// 在地图上显示起点/终点标记
const showTruckAnalysisPoint = (lat, lon, type) => {
  if (!props.map) return

  if (!truckAnalysisPointsLayer) {
    const source = new VectorSource()
    truckAnalysisPointsLayer = new VectorLayer({
      source,
      zIndex: 999
    })
    props.map.addLayer(truckAnalysisPointsLayer)
  }

  const source = truckAnalysisPointsLayer.getSource()
  const coordinate = fromLonLat([lon, lat])

  const feature = new Feature({
    geometry: new Point(coordinate),
    pointType: type
  })

  const color = type === 'start' ? '#67c23a' : '#f56c6c'
  const labelText = type === 'start' ? 'S' : 'E'

  const style = new Style({
    image: new Circle({
      radius: 6,
      fill: new Fill({ color }),
      stroke: new Stroke({ color: '#fff', width: 1 })
    }),
    text: new Text({
      text: labelText,
      font: 'bold 14px Arial',
      fill: new Fill({ color: '#fff' }),
      stroke: new Stroke({ color: '#000', width: 2 }),
      offsetY: -2
    })
  })

  feature.setStyle(style)
  source.addFeature(feature)
}

// 清除起点终点标记
const clearTruckAnalysisPoints = () => {
  if (truckAnalysisPointsLayer && props.map) {
    props.map.removeLayer(truckAnalysisPointsLayer)
    truckAnalysisPointsLayer = null
  }
}

// 计算分析
const analyzeRoute = async (record) => {
  if (!record.startLat || !record.startLon || !record.endLat || !record.endLon) {
    ElMessage.warning('请选择起点和终点')
    return
  }

  // 防止重复分析
  if (record.analyzing) {
    console.log('[MapTools] analyzeRoute: already analyzing, skip')
    return
  }

  try {
    record.analyzing = true
    // 重算时清除已保存状态，需要重新保存
    if (record.saved) {
      record.saved = false
    }
    currentAnalyzingRecord.value = record

    console.log('[MapTools] analyzeRoute: sending request for record', record.id, 'analysisId:', currentAnalysisId.value)

    const params = {
      requestName: `分析 #${record.id}`,
      startLat: record.startLat,
      startLon: record.startLon,
      endLat: record.endLat,
      endLon: record.endLon,
      truck: {
        length: truckParams.value.length,
        width: truckParams.value.width,
        height: truckParams.value.height,
        weight: truckParams.value.weight,
        axleWeight: truckParams.value.weight / 2,
        wheelbase: truckParams.value.wheelbase
      }
    }

    const result = await analyzeTruck(params)

    // 解包 API 响应：{code: 200, message: 'success', data: {...}}
    const analysisResult = result.data || result

    console.log('[MapTools] analyzeRoute: received response, roadSegments length =', analysisResult.roadSegments?.length)

    record.status = 'analyzed'
    record.result = analysisResult
    record.analyzing = false

    // 更新当前分析记录
    currentAnalyzingRecord.value = record

    ElMessage.success('分析完成，请点击"保存结果"保存')

    // 通知父组件显示结果
    emit('truck-analysis-locate', analysisResult)

  } catch (error) {
    ElMessage.error('分析失败：' + error.message)
    record.analyzing = false
  }
}

// 切换结果显示/隐藏
const toggleResult = (record) => {
  if (record.result) {
    record.visible = !record.visible
    if (record.visible) {
      emit('truck-analysis-locate', record.result)
      ElMessage.success('已在地图上显示路线')
    } else {
      emit('truck-analysis-hide')
      ElMessage.info('已隐藏路线')
    }
  }
}

// 删除记录
const deleteRecord = (id) => {
  const index = analysisRecords.value.findIndex(r => r.id === id)
  if (index !== -1) {
    analysisRecords.value.splice(index, 1)
    ElMessage.success('记录已删除')
  }
}

// 清空所有记录
const clearAnalysis = () => {
  // 清空 UUID 会话
  currentAnalysisId.value = null
  // 清空记录
  analysisRecords.value = []
  currentAnalyzingRecord.value = null
  currentRecordId.value = null
  selectMode.value = false

  // 清除地图上的路线和标记
  clearTruckAnalysisPoints()
  emit('truck-analysis-hide')

  ElMessage.success('已清空，回到初始状态')
}

// 清空当前分析（保留 UUID 会话，只清空记录）
const clearCurrentAnalysis = () => {
  analysisRecords.value = []
  currentAnalyzingRecord.value = null
  currentRecordId.value = null
  selectMode.value = false

  // 清除地图上的路线和标记
  clearTruckAnalysisPoints()
  emit('truck-analysis-hide')
}

// ==================== 历史分析方法 ====================

// 显示历史列表
const showHistory = async () => {
  showHistoryDialog.value = true
  loadingHistory.value = true
  try {
    const result = await getAnalysisHistory()
    analysisHistory.value = result || []
  } catch (error) {
    console.error('[MapTools] 加载历史失败:', error)
    ElMessage.error('加载历史失败：' + error.message)
  } finally {
    loadingHistory.value = false
  }
}

// 显示已保存列表
const showSavedList = async () => {
  showSavedListDialog.value = true
  await loadSavedList()
}

// 加载已保存列表
const loadSavedList = async () => {
  loadingSaved.value = true
  try {
    const result = await getSavedAnalysisList()
    savedAnalysisList.value = (result.data || result) || []
  } catch (error) {
    console.error('[MapTools] 加载列表失败:', error)
    ElMessage.error('加载列表失败：' + error.message)
  } finally {
    loadingSaved.value = false
  }
}

// 手动保存当前分析结果
const saveCurrentAnalysis = async () => {
  if (!currentAnalyzingRecord.value || !currentAnalyzingRecord.value.result) {
    ElMessage.warning('没有可保存的分析结果')
    return
  }

  const record = currentAnalyzingRecord.value

  // 如果已保存或正在保存，跳过
  if (record.saved) {
    ElMessage.info('分析结果已保存，无需重复保存')
    return
  }

  if (record.saving) {
    console.log('[MapTools] saveCurrentAnalysis: already saving, skip')
    ElMessage.info('正在保存中，请稍候...')
    return
  }

  const result = record.result

  try {
    record.saving = true
    const data = {
      id: record.dbId,  // 数据库记录 ID，用于更新
      analysisId: currentAnalysisId.value,  // 关联当前分析 UUID
      name: `记录 #${record.id}: ${record.startPoint} -> ${record.endPoint}`,
      startPoint: record.startPoint,
      endPoint: record.endPoint,
      startLat: record.startLat,
      startLon: record.startLon,
      endLat: record.endLat,
      endLon: record.endLon,
      truckParams: {
        length: truckParams.value.length,
        width: truckParams.value.width,
        height: truckParams.value.height,
        weight: truckParams.value.weight,
        axleWeight: truckParams.value.weight / 2,
        wheelbase: truckParams.value.wheelbase
      },
      routeGeoJson: result.routeGeoJson,
      turnPoints: result.turnPoints,
      violations: result.violations,
      roadSegments: result.roadSegments,  // 路段数据
      totalDistance: result.totalDistance,
      estimatedTime: result.estimatedTime
    }

    console.log('[MapTools] saveCurrentAnalysis: calling save API...', record.dbId ? 'UPDATE' : 'INSERT')
    const savedResult = await saveAnalysisRecord(data)
    // 保存数据库 ID，用于后续更新
    if (savedResult && savedResult.data && savedResult.data.id) {
      record.dbId = savedResult.data.id
    }
    ElMessage.success('保存成功')
    // 标记为已保存
    record.saved = true
  } catch (error) {
    ElMessage.error('保存失败：' + error.message)
  } finally {
    record.saving = false
  }
}

// 加载历史结果
const loadHistoryResult = (record) => {
  // 解析禁行点和转弯点
  let violations = []
  let turnPoints = []

  try {
    if (record.violationPoints) {
      violations = JSON.parse(record.violationPoints)
    }
  } catch (e) {
    console.error('[MapTools] 解析禁行点失败:', e)
  }

  try {
    if (record.turnPoints) {
      turnPoints = JSON.parse(record.turnPoints)
    }
  } catch (e) {
    console.error('[MapTools] 解析转弯点失败:', e)
  }

  const analysisResult = {
    isPassable: record.isPassable,
    routeGeoJson: record.routeGeoJson,
    violations: violations,
    turnPoints: turnPoints,
    totalDistance: null,
    estimatedTime: null
  }

  // 恢复货车参数
  if (record.truckLength) {
    truckParams.value.length = record.truckLength
    truckParams.value.width = record.truckWidth
    truckParams.value.height = record.truckHeight
    truckParams.value.weight = record.truckWeight
    truckParams.value.wheelbase = record.wheelbase
  }

  // 通知父组件显示结果
  emit('truck-analysis-locate', analysisResult)
  showHistoryDialog.value = false
  ElMessage.success('已加载历史分析结果')
}

// 加载已保存结果
const loadSavedResult = async (item) => {
  try {
    // 1. 生成/恢复 UUID（使用后端返回的 analysisId 或创建新 UUID）
    currentAnalysisId.value = item.analysisId || `ANALYSIS-${Date.now()}`

    // 2. 恢复货车参数
    if (item.truckParams) {
      truckParams.value = item.truckParams
    }

    // 3. 构建分析记录对象
    const startPoint = `${item.startLat?.toFixed(4) || '0'}, ${item.startLon?.toFixed(4) || '0'}`
    const endPoint = `${item.endLat?.toFixed(4) || '0'}, ${item.endLon?.toFixed(4) || '0'}`

    const record = {
      id: 1,  // 前端记录 ID
      dbId: item.id,  // 数据库记录 ID
      name: item.name || `记录 #1`,
      startPoint,
      endPoint,
      startLat: item.startLat,
      startLon: item.startLon,
      endLat: item.endLat,
      endLon: item.endLon,
      status: 'analyzed',
      saved: true,
      visible: true,
      analyzing: false,
      saving: false,
      result: {
        isPassable: item.isPassable,
        routeGeoJson: item.routeGeoJson,
        turnPoints: item.turnPoints,
        violations: item.violations,
        roadSegments: item.roadSegments,
        totalDistance: item.totalDistance,
        estimatedTime: item.estimatedTime
      }
    }

    // 4. 设置当前分析记录和记录列表
    currentAnalyzingRecord.value = record
    analysisRecords.value = [record]

    // 5. 恢复分析结果（地图显示）
    const analysisResult = {
      isPassable: item.isPassable,
      routeGeoJson: item.routeGeoJson,
      turnPoints: item.turnPoints,
      violations: item.violations,
      roadSegments: item.roadSegments,
      totalDistance: item.totalDistance,
      estimatedTime: item.estimatedTime
    }

    // 6. 通知父组件显示结果
    emit('truck-analysis-locate', analysisResult)

    showSavedListDialog.value = false
    ElMessage.success('已加载保存的分析结果')
  } catch (error) {
    console.error('[MapTools] 加载结果失败:', error)
    ElMessage.error('加载失败：' + error.message)
  }
}

// 查看已保存详情
const viewSavedDetail = async (item) => {
  try {
    // 从后端获取完整详情
    const result = await getSavedAnalysisDetail(item.id)
    currentSavedDetail.value = result.data || result
    showSavedDetailDialog.value = true
  } catch (error) {
    console.error('[MapTools] 获取详情失败:', error)
    ElMessage.error('获取详情失败：' + error.message)
  }
}

// 从详情对话框重算（使用当前货车参数）
const reanalyzeFromDetail = () => {
  if (!currentSavedDetail.value) {
    ElMessage.warning('没有可重算的数据')
    return
  }

  // 关闭详情对话框
  showSavedDetailDialog.value = false

  // 生成新 UUID
  currentAnalysisId.value = `ANALYSIS-${Date.now()}`

  // 不恢复保存时的货车参数，使用当前设置的参数
  // 构建分析记录
  const startPoint = `${currentSavedDetail.value.startLat?.toFixed(4) || '0'}, ${currentSavedDetail.value.startLon?.toFixed(4) || '0'}`
  const endPoint = `${currentSavedDetail.value.endLat?.toFixed(4) || '0'}, ${currentSavedDetail.value.endLon?.toFixed(4) || '0'}`

  const record = {
    id: 1,
    dbId: currentSavedDetail.value.id,
    name: currentSavedDetail.value.name || '重算记录',
    startPoint,
    endPoint,
    startLat: currentSavedDetail.value.startLat,
    startLon: currentSavedDetail.value.startLon,
    endLat: currentSavedDetail.value.endLat,
    endLon: currentSavedDetail.value.endLon,
    status: 'pending',  // 待分析状态
    saved: false,  // 需要重新保存
    visible: true,
    analyzing: false,
    saving: false,
    result: null
  }

  // 设置当前分析记录
  analysisRecords.value = [record]
  currentAnalyzingRecord.value = record

  ElMessage.success('已加载记录，请使用当前货车参数重新分析')
}

// 删除已保存的结果
const deleteSaved = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这条保存的分析结果吗？', '确认删除', {
      type: 'warning'
    })

    const response = await fetch(`/api/truck-analysis/geojson/${id}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      ElMessage.success('删除成功')
      loadSavedList()  // 刷新列表
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[MapTools] 删除失败:', error)
      ElMessage.error('删除失败：' + error.message)
    }
  }
}

// AI Chat 相关方法
import { generateResponse } from '@/api/ollama'

const handleChatSend = async () => {
  if (!chatInput.value.trim() || chatLoading.value) return

  const userMessage = chatInput.value.trim()
  chatInput.value = ''

  // 添加用户消息
  const now = new Date()
  const userMsg = {
    role: 'user',
    content: userMessage,
    time: now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  chatMessages.value.push(userMsg)

  // 更新当前会话
  updateCurrentSession(userMessage)

  // 滚动到底部
  scrollToBottom()

  try {
    chatLoading.value = true

    // 先添加一个空的 AI 消息占位
    const aiMessageIndex = chatMessages.value.length
    chatMessages.value.push({
      role: 'ai',
      content: '',
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    })
    scrollToBottom()

    // 构建带上下文的提示词（多轮对话支持）
    const contextPrompt = buildContextPrompt(userMessage)

    // 调用 Ollama API
    await generateResponse(
      contextPrompt,
      'gemma4:26b',
      (chunk, done) => {
        console.log('[Chat] chunk:', chunk, 'done:', done)
        if (chunk) {
          // 流式过程中逐步更新内容
          chatMessages.value[aiMessageIndex].content += chunk
        }
        if (done) {
          chatLoading.value = false
          console.log('[Chat] 响应完成，chatLoading=false')
          // 保存 AI 响应到上下文
          saveSessionToMemory()
          scrollToBottom()
        }
      }
    )

    // 如果 generateResponse 返回但 done 从未被调用（非流式情况）
    if (chatLoading.value) {
      chatLoading.value = false
      console.log('[Chat] 响应结束但 done 未触发，手动设置 chatLoading=false')
      saveSessionToMemory()
    }
  } catch (error) {
    chatLoading.value = false
    ElMessage.error('AI 响应失败：' + error.message)
    // 添加错误消息
    chatMessages.value.push({
      role: 'ai',
      content: '抱歉，我遇到了一些问题：' + error.message,
      time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    })
  }
}

const scrollToBottom = () => {
  setTimeout(() => {
    if (chatMessagesRef.value) {
      chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
    }
  }, 100)
}

const clearChatHistory = () => {
  chatMessages.value = []
  chatContextMemory.value[currentSessionId.value] = []
  saveSessionToMemory()
  ElMessage.success('已清空聊天记录')
}

// ==================== 会话管理相关 ====================

// 生成会话 ID
const generateSessionId = () => `chat_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`

// 生成会话标题（根据第一条消息）
const generateSessionTitle = (firstMessage) => {
  if (!firstMessage) return '新会话'
  const content = firstMessage.content || ''
  if (content.length <= 20) return content
  return content.substr(0, 18) + '...'
}

// 格式化时间为 HH:MM
const formatTime = (date) => {
  const d = new Date(date)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 格式化日期为 MM/DD HH:MM
const formatDateTime = (date) => {
  const d = new Date(date)
  const month = d.getMonth() + 1
  const day = d.getDate()
  const time = formatTime(date)
  return `${month}/${day} ${time}`
}

// 创建新会话
const createNewSession = () => {
  const newSession = {
    id: generateSessionId(),
    title: '新会话',
    messages: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }
  chatSessions.value.unshift(newSession)
  currentSessionId.value = newSession.id
  chatMessages.value = []
  chatContextMemory.value[newSession.id] = []
  saveSessionsToLocalStorage()
}

// 切换会话
const switchSession = (sessionId) => {
  const session = chatSessions.value.find(s => s.id === sessionId)
  if (!session) return

  // 保存当前会话到内存
  saveCurrentSessionToMemory()

  // 加载目标会话
  chatMessages.value = session.messages || []
  chatContextMemory.value[sessionId] = session.messages || []

  setTimeout(() => {
    scrollToBottom()
  }, 100)
}

// 删除当前会话
const deleteCurrentSession = () => {
  if (chatSessions.value.length <= 1) {
    ElMessage.warning('至少保留一个会话')
    return
  }

  const index = chatSessions.value.findIndex(s => s.id === currentSessionId.value)
  if (index === -1) return

  chatSessions.value.splice(index, 1)

  // 如果删除的是当前会话，切换到第一个会话
  if (currentSessionId.value === chatSessions.value[index]?.id || index >= chatSessions.value.length) {
    const newSession = chatSessions.value[0]
    currentSessionId.value = newSession.id
    chatMessages.value = newSession.messages || []
  }

  delete chatContextMemory.value[currentSessionId.value]
  saveSessionsToLocalStorage()
  ElMessage.success('已删除会话')
}

// 更新当前会话的标题和时间
const updateCurrentSession = (firstMessage) => {
  const session = chatSessions.value.find(s => s.id === currentSessionId.value)
  if (!session) return

  // 如果是第一条消息，生成标题
  if (chatMessages.value.length === 2) { // 2 是因为已经添加了 user + ai 占位
    session.title = generateSessionTitle({ content: firstMessage })
  }

  session.updatedAt = new Date().toISOString()
  saveSessionsToLocalStorage()
}

// 保存当前会话到内存
const saveCurrentSessionToMemory = () => {
  const session = chatSessions.value.find(s => s.id === currentSessionId.value)
  if (!session) return

  session.messages = [...chatMessages.value]
  session.updatedAt = new Date().toISOString()
}

// 保存会话到本地存储
const saveSessionToMemory = () => {
  saveCurrentSessionToMemory()
  saveSessionsToLocalStorage()
}

// 保存会话列表到 localStorage
const saveSessionsToLocalStorage = () => {
  try {
    localStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(chatSessions.value))
  } catch (e) {
    console.warn('[Chat] 保存会话到 localStorage 失败:', e)
  }
}

// 从 localStorage 加载会话列表
const loadSessionsFromLocalStorage = () => {
  try {
    const saved = localStorage.getItem(CHAT_STORAGE_KEY)
    if (saved) {
      chatSessions.value = JSON.parse(saved)
      // 确保每个会话都有 id
      if (chatSessions.value.length > 0) {
        currentSessionId.value = chatSessions.value[0].id
        chatMessages.value = chatSessions.value[0].messages || []
        chatContextMemory.value[currentSessionId.value] = chatMessages.value
        return
      }
    }
    // 如果没有 saved 数据，创建默认会话
    createNewSession()
  } catch (e) {
    console.warn('[Chat] 从 localStorage 加载会话失败:', e)
    createNewSession()
  }
}

// 构建带上下文的提示词（多轮对话支持）
const buildContextPrompt = (userMessage) => {
  const MAX_CONTEXT_MESSAGES = 10 // 最多保留 10 条历史消息作为上下文

  const context = chatContextMemory.value[currentSessionId.value] || []
  const recentMessages = context.slice(-MAX_CONTEXT_MESSAGES)

  if (recentMessages.length === 0) {
    return userMessage
  }

  // 构建上下文对话历史
  let contextText = '以下是之前的对话历史：\n\n'
  for (const msg of recentMessages) {
    const role = msg.role === 'user' ? '用户' : '助手'
    contextText += `${role}: ${msg.content}\n`
  }
  contextText += `\n以上是之前的对话。现在请回答用户的新问题：\n\n用户：${userMessage}`

  return contextText
}

// 暴露方法
defineExpose({
  handleDrawEvent,
  loadBatches,
  loadRoadNetworks
})

// 初始化 AI Chat 会话（在组件挂载时）
onMounted(() => {
  loadSessionsFromLocalStorage()
})
</script>

<style scoped>
.map-tools {
  position: fixed;
  z-index: 2000;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  min-width: 320px;
  overflow: hidden;
  transition: all 0.3s;
}

.map-tools.locked {
  opacity: 0.9;
}

.map-tools.locked .tools-header {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  cursor: default;
}

.tools-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  cursor: move;
  user-select: none;
}

.tools-header .title {
  font-weight: bold;
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.header-actions .el-button {
  color: white;
}

.tools-tabs {
  padding: 0;
}

.tools-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  background: #f5f7fa;
}

.tools-tabs :deep(.el-tabs__content) {
  padding: 12px;
  max-height: 450px;
  overflow-y: auto;
}

.tab-content {
  padding: 0;
}

/* 量测工具样式 */
.measure-result {
  margin-top: 15px;
  text-align: center;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}

.result-title {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.result-value {
  font-size: 22px;
  font-weight: bold;
  color: #409eff;
}

.measure-tips {
  margin-top: 10px;
}

:deep(.el-radio-group) {
  width: 100%;
}

:deep(.el-radio-button) {
  flex: 1;
}

:deep(.el-radio-button__inner) {
  width: 100%;
}

/* 符号库样式 */
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.search-input {
  flex: 1;
}

.upload-section {
  margin-bottom: 12px;
}

.symbol-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(70px, 1fr));
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  padding: 4px;
}

.symbol-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;
}

.symbol-item:hover {
  background: #f0f9ff;
  border-color: #409eff;
}

.symbol-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.symbol-preview {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.symbol-preview :deep(svg) {
  max-width: 100%;
  max-height: 100%;
}

.symbol-name {
  font-size: 10px;
  color: #666;
  margin-top: 4px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.pagination-info {
  text-align: center;
  font-size: 12px;
  color: #999;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid #eee;
}

/* 影像管理样式 */
.image-list {
  max-height: 200px;
  overflow-y: auto;
  margin-bottom: 12px;
}

.image-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 8px;
  background: #f5f7fa;
}

.image-item:hover {
  background: #ecf5ff;
  border-color: #409eff;
}

.image-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.image-info {
  flex: 1;
  min-width: 0;
}

.image-name {
  font-size: 14px;
  color: #333;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.image-meta {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
  display: flex;
  gap: 10px;
}

.image-actions {
  display: flex;
  gap: 6px;
  margin-left: 10px;
}

.empty-tip {
  text-align: center;
  color: #999;
  padding: 20px;
  font-size: 14px;
}

.image-adjustment-section {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #eee;
}

.adjustment-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;
}

.adjustment-item .label {
  width: 50px;
  font-size: 14px;
  color: #666;
}

.adjustment-item .value {
  width: 50px;
  text-align: right;
  font-size: 14px;
  color: #409eff;
}

.adjustment-item :deep(.el-slider) {
  flex: 1;
}

.adjustment-item :deep(.el-slider__runway) {
  height: 6px;
}

.adjustment-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 15px;
}

/* 批次折叠面板样式 */
.batch-collapse {
  margin-bottom: 12px;
}

.batch-collapse :deep(.el-collapse-item__header) {
  font-weight: 500;
  font-size: 14px;
  background: #f5f7fa;
  padding: 10px 15px;
}

.batch-collapse :deep(.el-collapse-item__content) {
  padding: 10px 15px;
}

.batch-images {
  margin-bottom: 10px;
}

.batch-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #eee;
}

/* 批次标题样式 */
.batch-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.batch-title-actions {
  display: flex;
  gap: 4px;
  align-items: center;
}

.batch-title-actions .el-button {
  padding: 4px;
  min-width: auto;
}

.batch-edit {
  flex: 1;
}

.batch-edit :deep(.el-input) {
  max-width: 200px;
}

/* 路网管理样式 */
.road-network-header {
  display: flex;
  gap: 8px;
  margin-bottom: 15px;
}

.road-network-list {
  max-height: 300px;
  overflow-y: auto;
}

.network-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-radius: 6px;
  background: #f5f7fa;
  margin-bottom: 8px;
}

.network-item:hover {
  background: #ecf5ff;
}

.network-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.network-actions {
  display: flex;
  gap: 4px;
}

/* 货车分析详情对话框样式 */
.truck-analysis-section {
  .analysis-id-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px;
    background: #f5f7fa;
    border-radius: 4px;
    margin-bottom: 15px;

    .id-label {
      font-size: 13px;
      color: #909399;
      font-weight: 500;
    }

    .id-value {
      font-size: 13px;
      font-weight: bold;
      color: #606266;
      font-family: monospace;
    }
  }

  .record-menu {
    display: flex;
    gap: 8px;
    margin-bottom: 15px;
    padding-bottom: 15px;
    border-bottom: 1px solid #eee;
  }

  .edit-section {
    .analysis-actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      margin-bottom: 15px;
    }
  }

  .analysis-list {

    &:last-child {
      border-bottom: none;
    }
  }

  .detail-title {
    margin: 0 0 10px;
    font-size: 14px;
    color: #606266;
    font-weight: 500;
  }

  .info-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 10px;
  }

  .info-item {
    display: flex;
    gap: 8px;
    font-size: 14px;

    .label {
      color: #606266;
      font-weight: 500;
    }

    .value {
      color: #333;
    }
  }

  .sharp-turn {
    color: #f56c6c;
    font-weight: 600;
  }

  .segment-properties {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 12px;

    .restriction {
      color: #f56c6c;
      font-weight: 500;
    }
  }

  /* 路段详情表格样式 */
  .road-segment-table {
    font-size: 13px;
  }

  .segment-name-cell {
    font-weight: 500;
    color: #333;
  }

  .coord-cell {
    display: flex;
    flex-direction: column;
    gap: 2px;
    font-size: 11px;

    .coord-start {
      color: #67c23a;
    }

    .coord-end {
      color: #409eff;
    }
  }

  .turn-info {
    font-size: 12px;
    color: #606266;
  }

  .violation-reason {
    font-size: 12px;
  }

  .nav-instruction-cell {
    font-size: 12px;
    color: #606266;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 200px;
  }

  .distance-cell {
    font-size: 12px;
    color: #409eff;
    font-weight: 500;
  }

  .text-muted {
    color: #c0c4cc;
  }

  .text-danger {
    color: #f56c6c;
  }

  .text-success {
    color: #67c23a;
  }

  /* 统计卡片样式 */
  .summary-cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 12px;
  }

  .summary-card {
    text-align: center;
    padding: 15px 10px;

    .card-label {
      font-size: 12px;
      color: #909399;
      margin-bottom: 8px;
    }

    .card-value {
      font-size: 20px;
      font-weight: 600;
      color: #409eff;
    }
  }
}

/* AI Chat 样式 */
.ai-chat-content {
  padding: 0;
  height: 500px;
  display: flex;
  flex-direction: column;
}

.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* 会话管理栏 */
.chat-session-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.session-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-selector .el-select .el-input__inner {
  font-size: 13px;
}

.session-time {
  margin-left: 8px;
  font-size: 11px;
  color: #909399;
}

.session-actions {
  display: flex;
  gap: 4px;
}

.session-actions .el-button {
  padding: 6px 10px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  background: #f5f7fa;
}

.chat-message {
  display: flex;
  align-items: flex-start;
  margin-bottom: 15px;
  gap: 10px;
}

.chat-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.chat-message.user .message-avatar {
  background: #409eff;
  color: white;
}

.chat-message.ai .message-avatar {
  background: #67c23a;
  color: white;
}

.message-content {
  max-width: 75%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-message.user .message-content {
  align-items: flex-end;
}

.message-text {
  padding: 10px 14px;
  border-radius: 12px;
  background: white;
  color: #303133;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
}

.chat-message.user .message-text {
  background: #409eff;
  color: white;
}

.message-time {
  font-size: 11px;
  color: #909399;
  padding: 0 4px;
}

/* 打字动画 */
.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
}

.typing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #909399;
  animation: typing 1.4s infinite;
}

.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.chat-input-area {
  border-top: 1px solid #e4e7ed;
  padding: 10px;
  background: white;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chat-input {
  width: 100%;
}

.chat-input :deep(.el-textarea__inner) {
  resize: none;
  font-size: 14px;
}

.chat-input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
