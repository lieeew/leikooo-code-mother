<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
      </div>
      <div class="header-right">
        <a-button type="default" @click="showAppDetail">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>
        <a-button
          type="primary"
          ghost
          @click="downloadCode"
          :loading="downloading"
          :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>
        <a-button type="primary" @click="deployApp" :loading="deploying">
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署
        </a-button>
        <a-button
          v-if="isOwner && versions[versions.length - 1]?.status === AppVersionStatusEnum.NEED_FIX"
          type="primary"
          danger
          @click="fixError"
          :loading="isFixing || isSubAgentRunning"
          :disabled="isSubAgentRunning"
        >
          <template #icon>
            <ToolOutlined />
          </template>
          {{ isSubAgentRunning ? '修复中...' : '修复错误' }}
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="message in messages" :key="message.id" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="message.content" />
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>AI 正在思考...</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- SubAgent 可折叠修复进度面板 -->
        <div v-if="isSubAgentRunning || subAgentResult" class="sub-agent-panel">
          <div class="sub-agent-header" @click="toggleSubAgentPanel">
            <div class="sub-agent-title">
              <ToolOutlined />
              <span>自动修复进度</span>
              <a-tag :color="getSubAgentStatusColor()" size="small">
                {{ getSubAgentStatusText() }}
              </a-tag>
              <span v-if="subAgentPhase !== 'idle'" class="sub-agent-phase-info">
                {{ subAgentPhase === 'fixing' ? '🔧 AI 修复中' : '🏗️ 构建中' }} 
                (第 {{ subAgentAttempt }} 次)
              </span>
            </div>
            <div class="sub-agent-actions">
              <a-button 
                v-if="isSubAgentRunning" 
                type="text" 
                danger 
                size="small"
                @click.stop="cancelSubAgent"
              >
                取消
              </a-button>
              <DownOutlined 
                :class="{ 'arrow-rotated': !subAgentExpanded }" 
                class="collapse-arrow"
              />
            </div>
          </div>
          <div v-show="subAgentExpanded" class="sub-agent-content">
            <div class="sub-agent-stream">
              <MarkdownRenderer v-if="subAgentContent" :content="subAgentContent" />
              <div v-else class="sub-agent-loading">
                <a-spin size="small" />
                <span>等待 AI 开始修复...</span>
              </div>
            </div>
            <!-- 构建结果 -->
            <div v-if="subAgentBuildLog" class="sub-agent-build-log">
              <div class="build-log-header">构建日志:</div>
              <pre class="build-log-content">{{ subAgentBuildLog }}</pre>
            </div>
            <!-- 完成状态 -->
            <div v-if="subAgentResult" class="sub-agent-result" :class="{ 'result-success': subAgentResult.success, 'result-failure': !subAgentResult.success }">
              <CheckCircleOutlined v-if="subAgentResult.success" />
              <CloseCircleOutlined v-else />
              <span>{{ subAgentResult.message || (subAgentResult.success ? '修复成功！' : '修复失败') }}</span>
            </div>
          </div>
        </div>

        <!-- 选中元素信息展示 -->
        <a-alert
          v-if="selectedElementInfo"
          class="selected-element-alert"
          type="info"
          closable
          @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <!-- 用户消息输入框 -->
        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.prevent="sendMessage"
                :disabled="isGenerating || isSubAgentRunning || !isOwner"
              />
            </a-tooltip>
            <a-textarea
              v-else
              v-model:value="userInput"
              :placeholder="isSubAgentRunning ? 'SubAgent 运行中，请稍候...' : getInputPlaceholder()"
              :rows="4"
              :maxlength="1000"
              @keydown.enter.prevent="sendMessage"
              :disabled="isGenerating || isSubAgentRunning"
            />
            <div class="input-actions">
              <a-button v-if="isGenerating" type="primary" danger @click="cancel">
                <template #icon>
                  <CloseOutlined />
                </template>
                停止
              </a-button>
              <a-button
                v-else
                type="primary"
                @click="sendMessage"
                :loading="isGenerating"
                :disabled="!isOwner || isSubAgentRunning"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧网页展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <h3>生成后的网页展示</h3>
          <div class="preview-actions">
            <a-button
              v-if="isOwner && previewUrl"
              type="link"
              :danger="isEditMode"
              @click="toggleEditMode"
              :class="{ 'edit-mode-active': isEditMode }"
              style="padding: 0; height: auto; margin-right: 12px"
            >
              <template #icon>
                <EditOutlined />
              </template>
              {{ isEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <!-- 预览和版本历史容器 -->
        <div class="preview-container">
          <div class="preview-content">
            <a-tabs v-model:activeKey="activePreviewTab" class="preview-tabs" @change="onPreviewTabChange">
              <a-tab-pane key="preview" tab="网页预览">
                <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
                  <div class="placeholder-icon">🌐</div>
                  <p>网站文件生成完成后将在这里展示</p>
                </div>
                <div v-else-if="isGenerating || rollingBack" class="preview-loading">
                  <a-spin size="large" />
                  <p>{{ rollingBack ? '正在回滚版本...' : '正在生成网站...' }}</p>
                </div>
                <iframe
                  v-else
                  :src="previewUrl"
                  class="preview-iframe"
                  frameborder="0"
                  @load="onIframeLoad"
                ></iframe>
              </a-tab-pane>
              <a-tab-pane key="code" tab="代码预览">
                <CodePreviewPanel
                  :file-tree="fileTree"
                  :file-content="fileContent"
                  :selected-file-path="selectedFilePath"
                  :loading-file-tree="loadingFileTree"
                  :loading-file-content="loadingFileContent"
                  @select-file="handleFileSelected"
                  @refresh-tree="refreshFileTree"
                />
              </a-tab-pane>
            </a-tabs>
          </div>
        </div>
      </div>
      <!-- 版本历史侧边栏 -->
      <div class="version-sidebar">
        <div class="version-sidebar-header">版本历史</div>
        <div
          v-for="version in versions"
          :key="version.id"
          class="version-item"
          :class="{
            'version-success': version.status === AppVersionStatusEnum.SUCCESS,
            'version-fixing': version.status === AppVersionStatusEnum.NEED_FIX,
            'version-building': [AppVersionStatusEnum.SOURCE_BUILDING, AppVersionStatusEnum.BUILDING].includes(version.status as AppVersionStatusEnum),
            'version-current': version.versionNum === appInfo?.currentVersionNum,
          }"
        >
          <div class="version-main">
            <span class="version-icon">
              <CheckCircleOutlined v-if="version.status === AppVersionStatusEnum.SUCCESS" />
              <SyncOutlined v-else-if="[AppVersionStatusEnum.SOURCE_BUILDING, AppVersionStatusEnum.BUILDING].includes(version.status as AppVersionStatusEnum)" class="version-loading" />
              <WarningOutlined v-else-if="version.status === AppVersionStatusEnum.NEED_FIX" />
            </span>
            <span class="version-label">v{{ version.versionNum }}</span>
          </div>
          <div class="version-actions">
            <a-button
              v-if="version.status === AppVersionStatusEnum.NEED_FIX && isOwner && versions[versions.length - 1]?.id === version.id"
              type="text"
              size="small"
              danger
              @click.stop="fixError"
            >
              修复
            </a-button>
            <a-button
              v-if="version.status === AppVersionStatusEnum.SUCCESS && isOwner && version.versionNum !== appInfo?.currentVersionNum"
              type="text"
              size="small"
              :disabled="rollingBack"
              @click.stop="rollbackVersion(version.versionNum || 0)"
            >
              回滚
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
      v-model:open="appDetailVisible"
      :app="appInfo"
      :show-actions="isOwner || isAdmin"
      @edit="editApp"
      @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
      v-model:open="deployModalVisible"
      :deploy-url="deployUrl"
      @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { cancelGeneration, getAppVo, getFixError, getFileTree, getFileContent, deployApp as deployAppApi } from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { listVersions, rollback } from '@/api/appVersionController'
import { AppVersionStatusEnum } from '@/constants/appVersion'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import CodePreviewPanel from '@/components/CodePreviewPanel.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { API_BASE_URL, getStaticPreviewUrl, getDeployUrl } from '@/config/env'
import { type ElementInfo, VisualEditor } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  CloseOutlined,
  DownloadOutlined,
  EditOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  SendOutlined,
  ToolOutlined,
  CheckCircleOutlined,
  SyncOutlined,
  WarningOutlined,
  DownOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<any>()

// 对话相关
interface Message {
  id?: number
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const messagesContainer = ref<HTMLElement>()

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)

// 版本相关
const versions = ref<API.AppVersionVO[]>([])
const isFixing = ref(false)
const rollingBack = ref(false)

// SubAgent 相关状态
const isSubAgentRunning = ref(false)
const subAgentPhase = ref<'idle' | 'fixing' | 'building'>('idle')
const subAgentAttempt = ref(0)
const subAgentContent = ref('')
const subAgentExpanded = ref(true)
const subAgentResult = ref<{success: boolean, attempts: number, message?: string} | null>(null)
const subAgentBuildLog = ref('')
const subAgentEventSource = ref<EventSource | null>(null)

// 代码预览相关
const activePreviewTab = ref('preview') // 'preview' | 'code'
const fileTree = ref<API.FileTreeNodeVO | null>(null)
const selectedFilePath = ref('')
const fileContent = ref<API.FileContentVO | null>(null)
const loadingFileTree = ref(false)
const loadingFileContent = ref(false)
const fileCache = new Map<string, API.FileContentVO>()

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

// 应用详情相关
const appDetailVisible = ref(false)

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value,
      pageSize: 10,
    }
    // 如果是加载更多，传递最后一条消息的创建时间作为游标
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      if (chatHistories.length > 0) {
        // 将对话历史转换为消息格式，并按时间正序排列（老消息在前）
        const historyMessages: Message[] = chatHistories
          .map((chat) => ({
            type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
            content: chat.message || '',
            createTime: chat.createTime,
          }))
          .reverse() // 反转数组，让老消息在前
        if (isLoadMore) {
          // 加载更多时，将历史消息添加到开头
          messages.value.unshift(...historyMessages)
        } else {
          // 初始加载，直接设置消息列表
          messages.value = historyMessages
          // 初始加载后滚动到底部，显示最新消息
          nextTick(() => scrollToBottom())
        }
        // 更新游标
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        // 检查是否还有更多历史
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
      }
      historyLoaded.value = true
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 获取版本列表
const fetchVersions = async () => {
  if (!appId.value) return
  try {
    const res = await listVersions({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      versions.value = res.data.data.sort((a, b) => (a.versionNum || 0) - (b.versionNum || 0))
    }
  } catch (error) {
    console.error('获取版本列表失败：', error)
  }
}

// 修复错误 - SubAgent SSE 连接
const fixError = async () => {
  if (!appId.value || isSubAgentRunning.value) return

  // 重置 SubAgent 状态
  isSubAgentRunning.value = true
  isGenerating.value = true
  subAgentPhase.value = 'idle'
  subAgentAttempt.value = 0
  subAgentContent.value = ''
  subAgentExpanded.value = true
  subAgentResult.value = null
  subAgentBuildLog.value = ''

  let eventSource: EventSource | null = null
  let streamCompleted = false

  try {
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL
    const url = `${baseURL}/app/sub-agent/fix?appId=${appId.value}`

    // 创建 EventSource 连接
    eventSource = new EventSource(url, {
      withCredentials: true,
    })
    subAgentEventSource.value = eventSource

    // 处理默认消息（AI 修复代码流式输出）
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        if (content !== undefined && content !== null) {
          subAgentContent.value += content
        }
      } catch (error) {
        console.error('SubAgent 解析消息失败:', error)
      }
    }

    // 处理 phase 事件
    eventSource.addEventListener('phase', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const data = JSON.parse(event.data)
        subAgentPhase.value = data.phase
        subAgentAttempt.value = data.attempt
      } catch (error) {
        console.error('SubAgent phase 事件解析失败:', error)
      }
    })

    // 处理 build-result 事件
    eventSource.addEventListener('build-result', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const data = JSON.parse(event.data)
        subAgentBuildLog.value = data.log || ''
      } catch (error) {
        console.error('SubAgent build-result 事件解析失败:', error)
      }
    })

    // 处理 done 事件
    eventSource.addEventListener('done', async function (event: MessageEvent) {
      if (streamCompleted) return

      streamCompleted = true
      eventSource?.close()
      subAgentEventSource.value = null

      try {
        const data = JSON.parse(event.data)
        const success = data.success
        const totalAttempts = data.totalAttempts
        const summary = data.summary
        const aiContent = data.aiContent

        // 记录结果
        subAgentResult.value = {
          success,
          attempts: totalAttempts,
          message: summary
        }

        // 在聊天区域插入 SubAgent 修复结果消息（作为 AI 回复）
        messages.value.push({
          type: 'ai',
          content: summary || (success ? '自动修复完成！' : '自动修复失败'),
        })
        nextTick(() => scrollToBottom())

        // 修复成功 → 刷新版本列表 + 预览
        if (success) {
          message.success('修复成功！')
          await fetchVersions()
          await updatePreview()
          // 等待预览加载
          for (let i = 0; i < 30; i++) {
            const isAvailable = await checkPreviewUrlAvailable(previewUrl.value)
            if (isAvailable) {
              previewReady.value = true
              break
            }
            await new Promise((resolve) => setTimeout(resolve, 3000))
          }
        } else {
          message.warning('修复未能完全成功，请查看详情或手动修复')
        }

        // === 通知主 Agent：SubAgent 修复完成 ===
        // 从 aiContent 中提取 [Fix Summary] 部分发给 /chat/gen/code
        if (aiContent) {
          // 提取 [Fix Summary] 部分
          const fixSummaryMatch = aiContent.match(/\[Fix Summary\]([\s\S]*?)(?=Files modified:|$)/i)
          const fixSummary = fixSummaryMatch ? `[Fix Summary]${fixSummaryMatch[1]}` : aiContent

          // 将 fixSummary 作为用户消息添加到对话中
          messages.value.push({
            type: 'user',
            content: fixSummary,
          })

          // 添加 AI 消息占位符
          const aiMessageIndex = messages.value.length
          messages.value.push({
            type: 'ai',
            content: '',
            loading: true,
          })

          await nextTick()
          scrollToBottom()

          // 调用 /chat/gen/code 接口发送 fixSummary 给主 Agent
          isGenerating.value = true
          await notifyMainAgent(fixSummary, aiMessageIndex)
        }

      } catch (error) {
        console.error('SubAgent done 事件解析失败:', error)
      } finally {
        isSubAgentRunning.value = false
        isGenerating.value = false
      }
    })

    // 处理错误事件
    eventSource.addEventListener('error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const data = JSON.parse(event.data)
        message.error('SubAgent 错误: ' + (data.error || '未知错误'))
      } catch (e) {
        message.error('SubAgent 连接错误')
      }

      streamCompleted = true
      eventSource?.close()
      subAgentEventSource.value = null
      isSubAgentRunning.value = false
      isGenerating.value = false
    })

    // 处理 EventSource 错误
    eventSource.onerror = function () {
      if (streamCompleted) return

      // 检查是否是正常的连接关闭
      if (eventSource?.readyState === EventSource.CLOSED) {
        streamCompleted = true
        if (!subAgentResult.value) {
          message.warning('SubAgent 连接已关闭')
          isSubAgentRunning.value = false
          isGenerating.value = false
        }
      }
    }

  } catch (error) {
    console.error('创建 SubAgent EventSource 失败：', error)
    message.error('启动修复失败，请重试')
    isSubAgentRunning.value = false
    isGenerating.value = false
  }
}

// 取消 SubAgent 修复
const cancelSubAgent = async () => {
  if (!appId.value || !isSubAgentRunning.value) return

  try {
    // 关闭 EventSource
    if (subAgentEventSource.value) {
      subAgentEventSource.value.close()
      subAgentEventSource.value = null
    }

    // 调用后端取消
    await cancelGeneration({ appId: appId.value })
    message.success('已取消修复')

    subAgentResult.value = {
      success: false,
      attempts: subAgentAttempt.value,
      message: '用户取消修复'
    }

  } catch (error) {
    console.error('取消 SubAgent 失败：', error)
  } finally {
    isSubAgentRunning.value = false
    isGenerating.value = false
  }
}

// 切换 SubAgent 面板折叠状态
const toggleSubAgentPanel = () => {
  subAgentExpanded.value = !subAgentExpanded.value
}

// 获取 SubAgent 状态颜色
const getSubAgentStatusColor = () => {
  if (subAgentResult.value) {
    return subAgentResult.value.success ? 'success' : 'error'
  }
  if (subAgentPhase.value === 'fixing') return 'warning'
  if (subAgentPhase.value === 'building') return 'processing'
  return 'default'
}

// 获取 SubAgent 状态文本
const getSubAgentStatusText = () => {
  if (subAgentResult.value) {
    return subAgentResult.value.success ? '已完成' : '失败'
  }
  if (subAgentPhase.value === 'fixing') return 'AI 修复中'
  if (subAgentPhase.value === 'building') return '构建中'
  return '等待开始'
}

// 版本点击处理
const handleVersionClick = async (version: API.AppVersionVO) => {
  if (version.status === AppVersionStatusEnum.NEED_FIX) {
    await fixError()
  } else {
    await rollbackVersion(version.versionNum || 0)
  }
}

// 获取文件树
const fetchFileTree = async () => {
  if (!appId.value || loadingFileTree.value) return
  loadingFileTree.value = true
  try {
    const res = await getFileTree({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      fileTree.value = res.data.data
    } else {
      message.error('获取文件树失败')
    }
  } catch (error) {
    console.error('获取文件树失败：', error)
    message.error('获取文件树失败')
  } finally {
    loadingFileTree.value = false
  }
}

// 获取文件内容
const fetchFileContent = async (filePath: string) => {
  if (!appId.value || loadingFileContent.value) return

  // 检查缓存
  if (fileCache.has(filePath)) {
    fileContent.value = fileCache.get(filePath) || null
    selectedFilePath.value = filePath
    return
  }

  loadingFileContent.value = true
  try {
    const res = await getFileContent({ appId: appId.value, filePath })
    if (res.data.code === 0 && res.data.data) {
      fileContent.value = res.data.data
      fileCache.set(filePath, res.data.data)
      selectedFilePath.value = filePath
    } else {
      message.error('获取文件内容失败')
    }
  } catch (error) {
    console.error('获取文件内容失败：', error)
    message.error('获取文件内容失败')
  } finally {
    loadingFileContent.value = false
  }
}

// 处理文件选择
const handleFileSelected = (filePath: string) => {
  fetchFileContent(filePath)
}

// 刷新文件树
const refreshFileTree = () => {
  fileCache.clear()
  selectedFilePath.value = ''
  fileContent.value = null
  fetchFileTree()
}

// 回滚版本
const rollbackVersion = async (versionNum: number) => {
  if (!appId.value || rollingBack.value) return
  
  rollingBack.value = true
  try {
    const res = await rollback({ appId: appId.value, versionNum })
    if (res.data.code === 0 && res.data.data) {
      message.success('回滚成功')
      // 轮询检查 currentVersionNum 是否匹配
      for (let i = 0; i < 10; i++) {
        await fetchAppInfo()
        if (appInfo.value?.currentVersionNum === versionNum) {
          break
        }
        await new Promise((resolve) => setTimeout(resolve, 1000))
      }
      await fetchVersions()
      await updatePreview()
      // 轮询检查预览URL是否可用
      for (let i = 0; i < 30; i++) {
        const isAvailable = await checkPreviewUrlAvailable(previewUrl.value)
        if (isAvailable) {
          previewReady.value = true
          break
        }
        previewReady.value = false
        await new Promise((resolve) => setTimeout(resolve, 3000))
      }
    } else {
      message.error('回滚失败：' + res.data.message)
    }
  } catch (error) {
    console.error('回滚失败：', error)
  } finally {
    rollingBack.value = false
  }
}

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id

  try {
    const res = await getAppVo({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data

      // 获取版本列表
      await fetchVersions()
      // 先加载对话历史
      await loadChatHistory()
      // 如果有至少2条对话记录，展示对应的网站
      if (messages.value.length >= 2) {
        updatePreview()
      }
      // 检查是否需要自动发送初始提示词
      // 只有在是自己的应用且没有对话历史时才自动发送
      if (
        appInfo.value.initPrompt &&
        isOwner.value &&
        messages.value.length === 0 &&
        historyLoaded.value
      ) {
        await sendInitialMessage(appInfo.value.initPrompt)
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  // 添加用户消息
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  await generateCode(prompt, aiMessageIndex)
}

// 发送消息
const sendMessage = async () => {
  if (!userInput.value.trim() || isGenerating.value) {
    return
  }

  let message = userInput.value.trim()
  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    message += elementContext
  }
  userInput.value = ''
  // 添加用户消息（包含元素信息）
  messages.value.push({
    type: 'user',
    content: message,
  })

  // 发送消息后，清除选中元素并退出编辑模式
  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  await generateCode(message, aiMessageIndex)
}

// 生成代码 - 使用 EventSource 处理流式响应
const generateCode = async (userMessage: string, aiMessageIndex: number) => {
  let eventSource: EventSource | null = null
  let streamCompleted = false

  try {
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL

    // 构建URL参数
    const params = new URLSearchParams({
      appId: appId.value || '',
      message: userMessage,
    })

    const url = `${baseURL}/app/chat/gen/code?${params}`

    // 创建 EventSource 连接
    eventSource = new EventSource(url, {
      withCredentials: true,
    })
    eventSourceRef.value = eventSource

    let fullContent = ''

    // 处理接收到的消息
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        // 解析JSON包装的数据
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        // 拼接内容
        if (content !== undefined && content !== null) {
          fullContent += content
          messages.value[aiMessageIndex].content = fullContent
          messages.value[aiMessageIndex].loading = false
          scrollToBottom()
        }
      } catch (error) {
        console.error('解析消息失败:', error)
        handleError(error, aiMessageIndex)
      }
    }

    // 处理done事件
    eventSource.addEventListener('done', function () {
      if (streamCompleted) return

      streamCompleted = true
      eventSource?.close()

      // 轮询检查最新版本状态，直到 status 不为 null
      pollVersionStatus()
    })

    // 轮询检查版本状态
    const pollVersionStatus = async (maxRetries = 10, interval = 1000) => {
      for (let i = 0; i < maxRetries; i++) {
        await fetchVersions()
        const latestVersion = versions.value[versions.value.length - 1]
        if (latestVersion?.status !== null && latestVersion?.status !== undefined) {
          await fetchAppInfo()
          isGenerating.value = false
          return
        }
        await new Promise((resolve) => setTimeout(resolve, interval))
      }
      // 达到最大重试次数后仍然执行
      await fetchAppInfo()
      isGenerating.value = false
    }

    // 处理business-error事件（后端限流等错误）
    eventSource.addEventListener('business-error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        // 显示具体的错误信息
        const errorMessage = errorData.message || '生成过程中出现错误'
        messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
        messages.value[aiMessageIndex].loading = false
        message.error(errorMessage)

        streamCompleted = true
        isGenerating.value = false
        eventSource?.close()
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        handleError(new Error('服务器返回错误'), aiMessageIndex)
      }
    })

    // 处理错误
    eventSource.onerror = function () {
      if (streamCompleted || !isGenerating.value) return
      // 检查是否是正常的连接关闭
      if (eventSource?.readyState === EventSource.CONNECTING) {
        streamCompleted = true
        isGenerating.value = false
        eventSource?.close()

        setTimeout(async () => {
          await fetchAppInfo()
          updatePreview()
        }, 1000)
      } else {
        handleError(new Error('SSE连接错误'), aiMessageIndex)
      }
    }
  } catch (error) {
    console.error('创建 EventSource 失败：', error)
    handleError(error, aiMessageIndex)
  }
}

// 错误处理函数
const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
  messages.value[aiMessageIndex].loading = false
  message.error('生成失败，请重试')
  isGenerating.value = false
}

// 通知主 Agent：SubAgent 修复完成
// 通过 /chat/gen/code 接口发送消息，让主 Agent 感知到修复状态
const notifyMainAgent = async (reportMessage: string, aiMessageIndex: number) => {
  let eventSource: EventSource | null = null
  let streamCompleted = false

  try {
    const baseURL = request.defaults.baseURL || API_BASE_URL
    const params = new URLSearchParams({
      appId: appId.value || '',
      message: reportMessage,
    })
    const url = `${baseURL}/app/chat/gen/code?${params}`

    eventSource = new EventSource(url, {
      withCredentials: true,
    })

    let fullContent = ''

    // 处理接收到的消息
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        const parsed = JSON.parse(event.data)
        const content = parsed.d

        if (content !== undefined && content !== null) {
          fullContent += content
          if (messages.value[aiMessageIndex]) {
            messages.value[aiMessageIndex].content = fullContent
            messages.value[aiMessageIndex].loading = false
            scrollToBottom()
          }
        }
      } catch (error) {
        console.error('[notifyMainAgent] 解析消息失败:', error)
      }
    }

    // 处理 done 事件
    eventSource.addEventListener('done', function () {
      if (streamCompleted) return

      streamCompleted = true
      eventSource?.close()

      // 刷新版本状态
      pollVersionStatus()
    })

    // 轮询检查版本状态
    const pollVersionStatus = async (maxRetries = 10, interval = 1000) => {
      for (let i = 0; i < maxRetries; i++) {
        await fetchVersions()
        const latestVersion = versions.value[versions.value.length - 1]
        if (latestVersion?.status !== null && latestVersion?.status !== undefined) {
          await fetchAppInfo()
          isGenerating.value = false
          return
        }
        await new Promise((resolve) => setTimeout(resolve, interval))
      }
      await fetchAppInfo()
      isGenerating.value = false
    }

    // 处理错误
    eventSource.onerror = function () {
      if (streamCompleted) return

      if (eventSource?.readyState === EventSource.CLOSED) {
        streamCompleted = true
        isGenerating.value = false
        eventSource?.close()
        setTimeout(async () => {
          await fetchAppInfo()
        }, 1000)
      }
    }

  } catch (error) {
    console.error('[notifyMainAgent] 通知主 Agent 失败:', error)
    if (messages.value[aiMessageIndex]) {
      messages.value[aiMessageIndex].content = '通知主 Agent 失败'
      messages.value[aiMessageIndex].loading = false
    }
    isGenerating.value = false
  }
}

// 更新预览
const updatePreview = () => {
  if (appId.value) {
    const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
    const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
    previewUrl.value = newPreviewUrl
    previewReady.value = false
  }
}

// 检查预览URL是否可用
const checkPreviewUrlAvailable = async (url: string): Promise<boolean> => {
  try {
    const response = await fetch(url, { method: 'GET' })
    return response.ok
  } catch {
    return false
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 下载代码
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    // 下载文件
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    // 清理
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = getDeployUrl(res.data.data)
      deployModalVisible.value = true
      message.success('部署成功')
    } else {
      message.error(res.data.message || '部署失败')
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 在新窗口打开预览
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return
  message.info('删除功能开发中...')
}

// 可视化编辑相关函数
const toggleEditMode = () => {
  // 检查 iframe 是否已经加载
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (!iframe) {
    message.warning('请等待页面加载完成')
    return
  }
  // 确保 visualEditor 已初始化
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

// 页面加载时获取应用信息
onMounted(() => {
  fetchAppInfo()

  // 监听 iframe 消息
  window.addEventListener('message', (event) => {
    visualEditor.handleIframeMessage(event)
  })
})

// 清理资源
onUnmounted(() => {
  // EventSource 会在组件卸载时自动清理
  if (isGenerating.value) {
    cancel()
  }
  // 清理 SubAgent EventSource
  if (subAgentEventSource.value) {
    subAgentEventSource.value.close()
    subAgentEventSource.value = null
  }
})

// 取消生成
const cancel = async () => {
  if (!appId.value) return

  try {
    const res = await cancelGeneration({ appId: appId.value })
    if (res.data.code === 0 && res.data.data) {
      message.success('已停止生成')
    }
  } catch (error) {
    console.error('取消生成失败：', error)
  } finally {
    isGenerating.value = false
    // 关闭 EventSource
    if (eventSourceRef.value) {
      eventSourceRef.value.close()
      eventSourceRef.value = null
    }
  }
}

const eventSourceRef = ref<EventSource | null>(null)

// 监听 tab 切换
const onPreviewTabChange = async (key: string) => {
  if (key === 'code' && !fileTree.value) {
    await fetchFileTree()
  }
}
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fdfdfd;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

/* 左侧对话区域 */
.chat-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  padding-right: 50px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
}

/* 右侧预览区域 */
.preview-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  min-width: 0;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.preview-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

/* 预览容器 - 撑满剩余空间 */
.preview-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.preview-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.preview-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.preview-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
  flex-shrink: 0;
  padding: 0 16px;
}

.preview-tabs :deep(.ant-tabs-content) {
  flex: 1;
  height: 100%;
  min-height: 0;
}

.preview-tabs :deep(.ant-tabs-tabpane) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.preview-tabs :deep(.ant-tabs-tabpane > div) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-iframe {
  flex: 1;
  width: 100%;
  height: 100%;
  border: none;
  min-height: 0;
}

.selected-element-alert {
  margin: 0 16px;
}

/* SubAgent 修复面板 */
.sub-agent-panel {
  margin: 0 16px 8px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fafafa;
  overflow: hidden;
}

.sub-agent-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #fa8c16 0%, #faad14 100%);
  color: white;
  cursor: pointer;
  user-select: none;
}

.sub-agent-header:hover {
  background: linear-gradient(135deg, #fa8c16 0%, #ffc53d 100%);
}

.sub-agent-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.sub-agent-phase-info {
  font-size: 12px;
  opacity: 0.9;
}

.sub-agent-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-arrow {
  transition: transform 0.3s ease;
  font-size: 12px;
}

.collapse-arrow.arrow-rotated {
  transform: rotate(-90deg);
}

.sub-agent-content {
  padding: 12px 16px;
  max-height: 300px;
  overflow-y: auto;
}

.sub-agent-stream {
  margin-bottom: 12px;
}

.sub-agent-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #999;
  font-size: 13px;
}

.sub-agent-build-log {
  background: #1e1e1e;
  border-radius: 4px;
  padding: 12px;
  margin-top: 12px;
}

.build-log-header {
  color: #ccc;
  font-size: 12px;
  margin-bottom: 8px;
  font-weight: 500;
}

.build-log-content {
  color: #67c23a;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 150px;
  overflow-y: auto;
  margin: 0;
}

.sub-agent-result {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 6px;
  font-weight: 500;
}

.sub-agent-result.result-success {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #52c41a;
}

.sub-agent-result.result-failure {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #ff4d4f;
}

/* 版本边栏 */
.version-sidebar {
  width: 120px;
  background: #f3f3f3;
  border-left: 1px solid #e5e5e5;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

/* 边栏标题 */
.version-sidebar-header {
  padding: 12px 16px;
  font-size: 11px;
  font-weight: 600;
  color: #6c6c6c;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid #e5e5e5;
}

/* 版本项 */
.version-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  cursor: pointer;
  transition: background-color 200ms ease;
  border-left: 2px solid transparent;
}

.version-item:hover {
  background: #e8e8e8;
}

.version-item.version-active {
  background: #e0e0e0;
  border-left-color: #007acc;
}

/* 版本主要内容 */
.version-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.version-icon {
  display: flex;
  align-items: center;
  font-size: 16px;
}

.version-label {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

/* 状态特定样式 */
.version-success .version-icon {
  color: #22863a;
}

.version-building .version-icon {
  color: #0366d6;
}

.version-building .version-loading {
  display: inline-block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.version-fixing {
  background: #fff5f5;
  border-left-color: #d73a49;
}

.version-fixing .version-icon {
  color: #d73a49;
}

.version-fixing:hover {
  background: #ffebeb;
}

/* 当前版本高亮 */
.version-current {
  background: #e6f7ff;
  border-left-color: #1890ff;
}

.version-current:hover {
  background: #bae7ff;
}

/* 操作按钮 */
.version-actions {
  opacity: 0;
  transition: opacity 200ms ease;
}

.version-item:hover .version-actions {
  opacity: 1;
}

/* 响应式设计 */
@media (max-width: 1440px) {
  .version-sidebar {
    width: 100px;
  }
}

@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: 50vh;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px 16px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
  }

  .message-content {
    max-width: 85%;
  }

  /* 选中元素信息样式 */
  .selected-element-alert {
    margin: 0 16px;
  }

  .selected-element-info {
    line-height: 1.4;
  }

  .element-header {
    margin-bottom: 8px;
  }

  .element-details {
    margin-top: 8px;
  }

  .element-item {
    margin-bottom: 4px;
    font-size: 13px;
  }

  .element-item:last-child {
    margin-bottom: 0;
  }

  .element-tag {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #007bff;
  }

  .element-id {
    color: #28a745;
    margin-left: 4px;
  }

  .element-class {
    color: #ffc107;
    margin-left: 4px;
  }

  .element-selector-code {
    font-family: 'Monaco', 'Menlo', monospace;
    background: #f6f8fa;
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: #d73a49;
    border: 1px solid #e1e4e8;
  }

  /* 编辑模式按钮样式 */
  .edit-mode-active {
    background-color: #52c41a !important;
    border-color: #52c41a !important;
    color: white !important;
  }

  .edit-mode-active:hover {
    background-color: #73d13d !important;
    border-color: #73d13d !important;
  }
}
</style>
