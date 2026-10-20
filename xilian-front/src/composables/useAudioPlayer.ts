import { ref } from 'vue'

/**
 * 全局唯一的音频播放器：同一时间只允许一条语音在播放，
 * 切换路由时也不会中断（模块级单例）。
 */
const playingKey = ref('')

let audioEl: HTMLAudioElement | null = null
/** 当前加载的 blob URL，用来判断是否可以直接 resume */
let currentUrl = ''

function ensureElement(): HTMLAudioElement {
  if (audioEl) return audioEl
  const element = new Audio()
  element.preload = 'auto'
  element.addEventListener('ended', () => {
    playingKey.value = ''
  })
  audioEl = element
  return element
}

export function useAudioPlayer() {
  function isPlaying(key: string): boolean {
    return playingKey.value === key
  }

  /**
   * 播放指定音频；同一个 key 且正在播放时表示暂停。
   * 注意：播完之后再点要能从头重播，否则 play() 会立刻 ended，表现为「点了没反应」。
   */
  async function toggle(key: string, url: string): Promise<void> {
    const element = ensureElement()

    if (playingKey.value === key) {
      element.pause()
      playingKey.value = ''
      return
    }

    if (currentUrl !== url) {
      element.src = url
      currentUrl = url
    } else if (element.ended || (Number.isFinite(element.duration) && element.currentTime >= element.duration - 0.05)) {
      // 已经播完 / 停在末尾：回到起点重新播放
      element.currentTime = 0
    }

    try {
      await element.play()
      playingKey.value = key
    } catch (error) {
      playingKey.value = ''
      throw error instanceof Error ? error : new Error('语音播放失败')
    }
  }

  function stop(): void {
    if (audioEl) {
      audioEl.pause()
      audioEl.currentTime = 0
    }
    playingKey.value = ''
  }

  return { playingKey, isPlaying, toggle, stop }
}
