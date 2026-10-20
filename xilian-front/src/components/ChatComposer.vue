<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  /** 正在生成回复 */
  streaming: boolean
}>()

const emit = defineEmits<{
  send: [text: string]
  stop: []
}>()

const text = ref('')
const composing = ref(false)
const textareaEl = ref<HTMLTextAreaElement | null>(null)

const canSend = computed(() => text.value.trim().length > 0 && !props.streaming)

function resize(): void {
  const element = textareaEl.value
  if (!element) return
  element.style.height = 'auto'
  element.style.height = `${Math.min(element.scrollHeight, 160)}px`
}

function submit(): void {
  const value = text.value.trim()
  if (!value || props.streaming) return
  emit('send', value)
  text.value = ''
  void nextTick(() => {
    resize()
    textareaEl.value?.focus()
  })
}

/**
 * Enter 发送 / Shift + Enter 换行。
 * 中文输入法选词时的回车必须放行，否则拼音上屏会被误当成发送。
 */
function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey) return
  if (composing.value || event.isComposing) return
  event.preventDefault()
  submit()
}

function onCompositionStart(): void {
  composing.value = true
}

function onCompositionEnd(): void {
  composing.value = false
}

watch(text, () => {
  void nextTick(resize)
})

onMounted(() => {
  resize()
  textareaEl.value?.focus()
})
</script>

<template>
  <footer class="composer">
    <div class="composer-inner" :class="{ streaming }">
      <textarea
        ref="textareaEl"
        v-model="text"
        class="input"
        rows="1"
        placeholder="和昔涟说点什么…"
        :disabled="streaming"
        aria-label="消息输入框"
        @keydown="onKeydown"
        @compositionstart="onCompositionStart"
        @compositionend="onCompositionEnd"
      ></textarea>

      <button
        v-if="streaming"
        type="button"
        class="action stop"
        title="停止生成"
        aria-label="停止生成"
        @click="emit('stop')"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <rect x="6" y="6" width="12" height="12" rx="2.5" />
        </svg>
      </button>

      <button
        v-else
        type="button"
        class="action send"
        :disabled="!canSend"
        title="发送"
        aria-label="发送消息"
        @click="submit"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M4.5 12h13" />
          <path d="M12 5.5 18.5 12 12 18.5" />
        </svg>
      </button>
    </div>
    <p class="hint">Enter 发送 · Shift + Enter 换行</p>
  </footer>
</template>

<style scoped>
.composer {
  flex: 0 0 auto;
  padding: 10px 16px calc(12px + env(safe-area-inset-bottom));
  background: linear-gradient(to top, var(--xl-bg) 62%, rgba(255, 247, 251, 0));
}

.composer-inner {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 820px;
  margin: 0 auto;
  padding: 6px 6px 6px 14px;
  background: var(--xl-surface);
  border: 1px solid var(--xl-border);
  border-radius: 18px;
  box-shadow: var(--xl-shadow-sm);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.composer-inner:focus-within {
  border-color: #ffb3d3;
  box-shadow: 0 6px 20px rgba(255, 111, 174, 0.18);
}

.input {
  flex: 1;
  min-height: 24px;
  max-height: 160px;
  padding: 7px 0;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-size: 15px;
  line-height: 1.55;
  color: var(--xl-text);
}

.input::placeholder {
  color: #c3aeb9;
}

.action {
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, #ff8cc3, var(--xl-primary));
  box-shadow: 0 4px 12px rgba(255, 111, 174, 0.32);
  transition: transform 0.18s ease, opacity 0.18s ease;
}

.action:hover:not(:disabled) {
  transform: translateY(-1px);
}

.action:active:not(:disabled) {
  transform: translateY(0);
}

.action:disabled {
  opacity: 0.45;
  box-shadow: none;
  cursor: not-allowed;
}

.action.stop {
  background: #f2e3ea;
  color: #b26a8d;
  box-shadow: none;
}

.hint {
  max-width: 820px;
  margin: 6px auto 0;
  font-size: 11px;
  color: var(--xl-text-3);
  text-align: center;
}

@media (max-width: 640px) {
  .composer {
    padding-left: 10px;
    padding-right: 10px;
  }

  .hint {
    display: none;
  }
}
</style>
