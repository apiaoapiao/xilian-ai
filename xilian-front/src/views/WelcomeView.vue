<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

// 生成会话ID
const generateChatId = () => {
  const randomId = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15)
  return `xilian_${randomId}`
}

// 开始新会话
const startChat = () => {
  const chatId = generateChatId()
  router.push(`/chat?chatId=${chatId}`)
}
</script>

<template>
  <div class="welcome-page">
    <button class="start-button" @click="startChat">开始</button>
  </div>
</template>

<style scoped>
.welcome-page {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background-image: url('/src/assets/image.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  overflow-x: hidden;
}

.start-button {
  padding: 15px 30px;
  font-size: 20px;
  background-color: #ff69b4;
  color: white;
  border: none;
  border-radius: 25px;
  cursor: pointer;
  transition: background-color 0.3s;
  margin-top: 80vh; /* 页面高度的五分之四 */
}

.start-button:hover {
  background-color: #ff1493;
}
</style>