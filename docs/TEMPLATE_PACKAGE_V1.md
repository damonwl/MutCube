# 第三方模板包协议 v1

状态：v1 稳定协议（2026-10-02）。基础包适用于 MutCube 0.1.2+；语音能力要求 0.1.3+。包格式版本与模板 Action 协议是两个独立字段：`packageVersion: 1`，`template.protocolVersion: 2`。v1 的既有字段、Action 语义、授权及数据边界不做静默破坏性变更；未来新增宿主能力须显式声明并提高 `minHostVersion`，旧宿主不会自动支持。安装器不兼容旧模板包，也不执行任意 Android/Kotlin 代码。

## 包结构

扩展名 `.mutcube-template`，内容为 ZIP。根目录必须有 `manifest.json`，所有其他文件必须逐一列入 `resources` 并提供 SHA-256。允许 `templates/`、`assets/`、`schemas/`、`skills/` 和根目录 `README.md`；入口固定为 `templates/<slug>/index.html`。不要放密钥或其他私有信息，包内容对安装者可见。

```text
manifest.json
templates/english/index.html
templates/english/app.js
templates/english/wordnet.js
assets/mutcube-sdk.js
assets/mock-host.js
assets/WORDNET_LICENSE.txt
README.md
```

`manifest.json` 顶层字段必须且只能为 `packageVersion`、`minHostVersion`、`developer`、`permissions`、`networkDomains`、`resources`、`template`。`developer` 有 `name`，可选 HTTPS `website` 和 SPDX 风格 `license`；宿主展示但不核实开发者或许可证声明。`minHostVersion` 和模板 `version` 使用三段数字版本号，每段 1–9 位数字。`template` 遵循现有 Action 协议 2：声明模板 ID、集合与 JSON Schema、Action 及 GUI/CHAT 渠道、Web 页面入口和 `capabilities`。完整可运行样板见 [`template-sdk/examples/english-reader/manifest.json`](../template-sdk/examples/english-reader/manifest.json)。

`resources` 的键是包内相对路径，值是该文件字节内容的 SHA-256 小写十六进制。开发时可暂填 `{}`，`pack` 会生成完整 Hash 清单；直接导入未打包目录不受支持。包最大解压总量 12 MiB、单文件 2 MiB、资源 128 个；拒绝重复路径、路径穿越、未声明文件以及 Hash 不符。SHA-256 只校验资源完整性，**不是开发者签名或身份验证**。

`schemas/` 与 `skills/` 当前仅作为可校验的静态资源目录；宿主不会因模板安装而自动安装 Skill，也不会执行其中的代码。

## 能力与授权

声明权限不等于取得权限。安装前展示开发者、版本、集合、能力及网络域名；绑定项目时由用户确认。权限由宿主按 `项目 ID + 模板 ID + 版本 + 集合 + 操作` 检查，模板页面不接触 Room、API Key、其他项目数据或任意文件系统。聊天侧只可调用 Action 中标为 `CHAT` 的操作；直接修改数据的 Action 不得暴露给聊天，版本化 AI 提案走确认流程。

v1 实际支持的包权限：

| 权限 | 档位 | 行为 |
| --- | --- | --- |
| `data.records` | 基础 | 必需；通过声明的 `action.run` Action 做查询、增删改与版本操作 |
| `host.permissions` | 基础 | `granted` 表示包已授权；`available` 表示当前可调用（语音能力还需完成对应服务配置）；另返回域名，不读取其他模板授权 |
| `ai.generate` | 基础 | 允许声明 `GENERATE` Action，调用宿主配置的模型 |
| `project.info` | 基础 | 仅返回当前项目 ID 和名称 |
| `ui.theme`、`ui.notice`、`ui.confirm` | 基础 | 读取主题色、提示及原生确认框 |
| `navigation.openChat` | 基础 | 返回当前项目的聊天界面，亦需在 `template.capabilities` 声明 |
| `file.pick` | 基础 | 每次由用户在系统文件选择器选取，返回名称、MIME 与最多 2 MiB 的 Base64 内容 |
| `media.image.pick` | 敏感 | 每次由用户在系统图片选择器选取，返回最多 2 MiB 的 Base64 图片 |
| `clipboard.read` | 敏感 | 每次读取都弹出原生确认，限制 10,000 字符 |
| `network.fetch` | 敏感 | 仅 HTTPS GET；合法 DNS 域名必须精确列入 `networkDomains`（不接受 IP 字面量），拒绝重定向、私网地址和非文本响应，最多 128 KiB |
| `speech.speak` | 敏感 | 仅在用户配置可用的 TTS Provider、模型和凭据后开放；模板不能选用 Provider、读取密钥或获取音频文件，不回退系统朗读 |
| `speech.recognize` | 敏感 | 仅在用户配置可用的 ASR Provider、模型和凭据后开放；用户主动触发原生录音，仅返回文字，取消时返回 `cancelled`，不回退系统识别。可按次传 `language: "en" | "zh" | "auto"`；省略时沿用用户全局设置，不允许模板切换 Provider 或模型 |

语音两项从 MutCube 0.1.3 起支持；声明其中任一能力的模板应将 `minHostVersion` 设为至少 `0.1.3`。已有 v1 模板包及 Action 协议 2 不变。

`speech.stop` / `speech.recognize.stop` 仅用于停止当前模板自己启动的语音任务，不需单独声明，但须分别拥有 `speech.speak` / `speech.recognize` 权限。`action.run` 与 `navigation.close` 是模板 Action 协议中的桥接能力，不属于包的 `permissions`。`data.records` / `ai.generate` 是对 Action 模式的授权，不提供任意 SQL 或任意模型请求。相机、通知、日历、项目对话全文与跨项目访问均不属于已实现能力。网络能力可能把模板可见内容发送到允许域名；语音 Provider 也可能接收朗读文字或录音，安装时应审阅权限及来源。

## 运行与生命周期

第三方页面运行在各模板独立的 HTTPS 虚拟来源 WebView。资源仅从已校验的本地包读取；WebView 禁止直接网络、文件和 Content 访问，网络请求必须通过受控 `network.fetch`。JS 桥只接受主框架及对应来源消息。每次 Action 都经宿主协议校验、项目绑定和数据权限检查。

第三方页面的 CSP 只允许从本模板来源加载脚本（`script-src 'self'`），不运行 HTML 中的内联 `<script>`。请将 JavaScript 放在包内独立文件，使用例如 `<script src="app.js"></script>` 引入，并把文件列入资源 Hash。CLI 的 `validate` / `pack` 会拒绝入口页的内联脚本；`example-fitness` 导出器会自动把内置页面脚本拆为 `app.js`。

同 ID 的安装包必须是更高版本；现有集合的 ID、策略与 JSON Schema 不得隐式改变。升级时新的包替换旧包，项目绑定仍须匹配版本并重新授权。v1 不执行迁移脚本；需要集合迁移时必须等待后续显式迁移协议，不能偷偷改 Schema。

卸载先解除所有项目绑定。用户可选择保留宿主数据库中的模板记录（默认），或输入模板 ID 后永久删除全部项目中属于该模板的数据。重新安装同 ID 的包不会自动恢复授权；数据仍受版本和权限检查。

目前只支持本地导入，未提供签名、开发者认证、模板市场或自动更新。请只安装信任来源的包。备份 v3 包含已安装模板资源与 Room 记录；旧备份如只含模板数据而不含对应可执行包，用户需要重新导入模板并授权。恢复时历史授权会被撤销，不会静默授予第三方包权限。

## 开发和验证

```bash
python3 scripts/mutcube-template init my-template
python3 scripts/mutcube-template validate my-template
python3 scripts/mutcube-template types my-template my-template-actions.d.ts
python3 scripts/mutcube-template pack my-template my-template.mutcube-template
python3 scripts/mutcube-template example-fitness build/fitness-example
python3 scripts/mutcube-template pack build/fitness-example build/fitness-example.mutcube-template
```

`example-fitness` 从当前内置训练模板源码导出完整协议与页面，使用独立的 `example.fitness` ID，不共享内置模板数据。首次开发请从 [独立开发者快速上手](../template-sdk/QUICKSTART.md) 开始；SDK、Mock Host、错误与调试说明见 [`template-sdk/README.md`](../template-sdk/README.md)。安全审查边界见 [模板安全验收](TEMPLATE_SECURITY_REVIEW.md)。

## 样板与 UI 技术栈

`init` 默认生成 `template-sdk/examples/english-reader/` 的英语学习样板，使用原生 JavaScript、SDK 和 Mock Host，无需前端构建。旧 Todo/Vant Todo 仅保留为历史试点和回归材料。第三方开发者可采用 Vue/Vant 或其他 UI 框架，但 Manifest、Action、项目授权与数据归属规则不变。

模板页面的 Vue/Vant 脚本和样式必须打进本地包。WebView 不加载 CDN，不放宽 `script-src 'self'`；构建后仍遵守单文件 2 MiB、包总量 12 MiB、最多 128 个资源的上限。Vant 不赋予模板新的宿主权限，也不允许直接访问 Room、密钥或项目外的数据。
