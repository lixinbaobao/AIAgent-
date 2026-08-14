import axios from 'axios'

// 根据环境变量设置 API 基础 URL
const API_BASE_URL = process.env.NODE_ENV === 'production'
 ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
 : 'http://localhost:8123/api' // 开发环境指向本地后端服务

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .filter(key => params[key] !== null && params[key] !== undefined && params[key] !== '')
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')

  const fullUrl = `${API_BASE_URL}${url}?${queryString}`

  // 创建EventSource
  const eventSource = new EventSource(fullUrl)

  eventSource.onmessage = event => {
    let data = event.data

    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }

  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }

  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

// AI恋爱大师聊天
export const chatWithLoveApp = (message, chatId) => {
  return connectSSE('/ai/love_app/chat/sse', { message, chatId })
}

// AI超级智能体聊天
export const chatWithManus = (message, sessionId) => {
  return connectSSE('/ai/manus/chat', { message, sessionId })
}

// ==================== 会话管理接口 ====================

// 获取会话列表
export const getSessions = () => {
  return request.get('/ai/manus/sessions')
}

// 创建新会话
export const createSession = (title) => {
  return request.post('/ai/manus/session', null, { params: { title } })
}

// 获取会话详情
export const getSession = (sessionId) => {
  return request.get(`/ai/manus/session/${sessionId}`)
}

// 删除会话
export const deleteSession = (sessionId) => {
  return request.delete(`/ai/manus/session/${sessionId}`)
}

// ==================== 文件上传接口 ====================

// 上传文件
export const uploadFile = (formData, onProgress) => {
  return request.post('/file/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        onProgress(percent)
      }
    }
  })
}

// 获取已上传文件列表
export const listUploadedFiles = () => {
  return request.get('/file/list')
}

// 删除已上传文件
export const deleteUploadedFile = (filePath) => {
  return request.delete('/file/delete', { params: { path: filePath } })
}

export default {
  chatWithLoveApp,
  chatWithManus,
  getSessions,
  createSession,
  getSession,
  deleteSession,
  uploadFile,
  listUploadedFiles,
  deleteUploadedFile
} 