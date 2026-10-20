/**
 * 会话 id 会作为后端 tmp/chat-memory 下的文件名，必须保证文件名安全。
 * 形如 xilian_lx8f3k2a_7d1c9e，前缀保留以便与旧会话兼容。
 */
export function createChatId(): string {
  const time = Date.now().toString(36)
  const random = Math.random().toString(36).slice(2, 8)
  return `xilian_${time}_${random}`
}

/** 消息 id 只在前端使用，需保证同一会话内唯一 */
export function createMessageId(): string {
  return `msg_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`
}
