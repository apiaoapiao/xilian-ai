import axios from 'axios'

/**
 * 接口前缀。
 * 默认走相对路径 /api，由 Vite（dev / preview）代理到 http://localhost:8123，
 * 这样浏览器同源请求，完全绕开后端 CORS 配置。
 * 分离部署时可通过 .env.local 里的 VITE_API_BASE_URL 覆盖。
 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '')

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60_000
})

/** 把任意异常转换成可以直接展示给用户的中文提示 */
export function toErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED') return '请求超时，请稍后重试'
    if (error.response) return `服务响应异常（HTTP ${error.response.status}）`
    return '无法连接后端服务，请确认服务已启动'
  }
  if (error instanceof Error && error.message) return error.message
  return '未知错误，请稍后重试'
}

/** 判断是否是「用户主动中断」导致的异常 */
export function isAbortError(error: unknown): boolean {
  if (axios.isCancel(error)) return true
  return error instanceof DOMException && error.name === 'AbortError'
}
