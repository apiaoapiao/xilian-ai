<script setup lang="ts">
import avatarUrl from '@/assets/xilianai.png'
import type { Conversation } from '@/types/chat'
import { formatRelativeTime } from '@/utils/time'

defineProps<{
  conversations: Conversation[]
  activeId: string
  loading: boolean
  error: string
}>()

const emit = defineEmits<{
  newChat: []
  select: [id: string]
  refresh: []
  close: []
}>()
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <img class="avatar" :src="avatarUrl" alt="昔涟头像" />
      <div class="brand-text">
        <strong>昔涟</strong>
        <span>记忆对话</span>
      </div>
      <button type="button" class="icon-btn close" aria-label="关闭会话列表" @click="emit('close')">×</button>
    </div>

    <button type="button" class="new-chat" @click="emit('newChat')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
        stroke-linecap="round" aria-hidden="true">
        <path d="M12 5v14M5 12h14" />
      </svg>
      <span>开启新对话</span>
    </button>

    <div class="list-head">
      <span>历史会话</span>
      <button
        type="button"
        class="refresh"
        :class="{ spinning: loading }"
        :disabled="loading"
        aria-label="刷新会话列表"
        title="刷新"
        @click="emit('refresh')"
      >
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M21 12a9 9 0 1 1-2.64-6.36" />
          <path d="M21 4v5h-5" />
        </svg>
      </button>
    </div>

    <p v-if="error" class="error">
      <span>{{ error }}</span>
      <button type="button" @click="emit('refresh')">重试</button>
    </p>

    <div class="list">
      <template v-if="loading && conversations.length === 0">
        <div v-for="n in 4" :key="`skeleton-${n}`" class="skeleton">
          <span class="skeleton-line"></span>
          <span class="skeleton-line short"></span>
        </div>
      </template>

      <button
        v-for="conversation in conversations"
        :key="conversation.id"
        type="button"
        class="item"
        :class="{ active: conversation.id === activeId }"
        @click="emit('select', conversation.id)"
      >
        <span class="item-title">{{ conversation.title || '新对话' }}</span>
        <span class="item-time">{{ formatRelativeTime(conversation.updatedAt) }}</span>
      </button>

      <p v-if="!loading && conversations.length === 0" class="empty">还没有历史会话，先开启一段新的对话吧</p>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 268px;
  flex: 0 0 268px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: linear-gradient(180deg, #fff4f9, #fde8f2);
  border-right: 1px solid var(--xl-border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 16px 12px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 4px 12px rgba(214, 121, 160, 0.3);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  flex: 1;
  min-width: 0;
}

.brand-text strong {
  font-size: 16px;
  color: var(--xl-text);
}

.brand-text span {
  font-size: 11px;
  color: var(--xl-text-3);
  letter-spacing: 0.06em;
}

.icon-btn {
  border: none;
  background: none;
  font-size: 20px;
  line-height: 1;
  color: var(--xl-text-2);
  width: 28px;
  height: 28px;
  border-radius: 8px;
}

.icon-btn:hover {
  background: rgba(255, 111, 174, 0.12);
}

.close {
  display: none;
}

.new-chat {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0 14px 14px;
  padding: 10px 14px;
  border: none;
  border-radius: 14px;
  font-size: 14px;
  color: #fff;
  background: linear-gradient(135deg, #ff8cc3, var(--xl-primary));
  box-shadow: 0 6px 16px rgba(255, 111, 174, 0.3);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.new-chat:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(255, 111, 174, 0.4);
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px 8px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--xl-text-3);
}

.refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 7px;
  background: none;
  color: var(--xl-text-3);
}

.refresh:hover:not(:disabled) {
  color: var(--xl-primary-dark);
  background: rgba(255, 111, 174, 0.12);
}

.refresh.spinning svg {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  width: 100%;
  padding: 9px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: none;
  text-align: left;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.item:hover {
  background: rgba(255, 255, 255, 0.75);
}

.item.active {
  background: #fff;
  border-color: #ffc9df;
  box-shadow: 0 4px 12px rgba(214, 121, 160, 0.15);
}

.item-title {
  width: 100%;
  font-size: 13.5px;
  color: var(--xl-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-time {
  font-size: 11px;
  color: var(--xl-text-3);
}

.item-time:empty {
  display: none;
}

.empty {
  margin: 18px 8px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--xl-text-3);
  text-align: center;
}

.error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 14px 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #fff5f9;
  border: 1px solid #f5c2d3;
  font-size: 11.5px;
  color: #d6437b;
}

.error button {
  border: none;
  background: none;
  padding: 0;
  font-size: 11.5px;
  color: #d6437b;
  text-decoration: underline;
}

.skeleton {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 9px 12px;
}

.skeleton-line {
  display: block;
  height: 10px;
  width: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.6), #fff, rgba(255, 255, 255, 0.6));
  background-size: 200% 100%;
  animation: shimmer 1.3s infinite linear;
}

.skeleton-line.short {
  width: 40%;
}

@keyframes shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 860px) {
  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 30;
    width: min(84vw, 300px);
    flex-basis: auto;
    transform: translateX(-102%);
    transition: transform 0.26s ease;
    box-shadow: 8px 0 30px rgba(120, 60, 90, 0.18);
  }

  .sidebar.open {
    transform: translateX(0);
  }

  .close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
