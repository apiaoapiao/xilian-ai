# 昔涟 · 前端（xilian-front）

昔涟角色扮演对话的前端，Vue 3 + TypeScript + Vite。

## 快速开始

```bash
pnpm install        # 或 npm install
pnpm dev            # http://localhost:5431
pnpm build          # 类型检查 + 生产构建，产物在 dist/
pnpm preview        # 预览构建产物（同样带 /api 代理）
pnpm type-check     # 只做类型检查
```

后端需要单独启动（默认 `http://localhost:8123`，context-path 为 `/api`）。

## 接口代理

后端 `WebConfig` 的 CORS 只放行固定来源，直接跨域访问会失败。因此前端默认请求**相对路径** `/api/*`，
由 Vite 的 dev / preview 服务器代理到 `http://localhost:8123`，浏览器同源、无需后端改动：

```ts
// vite.config.ts
proxy: { '/api': { target: 'http://localhost:8123', changeOrigin: true } }
```

若前后端分离部署（后端已放开 CORS），在 `.env.local` 中覆盖接口前缀即可：

```bash
VITE_API_BASE_URL=http://localhost:8123/api
```

## 目录结构

```
src/
├── api/            # 接口封装：axios 实例、SSE 流式对话、TTS
├── components/     # SessionSidebar / MessageBubble / ChatComposer
├── composables/    # useChatStore（会话与消息状态，模块级单例）、useAudioPlayer
├── types/          # 前端类型定义
├── utils/          # SSE 解析、并发控制、时间格式化、本地缓存、id 生成
├── views/          # WelcomeView（首页）、ChatView（对话页）
├── assets/         # 头像与首页背景（welcome-bg-*.jpg 为压缩后的派生图）
└── style.css       # 设计变量与全局样式
```

## 相对上一版的主要改动

**接口与数据**

- 统一走 `/api` 代理，去掉写死的 `http://localhost:8123`，解决端口改动后 CORS 失效的问题。
- 重写 SSE 解析：按事件帧解析、多行 `data` 用换行重组，修复了回复**换行全部丢失**和**逐行 trim 吃掉空格**的问题；同时处理半包，避免流式文本被截断。
- 流式请求支持 `AbortController`：切换会话、点击「停止」会真正中断后端请求，不再串会话。

**交互**

- 修复中文输入法回车误发送（composition 期间放行回车），Enter 发送 / Shift + Enter 换行，输入框随内容自适应高度。
- 发送中禁用输入并显示「停止生成」按钮与「正在回答…」状态。
- 消息列表自动滚动到底部，手动上滑后不再打扰，并提供「回到底部」按钮。
- 消息支持复制、失败重试、稳定 key（不再用数组下标）。
- 语音改为**点击播放才合成**（懒加载 + 文本级缓存 + blob URL 回收），不再每次回复都强制合成。
- 会话列表：本地缓存标题/时间，启动时并行补拉（并发 4），切换会话秒开；加载骨架屏与错误重试。

**体验与工程**

- 首页 8K 12.8 MB 背景图替换为 2 张响应式 JPEG（桌面 2560×1440 ≈ 450 KB、移动 1080×1920 ≈ 230 KB）。
- 左侧会话栏移动端改为抽屉 + 遮罩，整体适配窄屏与安全区。
- 全局样式重置、设计变量、滚动条、焦点样式、`prefers-reduced-motion`，固定浅色主题避免系统深色模式反色。
- 补齐 `index.html` 语言/标题/图标/主题色，删除模板残留（HelloWorld 组件、vite.svg 等）。
- 类型检查严格模式通过（`vue-tsc`）。

> `src/assets/image.png` 是首页背景的原图（7680×4320），已不再被引用，仅作为素材保留。
