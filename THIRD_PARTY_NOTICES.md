# 第三方项目声明

本文件记录 MutCube 直接复用的第三方项目、固定版本、许可证和修改情况。

当前未复制第三方项目源码。模型服务页使用 Lobe Icons 的静态品牌图标；其 MIT 许可全文随 APK 保存在 `app/src/main/assets/licenses/lobe-icons.txt`。主要直接依赖如下；应用内“第三方开源许可”页面由构建任务自动生成依赖清单和许可全文。

| 项目 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| Lobe Icons 静态 PNG | 1.97.0 | MIT | 模型服务商标识，浅色/深色各一份；商标仍归各品牌所有 |
| AndroidX Compose / Material 3 | Compose BOM 2026.06.01 | Apache-2.0 | 原生界面 |
| AndroidX Lifecycle | 2.11.0 | Apache-2.0 | ViewModel 与生命周期感知状态收集 |
| AndroidX Room | 2.8.5 | Apache-2.0 | SQLite 持久化 |
| Kotlin Symbol Processing | 2.3.10 | Apache-2.0 | Room 编译期代码生成 |
| Compose Unstyled | 2.9.2 | MIT | 自定义附件底部弹层 |
| Reorderable | 3.1.0 | Apache-2.0 | 模型服务列表拖拽排序与跟手动画 |
| OkHttp | 5.3.0 | Apache-2.0 | HTTPS 与 MiMo 流式响应读取 |
| SnakeYAML | 2.7 | Apache-2.0 | 安全解析标准 Skill 的 YAML frontmatter |
| kotlinx.serialization JSON | 1.11.0 | Apache-2.0 | 模型请求与响应 JSON 处理 |
| kotlinx.coroutines | 1.10.2 | Apache-2.0 | 异步数据流与生成任务取消 |
| Compose Markdown | 1.3.0 | MIT | 原生 Compose Markdown 渲染 |
| AndroidX WorkManager | 2.11.2 | Apache-2.0 | 周期备份提醒 |
| AppAuth for Android | 0.11.1 | Apache-2.0 | MCP OAuth 2.0 授权 |
| MCP Kotlin SDK | 0.15.0 | Apache-2.0 | MCP Streamable HTTP 客户端 |
| Ktor Client | 3.5.1 | Apache-2.0 | MCP HTTP 与 SSE 传输 |
| AboutLibraries | 15.1.1 | Apache-2.0 | 构建期生成第三方依赖与许可清单 |
