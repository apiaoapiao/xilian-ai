<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const route = useRoute()

// 状态管理
const chatId = ref<string>('')
const message = ref('')
// 为每个会话存储独立的消息列表
const sessionMessages = ref<Map<string, Array<{ type: 'user' | 'ai'; content: string; audio?: string; audioLoading?: boolean }>>>(new Map())
// 为每个会话存储最近5条语音的二进制数据
const audioCache = ref<Map<string, Array<{ content: string; audio: Blob }>>>(new Map())
const sessions = ref<Array<{ id: string; lastMessage: string; timestamp: Date }>>([])

// 计算属性，获取当前会话的消息列表
const messages = computed(() => {
  return sessionMessages.value.get(chatId.value) || []
})

// 返回首页
const goBack = () => {
  router.push('/')
}

// 生成会话ID
const generateChatId = () => {
  const randomId = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15)
  return `xilian_${randomId}`
}

// 创建新对话
const createNewConversation = () => {
  const newChatId = generateChatId()
  chatId.value = newChatId
  sessionMessages.value.set(newChatId, [])
  
  // 添加到会话列表
  sessions.value.unshift({
    id: newChatId,
    lastMessage: '',
    timestamp: new Date()
  })
  
  // 更新URL参数
  router.push({ query: { chatId: newChatId } })
}

// 切换会话
const switchSession = async (sessionId: string) => {
  chatId.value = sessionId
  router.push({ query: { chatId: sessionId } })
  
  try {
    // 调用后端接口获取历史对话
    const response = await axios.get('http://localhost:8123/api/text/memory', {
      params: { chatId: sessionId }
    })
    
    // 获取当前会话的音频缓存
    const sessionCache = audioCache.value.get(sessionId) || []
    
    // 将后端返回的消息格式转换为前端需要的格式，并恢复音频信息
    const formattedMessages = response.data.map((msg: any) => {
      const isAiMessage = msg.messageType === 'ASSISTANT'
      const message: { type: 'user' | 'ai'; content: string; audio?: string; audioLoading?: boolean } = {
        type: isAiMessage ? 'ai' : 'user',
        content: msg.text,
        audio: undefined,
        audioLoading: false
      }
      
      // 如果是AI消息，检查是否有对应的音频缓存
      if (isAiMessage) {
        const cachedAudio = sessionCache.find(item => item.content === msg.text)
        if (cachedAudio) {
          // 恢复音频URL
          message.audio = URL.createObjectURL(cachedAudio.audio)
        }
      }
      
      return message
    })
    
    // 更新会话消息列表
    sessionMessages.value.set(sessionId, formattedMessages)
    
  } catch (error) {
    console.error('获取历史对话失败:', error)
    // 如果获取失败，确保当前会话有一个空的消息列表
    if (!sessionMessages.value.has(sessionId)) {
      sessionMessages.value.set(sessionId, [])
    }
  }
}

// 发送消息
const sendMessage = async () => {
  if (!message.value.trim()) return
  
  // 添加用户消息
  const userMessage = message.value.trim()
  // 确保当前会话有消息列表
  if (!sessionMessages.value.has(chatId.value)) {
    sessionMessages.value.set(chatId.value, [])
  }
  // 获取当前会话的消息列表
  const currentMessages = sessionMessages.value.get(chatId.value) || []
  currentMessages.push({ type: 'user', content: userMessage })
  message.value = ''
  
  // 更新会话列表
  const sessionIndex = sessions.value.findIndex(s => s.id === chatId.value)
  if (sessionIndex !== -1) {
    const session = sessions.value[sessionIndex]
    if (session) {
      session.lastMessage = userMessage
      session.timestamp = new Date()
    }
  }
  
  try {
    // 添加AI消息占位符
    const currentMessages = sessionMessages.value.get(chatId.value) || []
    const aiMessageIndex = currentMessages.length
    currentMessages.push({ 
      type: 'ai', 
      content: '', 
      audioLoading: true 
    })
    
    // 调用流式对话接口
    const response = await fetch(`http://localhost:8123/api/ai/chat/stream?message=${encodeURIComponent(userMessage)}&chatId=${chatId.value}`)
    
    if (!response.ok) {
      throw new Error('Network response was not ok')
    }
    
    // 获取可读流
    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('No readable stream')
    }
    
    const decoder = new TextDecoder('utf-8')
    let accumulatedResponse = ''
    
    // 逐块读取数据
    while (true) {
      const { done, value } = await reader.read()
      
      if (done) {
        break
      }
      
      // 解码数据
      const chunk = decoder.decode(value, { stream: true })
      
      // 处理每一行
      const lines = chunk.split('\n')
      for (const line of lines) {
        if (line.trim() === '') continue
        
        // 去除 "data:" 前缀
        let cleanedLine = line.trim()
        if (cleanedLine.startsWith('data:')) {
          cleanedLine = cleanedLine.substring(5).trim()
        }
        
        // 累积响应
        accumulatedResponse += cleanedLine
        
        // 更新UI - 使用Vue的响应式更新机制
        const currentMessages = sessionMessages.value.get(chatId.value) || []
        const originalMessage = currentMessages[aiMessageIndex]
        if (originalMessage) {
          currentMessages[aiMessageIndex] = { 
            ...originalMessage, 
            content: accumulatedResponse 
          }
          // 触发Vue的响应式更新
          sessionMessages.value.set(chatId.value, [...currentMessages])
        }
      }
    }
    
    // 调用TTS接口，确保去除前缀
    await synthesizeAudio(accumulatedResponse, aiMessageIndex)
    
  } catch (error) {
    console.error('对话失败:', error)
    messages.value.push({ type: 'ai', content: '对不起，对话失败了，请稍后重试。' })
  }
}

// 语音合成
const synthesizeAudio = async (text: string, messageIndex: number) => {
  try {
    // 检查缓存中是否有相同内容的语音
    const sessionCache = audioCache.value.get(chatId.value) || []
    const cachedAudio = sessionCache.find(item => item.content === text)
    
    if (cachedAudio) {
      // 使用缓存的音频数据
      const audioUrl = URL.createObjectURL(cachedAudio.audio)
      // 直接从sessionMessages获取消息列表并更新，确保Vue能检测到变化
      const currentMessages = sessionMessages.value.get(chatId.value) || []
      if (currentMessages[messageIndex]) {
        currentMessages[messageIndex].audio = audioUrl
        currentMessages[messageIndex].audioLoading = false
        // 触发Vue的响应式更新
        sessionMessages.value.set(chatId.value, [...currentMessages])
      }
      return
    }
    
    // 调用TTS接口
    const response = await axios.post('http://localhost:8123/api/tts/synthesize', 
      { text },
      { responseType: 'blob' }
    )
    
    // 缓存音频数据
    const audioBlob = response.data
    const newCacheItem = { content: text, audio: audioBlob }
    
    // 限制每个会话最多缓存5条语音
    const updatedCache = [newCacheItem, ...sessionCache].slice(0, 5)
    audioCache.value.set(chatId.value, updatedCache)
    
    // 创建音频URL
    const audioUrl = URL.createObjectURL(audioBlob)
    // 直接从sessionMessages获取消息列表并更新，确保Vue能检测到变化
    const currentMessages = sessionMessages.value.get(chatId.value) || []
    if (currentMessages[messageIndex]) {
      currentMessages[messageIndex].audio = audioUrl
      currentMessages[messageIndex].audioLoading = false
      // 触发Vue的响应式更新
      sessionMessages.value.set(chatId.value, [...currentMessages])
    }
    
  } catch (error) {
    console.error('语音合成失败:', error)
    // 直接从sessionMessages获取消息列表并更新，确保Vue能检测到变化
    const currentMessages = sessionMessages.value.get(chatId.value) || []
    if (currentMessages[messageIndex]) {
      currentMessages[messageIndex].audioLoading = false
      // 触发Vue的响应式更新
      sessionMessages.value.set(chatId.value, [...currentMessages])
    }
  }
}

// 播放语音
let currentAudio: HTMLAudioElement | null = null

const playAudio = (audioUrl: string) => {
  // 停止当前正在播放的语音
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.currentTime = 0
  }
  
  // 创建新的音频对象并播放
  currentAudio = new Audio(audioUrl)
  currentAudio.play().catch(error => {
    console.error('语音播放失败:', error)
  })
}

// 获取会话的最后一条消息和时间戳
const getSessionInfo = async (sessionId: string) => {
  try {
    const response = await axios.get('http://localhost:8123/api/text/memory', {
      params: { chatId: sessionId }
    })
    
    const messages = response.data
    if (messages && messages.length > 0) {
      // 获取最后一条消息
      const lastMessage = messages[messages.length - 1]
      // 从文件名中解析时间戳（假设文件名格式为 xilian_时间戳_...）
      const timestampMatch = sessionId.match(/xilian_(\d+)/)
      const timestamp = timestampMatch && timestampMatch[1] ? new Date(parseInt(timestampMatch[1])) : new Date()
      
      return {
        id: sessionId,
        lastMessage: lastMessage.text || '新会话',
        timestamp: timestamp
      }
    }
  } catch (error) {
    console.error(`获取会话 ${sessionId} 信息失败:`, error)
  }
  
  // 默认值
  return {
    id: sessionId,
    lastMessage: '新会话',
    timestamp: new Date()
  }
}

// 组件挂载时初始化
onMounted(async () => {
  // 获取URL中的chatId，如果没有则生成新的
  const urlChatId = route.query.chatId as string
  chatId.value = urlChatId || generateChatId()
  
  try {
    // 调用后端接口获取所有历史对话ID
    const response = await axios.get('http://localhost:8123/api/text/filename')
    const chatIds = response.data || []
    
    // 清空当前会话列表
    sessions.value = []
    
    // 为每个聊天ID获取会话信息并添加到会话列表
    for (const sessionId of chatIds) {
      const sessionInfo = await getSessionInfo(sessionId)
      sessions.value.push(sessionInfo)
    }
    
    // 按时间戳排序，最新的会话排在前面
    sessions.value.sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime())
    
    // 确保当前会话也在列表中
    if (chatId.value && !sessions.value.some(s => s.id === chatId.value)) {
      const currentSessionInfo = await getSessionInfo(chatId.value)
      sessions.value.unshift(currentSessionInfo)
    }
  } catch (error) {
    console.error('获取历史对话ID列表失败:', error)
    
    // 如果获取失败，确保当前会话有一个空的消息列表
    if (!sessionMessages.value.has(chatId.value)) {
      sessionMessages.value.set(chatId.value, [])
    }
    
    // 添加当前会话到会话列表
    if (!sessions.value.some(s => s.id === chatId.value)) {
      sessions.value.unshift({
        id: chatId.value,
        lastMessage: '',
        timestamp: new Date()
      })
    }
  }
  
  // 加载当前会话的历史消息
  try {
    const response = await axios.get('http://localhost:8123/api/text/memory', {
      params: { chatId: chatId.value }
    })
    
    // 获取当前会话的音频缓存
    const sessionCache = audioCache.value.get(chatId.value) || []
    
    // 将后端返回的消息格式转换为前端需要的格式，并恢复音频信息
    const formattedMessages = response.data.map((msg: any) => {
      const isAiMessage = msg.messageType === 'ASSISTANT'
      const message: { type: 'user' | 'ai'; content: string; audio?: string; audioLoading?: boolean } = {
        type: isAiMessage ? 'ai' : 'user',
        content: msg.text,
        audio: undefined,
        audioLoading: false
      }
      
      // 如果是AI消息，检查是否有对应的音频缓存
      if (isAiMessage) {
        const cachedAudio = sessionCache.find(item => item.content === msg.text)
        if (cachedAudio) {
          // 恢复音频URL
          message.audio = URL.createObjectURL(cachedAudio.audio)
        }
      }
      
      return message
    })
    
    // 更新会话消息列表
    sessionMessages.value.set(chatId.value, formattedMessages)
    
  } catch (error) {
    console.error('获取当前会话历史对话失败:', error)
    // 如果获取失败，确保当前会话有一个空的消息列表
    if (!sessionMessages.value.has(chatId.value)) {
      sessionMessages.value.set(chatId.value, [])
    }
  }
})
</script>

<template>
  <div class="chat-page">
    <!-- 顶部导航栏 -->
    <div class="chat-header">
      <button class="back-button" @click="goBack">←</button>
      <h1>昔涟</h1>
      <div class="header-right"></div>
    </div>
    
    <!-- 聊天内容区域 -->
    <div class="chat-content">
      <!-- 左侧会话列表 -->
      <div class="session-list">
        <div class="avatar">
          <img src="/src/assets/xilianai.png" class="avatar-img" alt="昔涟AI">
        </div>
        <button class="new-conversation-btn" @click="createNewConversation">
          开启新对话
        </button>
        <div class="sessions">
          <div 
            v-for="session in sessions" 
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === chatId }"
            @click="switchSession(session.id)"
          >
            <div class="session-message">{{ session.lastMessage || '新会话' }}</div>
            <div class="session-time">{{ new Date(session.timestamp).toLocaleTimeString() }}</div>
          </div>
        </div>
      </div>
      
      <!-- 右侧对话区域 -->
      <div class="message-area">
        <div class="messages">
          <div 
            v-for="(msg, index) in messages" 
            :key="index"
            class="message"
            :class="msg.type"
          >
            <div class="message-content">{{ msg.content }}</div>
            <div v-if="msg.type === 'ai'" class="message-audio">
              <div v-if="msg.audioLoading" class="audio-progress">
                <div class="progress-bar"></div>
              </div>
              <button 
                v-else-if="msg.audio" 
                class="play-button"
                @click="playAudio(msg.audio)"
                title="播放语音"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 5L6 9H2v6h4l5 4V5z"></path>
                  <path d="M15.54 8.46a5 5 0 0 1 0 7.07"></path>
                  <path d="M19.07 4.93a10 10 0 0 1 0 14.14"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>
        
        <!-- 消息输入区域 -->
        <div class="input-area">
          <input 
            type="text" 
            v-model="message" 
            placeholder="输入消息..."
            @keyup.enter="sendMessage"
          />
          <button @click="sendMessage">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶部导航栏 - 高度缩小 */
.chat-header {
  background-color: #ff69b4;
  color: white;
  padding: 10px 15px;
  text-align: center;
  font-size: 18px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  box-sizing: border-box;
}

.back-button {
  background: none;
  border: none;
  color: white;
  font-size: 20px;
  cursor: pointer;
  padding: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  transition: background-color 0.2s;
}

.back-button:hover {
  background-color: rgba(255, 255, 255, 0.2);
}

.header-right {
  width: 30px;
}

.chat-header h1 {
  margin: 0;
  font-size: 18px;
}

.chat-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 会话列表样式 */
.session-list {
  width: 220px;
  background-color: #f8f8f8;
  border-right: 1px solid #ddd;
  display: flex;
  flex-direction: column;
}

.avatar {
  padding: 15px;
  display: flex;
  justify-content: center;
}

.avatar-img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
}

/* 新对话按钮样式 */
.new-conversation-btn {
  margin: 0 20px 15px;
  padding: 10px;
  background-color: #ff69b4;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s;
  box-shadow: 0 2px 5px rgba(255, 105, 180, 0.3);
}

.new-conversation-btn:hover {
  background-color: #ff1493;
}

.sessions {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  padding: 15px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  transition: background-color 0.2s;
}

.session-item:hover {
  background-color: #e8e8e8;
}

.session-item.active {
  background-color: #ffb6c1;
}

.session-message {
  font-size: 14px;
  margin-bottom: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 12px;
  color: #999;
}

/* 消息区域样式 */
.message-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: white;
  overflow: hidden;
}

.messages {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
  text-align: left;
}

/* 输入区域样式 */
.input-area {
  padding: 20px;
  border-top: 1px solid #ddd;
  display: flex;
  gap: 10px;
  background-color: white;
}

.message {
  margin-bottom: 20px;
  display: flex;
  max-width: 70%;
}

.message.user {
  margin-left: auto;
  justify-content: flex-end;
}

.message.ai {
  margin-right: auto;
  justify-content: flex-start;
}

.message-content {
  padding: 10px 15px;
  border-radius: 18px;
  line-height: 1.4;
  text-align: left;
}

.message.user .message-content {
  background-color: #007bff;
  color: white;
  border-bottom-right-radius: 4px;
}

.message.ai .message-content {
  background-color: #f0f0f0;
  color: black;
  border-bottom-left-radius: 4px;
}

/* 语音相关样式 */
.message-audio {
  margin-top: 5px;
  padding: 0 15px;
}

.audio-progress {
  width: 100px;
  height: 4px;
  background-color: #e0e0e0;
  border-radius: 2px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background-color: #ff69b4;
  animation: progress 2s infinite;
}

@keyframes progress {
  0% { width: 0%; }
  50% { width: 50%; }
  100% { width: 100%; }
}

.play-button {
  background-color: #ff69b4;
  color: white;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  font-size: 14px;
  padding: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.3s, transform 0.2s;
  box-shadow: 0 2px 5px rgba(255, 105, 180, 0.3);
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.play-button:hover {
  background-color: #ff1493;
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(255, 105, 180, 0.4);
}

.play-button:active {
  transform: translateY(0);
}

.play-button svg {
  width: 18px;
  height: 18px;
}


/* 输入区域样式 */
.input-area {
  padding: 20px;
  border-top: 1px solid #ddd;
  display: flex;
  gap: 10px;
}

.input-area input {
  flex: 1;
  padding: 10px 15px;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 14px;
}

.input-area button {
  padding: 10px 20px;
  background-color: #ff69b4;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s;
}

.input-area button:hover {
  background-color: #ff1493;
}
</style>