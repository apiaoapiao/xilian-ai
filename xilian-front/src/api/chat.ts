import type { ChatMessage, RawMessage } from '@/types/chat'
import { readSseStream } from '@/utils/sse'
import { API_BASE_URL, http } from './http'

/** 会话 id 列表（后端按 tmp/chat-memory 目录下的 .kryo 文件返回） */
export async function listChatIds(): Promise<string[]> {
  const { data } = await http.get<string[]>('/text/filename')
  return Array.isArray(data) ? data.filter((id): id is string => typeof id === 'string' && id.length > 0) : []
}

/** 某个会话的历史消息 */
export async function fetchHistory(chatId: string): Promise<ChatMessage[]> {
  const { data } = await http.get<RawMessage[] | null>('/text/memory', { params: { chatId } })
  if (!Array.isArray(data)) return []

  const messages: ChatMessage[] = []
  data.forEach((raw, index) => {
    const role = normalizeRole(raw.messageType)
    if (!role) return
    const content = typeof raw.text === 'string' && raw.text ? raw.text : raw.content
    if (typeof content !== 'string' || content.trim() === '') return
    messages.push({
      id: `history_${index}`,
      role,
      content,
      createdAt: 0,
      status: 'done',
      audioState: 'idle'
    })
  })
  return messages
}

/**
 * 流式对话。
 * 后端以 SSE 返回文本增量，回调 onDelta 时追加到气泡即可。
 */
export async function streamChat(options: {
  message: string
  chatId: string
  signal?: AbortSignal
  onDelta: (delta: string) => void
}): Promise<void> {
  const { message, chatId, signal, onDelta } = options
  const query = `message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(chatId)}`

  const response = await fetch(`${API_BASE_URL}/ai/chat/stream?${query}`, {
    method: 'GET',
    headers: { Accept: 'text/event-stream' },
    cache: 'no-store',
    signal
  })

  if (!response.ok) {
    throw new Error(`对话请求失败（HTTP ${response.status}）`)
  }

  await readSseStream(response, onDelta)
}

/** 语音合成：后端返回 audio/wav 二进制 */
export async function synthesizeSpeech(text: string, chatId: string): Promise<Blob> {
  const { data } = await http.post<Blob>(
    '/tts/synthesize',
    { text, chatId },
    { responseType: 'blob', timeout: 120_000 }
  )
  return data
}

function normalizeRole(messageType: string | undefined): ChatMessage['role'] | null {
  if (messageType === 'ASSISTANT') return 'assistant'
  if (messageType === 'USER') return 'user'
  // SYSTEM / TOOL 等消息不在气泡里展示
  return null
}
