/**
 * 以固定并发度执行异步任务，用于避免一次性打出过多请求。
 * 单个任务失败不会中断整体流程。
 */
export async function mapWithConcurrency<T>(
  items: readonly T[],
  limit: number,
  task: (item: T) => Promise<void>
): Promise<void> {
  const queue = items.slice()
  if (queue.length === 0) return

  const workerCount = Math.max(1, Math.min(limit, queue.length))
  const workers: Array<Promise<void>> = []

  for (let i = 0; i < workerCount; i += 1) {
    workers.push(
      (async () => {
        while (queue.length > 0) {
          const item = queue.shift()
          if (item === undefined) return
          try {
            await task(item)
          } catch {
            // 忽略单个任务的失败
          }
        }
      })()
    )
  }

  await Promise.all(workers)
}
