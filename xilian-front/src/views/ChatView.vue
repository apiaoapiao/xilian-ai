<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import avatarUrl from '@/assets/xilianai.png'
import { toErrorMessage } from '@/api/http'
import ChatComposer from '@/components/ChatComposer.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import SessionSidebar from '@/components/SessionSidebar.vue'
import { useAudioPlayer } from '@/composables/useAudioPlayer'
import { useChatStore } from '@/composables/useChatStore'
import type { ChatMessage } from '@/types/chat'

const route = useRoute()
const router = useRouter()
const {
  conversations,
  activeId,
  activeMessages,
  isStreaming,
  isLoadingList,
  listError,
  historyError,
  loadingChats,
  ensureList,
  refreshList,
  activate,
  createConversation,
  sendMessage,
  stopStreaming,
  retry,
  getSpeechUrl
} = useChatStore()
const { isPlaying, toggle: toggleAudio, stop: stopAudio } = useAudioPlayer()

const drawerOpen = ref(false)
const scrollerEl = ref<HTMLElement | null>(null)
const pinned = ref(true)

const suggestions = ['今天过得怎么样呀？', '给我讲讲你的故事吧', '陪我聊聊天好吗？']

const loadingHistory = computed(() => Boolean(loadingChats[activeId.value]))
const isEmpty = computed(() => !loadingHistory.value && activeMessages.value.length === 0)

/** 用「消息条数 + 末条内容长度」作为滚动信号，避免深度 watch 整个列表 */
const scrollSignal = computed(() => {
  const list = activeMessages.value
  const last = list[list.length - 1]
  return `${list.length}|${last?.content.length ?? 0}|${last?.status ?? ''}`
})

function scrollToBottom(smooth = false): void {
  const element = scrollerEl.value
  if (!element) return
  element.scrollTo({ top: element.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  pinned.value = true
}

function onScroll(): void {
  const element = scrollerEl.value
  if (!element) return
  pinned.value = element.scrollHeight - element.scrollTop - element.clientHeight < 80
}

// ---- 路由 <-> 会话同步 ----
function readChatIdFromRoute(): string {
  const raw = route.query.chatId
  if (typeof raw === 'string') return raw
  if (Array.isArray(raw)) return raw[0] ?? ''
  return ''
}

async function syncFromRoute(replaceWhenMissing = false): Promise<void> {
  const id = readChatIdFromRoute()
  if (!id) {
    const newId = createConversation()
    const target = { path: '/chat', query: { chatId: newId } }
    await (replaceWhenMissing ? router.replace(target) : router.push(target))
    return
  }
  await activate(id)
}

async function startNewChat(): Promise<void> {
  drawerOpen.value = false
  const id = createConversation()
  await router.push({ path: '/chat', query: { chatId: id } })
}

async function selectConversation(id: string): Promise<void> {
  drawerOpen.value = false
  if (id === activeId.value) return
  await router.push({ path: '/chat', query: { chatId: id } })
}

async function goHome(): Promise<void> {
  stopAudio()
  await router.push('/')
}

// ---- 交互 ----
async function handleSend(text: string): Promise<void> {
  // 极少数情况下（首屏 URL 里还没有 chatId）先不发送，避免建出「孤儿会话」
  if (!activeId.value) return
  await sendMessage(text)
}

async function handlePlay(message: ChatMessage): Promise<void> {
  // 合成中重复点击直接忽略（同一个气泡只跑一个合成）
  if (message.audioState === 'loading') return

  if (isPlaying(message.id)) {
    stopAudio()
    return
  }

  let url = message.audioUrl
  if (!url) {
    message.audioState = 'loading'
    message.audioError = ''
    try {
      url = await getSpeechUrl(message.content, activeId.value)
      message.audioUrl = url
      message.audioState = 'ready'
    } catch (error) {
      message.audioState = 'failed'
      message.audioError = toErrorMessage(error) || '语音合成失败'
      return
    }
  }

  try {
    await toggleAudio(message.id, url)
    message.audioError = ''
  } catch {
    // 关键：播放失败（常见于合成耗时过长导致浏览器的用户手势过期）
    // 不能丢掉已经合成好的音频，否则下一次点击又会重新合成一遍。
    message.audioState = 'ready'
    message.audioError = '浏览器拦截了自动播放，点一次即可播放'
  }
}

async function handleRetry(message: ChatMessage): Promise<void> {
  await retry(activeId.value, message.id)
}

watch(scrollSignal, async () => {
  await nextTick()
  if (pinned.value) scrollToBottom()
})

// 切换会话时重新贴底，避免停留在上一个会话的滚动位置
watch(activeId, async () => {
  pinned.value = true
  await nextTick()
  scrollToBottom()
})

watch(
  () => route.query.chatId,
  () => {
    void syncFromRoute()
  }
)

onMounted(() => {
  void ensureList()
  void syncFromRoute(true)
  void nextTick(() => scrollToBottom())
})
</script>

<template>
  <div class="chat-page">
    <SessionSidebar
      :conversations="conversations"
      :active-id="activeId"
      :loading="isLoadingList"
      :error="listError"
      :class="{ open: drawerOpen }"
      @new-chat="startNewChat"
      @select="selectConversation"
      @refresh="refreshList"
      @close="drawerOpen = false"
    />

    <div v-if="drawerOpen" class="backdrop" @click="drawerOpen = false"></div>

    <section class="main">
      <header class="topbar">
        <button type="button" class="icon-btn" aria-label="返回首页" title="返回首页" @click="goHome">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M15 18 9 12l6-6" />
          </svg>
        </button>

        <button type="button" class="icon-btn menu" aria-label="会话列表" @click="drawerOpen = true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" aria-hidden="true">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
        </button>

        <h1 class="title">昔涟</h1>

        <span class="status" :class="{ live: isStreaming }">
          {{ isStreaming ? '正在回答…' : '记忆对话' }}
        </span>
      </header>

      <div ref="scrollerEl" class="scroller" @scroll.passive="onScroll">
        <div class="stream">
          <p v-if="historyError" class="banner">
            <span>{{ historyError }}</span>
            <button type="button" @click="syncFromRoute()">重试</button>
          </p>

          <div v-if="loadingHistory" class="skeleton-list">
            <div v-for="n in 3" :key="`m-skeleton-${n}`" class="bubble-skeleton" :class="{ right: n % 2 === 0 }"></div>
          </div>

          <div v-else-if="isEmpty" class="empty">
            <img class="empty-avatar" :src="avatarUrl" alt="昔涟头像" />
            <h2>在这里，记忆不会消散</h2>
            <p>我是昔涟。今天想和我说些什么呢？</p>
            <div class="chips">
              <button v-for="item in suggestions" :key="item" type="button" class="chip" @click="handleSend(item)">
                {{ item }}
              </button>
            </div>
          </div>

          <template v-else>
            <MessageBubble
              v-for="message in activeMessages"
              :key="message.id"
              :message="message"
              :playing="isPlaying(message.id)"
              @play="handlePlay(message)"
              @retry="handleRetry(message)"
            />
          </template>
        </div>
      </div>

      <button v-if="!pinned" type="button" class="to-bottom" aria-label="回到底部" @click="scrollToBottom(true)">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 5v14" />
          <path d="M19 12l-7 7-7-7" />
        </svg>
      </button>

      <ChatComposer :streaming="isStreaming" @send="handleSend" @stop="stopStreaming" />
    </section>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--xl-bg);
}

.main {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.topbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 56px;
  padding: 0 12px;
  background: rgba(255, 247, 251, 0.86);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--xl-border);
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: none;
  color: var(--xl-text-2);
  transition: background-color 0.18s ease, color 0.18s ease;
}

.icon-btn:hover {
  background: rgba(255, 111, 174, 0.12);
  color: var(--xl-primary-dark);
}

.menu {
  display: none;
}

.title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--xl-text);
}

.status {
  margin-left: auto;
  font-size: 11.5px;
  color: #b0628c;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 111, 174, 0.14);
}

.status.live {
  color: #fff;
  background: var(--xl-primary);
}

.scroller {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scroll-behavior: auto;
  position: relative;
}

.stream {
  max-width: 820px;
  min-height: 100%;
  margin: 0 auto;
  padding: 20px 18px 8px;
  display: flex;
  flex-direction: column;
}

.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 0 0 14px;
  padding: 9px 12px;
  border-radius: 12px;
  background: #fff5f9;
  border: 1px solid #f5c2d3;
  font-size: 12.5px;
  color: #d6437b;
}

.banner button {
  border: none;
  background: none;
  padding: 0;
  font-size: 12.5px;
  color: #d6437b;
  text-decoration: underline;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin: auto 0;
  padding: 12px 8px 5vh;
}

.empty-avatar {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 10px 26px rgba(214, 121, 160, 0.32);
}

.empty h2 {
  margin: 16px 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: var(--xl-text);
}

.empty p {
  margin: 0;
  font-size: 13.5px;
  color: var(--xl-text-3);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
}

.chip {
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--xl-border);
  background: var(--xl-surface);
  font-size: 12.5px;
  color: var(--xl-text-2);
  box-shadow: var(--xl-shadow-sm);
  transition: transform 0.18s ease, border-color 0.18s ease, color 0.18s ease;
}

.chip:hover {
  transform: translateY(-1px);
  border-color: #ffc9df;
  color: var(--xl-primary-dark);
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 10px;
}

.bubble-skeleton {
  height: 54px;
  width: min(70%, 460px);
  border-radius: 16px;
  background: linear-gradient(90deg, #f7e7ef, #fff, #f7e7ef);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite linear;
}

.bubble-skeleton.right {
  align-self: flex-end;
  background: linear-gradient(90deg, #ffe1ee, #fff2f8, #ffe1ee);
  background-size: 200% 100%;
}

@keyframes shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

.to-bottom {
  position: absolute;
  right: 22px;
  bottom: 104px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--xl-border);
  border-radius: 50%;
  background: var(--xl-surface);
  color: var(--xl-text-2);
  box-shadow: var(--xl-shadow-md);
}

.to-bottom:hover {
  color: var(--xl-primary-dark);
}

.backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
  background: rgba(52, 26, 38, 0.32);
  backdrop-filter: blur(1px);
}

@media (max-width: 860px) {
  .menu {
    display: inline-flex;
  }

  .stream {
    padding: 14px 12px 4px;
  }
}
</style>
