# 内置模板协议 2

每个项目只启用一个模板。用户在授权界面选择其他模板时，宿主在同一事务中禁用旧绑定并撤销旧权限，随后启用新绑定；旧模板的数据、版本与执行记录保留，不迁移给新模板。重新授权旧模板可恢复访问其原数据。旧版本已经启用多个模板的项目，在模板选择入口重新确认保留哪个模板，不擅自删除或选择用户数据。

日期：2026-09-17。旧协议 1、隐式 main、interactions、旧 data.* 桥接、interaction.run、ui.emit 和类型别名均已移除。

## 服务声明

Manifest 必须且仅包含 protocolVersion=2、id、version、name、entry、collections、actions、capabilities。入口只能是本地 templates/<name>/index.html。

集合声明 id、schema、policy，策略为 IMMUTABLE_HISTORY、MUTABLE、VERSIONED。集合 JSON 结构与安全校验由宿主执行，不能传入 SQL 或自行选择项目。

Action 声明 id、description、mode、channels、inputSchema、collection。GENERATE 另需 requestCollection 和 instruction；输入快照集合必须只追加、与输出集合不同。bindings 可注入指定集合的最新记录（LATEST）或已确认版本（CURRENT），字段不能由调用者覆盖，必须声明 required。

执行类型：

- APPEND：按集合契约新增记录，稳定 requestId 幂等，同键不同内容拒绝。
- LIST：分页读取，公开参数为可选 beforeTime / beforeKey，必须同时出现或同时省略；每页最多 20 条。
- CURRENT：空对象输入，读取版本指针及已确认内容。
- UPDATE：key、expectedRevision、data；只允许 MUTABLE，事务内修订检查。
- DELETE：key、expectedRevision；只允许 MUTABLE，原生确认后再次检查授权和修订。
- GENERATE：固定声明输入/输出集合，先保存输入，再执行 AI，校验输出后事务化保存。VERSIONED 输出要求公开输入含 baseRevision。
- SELECT_VERSION：key、expectedRevision；只允许 VERSIONED，要求对应成功生成运行及一致的源修订，宿主确认后切换指针。受信任内置 GUI 可由宿主允许可视化启用点击直接确认，默认仍弹出原生确认；Manifest/请求不能自行开启，CHAT 不允许版本切换。

CHAT 只能声明 LIST / CURRENT / GENERATE，不授予直接修改事实或自动启用候选的权限。

## 页面桥接

请求：`{protocolVersion:2,requestId,capability:"action.run",input:{actionId,data}}`。

成功：`{requestId,result:{key,data,pendingConfirmation}}`；失败：`{requestId,error}`。data 是结构化 JSON，LIST 包含 records 和下一页游标 next。项目及模板身份从原生装配获取，不由页面传入。

仅额外允许 navigation.openChat 和 navigation.close；后者由原生返回触发退出钩子后调用，携带 input:{accepted:boolean}，请求 ID 必须匹配原生退出请求。拒绝退出立即解除等待；导航不依赖数据权限，避免权限撤销后无法离开。WebView 禁止网络、文件、内容访问与本地存储，只允许该模板单个入口资源，支持宿主主题变量。

GUI 与聊天都调用 TemplateActionEngine.execute；没有旧数据操作或 prepared-input 绕过路径。模型密钥不会下发到网页或持久化到运行诊断中。

## 样例

`template:builtin/NotesTemplate.kt` 声明 notes（MUTABLE）、requests（IMMUTABLE_HISTORY）、documents（VERSIONED）及 8 个 Action。业务界面位于 app 的 templates/notes/index.html；新增业务不需要给宿主添加业务字段分支。

`template:builtin/FitnessTemplate.kt` 声明资料、草稿、实际记录、请求和计划五个集合及 11 个 Action，全部复用通用引擎。页面位于 templates/fitness/index.html；契约与验收边界见 [健身服务协议 2](FITNESS_SERVICE_V2.md)。
