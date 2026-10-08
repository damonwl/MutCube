# 会话产品能力验收

历史验收日期：2026-09-15；状态说明更新：2026-10-08。

> 下文保留当时的能力、模型和测试基线，不代表当前仍停留在“进入模板之前”。
> 当前版本、模板与语音能力以 [README](../README.md) 和 [开发进度](PROGRESS.md) 为准。
> 2026-10-08 的会话、项目历史、备份及模板交互回归见 [开源准备审查](OPEN_SOURCE_READINESS_AUDIT.md)。

本文冻结进入模板运行时之前的会话产品验收边界。验收关注用户可完成的闭环、数据恢复、安全边界和失败行为，
不以复刻任何参考项目的实现为目标。

## 验收结论

会话产品第一阶段通过验收，可以作为后续模板系统的稳定宿主。主应用已在中文 Android 36 模拟器使用
MiMo `mimo-v2.5-pro` 完成真实请求，并在冷启动后恢复会话、结构化消息和模型用量。

## 能力矩阵

| 范围 | 已验收能力 | 主要证据 |
| --- | --- | --- |
| 会话生命周期 | 空白页不落库、首条消息创建、重命名、自动/手动标题、置顶、软删除、撤销、清空 | Room Repository 仪器测试；真实 MiMo 首轮标题测试 |
| 项目 | 创建、筛选、重命名、置顶、删除；会话加入、移动、移出；删除项目不删除会话 | `RoomConversationRepositoryTest`；Room 4→10 迁移测试 |
| 消息树 | 编辑后分支、重新生成候选、候选切换、删除后代、从节点派生会话 | Repository 分支测试；生成服务重新生成与中止测试 |
| 生成 | 流式回复、停止、重试、错误分类、上下文裁剪、跨会话取消、模型、Token、缓存、速度与耗时 | `ChatGenerationServiceTest`；`MiMoModelGatewayTest`；模拟器真实请求 |
| 结构化内容 | 文本、附件、推理、工具调用、工具结果分部件持久化并可审计 | Room 11 数据模型测试；有界工具循环测试 |
| 附件 | 拍照、相册、文件选择；草稿移除、私有复制、仅附件发送、持久化、分支复制 | 相机模拟器检查；附件 Repository/生成服务测试 |
| 文档上下文 | 限量提取文本、Markdown、CSV、JSON、XML、YAML、DOCX、PPTX、XLSX | `DocumentTextExtractorTest`；附件提示组装测试 |
| Provider | 多档案、启停排序、配置分享、三种协议、Key 分档案加密、连接测试、模型发现、项目覆盖 | Provider 配置/传输与模型网关测试；模拟器 Key 保存/恢复检查 |
| 模型设置 | 模型、系统提示词、上下文、图片、Temperature、Top P、最大输出 Token；项目按字段继承 | 生成配置测试；Room 8/9 迁移与项目覆盖测试 |
| 消息操作 | 复制、系统分享、收藏与跳转、翻译、TTS 朗读 | Repository 收藏/译文测试；模拟器 UI 检查 |
| 查找与导出 | 标题/正文搜索；Markdown 和 schema v2 JSON 导出；不导出私有 URI | Repository 搜索测试；导出测试 |
| 显示行为 | 系统/浅色/深色、三档字号、消息时间、自动滚动 | `DisplaySettingsStoreTest`；模拟器 UI 检查 |
| 输入快捷操作 | 模型入口、系统相机、系统语音识别、附件、发送/停止 | 中文模拟器逐入口检查 |
| 记忆与工具 | 全局/项目隔离、用户管理、默认禁止写入、敏感信息过滤、最多三轮白名单工具 | 记忆作用域 Repository 测试；工具循环测试 |
| 数据安全 | Android Keystore + AES-GCM、禁用系统备份、诊断、孤立附件回收 | Keystore 仪器测试；安全诊断测试；清理边界代码审计 |
| 高级上下文 | 对话提示词、快捷提示词、Skills、单一激活模式、关键词知识条目及注入上限 | `ContextLibraryStoreTest`；`ChatGenerationServiceTest` |
| 扩展与语音 | MCP Streamable HTTP、OAuth、工具权限；系统/网络 TTS 与 ASR | MCP SDK 边界测试；语音设置测试；模拟器权限检查 |
| 备份与后台 | 本地/WebDAV/S3 备份、周期提醒、后台生成前台服务和完成通知 | AWS V4 签名测试；WorkManager 调度检查；服务仪器测试 |
| 合规与更新 | Gitee Release 检查、MIT 声明、自动生成的第三方许可全文清单 | 更新检查测试；AboutLibraries 生成资源；模拟器许可页检查 |

## 本次验证基线

- `./gradlew --no-configuration-cache :app:compileDebugKotlin lint test :app:assembleDebug`：通过。
- `./gradlew --no-configuration-cache :core:database:connectedDebugAndroidTest`：Android 36 模拟器 17 项通过。
- `./gradlew --no-configuration-cache :core:security:connectedDebugAndroidTest`：Android 36 模拟器 1 项通过。
- Debug APK 覆盖安装成功；主应用数据保留，API Key 仍显示已配置。
- 输入框聚焦后，“标准”进入模型设置，相机打开系统相机，语音识别 Intent 可由模拟器系统解析。

## 不阻塞本阶段的后续项

- 模板列表、Manifest、运行时、授权和模板数据持久化属于下一阶段。
- 数学公式、脚注和任务列表属于 Markdown 增强，已按讨论延后。
- PDF、旧版 Office、扫描件、音频和视频的正文解析属于文档理解增强。当前文件仍可选择、保存、展示和发送，
  但系统不会假装读取尚未提取的正文。
- 未列入当前三种协议的新 Provider，需要新增独立协议适配器或使用其 OpenAI-compatible 入口。
