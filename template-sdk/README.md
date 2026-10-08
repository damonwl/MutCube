# MutCube Template SDK

首次开发请按 [从空目录到真机安装](QUICKSTART.md) 操作；包字段和授权边界以 [模板包 v1 规范](../docs/TEMPLATE_PACKAGE_V1.md) 为准。

`@mutcube/template-sdk` 是浏览器 ES Module + TypeScript 声明文件，不依赖 Android 编译环境。`src/index.js` 是运行代码，`src/index.d.ts` 提供类型；`MockHost` 可在本地浏览器中预览，正式包仍必须经过 App 的权限与 Action 校验。

先运行 `python3 scripts/mutcube-template init my-template`，然后在 `templates/<slug>/app.js` 中：

```js
import { connect } from '../../assets/mutcube-sdk.js';
const host = connect(window.MutCube);
const project = await host.project.info();
const page = await host.data.query('profile.list');
await host.data.create('profile.create', { goal: 'IELTS', level: 'B1', dailyMinutes: 20 });
```

`connect()` 使用宿主注入的安全消息桥。所有调用都有 request ID；超时不表示 Action 一定没有成功，写入操作应先查询运行记录或使用相同 request ID 核对。`host.invoke(actionId, input)` 是底层入口；`host.data.query/create/update/delete` 和 `host.ai.generate` 只是对已声明 Action 的易用包装，不会绕过 Manifest。`host.ui.theme/notice/confirm`、`host.project.info`、`host.navigation.openChat`、`host.clipboard.read` 和 `host.network.fetch` 须有相应权限。

本地调试可在页面加载前创建 `new MockHost({ handlers })`，其中 `handlers[actionId]` 返回与真实 Action 相同的 `{key, data, pendingConfirmation}` 结构。`network.fetch` 也可以注入同名 handler。`attachMockDevtools(mock)` 提供 Action 调用、权限开关、深浅主题和脱敏调用日志；不记录请求正文。Mock Host 的权限开关只是模拟，正式授权始终由 Android 宿主执行。英语学习样板在浏览器中自动加载该调试面板。

测试命令：`node --test template-sdk/test/*.test.js`。校验及打包：`python3 scripts/mutcube-template validate <目录>`、`python3 scripts/mutcube-template pack <目录> <输出.mutcube-template>`。命令会将 SDK 和 Mock Host 模块加入包、生成资源 Hash，且拒绝覆盖已有输出。`validate` 校验开发目录结构和基本声明，真正安装时仍由 Android 安装器再次校验完整协议、文件和 Hash。

可运行 `python3 scripts/mutcube-template types <目录> <输出.d.ts>`，从 Action 输入 Schema 和集合 Schema 生成 `TemplateActions` 类型。TypeScript 开发时使用 `connectTyped<TemplateActions>()`，`invoke` 的 Action ID、输入参数和返回值就有类型检查；普通 `connect()` 保持原有行为。例如：

```ts
import { connectTyped } from '@mutcube/template-sdk';
import type { TemplateActions } from './template-actions';
const host = connectTyped<TemplateActions>();
const result = await host.invoke('profile.create', { goal: 'IELTS', level: 'B1', dailyMinutes: 20 });
console.log(result.data.revision);
```

类型生成器仅覆盖协议采用的基础 JSON Schema 类型。`GENERATE` 的模型输出被标为 `unknown`：模板必须在运行时按自己的业务 Schema 校验，不能把静态类型当成 AI 输出保证。生成的类型是开发辅助，Android 宿主仍以 Manifest 校验为准。

常见失败：`NO_HOST` 表示未在 Android 运行且未传入 Mock Host；`TIMEOUT` 表示宿主未在限定时间内回应；宿主返回的其他错误请查看模板运行记录、权限和模型配置。SDK 不提供 API Key 读取、任意文件读取或跨项目数据库访问。完整错误处理见 [错误码说明](ERRORS.md)。

## 官方开发样板：英语学习

[`examples/english-reader`](examples/english-reader/README.md) 是 `init` 默认生成的完整样板：学习目标设置、原创模拟文章、点词与选句、词典释义、AI 语境解释、宿主 TTS、生词本与自评复习。它使用原生 JavaScript，不需要 npm 构建，也不要求第三方模板采用统一 UI 框架。数据集合、GUI/CHAT Action、权限和内容许可均随样板提供；WordNet 数据须保留其独立许可，在线 Wiktionary 释义须保留来源。它不内置未经授权的真题。

[`examples/speech-demo`](examples/speech-demo/README.md) 展示宿主语音能力。声明 `speech.speak` / `speech.recognize` 后，通过 `host.speech.speak(text)`、`host.speech.stop()`、`host.speech.recognize()` 和 `host.speech.stopRecognition()` 调用。录音 UI、系统权限、Provider 配置、密钥和音频文件均由宿主管理；模板只收到文字或完成状态。模板语音不会回退系统服务；只有对应的 TTS/ASR Provider、模型和凭据已配置，能力才会列入 `host.permissions.status().available`。`host.on('speech', listener)` 可订阅 `preparing`、`playing`、`recording`、`transcribing`、`finished` 状态及音量级别。

英语练习可调用 `host.speech.recognize({ language: 'en' })`；`'zh'` 指定中文，`'auto'` 让服务自动识别，省略参数则沿用宿主全局语音设置。语言选择只影响本次转写，不改变用户设置，也不允许模板选择 Provider 或模型。宿主原生确认框会显示本次语言。

宿主通过 `--bg`、`--fg`、`--card`、`--muted`、`--action`、`--onaction` 提供主题色，英语样板直接使用这些变量，并在浏览器 Mock Host 中预览深浅主题。模板自身负责布局、字号和圆角；原生 App 的 Compose 组件不会变成 WebView 组件。

旧 Todo/Vant Todo 目录仅保留为历史试点和回归材料，不再作为默认初始化或推荐开发样板。
