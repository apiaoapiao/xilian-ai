<script setup lang="ts">
import { computed, ref } from 'vue'
import avatarUrl from '@/assets/xilianai.png'
import type { ChatMessage } from '@/types/chat'
import { formatClock } from '@/utils/time'

const props = defineProps<{
  message: ChatMessage
  playing: boolean
}>()

const emit = defineEmits<{
  play: []
  retry: []
}>()

const copied = ref(false)

const isAssistant = computed(() => props.message.role === 'assistant')
const canPlay = computed(
  () => isAssistant.value && props.message.status === 'done' && props.message.content.trim().length > 0
)
const playLabel = computed(() => {
  if (props.message.audioState === 'loading') return '合成中'
  return props.playing ? '暂停' : '语音'
})

/** 复制回复内容；非安全上下文下剪贴板 API 不可用，退化为 execCommand */
async function handleCopy(): Promise<void> {
  const text = props.message.content
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const helper = document.createElement('textarea')
    helper.value = text
    helper.style.position = 'fixed'
    helper.style.opacity = '0'
    document.body.appendChild(helper)
    helper.select()
    try {
      document.execCommand('copy')
    } catch {
      // 忽略：部分浏览器禁用
    }
    document.body.removeChild(helper)
  }
  copied.value = true
  window.setTimeout(() => {
    copied.value = false
  }, 1500)
}
</script>

<template>
  <div class="message" :class="[message.role, { failed: message.status === 'error' }]">
    <img v-if="isAssistant" class="avatar" :src="avatarUrl" alt="" aria-hidden="true" />

    <div class="body">
      <div class="bubble">
        <template v-if="message.content">{{ message.content }}</template>
        <span v-else-if="message.status === 'streaming'" class="typing" aria-label="正在输入">
          <i></i><i></i><i></i>
        </span>
        <span v-else-if="message.status === 'aborted'" class="muted">已停止生成</span>
      </div>

      <p v-if="message.status === 'error'" class="error-tip">
        <span>{{ message.error || '回复失败' }}</span>
        <button type="button" class="link-btn" @click="emit('retry')">重试</button>
      </p>

      <div v-if="message.content || canPlay" class="meta">
        <span v-if="message.createdAt" class="time">{{ formatClock(message.createdAt) }}</span>

        <button
          v-if="canPlay"
          type="button"
          class="tool-btn"
          :class="{ active: playing }"
          :disabled="message.audioState === 'loading'"
          @click="emit('play')"
        >
          <span v-if="message.audioState === 'loading'" class="spinner" aria-hidden="true"></span>
          <svg v-else-if="playing" width="13" height="13" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <rect x="6" y="5" width="4" height="14" rx="1.4" />
            <rect x="14" y="5" width="4" height="14" rx="1.4" />
          </svg>
          <svg v-else width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M11 5 6 9H2v6h4l5 4V5z" />
            <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
            <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
          </svg>
          <span>{{ playLabel }}</span>
        </button>

        <span v-if="message.audioError" class="audio-error">{{ message.audioError }}</span>

        <button v-if="message.content" type="button" class="tool-btn" @click="handleCopy">
          {{ copied ? '已复制' : '复制' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 18px;
}

.message.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  flex: 0 0 auto;
  margin-top: 2px;
  box-shadow: 0 2px 8px rgba(214, 121, 160, 0.28);
}

.body {
  max-width: min(680px, 78%);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.message.user .body {
  align-items: flex-end;
}

.bubble {
  padding: 11px 15px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  text-align: left;
  user-select: text;
}

.message.assistant .bubble {
  background: var(--xl-surface);
  color: var(--xl-text);
  border: 1px solid var(--xl-border);
  border-top-left-radius: 6px;
  box-shadow: var(--xl-shadow-sm);
}

.message.user .bubble {
  background: linear-gradient(135deg, #ff8cc3, var(--xl-primary));
  color: #fff;
  border-top-right-radius: 6px;
  box-shadow: 0 6px 16px rgba(255, 111, 174, 0.28);
}

.message.failed .bubble {
  border-color: #f5c2d3;
  background: #fff5f9;
}

.muted {
  color: var(--xl-text-3);
  font-size: 13px;
}

.typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 20px;
}

.typing i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--xl-primary);
  opacity: 0.45;
  animation: blink 1.1s infinite ease-in-out;
}

.typing i:nth-child(2) {
  animation-delay: 0.18s;
}

.typing i:nth-child(3) {
  animation-delay: 0.36s;
}

@keyframes blink {
  0%,
  70%,
  100% {
    transform: translateY(0);
    opacity: 0.35;
  }
  35% {
    transform: translateY(-3px);
    opacity: 1;
  }
}

.error-tip {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #d6437b;
}

.link-btn {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: #d6437b;
  text-decoration: underline;
}

.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 4px;
  min-height: 18px;
}

.message.user .meta {
  flex-direction: row-reverse;
}

.time {
  font-size: 11px;
  color: var(--xl-text-3);
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: none;
  padding: 1px 2px;
  font-size: 11px;
  color: var(--xl-text-3);
  border-radius: 6px;
  transition: color 0.2s ease;
}

.tool-btn:hover:not(:disabled) {
  color: var(--xl-primary-dark);
}

.tool-btn.active {
  color: var(--xl-primary-dark);
}

.tool-btn:disabled {
  cursor: progress;
  opacity: 0.75;
}

.audio-error {
  font-size: 11px;
  color: #d6437b;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spinner {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  border: 1.6px solid var(--xl-primary-light);
  border-top-color: var(--xl-primary);
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .body {
    max-width: 84%;
  }

  .message.user .meta {
    flex-direction: row;
  }
}
</style>
