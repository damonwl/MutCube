# 第三方模板 SDK 验收

更新日期：2026-10-08。历史结果按日期保留，当前综合验证见 [开源准备审查](OPEN_SOURCE_READINESS_AUDIT.md)。

## 当前范围

当前开发样板为独立英语学习模板，不调用内置健身模板；下方 Todo/Vant 验收是历史记录，不代表当前默认样板。开发者可按以下顺序复现：

1. `python3 scripts/mutcube-template init <新目录> --id org.example.my-english --developer "开发者名称"` 创建英语学习样板，再修改 `manifest.json` 中的名称与 Action。完整步骤见 [QUICKSTART](../template-sdk/QUICKSTART.md)。
2. 在浏览器打开示例，使用 Mock Host 验证页面与 Action；必要时用 `node --test template-sdk/test/*.test.js` 跑 SDK 测试。
3. `python3 scripts/mutcube-template validate <目录>` 检查目录、Manifest 与资源限制。
4. `python3 scripts/mutcube-template types <目录> <新文件.d.ts>` 生成 Action 类型；AI `GENERATE` 输出仍须运行时校验。
5. `python3 scripts/mutcube-template pack <目录> <新文件.mutcube-template>` 打包。安装器会再次验证 Hash、协议、权限和资源。
6. 在 App 的模板管理页导入包，查看开发者/权限后确认安装；在项目中绑定并运行。
7. 升级、解绑、卸载时检查数据保留选项；此过程不需要开发者读取宿主 Room、API Key 或私有文件。

## 自动化验证

- SDK JS 单测覆盖桥接信封、Mock Host、权限、语音回调和脱敏日志。
- Kotlin 单元测试覆盖包校验、Manifest、Action Runtime 与示例结构。
- `InstalledTemplateStoreTest` 在隔离数据库/缓存中覆盖真实包安装、资源读取与 WebView 加载。
- `VantTodoPackageTest` 从 Vant 编译产物导入外部包，在隔离项目中新增、重新加载、修改待办；不写入用户现有数据。
- `CleanRoomPackageTest` 将只使用公开 CLI 生成的独立 ID 英语包安装到真机隔离项目，执行学习资料新增、查询并在解绑后确认 Action 被拒绝；不调用真实 AI/TTS。

2026-09-30 验收结果：Android 36 模拟器上 `VantTodoPackageTest` 为 1/1 通过，`InstalledTemplateStoreTest` 为 3/3 通过；SDK JS 单测 6/6 通过，TypeScript 声明编译、`testDebugUnitTest`、`assembleDebug` 和 `lintDebug` 通过。CLI 的 `init`、`validate`、`types`、`pack` 已以独立 Todo 目录完成冒烟测试。

2026-10-01 真机复验：开启「通过 USB 安装」后，Debug 应用与测试 APK 安装成功。外部 Vant 包的 `VantTodoPackageTest` 为 1/1 通过，`InstalledTemplateStoreTest` 为 3/3 通过；外部语音示例包的 `SpeechTemplatePackageTest` 为 1/1 通过，覆盖能力未配置、原生确认、取消和非法语言参数。上述测试使用隔离数据库/目录；语音用例采用模拟服务，**不代表本次已验证真实 Provider 的发声和麦克风转写**。此前 `INSTALL_FAILED_USER_RESTRICTED` 是安装限制，并非模板逻辑失败。

2026-10-02 独立开发者与安全复验：从空目录使用公开 CLI 生成 `org.example.cleanroom` 包（含独立开发者名），`validate`、`types`、`pack` 与 ZIP 完整性检查通过。真机上 `CleanRoomPackageTest` 1/1、`InstalledTemplateStoreTest` 4/4、`SpeechTemplatePackageTest` 1/1、`TemplateBackupTest` 1/1、`VantTodoPackageTest` 1/1 通过。SDK/CLI Node 测试 8/8 通过；`testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过。测试均使用隔离项目/数据库/目录；此次没有真实 Provider 的 TTS/ASR 音频验收，也没有第三方独立安全审计。安全边界与残余风险见 [安全复查](TEMPLATE_SECURITY_REVIEW.md)。

## 2026-10-08 复验补充

2026-10-08 复验：SDK/CLI Node 测试 8/8、TypeScript 严格类型检查和模板页面脚本通过。
真机 `InstalledTemplateStoreTest` 4/4、`TemplateBackupTest` 3/3、CLI 独立生成包的
`CleanRoomPackageTest` 1/1、外部语音包 `SpeechTemplatePackageTest` 1/1 通过。
备份新增并发事务快照与正确 Hash/错误 Room 结构拒绝用例。所有数据操作使用隔离项目、数据库或目录；
语音使用替身，本次未调用真实 TTS/ASR。Debug/AndroidTest/unsigned Release 构建通过。
本轮没有重跑 Vant 专项，不将过去的 Vant 验收当作本轮新增证据。

## 发布策略

2026-10-08 英语样板切换复验：CLI 默认初始化及自定义 ID 均生成英语学习包；SDK/CLI Node 测试 9/9、TypeScript 严格检查和 AndroidTest 构建通过。真机 `CleanRoomPackageTest` 1/1 通过，覆盖英语包安装、学习资料新增/查询、解绑后 Action 拒绝；使用隔离数据，不调用真实 AI/TTS，也不代表重新完成全部英语页面的人工验收。

SDK 当前保留在仓库中，`package.json` 的 `private: true` 暂不发布 npm。原因是 v1 包规范及宿主权限边界仍需真实第三方试用；发布前要确定版本兼容政策、分发包内文件、类型声明测试、变更日志和公开支持渠道。开发者目前可从本仓库引用 `template-sdk/src/`，CLI 会把运行时代码复制到本地模板包，不依赖 npm 在线加载。
