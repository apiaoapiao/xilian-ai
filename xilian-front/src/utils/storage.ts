/** 会话标题/时间的本地缓存，用来在首次加载时立刻渲染列表，避免 N 次请求 */

const META_KEY = 'xilian.conversation-meta.v1'

export interface StoredConversationMeta {
  title: string
  updatedAt: number
}

function safeParse(raw: string | null): Record<string, StoredConversationMeta> {
  if (!raw) return {}
  try {
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const result: Record<string, StoredConversationMeta> = {}
    for (const [id, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (!value || typeof value !== 'object') continue
      const item = value as Partial<StoredConversationMeta>
      result[id] = {
        title: typeof item.title === 'string' ? item.title : '',
        updatedAt: typeof item.updatedAt === 'number' ? item.updatedAt : 0
      }
    }
    return result
  } catch {
    return {}
  }
}

export function readMeta(): Record<string, StoredConversationMeta> {
  try {
    return safeParse(window.localStorage.getItem(META_KEY))
  } catch {
    // 隐私模式下 localStorage 可能不可用
    return {}
  }
}

export function writeMeta(meta: Record<string, StoredConversationMeta>): void {
  try {
    window.localStorage.setItem(META_KEY, JSON.stringify(meta))
  } catch {
    // 忽略写入失败（配额/隐私模式）
  }
}
