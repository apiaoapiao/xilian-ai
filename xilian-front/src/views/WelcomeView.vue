<script setup lang="ts">
import { useRouter } from 'vue-router'
import bgDesktop from '@/assets/welcome-bg-desktop.jpg'
import bgMobile from '@/assets/welcome-bg-mobile.jpg'
import { createChatId } from '@/utils/id'

const router = useRouter()

function startChat(): void {
  void router.push({ path: '/chat', query: { chatId: createChatId() } })
}
</script>

<template>
  <div
    class="welcome"
    :style="{
      '--bg-desktop': `url('${bgDesktop}')`,
      '--bg-mobile': `url('${bgMobile}')`
    }"
  >
    <div class="welcome-bottom">
      <div class="panel">
        <h1 class="brand">
          昔涟
          <span>记忆对话</span>
        </h1>
        <p class="tagline">翻开这页书，和记忆中的她说说话</p>
        <button type="button" class="start-btn" @click="startChat">
          开始对话
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M4.5 12h13" />
            <path d="M12 5.5 18.5 12 12 18.5" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.welcome {
  position: relative;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background-color: #2b1740;
  background-image: var(--bg-mobile);
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 底部半透明面板：无论背景画面多亮，文字都保持可读 */
.welcome::after {
  content: '';
  position: absolute;
  inset: auto 0 0 0;
  height: 38%;
  background: linear-gradient(to top, rgba(38, 14, 34, 0.5), rgba(38, 14, 34, 0));
  pointer-events: none;
}

.welcome-bottom {
  position: absolute;
  z-index: 1;
  inset: auto 0 max(7vh, 28px);
  display: flex;
  justify-content: center;
  padding: 0 20px calc(env(safe-area-inset-bottom));
}

.panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 440px;
  padding: 22px 26px 24px;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  background: rgba(44, 17, 38, 0.36);
  backdrop-filter: blur(16px) saturate(1.2);
  -webkit-backdrop-filter: blur(16px) saturate(1.2);
  box-shadow: 0 18px 48px rgba(30, 10, 25, 0.38);
  text-align: center;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: clamp(24px, 5vw, 30px);
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #fff;
  text-shadow: 0 4px 18px rgba(120, 40, 90, 0.55);
}

.brand span {
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.3em;
  color: rgba(255, 255, 255, 0.8);
}

.tagline {
  margin-bottom: 6px;
  font-size: 13px;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.88);
}

.start-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 13px 30px;
  border: none;
  border-radius: 999px;
  font-size: 16px;
  font-weight: 500;
  letter-spacing: 0.06em;
  color: #fff;
  background: linear-gradient(135deg, #ff9ac9, var(--xl-primary) 58%, #f0529a);
  box-shadow: 0 10px 26px rgba(255, 79, 150, 0.42), inset 0 1px 0 rgba(255, 255, 255, 0.4);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.start-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 32px rgba(255, 79, 150, 0.52), inset 0 1px 0 rgba(255, 255, 255, 0.45);
}

.start-btn:active {
  transform: translateY(0);
}

@media (min-width: 768px) {
  .welcome {
    background-image: var(--bg-desktop);
  }
}
</style>
