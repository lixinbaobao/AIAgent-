<template>
  <div class="super-agent-container">
    <!-- 左侧会话列表边栏 -->
    <div class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <button class="new-chat-btn" @click="createNewSession">
          <span class="plus-icon">+</span>
          新对话
        </button>
      </div>

      <div class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: currentSessionId === session.id }"
          @click="selectSession(session.id)"
        >
          <div class="session-title">{{ session.title }}</div>
          <div class="session-time">{{ formatSessionTime(session.updateTime) }}</div>
          <button class="delete-btn" @click.stop="handleDeleteSession(session.id)">×</button>
        </div>

        <div v-if="sessions.length === 0" class="no-sessions">
          暂无历史会话
        </div>
      </div>
    </div>

    <!-- 侧边栏切换按钮（始终显示） -->
    <div class="sidebar-toggle" @click="toggleSidebar">
      <span v-if="!sidebarCollapsed">‹</span>
      <span v-else>›</span>
    </div>

    <!-- 右侧聊天区域 -->
    <div class="main-content">
      <div class="header">
        <div class="back-button" @click="goBack">返回</div>
        <h1 class="title">AI超级智能体</h1>
        <div class="placeholder"></div>
      </div>

      <div class="content-wrapper">
        <div class="chat-area">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            ai-type="super"
            @send-message="sendMessage"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import { chatWithManus, getSessions, getSession, deleteSession, createSession as createSessionApi } from '../api'

// 设置页面标题和元数据
useHead({
  title: 'AI超级智能体 - 三金AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI超级智能体是三金AI超级智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    },
    {
      name: 'keywords',
      content: 'AI超级智能体,智能助手,专业问答,AI问答,专业建议,三金,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
let eventSource = null

// 会话相关
const sessions = ref([])
const currentSessionId = ref(null)
const sidebarCollapsed = ref(false)

// 加载会话列表
const loadSessions = async () => {
  try {
    const response = await getSessions()
    sessions.value = response.data || []
    console.log('加载会话列表:', sessions.value.length, '个会话')
  } catch (error) {
    console.error('加载会话列表失败:', error)
  }
}

// 选择会话
const selectSession = async (sessionId) => {
  try {
    currentSessionId.value = sessionId
    const response = await getSession(sessionId)
    const session = response.data

    if (session && session.messages && session.messages.length > 0) {
      // 转换消息格式
      messages.value = session.messages.map(msg => ({
        content: msg.content,
        isUser: msg.role === 'user',
        type: msg.role === 'user' ? 'user-question' : 'ai-answer',
        time: msg.time
      }))
    } else {
      // 空会话，添加欢迎消息
      messages.value = []
      messages.value.push({
        content: '你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？',
        isUser: false,
        type: 'ai-answer',
        time: Date.now()
      })
    }
  } catch (error) {
    console.error('加载会话失败:', error)
    messages.value = []
  }
}

// 创建新会话
const createNewSession = async () => {
  try {
    // 关闭当前 SSE 连接（如果有）
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    
    // 重置连接状态
    connectionStatus.value = 'disconnected'
    
    // 调用后端接口创建新会话，立即生成历史会话记录
    const response = await createSessionApi('新对话')
    const newSession = response.data
    
    // 设置当前会话ID
    currentSessionId.value = newSession.id
    
    // 清空消息
    messages.value = []

    // 添加欢迎消息
    messages.value.push({
      content: '你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？',
      isUser: false,
      type: 'ai-answer',
      time: Date.now()
    })

    // 刷新会话列表，让新会话出现在左侧历史记录中
    await loadSessions()
    
    console.log('新会话已创建:', newSession.id)
  } catch (error) {
    console.error('创建新会话失败:', error)
    // 降级处理：即使创建失败，也重置当前状态
    currentSessionId.value = null
    messages.value = []
    messages.value.push({
      content: '你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？',
      isUser: false,
      type: 'ai-answer',
      time: Date.now()
    })
  }
}

// 删除会话
const handleDeleteSession = async (sessionId) => {
  try {
    await deleteSession(sessionId)
    sessions.value = sessions.value.filter(s => s.id !== sessionId)

    // 如果删除的是当前会话，清空消息
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
    }
  } catch (error) {
    console.error('删除会话失败:', error)
  }
}

// 发送消息
const sendMessage = async (message) => {
  // 先显示用户消息
  addMessage(message, true, 'user-question')

  // 如果没有当前会话ID，先创建新会话
  if (!currentSessionId.value) {
    try {
      console.log('创建新会话...')
      const response = await createSessionApi(message.substring(0, 20))
      currentSessionId.value = response.data.id
      console.log('新会话已创建:', currentSessionId.value)
    } catch (error) {
      console.error('创建会话失败:', error)
      addMessage('创建会话失败，请检查后端是否启动：' + error.message, false, 'ai-error')
      return
    }
  }

  // 连接SSE
  if (eventSource) {
    eventSource.close()
  }

  // 设置连接状态
  connectionStatus.value = 'connecting'

  let isFirstResponse = true

  // 发送消息
  eventSource = chatWithManus(message, currentSessionId.value)

  // 监听SSE消息
  eventSource.onmessage = (event) => {
    const data = event.data

    if (data && data !== '[DONE]') {
      if (isFirstResponse) {
        addMessage(data, false, 'ai-answer')
        isFirstResponse = false
      } else {
        // 逐字追加内容（真正的流式输出）
        const lastMsg = messages.value[messages.value.length - 1]
        if (lastMsg && !lastMsg.isUser) {
          lastMsg.content += data
        }
      }
    }

    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      eventSource.close()
      // 刷新会话列表
      console.log('对话完成，刷新会话列表...')
      loadSessions()
    }
  }

  // 监听SSE错误
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()
  }
}

// 添加消息到列表
const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
}

// 格式化会话时间
const formatSessionTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date

  // 一天内显示时间
  if (diff < 86400000) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  // 一周内显示天数
  if (diff < 604800000) {
    const days = Math.floor(diff / 86400000)
    return `${days}天前`
  }
  // 其他显示日期
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// 切换侧边栏
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时
onMounted(async () => {
  await loadSessions()

  // 如果有历史会话，选择最新的一个
  if (sessions.value.length > 0) {
    await selectSession(sessions.value[0].id)
  } else {
    // 添加欢迎消息
    addMessage('你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？', false)
  }
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.super-agent-container {
  display: flex;
  min-height: 100vh;
  background-color: #f9fbff;
  position: relative;
}

/* 左侧边栏样式 */
.sidebar {
  width: 260px;
  background-color: #f0f2f5;
  border-right: 1px solid #e0e0e0;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease, margin-left 0.3s ease;
  position: relative;
  z-index: 10;
}

.sidebar.collapsed {
  width: 0;
  min-width: 0;
  margin-left: -260px;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
}

.new-chat-btn {
  width: 100%;
  padding: 12px 16px;
  background-color: #3f51b5;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: background-color 0.2s;
}

.new-chat-btn:hover {
  background-color: #303f9f;
}

.plus-icon {
  font-size: 18px;
  font-weight: bold;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 12px;
  margin-bottom: 4px;
  background-color: white;
  border-radius: 8px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.session-item:hover {
  background-color: #e8eaf6;
}

.session-item.active {
  background-color: #e8eaf6;
  border-left: 3px solid #3f51b5;
}

.session-title {
  font-size: 14px;
  color: #333;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}

.session-time {
  font-size: 12px;
  color: #999;
}

.delete-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: #999;
  font-size: 16px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: #f44336;
}

.no-sessions {
  text-align: center;
  color: #999;
  padding: 20px;
  font-size: 14px;
}

/* 侧边栏切换按钮（始终可见） */
.sidebar-toggle {
  position: absolute;
  left: 260px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 60px;
  background-color: #3f51b5;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 0 8px 8px 0;
  font-size: 16px;
  font-weight: bold;
  z-index: 11;
  transition: left 0.3s ease;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

.sidebar-toggle:hover {
  background-color: #303f9f;
}

.sidebar.collapsed + .sidebar-toggle {
  left: 0;
}

/* 右侧主内容样式 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-left: 24px;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 16px 24px;
  background-color: #3f51b5;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
  justify-self: start;
}

.back-button:hover {
  opacity: 0.8;
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  text-align: center;
  justify-self: center;
}

.placeholder {
  width: 1px;
  justify-self: end;
}

.content-wrapper {
  flex: 1;
  overflow: hidden;
}

.chat-area {
  height: calc(100vh - 56px);
  padding: 16px;
  overflow: hidden;
}

/* 响应式样式 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    height: 100%;
    z-index: 100;
  }

  .sidebar.collapsed {
    margin-left: -260px;
  }

  .sidebar-toggle {
    left: 260px;
  }

  .sidebar.collapsed + .sidebar-toggle {
    left: 0;
  }

  .header {
    padding: 12px 16px;
  }

  .title {
    font-size: 18px;
  }

  .chat-area {
    padding: 12px;
  }

  .main-content {
    margin-left: 0;
  }
}
</style>