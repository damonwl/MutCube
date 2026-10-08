# 开源准备审查与修复记录

日期：2026-10-08。应用仍为开发版 0.1.3；本轮没有发布 APK、推送远端或创建 GitHub 仓库。

后续发布补充：GitHub 仓库已用独立首次提交建立；[CI 37720330804](https://github.com/damonwl/MutCube/actions/runs/37720330804) 的三个作业全部通过，Android 和数据库报告已上传。v0.1.3 预发布使用原证书签名 APK，详见 [发布说明](releases/v0.1.3.md)。以下内容保留原审查时点，不代表当前尚未建立仓库或尚未执行 CI。

## 审查范围和方法

自动化编译、JVM 测试和 Android Lint 覆盖工程模块；人工重点检查模型流式传输、生成生命周期、
Room 备份恢复、模板包与权限、MCP 授权、语音临时文件、UI 职责和开源工程资料。
这不是逐行安全认证，也不宣称消除了所有潜在缺陷。

## 已修复问题

| 问题 | 原因与处理 | 回归证据 |
| --- | --- | --- |
| 快速流式响应可能丢字或提前结束 | 网络回调用 `trySend`，缓冲区满后丢弃事件；三种 Provider 改用有界阻塞背压，取消时退出读取 | `StreamBackpressureTest`，每种协议 256 个增量、慢消费者逐字验证 |
| 日志保存失败影响 AI 回复 | 诊断写入位于生成的 finally，失败可覆盖原异常；改为 IO 写入且隔离可选日志失败 | `LoggingModelGatewayTest` 成功与失败分支 |
| 并发诊断写入丢记录 | RequestLog 的更新加同步保护；扩展审计 append/clear 以 Mutex 串行处理 | 编译和相关存储回归；未进行磁盘故障注入 |
| 停止朗读后音频临时文件残留 | 消费端 finally 清理、队列未投递元素回收 | `NetworkSpeechPlaybackTest` 取消及播放失败 |
| 备份遗漏并发写入或文件摘要不一致 | Room 写事务锁内复制主文件与 WAL；所有归档文件先暂存再 Hash | 真机并发事务备份测试 |
| 正确 Hash 的错误数据库可覆盖用户数据 | 暂存副本先移除可伪造的 identity 表，再运行 Room 实际结构与迁移验证 | 真机伪造数据库测试；原数据保持可访问 |
| 恢复关闭数据库后仍允许继续操作 | 替换失败明确返回需重启异常，区分回滚完整性；界面阻止略过关闭提示 | 编译；正常恢复和校验失败真机回归。未模拟磁盘耗尽后的回滚失败 |
| 快速点击可能重复排队生成、旧任务清理新状态 | 生成 Job 启动前检查，捕获发送目标，界面更新按 generation attempt 标识隔离；恢复先取消并等待生成 | 编译、会话服务回归；无独立 ViewModel 全量竞态测试 |
| MCP 弹窗期间撤权仍可能执行 | 执行前重读配置，校验端点、身份、权限和输入结构；60 秒调用上限；取消不吞掉 | `McpInvocationPolicyTest` 与聊天工具服务测试 |
| MCP 刷新覆盖撤权、工具别名碰撞 | 保留最新启停/拒绝状态；Schema 变化重置为询问；别名由原始服务器/工具身份摘要生成 | 参数变化、长名称、特殊字符和相同前缀测试 |
| 新系统设备迁移边界不明确 | 保留 allowBackup=false，增加 cloud-backup/device-transfer 全域排除规则 | Manifest 编译与 Lint |
| 自动化测试依赖旧默认模型 | 非 Live 测试显式配置虚拟 Provider，不读写用户模型或密钥 | 真机重新回归通过 |

## 架构整理

`MutCubeApp.kt` 从约 3,089 行整理到约 2,044 行。拆出聊天展示、模板目录和设置路由，
保留平台启动器、顶层导航与确认在外壳；`AppContainer` 从 Application 单独拆出。
没有改变 SDK、模板数据归属、用户授权和聊天持久化模型，没有引入新的 DI/UI 框架。

顶层 UI/ViewModel 仍较大；后续按业务边界继续演进，而不是为追求行数将状态拆散。

## 验证结果

- Debug、AndroidTest 和启用 R8/资源压缩的 unsigned Release 构建通过。
- JVM 单元测试 156 项通过，0 失败、0 错误。
- SDK 的 8 项 Node 测试、TypeScript 严格类型检查、模板及健身页面脚本回归通过。
- 真机 Android 16：主要回归 23 项中 21 项通过、2 项真实 Provider 测试按显式门禁跳过；
  独立 CLI Todo 包和语音模板包桥接各 1 项通过。语音桥接使用替身，不等于本轮验证真实录音/转写服务。
- 数据库专项测试包被真机系统拒绝安装；33 项 Room 迁移、Repository 和版本授权专项改在隔离模拟器通过。
- 真机覆盖安装未卸载，不执行用户数据清空或覆盖恢复。人工检查首页启动显示。
- Lint 无 Error；依赖更新提示、重复深浅色品牌图标、KTX/Compose 风格等 Warning/Hint 仍保留。
- `git diff --check` 通过。

连续重复构建时发现默认 2 GiB Gradle 堆不足。堆上限改为 4 GiB，并限制 worker 数为 2 后重跑。

## 开源工程资料

- 新增 `CONTRIBUTING.md` 和 `SECURITY.md`，补充复现、验证、许可与敏感信息处理规则。
- 双语 README 修正版本和已实现的模板 TTS/ASR 边界；更新备份 v3、诊断排除和架构文档。
- 来源策略统一允许合规的 MIT、Apache-2.0、BSD 等宽松组件，保留各自许可和适用 NOTICE；
  不允许复制 AGPL 参考应用实现。
- GitHub Actions 提供 Android 构建/JVM/Lint/Release、SDK 与模拟器数据库测试三个任务；
  仅 contents:read，不使用 Provider 密钥、不发布安装包，第三方 Action 固定提交 SHA。
  YAML 已本地解析，实际 GitHub 托管执行仍需仓库上线后验证。
- 忽略签名文件、`.env` 和私人备份。检查 145 个已有 Git 提交中的长 `sk-` 密钥与私钥头模式，
  命中只有 `SensitiveTextRedactorTest` 中的人造测试值；此检查不替代完整密钥扫描与人工发布复核。

CI 参考：[GitHub checkout](https://github.com/actions/checkout)、
[Gradle Actions](https://github.com/gradle/actions)、
[Android SDK setup](https://github.com/android-actions/setup-android)。

## 发布前的外部步骤

维护者仍需创建或指定 GitHub 仓库、开启私密漏洞报告与密钥扫描、实际验证 CI，
并自行保管正式发布证书。模板签名/市场、账号同步、完整本地化等未实现能力继续按 README 边界说明，
不因本轮审查而标记为已完成。
