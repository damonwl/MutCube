# MutCube 架构设计

## 产品原则

MutCube 不是在聊天应用中嵌入几个网页，而是一个 AI 服务容器。聊天是一种交互界面，模板也是一种交互
界面；二者共同使用模型网关、空间上下文、记忆策略和用户数据平台。

## “空间”概念

新项目不设置 `Assistant`。取而代之的是 **空间（Space）**：

- 空间是可选的上下文容器，不是聊天对象，也不代表一个拟人化助手；
- 空间可以配置模型偏好、指令、记忆策略、模板服务和数据授权；
- 会话默认独立存在，用户可以把它加入空间，也可以从空间移出；
- 模板绑定空间，从而共享该空间允许使用的上下文和用户数据；
- 删除空间不应隐式删除用户拥有的会话和业务数据。

## 系统边界

```text
原生界面外壳
├── 聊天界面
├── 空间界面
└── 模板界面
        │
应用服务层
├── 会话服务
├── 空间服务
├── 模板运行时
├── 权限服务
└── 记忆服务
        │
核心层
├── 模型网关
├── 领域模型
├── 用户数据存储
└── 密钥存储
```

依赖方向只能向内。模板代码不能直接访问数据库、凭据、Android API 或模型供应商；所有特权操作都必须
经过宿主暴露的能力接口。

## 核心决策

1. 会话可以独立存在，也可以选择加入一个空间。
2. 模板绑定空间，不绑定会话。
3. 模板触发的 AI 工作保存为隐藏的 `TemplateRun`，不伪装成普通会话。
4. 业务数据归用户所有，模板只声明数据结构并获得可撤销的访问权限。
5. 历史事实默认不可变；未来计划只能通过经过授权和确认的变更提案修改。
6. 聊天与模板是原生外壳中的平级界面，切换时不产生嵌套导航。
7. 模型配置只引用安全存储中的凭据，领域记录和日志不得包含明文密钥。

## 初始模块

- `app`：Android 入口及依赖组装。
- `core:model`：不依赖具体业务实现的领域标识和访问规则。
- `core:database`：Room 数据库、DAO 和 Repository 实现；消息采用节点、候选版本、内容部件三层结构，
  不包含界面状态。
- `core:ai`：供应商无关的模型请求契约及 MiMo Provider。
- `core:security`：基于 Android Keystore 的本地凭据加密存储。
- `feature:chat`：聊天生成用例，负责组装上下文、调用模型、持久化完整或中止回复，以及安全替换重新生成
  的回复；不包含 Compose 界面。

模板现已拆分为 `template:core`、`template:runtime`、`template:builtin` 和 `template:ui`。采用不兼容旧设计的协议 2，由 GUI 与项目聊天共用 Action 引擎，宿主提供授权数据操作和模型网关。当前边界见 [模板模块说明](TEMPLATE_MODULES.md)。

Skill 系统的完整目标设计见 [Skill 系统设计](SKILL_SYSTEM_DESIGN.md)。Skill 是按需加载的标准工作流程，不替代模板 Action、项目记忆或宿主权限校验；当前只实现了其中一部分，文档开头列出实际进度。

## 当前数据流

```text
Compose UI → MutCubeViewModel ┬→ ConversationRepository → Room → SQLite
                              ├→ ChatGenerationService ┬→ ModelGateway → Provider API
                              │                        ├→ BuiltInToolService
                              │                        ├→ ConversationRepository
                              │                        └→ CredentialStore
                              └→ CredentialStore → Android Keystore
```

`app` 通过 `AppContainer` 进行手动依赖组装。当前规模不引入依赖注入框架；Repository 接口已经隔离 Room
类型，后续增加同步或测试实现时不需要修改界面层。

## Android 展示层职责（2026-10-08）

- `MutCubeApplication`：应用生命周期及受监督的模板初始化任务；不混入依赖构造实现。
- `AppContainer`：独立的组合根，组装 Repository、Gateway、Runtime 与平台服务。
- `MutCubeApp`：顶层导航、平台 Activity Result、权限与破坏性操作确认。
- `ChatSurface`：消息列表、流式展示、消息操作及附件展示。
- `TemplateCatalogSurface`：模板目录与预览。
- `SettingsDestinationContent`：设置子页路由；通过 `SettingsHostActions` 请求外壳执行平台操作。

页面拆分不改变原生聊天与 WebView 模板的平级关系，也不复制 ViewModel 或业务 Repository。
顶层仍保留较多导航协调代码，后续新页面继续按职责拆分，不为了行数引入新的框架或重写业务层。

内置工具采用宿主白名单，不允许模型指定任意代码、Shell、网址或数据库语句。当前只提供设备时间、记忆检索和
受设置开关约束的记忆写入；工具最多连续执行三轮。工具调用、结果和推理内容作为结构化消息部件持久化，正文
仍保持为最终回答。项目记忆与全局记忆均归用户所有，删除项目只将其记忆转为全局，不删除数据。
