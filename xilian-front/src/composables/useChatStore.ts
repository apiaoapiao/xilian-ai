import { computed, reactive, ref } from 'vue'
import { fetchHistory, listChatIds, streamChat, synthesizeSpeech } from '@/api/chat'
import { isAbortError, toErrorMessage } from '@/api/http'
import type { ChatMessage, ChatRole, Conversation } from '@/types/chat'
import { mapWithConcurrency } from '@/utils/concurrency'
import { createChatId, createMessageId } from '@/utils/id'
import { readMeta, writeMeta } from '@/utils/storage'

/** 启动时最多补拉多少个会话的标题，避免一次打出过多请求 */
const TITLE_FETCH_LIMIT = 20
/** 语音 blob 缓存条数（URL.revokeObjectURL 后需重新合成） */
const SPEECH_CACHE_LIMIT = 8

// ---- 模块级单例：路由切换不会丢失会话与正在进行的对话 ----
const conversations = ref<Conversation[]>([])
const activeId = ref('')
const messagesByChat = reactive<Record<string, ChatMessage[]>>({})
const loadingChats = reactive<Record<string, boolean>>({})
const loadedChats = reactive<Record<string, boolean>>({})
const isStreaming = ref(false)
const isLoadingList = ref(false)
const listError = ref('')
const historyError = ref('')

const activeMessages = computed<ChatMessage[]>(() => messagesByChat[activeId.value] ?? [])

const speechCache = new Map<string, string>()
/** 合成中的任务：同一段文本只发一次请求，重复点击复用同一个 Promise */
const pendingSpeech = new Map<string, Promise<string>>()
/** 合成队列：保证全局同一时刻只有一个合成请求在飞 */
let speechQueue: Promise<void> = Promise.resolve()

let streamAbort: AbortController | null = null
let historyRequestSeq = 0
let listLoaded = false

function createMessage(role: ChatRole, content: string, patch: Partial<ChatMessage> = {}): ChatMessage {
  // 用 reactive 包一层，后续直接改字段即可触发视图更新
  return reactive<ChatMessage>({
    id: createMessageId(),
    role,
    content,
    createdAt: Date.now(),
    status: 'done',
    audioState: 'idle',
    ...patch
  })
}

function deriveTitle(messages: readonly ChatMessage[]): string {
  const firstUser = messages.find((message) => message.role === 'user' && message.content.trim())
  const fallback = messages.find((message) => message.content.trim())
  const text = (firstUser ?? fallback)?.content ?? ''
  return text.replace(/\s+/g, ' ').trim().slice(0, 24)
}

function sortConversations(): void {
  conversations.value = [...conversations.value].sort((a, b) => b.updatedAt - a.updatedAt)
}

function persistMeta(): void {
  const meta: Record<string, { title: string; updatedAt: number }> = {}
  for (const conversation of conversations.value) {
    if (!conversation.title && !conversation.updatedAt) continue
    meta[conversation.id] = { title: conversation.title, updatedAt: conversation.updatedAt }
  }
  writeMeta(meta)
}

/** 更新（或补建）会话的标题与时间；bump 为 true 时会重新排序 */
function touchConversation(id: string, title?: string, bump = true): void {
  const now = Date.now()
  const existing = conversations.value.find((conversation) => conversation.id === id)
  if (existing) {
    if (title) existing.title = title
    if (bump) existing.updatedAt = now
  } else {
    // 标题留空，由历史消息推导；UI 层再显示「新对话」占位
    conversations.value.unshift({ id, title: title ?? '', updatedAt: now })
  }
  if (bump) sortConversations()
  persistMeta()
}

/** 拉取会话列表；已知标题来自本地缓存，未知的并发补拉 */
async function refreshList(): Promise<void> {
  isLoadingList.value = true
  listError.value = ''
  try {
    const ids = await listChatIds()
    const stored = readMeta()
    const known = new Map(conversations.value.map((conversation) => [conversation.id, conversation]))

    const next: Conversation[] = ids.map((id) => {
      const local = known.get(id)
      const cached = stored[id]
      return {
        id,
        title: local?.title || cached?.title || '',
        updatedAt: Math.max(local?.updatedAt ?? 0, cached?.updatedAt ?? 0)
      }
    })

    // 新建但后端还没落盘的会话要保留
    if (activeId.value && !next.some((conversation) => conversation.id === activeId.value)) {
      const local = known.get(activeId.value)
      next.unshift({
        id: activeId.value,
        title: local?.title ?? '',
        updatedAt: local?.updatedAt ?? Date.now()
      })
    }

    conversations.value = next
    sortConversations()

    const missing = conversations.value.filter((conversation) => !conversation.title).slice(0, TITLE_FETCH_LIMIT)
    await mapWithConcurrency(missing, 4, async (conversation) => {
      try {
        const messages = await fetchHistory(conversation.id)
        // 顺手缓存消息，切换会话时即可秒开
        if (conversation.id !== activeId.value && !loadedChats[conversation.id]) {
          messagesByChat[conversation.id] = messages
          loadedChats[conversation.id] = true
        }
        const title = deriveTitle(messages)
        if (title) conversation.title = title
      } catch {
        // 单个会话失败不影响列表展示
      }
    })

    sortConversations()
    persistMeta()
  } catch (error) {
    listError.value = toErrorMessage(error)
  } finally {
    isLoadingList.value = false
  }
}

/** 首次进入时加载一次列表 */
async function ensureList(): Promise<void> {
  if (listLoaded) return
  listLoaded = true
  await refreshList()
}

async function loadHistory(chatId: string): Promise<void> {
  const requestSeq = ++historyRequestSeq
  loadingChats[chatId] = true
  historyError.value = ''
  try {
    const messages = await fetchHistory(chatId)
    if (requestSeq !== historyRequestSeq) return
    messagesByChat[chatId] = messages
    loadedChats[chatId] = true
    const title = deriveTitle(messages)
    if (title) touchConversation(chatId, title, false)
  } catch (error) {
    if (requestSeq === historyRequestSeq) historyError.value = toErrorMessage(error)
  } finally {
    loadingChats[chatId] = false
  }
}

/** 切换到某个会话（切换时会中断正在进行的生成） */
async function activate(chatId: string): Promise<void> {
  if (!chatId) return
  if (activeId.value !== chatId) {
    if (isStreaming.value) stopStreaming()
    activeId.value = chatId
    historyError.value = ''
    touchConversation(chatId, undefined, false)
  }
  if (!loadedChats[chatId]) await loadHistory(chatId)
}

/** 新建一个本地会话（后端会在首次对话时落盘） */
function createConversation(): string {
  const id = createChatId()
  messagesByChat[id] = []
  loadedChats[id] = true
  touchConversation(id, undefined, true)
  return id
}

async function runStream(chatId: string, text: string): Promise<void> {
  const list = messagesByChat[chatId]
  if (!list) return

  const assistant = createMessage('assistant', '', { status: 'streaming' })
  list.push(assistant)

  const controller = new AbortController()
  streamAbort = controller
  isStreaming.value = true

  try {
    await streamChat({
      message: text,
      chatId,
      signal: controller.signal,
      onDelta: (delta) => {
        assistant.content += delta
      }
    })

    if (assistant.status === 'streaming') {
      if (assistant.content.trim()) {
        assistant.status = 'done'
      } else {
        assistant.status = 'error'
        assistant.error = '没有收到回复，请稍后重试'
      }
    }
  } catch (error) {
    if (isAbortError(error)) {
      // 用户主动停止：有内容就保留，没内容标记为已停止
      assistant.status = assistant.content.trim() ? 'done' : 'aborted'
    } else {
      assistant.status = 'error'
      assistant.error = toErrorMessage(error)
    }
  } finally {
    isStreaming.value = false
    streamAbort = null
    touchConversation(chatId)
  }
}

/** 发送消息：先插入用户气泡，再流式追加 AI 回复 */
async function sendMessage(raw: string): Promise<void> {
  const text = raw.trim()
  if (!text || isStreaming.value) return

  const chatId = activeId.value || createConversation()
  activeId.value = chatId
  const list = messagesByChat[chatId]
  if (!list) {
    messagesByChat[chatId] = []
  }
  loadedChats[chatId] = true

  const target = messagesByChat[chatId]
  if (!target) return

  target.push(createMessage('user', text))
  touchConversation(chatId, text.replace(/\s+/g, ' ').slice(0, 24))

  await runStream(chatId, text)
}

/** 中断当前生成 */
function stopStreaming(): void {
  streamAbort?.abort()
}

/** 重新生成某条失败的消息 */
async function retry(chatId: string, messageId: string): Promise<void> {
  if (isStreaming.value) return
  const list = messagesByChat[chatId]
  if (!list) return

  const index = list.findIndex((message) => message.id === messageId)
  if (index < 0) return

  const userMessage = list
    .slice(0, index)
    .reverse()
    .find((message) => message.role === 'user')
  if (!userMessage) return

  list.splice(index, 1)
  await runStream(chatId, userMessage.content)
}

/**
 * 按需合成语音（懒加载 + 文本级缓存 + 全局单飞）。
 *
 * 两个约束：
 * 1. 同一段文本只会合成一次；合成中的重复点击直接复用同一个 Promise。
 * 2. 全局同一时刻只允许一个合成请求在飞：其它消息的合成排队等待，
 *    避免 GPT-SoVITS 被并发请求（显存/耗时都会翻倍）。
 */
async function getSpeechUrl(text: string, chatId: string): Promise<string> {
  const cached = speechCache.get(text)
  if (cached) return cached

  const inFlight = pendingSpeech.get(text)
  if (inFlight) return inFlight

  const task = enqueueSpeech(async () => {
    // 排队期间可能已经被别的请求合成完了
    const ready = speechCache.get(text)
    if (ready) return ready

    const blob = await synthesizeSpeech(text, chatId)
    const url = URL.createObjectURL(blob)
    speechCache.set(text, url)
    evictSpeechCache()
    return url
  })

  pendingSpeech.set(text, task)
  // 无论成功失败都要清掉占位（注意不要用 finally，避免产生未处理的 rejection）
  task.then(
    () => pendingSpeech.delete(text),
    () => pendingSpeech.delete(text)
  )
  return task
}

/** 把合成任务串成一条队列，保证同一时刻只有一个合成请求 */
function enqueueSpeech<T>(job: () => Promise<T>): Promise<T> {
  const run = speechQueue.then(job, job)
  speechQueue = run.then(
    () => undefined,
    () => undefined
  )
  return run
}

/** 淘汰缓存时跳过仍被消息引用的文本，否则那条消息的 blob URL 会失效 */
function evictSpeechCache(): void {
  while (speechCache.size > SPEECH_CACHE_LIMIT) {
    let evictedKey: string | undefined
    for (const key of speechCache.keys()) {
      if (!isSpeechReferenced(key)) {
        evictedKey = key
        break
      }
    }
    if (!evictedKey) return // 全部都在用，宁可多占一点内存
    const url = speechCache.get(evictedKey)
    speechCache.delete(evictedKey)
    if (url) URL.revokeObjectURL(url)
  }
}

function isSpeechReferenced(text: string): boolean {
  for (const list of Object.values(messagesByChat)) {
    for (const message of list) {
      if (message.role === 'assistant' && message.content === text) return true
    }
  }
  return false
}

export function useChatStore() {
  return {
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
  }
}
