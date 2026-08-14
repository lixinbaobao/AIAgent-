<template>
  <div class="home-container">
    <div class="header">
      <div class="glitch-wrapper">
        <h1 class="glitch-title">三金AI超级智能体</h1>
      </div>
      <p class="subtitle">/ 探索AI的无限可能 /</p>
      <div class="cyber-line"></div>
    </div>

    <div class="main-content">
      <!-- 左侧：文件上传区域 -->
      <div class="left-panel">
        <div class="panel-title">文件上传</div>
        <div class="panel-subtitle">支持拖拽上传，最大单个文件 50MB</div>

        <!-- 拖拽上传区域 -->
        <div
          class="drop-zone"
          :class="{ 'drag-over': isDragOver }"
          @dragover.prevent="handleDragOver"
          @dragleave.prevent="handleDragLeave"
          @drop.prevent="handleDrop"
          @click="triggerFileInput"
        >
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
        <!-- 隐藏的文件选择input（移到外面，避免点击事件冲突） -->
        <input
          ref="fileInput"
          type="file"
          multiple
          style="position: absolute; width: 0; height: 0; opacity: 0; overflow: hidden"
          @change="handleFileSelect"
        />

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
            <!-- 错误提示 -->
            <div class="file-error" v-if="file.error">
              {{ file.error }}
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div class="empty-state" v-else>
          <p>暂无上传文件</p>
        </div>
      </div>

      <!-- 右侧：应用卡片 -->
      <div class="right-panel">
        <div class="apps-container">
          <div class="app-card" @click="navigateTo('/love-master')">
            <div class="card-glow"></div>
            <div class="app-icon love-icon">❤️</div>
            <div class="app-info">
              <div class="app-title">AI恋爱大师</div>
              <div class="app-desc">智能情感顾问，帮你解答恋爱烦恼</div>
            </div>
            <div class="app-button">
              <span class="btn-text">立即体验</span>
              <span class="btn-icon">→</span>
            </div>
          </div>

          <div class="app-card" @click="navigateTo('/super-agent')">
            <div class="card-glow"></div>
            <div class="app-icon robot-icon">🤖</div>
            <div class="app-info">
              <div class="app-title">AI超级智能体</div>
              <div class="app-desc">全能型AI助手，解决各类专业问题</div>
            </div>
            <div class="app-button">
              <span class="btn-text">立即体验</span>
              <span class="btn-icon">→</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="cyber-circles">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { uploadFile, deleteUploadedFile } from '../api'

useHead({
  title: '三金AI超级智能体应用平台 - 首页',
  meta: [
    {
      name: 'description',
      content: '三金AI超级智能体应用平台提供AI恋爱大师和AI超级智能体服务，满足您的各种AI对话需求'
    },
    {
      name: 'keywords',
      content: 'AI智能体,AI应用,AI恋爱大师,AI助手,智能对话,三金,AI超级智能体,首页'
    }
  ]
})

const router = useRouter()
const fileInput = ref(null)
const isDragOver = ref(false)
const fileList = ref([])

const navigateTo = (path) => {
  router.push(path)
}

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
  console.log('触发文件选择')
  if (fileInput.value) {
    fileInput.value.click()
  } else {
    console.error('fileInput ref 不存在')
  }
}

// 处理文件选择
const handleFileSelect = (event) => {
  const files = Array.from(event.target.files)
  console.log('选择了', files.length, '个文件')
  if (files.length > 0) {
    uploadFiles(files)
  }
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

  console.log('开始上传文件:', fileItem.name)

  try {
    // 初始进度设为 5%，表示开始上传
    fileItem.progress = 5

    const response = await uploadFile(formData, (percent) => {
      // 真实上传进度，最多到 90%（剩下 10% 留给后端处理）
      fileItem.progress = Math.min(percent, 90)
    })

    // 上传完成，后端处理中，设为 95%
    fileItem.progress = 95

    // 处理完成，设为 100%
    fileItem.progress = 100
    fileItem.serverPath = response.data?.path || ''
    console.log('文件上传成功:', fileItem.name, response.data)
  } catch (error) {
    fileItem.progress = 0
    let errorMsg = '上传失败'
    if (error.response) {
      errorMsg = `上传失败（${error.response.status}）：${error.response.data?.message || error.message}`
    } else if (error.message === 'Network Error') {
      errorMsg = '网络错误，请检查后端是否启动（http://localhost:8123）'
    } else {
      errorMsg = `上传失败：${error.message}`
    }
    fileItem.error = errorMsg
    console.error('文件上传失败:', fileItem.name, error)
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
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@400;500;700&display=swap');

:root {
  --neon-blue: #00f0ff;
  --neon-blue-rgb: 0, 240, 255;
  --neon-purple: #9000ff;
  --neon-pink: #ff00d4;
  --cyber-black: #0a0a12;
  --cyber-dark: #111122;
  --cyber-light: #edf7ff;
}

.home-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--cyber-dark);
  background-image:
    linear-gradient(0deg, rgba(8, 17, 34, 0.9), rgba(5, 8, 20, 0.9)),
    url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><rect x="0" y="0" width="100" height="1" fill="%23111133" opacity="0.3"/><rect x="0" y="0" width="1" height="100" fill="%23111133" opacity="0.3"/></svg>');
  background-size: auto, 40px 40px;
  position: relative;
  overflow: hidden;
}

/* 头部 */
.header {
  padding: 50px 20px 30px;
  text-align: center;
  background-color: transparent;
  position: relative;
  z-index: 2;
}

.glitch-wrapper {
  position: relative;
  display: inline-block;
  margin-bottom: 15px;
}

.glitch-title {
  font-family: 'Orbitron', sans-serif;
  font-size: 2.8rem;
  font-weight: 700;
  color: var(--cyber-light);
  text-shadow:
    0 0 5px rgba(0, 240, 255, 0.7),
    0 0 10px rgba(0, 240, 255, 0.5),
    0 0 20px rgba(0, 240, 255, 0.3);
  letter-spacing: 2px;
  position: relative;
  animation: glitch 3s infinite;
}

.glitch-title::before,
.glitch-title::after {
  content: '三金AI超级智能体';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  opacity: 0.8;
}

.glitch-title::before {
  color: var(--neon-pink);
  z-index: -1;
  animation: glitch-anim 2s infinite;
}

.glitch-title::after {
  color: var(--neon-blue);
  z-index: -2;
  animation: glitch-anim-2 3s infinite;
}

.subtitle {
  font-family: 'Orbitron', sans-serif;
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.7);
  max-width: 600px;
  margin: 0 auto 15px;
  letter-spacing: 3px;
  text-transform: uppercase;
}

.cyber-line {
  height: 2px;
  width: 80%;
  max-width: 600px;
  margin: 0 auto;
  background: linear-gradient(90deg, transparent, var(--neon-blue), transparent);
  position: relative;
}

.cyber-line::before,
.cyber-line::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 10px;
  height: 10px;
  background-color: var(--neon-blue);
  border-radius: 50%;
  transform: translateY(-50%);
  box-shadow: 0 0 10px 2px var(--neon-blue);
}

.cyber-line::before {
  left: 20%;
}

.cyber-line::after {
  right: 20%;
}

/* 主内容区：左右布局 */
.main-content {
  display: flex;
  gap: 40px;
  max-width: 1400px;
  margin: 30px auto;
  padding: 0 30px;
  flex: 1;
  position: relative;
  z-index: 2;
}

/* 左侧面板 */
.left-panel {
  flex: 1;
  background-color: rgba(17, 23, 41, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  padding: 30px;
  box-shadow:
    0 8px 32px rgba(0, 240, 255, 0.15),
    inset 0 0 0 1px rgba(255, 255, 255, 0.1);
  max-height: calc(100vh - 250px);
  overflow-y: auto;
}

.panel-title {
  font-family: 'Orbitron', sans-serif;
  font-size: 1.5rem;
  font-weight: 600;
  color: white;
  margin-bottom: 8px;
  text-shadow: 0 0 10px rgba(0, 240, 255, 0.5);
}

.panel-subtitle {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 24px;
}

/* 拖拽区域 */
.drop-zone {
  background: rgba(255, 255, 255, 0.03);
  border: 2px dashed rgba(64, 150, 255, 0.5);
  border-radius: 12px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 24px;
}

.drop-zone:hover {
  border-color: rgba(64, 150, 255, 0.8);
  background: rgba(64, 150, 255, 0.05);
}

.drop-zone.drag-over {
  border-color: #00f0ff;
  background: rgba(0, 240, 255, 0.1);
  transform: scale(1.01);
}

.drop-zone-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.folder-icon {
  width: 56px;
  height: 56px;
  color: #4096ff;
  margin-bottom: 12px;
}

.folder-icon svg {
  width: 100%;
  height: 100%;
}

.drop-text {
  font-size: 16px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
  margin: 0 0 6px 0;
}

.drop-hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

/* 文件列表 */
.file-list {
  background: rgba(255, 255, 255, 0.02);
  border-radius: 12px;
  padding: 16px;
}

.file-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.file-list-header span {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
}

.clear-all-btn {
  background: none;
  border: none;
  color: #ff4d4f;
  font-size: 12px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.clear-all-btn:hover {
  background-color: rgba(255, 77, 79, 0.1);
}

.file-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
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
  width: 32px;
  height: 32px;
  color: #4096ff;
  margin-right: 10px;
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
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 150px;
}

.file-size {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 2px;
}

.file-progress {
  display: flex;
  align-items: center;
  width: 140px;
  margin: 0 12px;
  flex-shrink: 0;
}

.progress-bar {
  flex: 1;
  height: 5px;
  background-color: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
  margin-right: 8px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4096ff, #00f0ff);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
  width: 32px;
  text-align: right;
}

.delete-btn {
  width: 28px;
  height: 28px;
  background-color: rgba(255, 77, 79, 0.1);
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
  width: 14px;
  height: 14px;
}

.file-error {
  color: #ff4d4f;
  font-size: 12px;
  padding: 8px 12px;
  background-color: rgba(255, 77, 79, 0.1);
  border-radius: 6px;
  margin-top: 8px;
  word-break: break-all;
}

.empty-state {
  text-align: center;
  padding: 30px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
}

/* 右侧面板 */
.right-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.apps-container {
  display: flex;
  flex-direction: column;
  gap: 30px;
  width: 100%;
  max-width: 400px;
}

.app-card {
  width: 100%;
  background-color: rgba(17, 23, 41, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow:
    0 8px 32px rgba(0, 240, 255, 0.2),
    inset 0 0 0 1px rgba(255, 255, 255, 0.1);
  padding: 25px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.card-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(
    circle at center,
    rgba(var(--neon-blue-rgb), 0.1) 0%,
    transparent 70%
  );
  opacity: 0;
  transition: opacity 0.5s;
  pointer-events: none;
}

.app-card:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow:
    0 15px 50px rgba(0, 240, 255, 0.3),
    inset 0 0 0 1px rgba(0, 240, 255, 0.5);
}

.app-card:hover .card-glow {
  opacity: 1;
}

.app-icon {
  font-size: 3rem;
  margin-bottom: 15px;
  width: 70px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  position: relative;
  z-index: 1;
}

.love-icon {
  background: linear-gradient(135deg, #ff007a, #ff5722);
  box-shadow: 0 0 20px rgba(255, 0, 122, 0.5);
}

.robot-icon {
  background: linear-gradient(135deg, #00b2ff, #4f56ff);
  box-shadow: 0 0 20px rgba(0, 178, 255, 0.5);
}

.app-info {
  text-align: center;
  margin-bottom: 20px;
  width: 100%;
}

.app-title {
  font-family: 'Orbitron', sans-serif;
  font-size: 1.3rem;
  font-weight: bold;
  color: white;
  margin-bottom: 8px;
  text-shadow: 0 0 10px rgba(0, 240, 255, 0.5);
}

.app-desc {
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.7);
  line-height: 1.5;
}

.app-button {
  background: linear-gradient(90deg, #0088ff, #00b2ff);
  color: white;
  padding: 10px 24px;
  border-radius: 30px;
  font-weight: 500;
  transition: all 0.3s;
  margin-top: auto;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(0, 240, 255, 0.3);
}

.app-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.7s;
}

.app-button:hover {
  box-shadow: 0 0 15px rgba(0, 178, 255, 0.7);
  transform: scale(1.05);
}

.app-button:hover::before {
  left: 100%;
}

.btn-text {
  margin-right: 8px;
  letter-spacing: 1px;
}

.btn-icon {
  font-size: 1.1rem;
  transition: transform 0.3s;
}

.app-button:hover .btn-icon {
  transform: translateX(4px);
}

/* 背景圆圈动画 */
.cyber-circles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 1;
}

.circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.circle-1 {
  width: 300px;
  height: 300px;
  top: -100px;
  right: -100px;
  background: linear-gradient(135deg, var(--neon-blue), var(--neon-purple));
  animation: float 15s infinite alternate;
}

.circle-2 {
  width: 500px;
  height: 500px;
  bottom: -200px;
  left: -200px;
  background: linear-gradient(135deg, var(--neon-purple), var(--neon-pink));
  animation: float 20s infinite alternate-reverse;
}

.circle-3 {
  width: 200px;
  height: 200px;
  top: 40%;
  right: 15%;
  background: linear-gradient(135deg, var(--neon-pink), var(--neon-blue));
  animation: float 12s infinite alternate;
}

/* 动画效果 */
@keyframes float {
  0% {
    transform: translate(0, 0) rotate(0deg);
  }
  100% {
    transform: translate(50px, 50px) rotate(10deg);
  }
}

@keyframes glitch {
  0% {
    text-shadow:
      0 0 5px rgba(0, 240, 255, 0.7),
      0 0 10px rgba(0, 240, 255, 0.5);
  }
  50% {
    text-shadow:
      0 0 5px rgba(0, 240, 255, 0.7),
      0 0 10px rgba(0, 240, 255, 0.5),
      0 0 20px rgba(0, 240, 255, 0.3);
  }
  100% {
    text-shadow:
      0 0 5px rgba(0, 240, 255, 0.7),
      0 0 10px rgba(0, 240, 255, 0.5);
  }
}

@keyframes glitch-anim {
  0%, 100% {
    transform: translate(0);
  }
  20% {
    transform: translate(-5px, 5px);
  }
  40% {
    transform: translate(-5px, -5px);
  }
  60% {
    transform: translate(5px, 5px);
  }
  80% {
    transform: translate(5px, -5px);
  }
}

@keyframes glitch-anim-2 {
  0%, 100% {
    transform: translate(0);
  }
  20% {
    transform: translate(3px, -3px);
  }
  40% {
    transform: translate(3px, 3px);
  }
  60% {
    transform: translate(-3px, -3px);
  }
  80% {
    transform: translate(-3px, 3px);
  }
}

/* 滚动条样式 */
.left-panel::-webkit-scrollbar {
  width: 6px;
}

.left-panel::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 3px;
}

.left-panel::-webkit-scrollbar-thumb {
  background: rgba(0, 240, 255, 0.3);
  border-radius: 3px;
}

.left-panel::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 240, 255, 0.5);
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .left-panel {
    max-height: none;
  }

  .apps-container {
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .glitch-title {
    font-size: 2rem;
  }

  .subtitle {
    font-size: 0.9rem;
  }

  .main-content {
    padding: 0 15px;
    gap: 20px;
  }

  .left-panel {
    padding: 20px;
  }

  .app-card {
    padding: 20px;
  }

  .app-icon {
    font-size: 2.5rem;
    width: 60px;
    height: 60px;
  }

  .file-progress {
    width: 100px;
  }

  .file-name {
    max-width: 100px;
  }
}
</style>
