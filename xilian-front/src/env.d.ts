/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 接口前缀，默认 /api（由 Vite 代理到后端） */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
