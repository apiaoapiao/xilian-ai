/** 消息角色 */
export type ChatRole = 'user' | 'assistant'

/** 消息生成状态 */
export type MessageStatus = 'done' | 'streaming' | 'error' | 'aborted'

/** 语音（TTS）状态：默认懒加载，点击播放时才合成 */
export type AudioState = 'idle' | 'loading' | 'ready' | 'failed'

/** 一条聊天消息 */
export interface ChatMessage {
  /** 前端生成的稳定 id，用作 v-for 的 key */
  id: string
  role: ChatRole
  content: string
  /** 毫秒时间戳；来自后端历史记录的消息为 0 */
  createdAt: number
  status: MessageStatus
  /** status 为 error 时的提示文案 */
  error?: string
  audioState: AudioState
  /** 已合成语音的 blob URL */
  audioUrl?: string
  /** 语音相关错误提示（合成失败 / 播放被拦截），成功后清空 */
  audioError?: string
}

/** 会话（左侧列表项） */
export interface Conversation {
  id: string
  title: string
  /** 毫秒时间戳，用于排序；未知时为 0 */
  updatedAt: number
}

/** 后端 /text/memory 返回的原始消息结构（Spring AI Message 的序列化结果） */
export interface RawMessage {
  messageType?: string
  text?: string
  content?: string
}
