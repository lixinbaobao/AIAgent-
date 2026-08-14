<template>
  <div class="file-upload-page">
    <div class="container">
      <h1 class="page-title">文件上传</h1>
      <p class="page-desc">支持拖拽上传，最大单个文件 50MB</p>

      <!-- 拖拽上传区域 -->
      <div
        class="drop-zone"
        :class="{ 'drag-over': isDragOver }"
        @dragover.prevent="handleDragOver"
        @dragleave.prevent="handleDragLeave"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <input
          ref="fileInput"
          type="file"
          multiple
          style="display: none"
          @change="handleFileSelect"
        />
        <div class="drop-zone-content">
          <div class="folder-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V7z" />
            </svg>
          </div>
          <p class="drop-text">拖拽文件到此或者点击上传</p>
          <p class="drop-hint">支持多文件同时上传</p>
        </div>
      </div>

      <!-- 已上传文件列表 -->
      <div class="file-list" v-if="fileList.length > 0">
        <div class="file-list-header">
          <span>已上传文件 ({{ fileList.length }})</span>
          <button class="clear-all-btn" @click="clearAll">清空全部</button>
        </div>
        <div class="file-item" v-for="file in fileList" :key="file.id">
          <div class="file-info">
            <div class="file-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z" />
                <path d="M14 2v6h6" />
              </svg>
            </div>
            <div class="file-details">
              <span class="file-name">{{ file.name }}</span>
              <span class="file-size">{{ formatFileSize(file.size) }}</span>
            </div>
          </div>
          <div class="file-progress">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: file.progress + '%' }"></div>
            </div>
            <span class="progress-text">{{ file.progress }}%</span>
          </div>
          <button class="delete-btn" @click="deleteFile(file.id)" title="删除">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2m3 0v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6h14z" />
            </svg>
          </button>
        </div>
      </div>

      <!-- 空状态 -->
      <div class="empty-state" v-else>
        <p>暂无上传文件</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { uploadFile, deleteUploadedFile, listUploadedFiles } from '../api'

const fileInput = ref(null)
const isDragOver = ref(false)
const fileList = ref([])

// 生成唯一ID
const generateId = () => Date.now() + Math.random().toString(36).substr(2, 9)

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 触发文件选择
const triggerFileInput = () => {
  fileInput.value.click()
}

// 处理文件选择
const handleFileSelect = (event) => {
  const files = Array.from(event.target.files)
  uploadFiles(files)
  event.target.value = ''
}

// 拖拽进入
const handleDragOver = () => {
  isDragOver.value = true
}

// 拖拽离开
const handleDragLeave = () => {
  isDragOver.value = false
}

// 拖拽放下
const handleDrop = (event) => {
  isDragOver.value = false
  const files = Array.from(event.dataTransfer.files)
  uploadFiles(files)
}

// 上传文件
const uploadFiles = (files) => {
  files.forEach((file) => {
    const fileItem = {
      id: generateId(),
      name: file.name,
      size: file.size,
      progress: 0,
      file: file
    }
    fileList.value.push(fileItem)
    uploadSingleFile(fileItem)
  })
}

// 上传单个文件
const uploadSingleFile = async (fileItem) => {
  const formData = new FormData()
  formData.append('file', fileItem.file)

  try {
    // 模拟进度
    const progressInterval = setInterval(() => {
      if (fileItem.progress < 90) {
        fileItem.progress += Math.random() * 15
        if (fileItem.progress > 90) fileItem.progress = 90
      }
    }, 200)

    const response = await uploadFile(formData)
    clearInterval(progressInterval)
    fileItem.progress = 100
    fileItem.serverPath = response.data?.path || ''
  } catch (error) {
    fileItem.progress = 0
    fileItem.error = error.message || '上传失败'
  }
}

// 删除文件
const deleteFile = async (id) => {
  const index = fileList.value.findIndex((f) => f.id === id)
  if (index > -1) {
    const file = fileList.value[index]
    if (file.serverPath) {
      try {
        await deleteUploadedFile(file.serverPath)
      } catch (e) {
        console.error('删除服务器文件失败', e)
      }
    }
    fileList.value.splice(index, 1)
  }
}

// 清空全部
const clearAll = () => {
  fileList.value = []
}
</script>

<style scoped>
.file-upload-page {
  min-height: 100vh;
  background-color: #f5f7fa;
  padding: 40px 20px;
}

.container {
  max-width: 800px;
  margin: 0 auto;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.page-desc {
  font-size: 14px;
  color: #8c8c8c;
  margin: 0 0 32px 0;
}

/* 拖拽区域 */
.drop-zone {
  background: #ffffff;
  border: 2px dashed #91caff;
  border-radius: 12px;
  padding: 60px 40px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 32px;
}

.drop-zone:hover {
  border-color: #4096ff;
  background-color: #f0f7ff;
}

.drop-zone.drag-over {
  border-color: #1677ff;
  background-color: #e6f4ff;
  transform: scale(1.01);
}

.drop-zone-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.folder-icon {
  width: 64px;
  height: 64px;
  color: #4096ff;
  margin-bottom: 16px;
}

.folder-icon svg {
  width: 100%;
  height: 100%;
}

.drop-text {
  font-size: 18px;
  font-weight: 500;
  color: #262626;
  margin: 0 0 8px 0;
}

.drop-hint {
  font-size: 13px;
  color: #8c8c8c;
  margin: 0;
}

/* 文件列表 */
.file-list {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.file-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.file-list-header span {
  font-size: 15px;
  font-weight: 500;
  color: #262626;
}

.clear-all-btn {
  background: none;
  border: none;
  color: #ff4d4f;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.clear-all-btn:hover {
  background-color: #fff1f0;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f5f5f5;
}

.file-item:last-child {
  border-bottom: none;
}

.file-info {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.file-icon {
  width: 40px;
  height: 40px;
  color: #4096ff;
  margin-right: 12px;
  flex-shrink: 0;
}

.file-icon svg {
  width: 100%;
  height: 100%;
}

.file-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.file-name {
  font-size: 14px;
  color: #262626;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.file-size {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}

.file-progress {
  display: flex;
  align-items: center;
  width: 200px;
  margin: 0 20px;
  flex-shrink: 0;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background-color: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
  margin-right: 12px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4096ff, #1677ff);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 12px;
  color: #8c8c8c;
  width: 36px;
  text-align: right;
}

.delete-btn {
  width: 32px;
  height: 32px;
  background-color: #fff1f0;
  border: none;
  border-radius: 6px;
  color: #ff4d4f;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.delete-btn:hover {
  background-color: #ff4d4f;
  color: #ffffff;
}

.delete-btn svg {
  width: 16px;
  height: 16px;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #8c8c8c;
  font-size: 14px;
}
</style>
