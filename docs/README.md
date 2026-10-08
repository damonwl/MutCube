# 文档导航

更新日期：2026-10-08。当前源码版本为开发版 0.1.3（versionCode 4）。

## 当前状态与验证

| 文档 | 用途 |
| --- | --- |
| [中文 README](../README.md) / [English README](../README_EN.md) | 产品能力、安装构建、已知边界 |
| [开发进度](PROGRESS.md) | 按日期记录实现与验收，不覆盖历史结论 |
| [开源准备审查](OPEN_SOURCE_READINESS_AUDIT.md) | 2026-10-08 修复、重构、测试证据及未验证事项 |
| [贡献指南](../CONTRIBUTING.md) | 开发环境、检查命令、提交与架构约束 |
| [安全政策](../SECURITY.md) | 漏洞报告、凭据与扩展信任边界 |
| [APK 分发与签名](DISTRIBUTION.md) | 区分源码版本、测试 APK 和正式签名安装包 |

本轮 JVM 测试 156 项通过；SDK Node 测试 8 项及 TypeScript 严格检查通过。
真机主要回归 21 项通过、2 项真实 Provider 用例跳过，另有独立 Todo 包与语音桥接各 1 项通过。
33 项数据库专项在模拟器通过，不计为真机结果；语音桥接使用替身，不计为真实 TTS/ASR 验收。
Debug/AndroidTest/unsigned Release 构建通过，Lint 无 Error，但仍有 Warning/Hint。
GitHub CI 配置已加入源码，尚未在 GitHub 托管环境运行。

## 宿主架构与数据

- [总体架构](ARCHITECTURE.md)：模块边界、组合根及 Android 展示层职责。
- [模型网关](MODEL_GATEWAY.md)：Provider、生成、流式背压、工具与语音协议。
- [数据模型](DATA_MODEL.md)：会话树、项目、记忆和 Room 迁移。
- [上下文与记忆](CONTEXT_AND_MEMORY.md)：压缩检查点、来源检索与长期记忆边界。
- [备份与恢复](DATA_BACKUP.md)：备份 v3、凭据排除、结构验证及失败重启。
- [Skill 系统](SKILL_SYSTEM_DESIGN.md)：标准包、按需加载、作用域与执行限制。

## 第三方模板开发

建议按以下顺序阅读：

1. [模板模块与集成边界](TEMPLATE_MODULES.md)：宿主、服务、项目实例、Action 与用户数据。
2. [Template Package v1](TEMPLATE_PACKAGE_V1.md)：Manifest、文件结构、权限与安装升级规则。
3. [SDK README](../template-sdk/README.md) 与 [QUICKSTART](../template-sdk/QUICKSTART.md)：浏览器开发、Mock Host、类型与 CLI。
4. [SDK 错误码](../template-sdk/ERRORS.md)：失败语义与处理方式。
5. [SDK 验收](TEMPLATE_SDK_ACCEPTANCE.md) 与 [安全复查](TEMPLATE_SECURITY_REVIEW.md)：复现方法和残余风险。

官方开发样板：[英语阅读与生词](../template-sdk/examples/english-reader/README.md)（CLI 默认初始化）。
专项示例：[语音桥接](../template-sdk/examples/speech-demo/README.md)。旧 Todo/Vant Todo 仅作为历史回归材料保留。
SDK 和开发文档直接随源码维护，不发布 npm 包，不强制模板使用统一 UI 组件。

内置模板与交互参考：[模板开发说明](TEMPLATE_DEVELOPMENT_GUIDE.md)、
[聊天与模板交互](TEMPLATE_CHAT_INTERACTION.md)、[健身服务](FITNESS_SERVICE_V2.md)。

## 来源与历史记录

- [来源策略](ORIGIN_POLICY.md)、[组件复用流程](OPEN_SOURCE_REUSE.md)、[第三方声明](../THIRD_PARTY_NOTICES.md)。
- [会话验收](CONVERSATION_ACCEPTANCE.md) 与 [会话功能审计](FEATURE_COMPLETION_AUDIT.md) 保留 2026-09-15 的历史基线。
- `TEMPLATE_PHASE_ONE_*`、`MIGRATION_PLAN.md`、`UI_V0_1.md` 等属于阶段设计/验收记录，
  不应将其中“后续实现”或旧测试数量当作当前产品状态；当前状态以 README、最新进度及对应实现文档为准。
