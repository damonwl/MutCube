# GitHub 开源能力复用流程

## 目标

MutCube 不从头制造每一个轮子。对于网络通信、流式解析、数据库、加密存储、Markdown、WebView 安全、
JSON Schema 校验和测试工具等通用能力，开发前优先检索成熟项目。

## 检索步骤

1. 根据当前纵向功能列出需要解决的问题，而不是先寻找整套 AI 客户端；
2. 使用 GitHub 搜索候选仓库项目，优先官方库、维护活跃且职责单一的组件；
3. 固定候选版本或提交，读取根许可证和目标文件版权头；
4. 检查依赖树、安全公告、最近维护状态、最低 Android 版本和包体积；
5. 记录“采用、参考理念或放弃”的结论；
6. 采用后补充第三方声明、自动化测试和替换边界。

## 决策顺序

```text
Android 或 Kotlin 官方能力
→ 成熟 MIT / Apache-2.0 / BSD 等宽松许可证依赖
→ 合规复用宽松许可证源码并保留声明
→ 参考不兼容许可证项目的设计理念后独立实现
→ 最后才自行设计全新通用组件
```

## 引入方式

### 作为依赖

适合稳定、边界清晰的库。必须锁定版本，并通过我们自己的接口包裹，防止业务层直接依赖第三方类型。

### 复用源码

仅用于依赖方式不适合、代码规模可控且修改确有必要的兼容宽松许可证实现。需要记录原仓库、提交 SHA、文件路径、
原始许可证、MutCube 内路径和修改摘要。

### 只参考理念

不兼容许可证项目不得复制代码。先把观察到的产品行为和设计原则写成独立需求，再根据 MutCube 的模型、命名和
模块边界重新实现。设计参考不能演变成源代码的机械翻译。

## 每次引入的登记模板

```text
名称：
GitHub 地址：
固定版本或提交：
许可证：
用途：
引入方式：依赖 / 源码复用 / 仅参考理念
使用范围：
本地修改：
替换边界：
审核日期：
```

## 当前阶段的优先检索项

### 2026-09-16 模板本地页面及消息桥接

- 组件：AndroidX WebKit 1.16.0，Apache-2.0；以 Gradle 依赖引入。
- 官方资料：https://developer.android.com/jetpack/androidx/releases/webkit
- 使用范围：WebViewAssetLoader 提供本地 HTTPS 来源，WebViewCompat.addWebMessageListener 限制消息来源与主框架；权限与项目身份仍由 MutCube Repository 执行。
- JSON 契约使用已有 kotlinx.serialization，并实现有界子集校验，不宣称兼容完整 JSON Schema。支持 type/properties/required/additionalProperties/items/title/description，未知关键字拒绝。

### 2026-09-16 录音实现参考

- 项目：aahlenst/android-audiorecord-sample
- 地址：https://github.com/aahlenst/android-audiorecord-sample
- 许可证：Apache-2.0
- 引入方式：仅参考设计，不复制源码、不增加依赖。
- 使用范围：AudioRecord 独立采集线程、连续音频写入和麦克风资源生命周期。MutCube 自行实现 WAV 封装、音量波形和停顿检测；没有采用示例中的旧版存储与 Activity 代码。
- 审核日期：2026-09-16。

- OpenAI 兼容接口的数据模型与 SSE 流解析；
- Android Keystore 凭据加密封装；
- Room 会话树或消息分页的通用模式；
- WebView 内容隔离、来源校验和 JavaScript Bridge 防护；
- JSON Schema 或模板 Manifest 校验；
- Compose 聊天列表、输入区域和流式内容展示的基础组件。
