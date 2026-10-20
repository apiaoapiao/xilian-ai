/**
 * 增量解析 text/event-stream 数据流。
 *
 * 后端（Spring MVC + Flux<String>）每个元素会被编码成：
 *   data:<文本，其中 \n 被替换成 \n data:>\n\n
 * 因此必须按「帧」解析并把多行 data 用 \n 重新拼起来，
 * 否则回复里的换行会全部丢失，逐行 trim 还会吃掉词间空格。
 */
export async function readSseStream(
  response: Response,
  onData: (data: string) => void
): Promise<void> {
  const body = response.body
  if (!body) throw new Error('当前浏览器不支持流式响应')

  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')

  let buffer = ''
  let dataLines: string[] = []

  const dispatch = () => {
    if (dataLines.length === 0) return
    const payload = dataLines.join('\n')
    dataLines = []
    if (payload.length > 0) onData(payload)
  }

  const consumeLine = (rawLine: string) => {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (line === '') {
      // 空行代表一个事件结束
      dispatch()
      return
    }
    if (line.startsWith(':')) return // 注释 / 心跳

    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    // 注意：Spring 不会在冒号后补空格，所以这里不能额外 trim，否则会吃掉文本开头的空格
    const value = colon === -1 ? '' : line.slice(colon + 1)
    if (field === 'data') dataLines.push(value)
  }

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 只处理完整的行，半包留在 buffer 里等下一次读取
      let index = buffer.indexOf('\n')
      while (index !== -1) {
        consumeLine(buffer.slice(0, index))
        buffer = buffer.slice(index + 1)
        index = buffer.indexOf('\n')
      }
    }

    buffer += decoder.decode()
    if (buffer.length > 0) consumeLine(buffer)
    dispatch()
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // 读取器仍被占用时忽略
    }
  }
}
