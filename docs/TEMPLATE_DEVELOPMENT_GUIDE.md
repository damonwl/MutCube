# MutCube 模板开发说明

日期：2026-09-17。适用版本：当前代码中的模板协议 2。

本文面向内置模板开发者，以实际实现为准。第三方模板请从 [模板包 v1 规范](TEMPLATE_PACKAGE_V1.md) 与 [Template SDK](../template-sdk/README.md) 入手，官方开发样板为 [英语学习模板](../template-sdk/examples/english-reader/README.md)。本文仍用于理解共用的 Action 协议 2。旧协议、旧健身模板及其数据不再兼容。

## 1. 开发模型

模板是一个服务定义，而不只是聊天提示词或 HTML 页面：它声明业务数据结构、可执行操作、AI 指令及 GUI。宿主提供数据库、权限检查、模型网关、凭据和原生确认。

项目使用模板时形成服务实例。当前以 `(projectId, templateId)` 唯一定位，一个项目中的同一种模板只支持一个实例。同一模板用于不同项目时，数据默认隔离。

模板 GUI 和项目聊天是服务的两种入口，统一执行已声明的 Action。AI 负责生成、整理等不确定性工作；权限、结构校验、事务和版本检查仍由确定性程序执行。

| 概念 | 开发者需要理解的含义 |
| --- | --- |
| Manifest | 模板身份、版本、入口、集合及 Action 的声明 |
| Collection | 业务数据契约及修改策略 |
| Action | 某项公开操作及其输入契约、入口范围 |
| Binding | 将已授权的持久化内容注入 Action 输入，不是项目绑定 |
| 项目绑定 | 用户将指定版本的模板启用到项目，并授权必要操作 |
| TemplateRun | 隐藏 AI 执行的输入、输出、状态及上下文记录 |
| 当前版本指针 | 指向用户已经确认的候选；不等于最近生成的记录 |

数据归用户，由宿主 Room 数据库保存，不是每个模板附带一份数据库。记录以项目、模板、集合及键定位。停用模板不清理用户数据；删除项目后的保留数据可在数据管理中查看，但不能继续以已删除项目身份调用服务。

## 2. 当前支持范围

可以实现：

- 随 APK 发布的 HTML 服务界面及原生主题适配。
- 数据新增、分页查询、可修改集合的更新和删除。
- GUI / CHAT 两种入口和宿主上下文注入。
- 使用项目配置的 Provider、模型及密钥进行 AI 生成。
- 输入及结果持久化、隐藏运行记录、近期结果与历史摘要。
- 版本候选、原生确认及修订冲突检查。

本节描述内置模板的早期能力范围。现已另有本地第三方包安装和开发者 SDK；仍没有远程模板市场、开发者签名、多实例、任意 SQL、任意原生接口、独立 Proposal 表、聊天通用确认写入或软删除回收站。具体边界以模板包 v1 规范为准。

能力声明和集合权限不能被视为完整第三方沙箱。内置模板随应用发布；第三方模板按包协议在独立来源 WebView 中运行，但安装包来源仍须用户自行审阅。

## 3. 文件及接入位置

```text
template/builtin/src/main/java/com/dwl/mutcube/template/builtin/
  NotesTemplate.kt                 # 已有服务声明，可作为开发参考
  YourTemplate.kt                  # 新增服务声明

app/src/main/assets/templates/
  notes/index.html                 # 已有服务页面
  your-template/index.html         # 新增页面，样式和脚本内联

app/src/main/java/com/dwl/mutcube/
  MutCubeApplication.kt            # AppContainer.builtinTemplates 注册

template/core/                     # 通用声明与执行引擎，不添加业务字段
template/runtime/                  # 宿主数据和模型适配，不添加业务分支
template/ui/                       # WebView、确认及运行记录
core/database/                     # 宿主持久层
```

开发顺序：

1. 确定业务数据集合及修改策略。
2. 为每个用户操作定义 Action，决定是否向聊天开放。
3. 使用 `TemplateManifest.parse(...)` 校验声明；不要绕过解析器直接构造未校验对象。
4. 编写本地 HTML 页面，通过桥接调用 Action。
5. 将 `YourTemplate.manifest()` 加入 `AppContainer.builtinTemplates`。
6. 构建 App；在项目中授权启用，再验证持久化及聊天入口。

当前注册使用 Kotlin 定义返回 Manifest。可以在 Kotlin 中生成 JSON 或解析字符串；将 manifest.json 放进目录不会自动安装或注册模板。

## 4. Manifest 格式

顶层必须且仅包含以下字段，不能添加未支持的扩展字段：

| 字段 | 格式及约束 |
| --- | --- |
| protocolVersion | 数字 `2`，不接受字符串或协议 1 |
| id | `[a-z][a-z0-9_.-]{0,79}`；发布后保持稳定 |
| version | 三段数字，例如 `2.0.0` |
| name | 非空显示名称，最长 100 字符 |
| entry | `templates/<目录>/index.html`；目录仅含小写字母、数字和连字符 |
| collections | 1～30 个集合，ID 不重复 |
| actions | 1～50 个 Action，ID 不重复 |
| capabilities | 必须含 `action.run`；只能额外声明两项导航能力 |

声明字符串最长 100,000 个字符。集合和 Action 的 ID 使用相同的标识格式；这是代码检查的格式，不应自行放宽。

### 4.1 可直接解析的最小示例

下面是只保存和查询便笺的完整声明，不依赖 AI 配置：

```json
{
  "protocolVersion": 2,
  "id": "example.memo",
  "version": "2.0.0",
  "name": "便笺",
  "entry": "templates/memo/index.html",
  "capabilities": ["action.run", "navigation.openChat", "navigation.close"],
  "collections": [
    {
      "id": "memos",
      "policy": "MUTABLE",
      "schema": {
        "type": "object",
        "properties": {"text": {"type": "string"}},
        "required": ["text"],
        "additionalProperties": false
      }
    }
  ],
  "actions": [
    {
      "id": "memo.save",
      "description": "保存用户便笺",
      "mode": "APPEND",
      "channels": ["GUI"],
      "collection": "memos",
      "inputSchema": {
        "type": "object",
        "properties": {"text": {"type": "string"}},
        "required": ["text"],
        "additionalProperties": false
      }
    },
    {
      "id": "memo.list",
      "description": "分页读取当前项目便笺",
      "mode": "LIST",
      "channels": ["GUI", "CHAT"],
      "collection": "memos",
      "inputSchema": {
        "type": "object",
        "properties": {
          "beforeTime": {"type": "integer"},
          "beforeKey": {"type": "string"}
        },
        "required": [],
        "additionalProperties": false
      }
    }
  ]
}
```

接入时需要同时创建 `templates/memo/index.html` 并在注册列表加入此声明。该示例不是已经发布的模板。

### 4.2 Schema 支持范围

宿主实现的是有限 JSON Schema 子集，不是完整标准实现：

- 类型：object、array、string、number、integer、boolean。
- 关键词：type、properties、required、additionalProperties、items、title、description。
- 所有 object 必须设置 `additionalProperties: false`。
- 建议所有对象显式写出 properties 和 required；Action 及完整生成输入的处理依赖这些字段。
- array 必须声明 items；数组数据最多 1,000 项。
- 每个对象契约最多 100 个属性，递归深度检查上限为 12。
- 数据不接受 null；可选字段应省略，而不是赋值 null。
- number 要求有限数值；integer 要求可表示为有符号 64 位整数。

暂不支持 enum、minimum、maximum、minLength、format、$ref、oneOf 等关键词；加入会导致校验失败，而不是被忽略。空文本、业务范围和互斥选项等校验应在 GUI 实现，不要误认为宿主已检查这些业务约束。

## 5. 数据集合策略

| policy | 适用数据 | 更新与删除 | 当前版本 |
| --- | --- | --- | --- |
| MUTABLE | 可编辑资料、普通笔记 | 允许声明 UPDATE / DELETE | 不支持 CURRENT / SELECT_VERSION |
| IMMUTABLE_HISTORY | 已提交事实、AI 请求快照 | 不允许覆盖和删除 | 不支持 CURRENT / SELECT_VERSION |
| VERSIONED | AI 生成的计划或文稿 | 不覆盖、不删除旧版本 | 支持读取与确认指针 |

新增记录 revision 从 1 开始。可修改记录更新后 revision 增加；没有回收站，删除经原生确认后是真实删除。

启用时宿主按 Action 和 bindings 计算必要集合权限。页面不能通过请求自行选择项目或模板身份，也不能调用未声明的操作。集合策略声明并不自动开放每一种操作，必须存在对应 Action。

## 6. Action 声明及输入

每个 Action 必须声明 id、description、mode、channels、inputSchema、collection。description 非空且最多 2,000 字符，也是聊天模型理解工具用途的重要说明。

可选字段仅为 requestCollection、instruction、bindings。前两项只用于 GENERATE；其他执行类型不能声明它们。

| mode | 输入 data | 关键要求 |
| --- | --- | --- |
| APPEND | 符合集合契约的对象 | 注入上下文后的输入契约必须与目标集合契约一致 |
| LIST | `{}` 或双游标对象 | inputSchema 必须声明可选 beforeTime / beforeKey |
| CURRENT | `{}` | inputSchema 是 properties={}、required=[] 的严格空对象 |
| UPDATE | `{key,expectedRevision,data}` | MUTABLE；内层 data 的契约必须等于目标集合契约 |
| DELETE | `{key,expectedRevision}` | MUTABLE；原生确认 |
| GENERATE | 公开生成输入 | 另需 instruction 和 requestCollection |
| SELECT_VERSION | `{key,expectedRevision}` | VERSIONED；成功生成运行、源修订一致及原生确认 |

UPDATE、DELETE、SELECT_VERSION 的 key 为 string、expectedRevision 为 integer；这些字段必填且不能声明 bindings。LIST 和 CURRENT 也不能声明 bindings。

### 6.1 修订值不要混用

- UPDATE / DELETE 的 expectedRevision：目标记录的 revision，必须大于 0。
- GENERATE 的 baseRevision：目标集合当前版本指针的 revision，未确认任何版本时为 0。
- SELECT_VERSION 的 expectedRevision：同一版本指针的 revision，不是候选记录自身的 revision。

SELECT_VERSION 不接受普通 APPEND 记录作为任意候选。必须存在对应 SUCCEEDED 的生成运行，而且该运行的目标集合及输入 baseRevision 与本次确认一致。

源版本发生变化后，旧候选仍保留，但不能以新指针修订直接强行启用；应刷新 CURRENT 并重新生成。

### 6.2 GUI 与 CHAT

聊天能力可声明可选的 `title` 和 `example`，用于原生能力面板及执行卡片；结果定位事件与初始化消费约定见 [聊天模板交互](TEMPLATE_CHAT_INTERACTION.md)。这些字段不改变动作权限。

channels 至少包含一个入口：GUI 或 CHAT。只有 LIST、CURRENT、GENERATE 可以向 CHAT 开放；APPEND、UPDATE、DELETE 和 SELECT_VERSION 不允许直接交给模型。

宿主按照模板 ID 和 Action ID 的摘要生成聊天工具名，不需要开发者手写固定工具名。调用前仍检查项目绑定及授权；同项目多个正常会话共享该服务实例的数据访问能力，不需要为每个会话创建模板实例。

写好工具描述，例如：“先读取当前 revision，再生成候选，提醒用户进入模板确认；不得自动启用。” 不要仅写“生成”，也不要给模型提供任意 SQL 或跨项目身份参数。

## 7. AI 生成与宿主上下文

新增 GENERATE 时，至少准备两个集合：只追加的请求快照集合，以及输出集合。两者不能同名。

公开 inputSchema 描述页面或聊天可以提供的参数。宿主注入 bindings 后形成完整输入；requestCollection 的 schema 必须与该完整契约一致。输出结构直接由 collection 的 schema 决定，没有额外 outputSchema 字段。

binding 的 field 使用小写标识符，遵循 `[a-z][a-z0-9_.-]{0,79}`，例如 `current_plan`，不要使用 `currentPlan`。

以下是智能笔记的 Action 片段，应放在已经声明 notes、requests、documents 的 Manifest 中，不能单独作为完整 Manifest 解析：

```json
{
  "id": "document.generate",
  "description": "整理最近笔记为候选。先读取当前版本 revision，传 baseRevision；用户确认前不生效。",
  "mode": "GENERATE",
  "channels": ["GUI", "CHAT"],
  "collection": "documents",
  "requestCollection": "requests",
  "instruction": "按 request 整理 note，保留事实。只返回包含 text 字段的中文 JSON 对象。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "request": {"type": "string"},
      "baseRevision": {"type": "integer"}
    },
    "required": ["request", "baseRevision"],
    "additionalProperties": false
  },
  "bindings": [
    {"field": "note", "collection": "notes", "source": "LATEST", "required": true}
  ]
}
```

对应完整 requests 契约必须包含 request、baseRevision 和 note；note 是 notes 集合的完整对象契约。required=true 的注入字段加入完整输入的 required 列表。GUI 和聊天都只传 request/baseRevision，不能自己传 note。

Binding 的 source：

- LATEST：读取指定集合按更新时间排序的第一条记录，注入其业务对象，不注入 key/revision 包装。
- CURRENT：读取 VERSIONED 集合已确认的业务对象，不选择未确认的最新候选。
- required=true：缺失上下文则拒绝执行；required=false：缺失时不注入字段。

当前绑定不能选择某个自定义 key、筛选全部历史或执行任意查询。LATEST 遇到相同更新时间也不能视为可靠的业务事件顺序；如业务依赖精确顺序，需另外设计，不能依赖它推断。

### 7.1 实际生成上下文

运行时使用项目的模型覆盖及全局配置，并从宿主读取密钥。生成上下文包含项目/全局指令、Action instruction、输出契约、已授权记忆、目标结果集合的有界近期数据及较早结果摘要缓存。缓存失效时使用注明来源和截断的本地原始摘录，不先请求模型生成摘要；项目对话证据预先展开，充分时直接生成，仅缺关键事实时最多两轮补充检索。

普通聊天 A 的全部历史不会全文注入模板调用。宿主现在自动检索本项目相关会话，并为支持工具的模型提供只读项目搜索与来源展开；范围、预算及限制见 PROJECT_CONTEXT.md。不能把“同项目”理解为模型天然知道全部内容，聊天证据也不是已确认业务记录。其他集合的结构化事实仍通过声明 bindings 等明确输入路径传入。

当前 GENERATE 是一次结构化模型生成，不是任意 Agent 工作流。它不自动提供网页检索、MCP 或多步骤工具编排；不要因为聊天支持某项工具就假设模板生成也支持。

### 7.2 候选生成与确认

1. 调用 CURRENT，取得当前指针 revision；首次为 0。
2. 调用 GENERATE，传入 baseRevision。
3. 宿主保存完整输入及运行，调用模型并校验结果；写入结果前重新检查源版本。
4. 页面展示候选，不能仅凭“生成成功”标记为已启用。
5. 用户触发 SELECT_VERSION，传生成响应的外层 key 及源指针 revision。
6. 宿主接受用户确认后再次检查绑定、权限和修订，再切换当前指针。受信任内置模板 GUI 可由宿主开启单次可视化确认：预览中的“启用”点击直接生效，不额外展示 JSON 弹窗。此选项由宿主代码提供，桥接请求与 Manifest 不能开启；默认关闭，未受信任模板和删除操作仍需要原生确认。内置代码必须确保生成结束不自动调用 SELECT_VERSION，仅从明确的启用按钮调用；聊天引擎不使用 GUI 确认回调。
7. 刷新 CURRENT，显示真正已生效的内容。

运行不进入普通聊天历史。开发者和用户可从原生“运行记录”查看输入、结果及上下文；摘要只是辅助上下文，不替代原始事实。

## 8. HTML 与桥接

### 8.1 安全及资源约束

当前 WebView 只允许加载声明的单个 index.html，阻止网络、文件及 content 访问，关闭 DOM 存储。CSS 和 JavaScript 应内联；不能依赖 CDN、外链 CSS/JS、相对资源文件、localStorage 或 IndexedDB。

不要在页面填写或保存 API Key，不直接 fetch 模型接口，不申请任意宿主能力。业务数据通过已声明 Action 保存；渲染用户或 AI 内容优先用 textContent，避免不受控的 innerHTML。

### 8.2 可复用的桥接代码

下面代码可内联到页面脚本中。页面只安装一个 MutCube.onmessage 处理器，统一关联并发请求。

```javascript
const pendingRequests = new Map();

MutCube.onmessage = event => {
  const response = JSON.parse(event.data);
  const pending = pendingRequests.get(response.requestId);
  if (!pending) return;
  clearTimeout(pending.timer);
  pendingRequests.delete(response.requestId);
  if (response.error) pending.reject(new Error(response.error));
  else pending.resolve(response.result);
};

function invoke(capability, input, requestId = crypto.randomUUID()) {
  if (pendingRequests.has(requestId)) {
    return Promise.reject(new Error('同一请求仍在等待响应'));
  }
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      pendingRequests.delete(requestId);
      reject(new Error('等待超时，请检查运行记录后刷新，勿盲目重复提交'));
    }, 200000);
    pendingRequests.set(requestId, {resolve, reject, timer});
    try {
      MutCube.postMessage(JSON.stringify({
        protocolVersion: 2,
        requestId,
        capability,
        ...(input === undefined ? {} : {input})
      }));
    } catch (error) {
      clearTimeout(timer);
      pendingRequests.delete(requestId);
      reject(error);
    }
  });
}

const runAction = (actionId, data = {}, requestId) =>
  invoke('action.run', {actionId, data}, requestId);

// 上文最小示例中的操作；放在 async 函数或事件处理器内执行。
async function saveMemo(text) {
  if (!text.trim()) throw new Error('请输入便笺内容');
  const response = await runAction('memo.save', {text});
  return response.data; // {key, revision, data:{text}, updatedAt}
}

async function listMemos(cursor = {}) {
  const response = await runAction('memo.list', cursor);
  return response.data; // {records:[...], next:对象或null}
}
```

requestId 只能含字母、数字和连字符，长度 1～80。不要使用日期中的斜杠、下划线或路径，也不要在不同业务操作之间故意复用 ID。

### 8.3 响应结构

成功响应外层统一为：

```json
{
  "requestId": "请求标识",
  "result": {
    "key": "请求标识",
    "data": {},
    "pendingConfirmation": false
  }
}
```

外层 result.key 是本次请求 ID，不总是目标业务记录键。UPDATE、SELECT_VERSION 等目标键应从内层 data.key 读取；GENERATE 的外层 key 可用作候选键。

各类型内层 data：

| 操作 | result.data |
| --- | --- |
| APPEND / UPDATE | `{key,revision,data:<业务对象>,updatedAt}` |
| LIST | `{records:[<记录包装>],next:<游标或null>}` |
| CURRENT | `{revision,key,data:<业务对象或null>}`，无当前版本时 revision=0 |
| GENERATE | 集合契约要求的业务对象；VERSIONED 时 pendingConfirmation=true |
| DELETE | `{confirmed:true}`；取消确认时为 false |
| SELECT_VERSION | `{confirmed:true,key,revision}`；取消确认时仅有 confirmed=false |

失败响应为 `{requestId,error:"可展示的错误说明"}`。不要读取已经删除的旧 json 字符串字段，不要把取消确认当作成功修改。

LIST 每页最多 20 条，另有约 90,000 字符的记录预算，可能提前截断一页。只要 next 非 null 就继续分页，不以 records.length 是否为 20 判断是否结束。极大单条记录无法装入预算时会失败，建议业务数据保持小而结构化。

### 8.4 主题

宿主向页面根元素注入以下 CSS 变量，并在主题变化时更新：

```css
:root {
  --bg: #f7f7f5;
  --fg: #222;
  --card: #fff;
  --muted: #777;
  --action: #333;
  --onaction: #fff;
}
body { background: var(--bg); color: var(--fg); }
button { background: var(--action); color: var(--onaction); }
dialog { background: var(--card); color: var(--fg); }
```

初始值是回退值，不应覆盖宿主注入值。所有标题、输入、弹窗、空态和错误态都检查深浅色；不要固定黑色文字或白色卡片。

### 8.5 导航及退出

打开当前项目正常聊天：`await invoke('navigation.openChat')`。如有未保存输入，页面应先询问或保存，再调用导航。

如果声明 navigation.close，原生返回会调用可选 `window.MutCubeBeforeLeave`：返回 true 允许离开，false 保留页面，支持 Promise。宿主随后发送带原生请求 ID 的 navigation.close，页面不要自行生成 ID 来模拟此能力。

```javascript
window.MutCubeBeforeLeave = async () => {
  if (savingOrGenerating) return false;
  if (!hasUnsavedChanges()) return true;
  return await showOwnLeaveDialog();
};
```

以上变量和函数由业务页面实现，参照智能笔记的 `<dialog>`。不要依赖浏览器默认 confirm/alert，当前 WebView 没有为它们实现完整 UI。不要仅修改内存状态就返回 true 并宣称“已保存”。

退出钩子等待保护为 30 秒。页面长时间不回应时宿主保留内容并解除等待；这不是自动保存保证。系统强杀、崩溃或页面销毁不能靠退出钩子保证保存，必要的草稿应在编辑期间调用保存 Action。

## 9. 幂等、失败及重试

- APPEND 用 requestId 作为记录键；相同 ID 与相同保存内容可重放，同 ID 不同内容拒绝。
- 成功 GENERATE 可使用同一 ID 和同一完整输入重放；不会再次调用模型。
- 失败、取消或中断的生成请求不能按相同 ID 重新生成，明确重试时使用新 ID。
- bindings 在每次调用重新解析；即使公开参数没变，宿主上下文变化也可能导致完整输入不同，不要假设稳定 ID 仍可重放。
- UPDATE / DELETE / SELECT_VERSION 使用修订检查，不具备 APPEND 的同 ID 完整重放保证。响应丢失时先刷新记录或 CURRENT 再决定下一步。
- 模型失败不回滚已保存输入；事实提交成功而后续生成失败时，只重试生成，不再次提交事实。

主生成的模型收集阶段默认超时为 180 秒；本地检索、缓存整理、排队等发生在该阶段之外，不再先进行单独模型摘要请求。页面等待超时不等于宿主取消成功，当前没有公开取消运行能力。App 的同一 TemplateRuntime 使用互斥锁串行生成，不应假设多个生成请求会并行。原生模板页按实际运行状态显示检索、生成、推理、接收内容和保存阶段，不展示模拟进度百分比。

运行状态包括 RUNNING、SUCCEEDED、FAILED、CANCELLED、INTERRUPTED。错误消息会脱敏；不要将原始 Provider 响应、凭据或敏感请求头写进模板数据。

## 10. 版本升级

protocolVersion 是桥接协议版本；version 是模板发布版本；schemaVersion 是宿主集合版本。三者不同，当前集合注册 schemaVersion 固定为 1。

同 ID、同集合名的契约或修改策略发生改变时，宿主拒绝静默覆盖。仅增加 Manifest version 不能完成数据迁移；当前没有通用迁移 SDK，需单独设计宿主迁移或新集合。新增集合也不自动转换旧记录。

模板绑定必须匹配当前声明版本。升级后的启用需要用户重新确认权限，不能把旧授权视为对新增能力的自动同意。

Room 17→18 的一次性旧模板数据清理是本项目这次破坏性切换的特例，不是每次模板更新的正常做法。不要复用该清理逻辑处理未来版本升级。

## 11. 验证清单与命令

发布前至少检查：

- Manifest 解析成功；旧协议、未知字段、未知操作及不合法契约拒绝。
- GUI 保存后重新进入可恢复，编辑/删除修订冲突有合理处理。
- 分页覆盖多于 100 条数据，无重复、遗漏；按 next 继续。
- 数据与请求不越过项目；未启用或撤销授权后拒绝调用。
- 聊天只暴露声明的查询/生成操作，不能直接修改事实。
- 必需 binding 缺失、上下文变化及恶意覆盖字段时拒绝。
- AI 非法 JSON、网络失败、超时与进程中断不覆盖旧结果。
- 候选生成后不生效；取消确认不生效；源版本变化不能确认旧候选。
- 未保存退出、保存失败、重复点击和切换聊天不静默丢失数据。
- 深浅主题、键盘、滚动、空态及等待状态可读且可操作。
- 备份恢复保留数据但撤销权限，不恢复密钥或静默授权。

本仓库验证命令（需正常配置 Android SDK / JDK）：

```bash
./gradlew --no-configuration-cache \
  :template:core:testDebugUnitTest \
  :template:builtin:testDebugUnitTest \
  :template:runtime:testDebugUnitTest \
  :core:database:testDebugUnitTest \
  :app:assembleDebug :app:lintDebug

node scripts/test-template-service.cjs

./gradlew --no-configuration-cache \
  :app:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest

# 按顺序安装，确认每次 Success 后再继续。
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.dwl.mutcube.storage.TemplateServiceFlowTest,com.dwl.mutcube.storage.FitnessServiceFlowTest,com.dwl.mutcube.storage.TemplateBackupTest,com.dwl.mutcube.storage.FitnessWebViewTest \
  com.dwl.mutcube.debug.test/androidx.test.runner.AndroidJUnitRunner

adb install -r core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
adb shell am instrument -w \
  com.dwl.mutcube.core.database.test/androidx.test.runner.AndroidJUnitRunner
```

多设备连接时为每个 adb 命令指定 `-s <序列号>`。新业务测试使用隔离数据库；不要向用户真实数据库写虚构训练事实，也不要使用卸载 App 的方式清理单个模板。

当前自动测试使用替身模型验证结构和治理，不替代真实 Provider 的端到端验证。真实模型应另外验证生成、跨聊天入口读取、候选确认与重新打开恢复。

健身实际页面回归还可运行 `node scripts/test-fitness-service.cjs`。需要真实模型验收时，已安装测试 APK 后显式执行以下命令；会产生 Provider 费用，但使用隔离数据库，不写入用户训练历史，也不修改现有凭据：

```bash
adb shell am instrument -w -e liveProvider true \
  -e class com.dwl.mutcube.storage.TemplateLiveProviderTest,com.dwl.mutcube.storage.FitnessWebViewTest#actualPageAndProjectChatWithRealProvider \
  com.dwl.mutcube.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## 12. 参考源码与文档

- [智能笔记声明](../template/builtin/src/main/java/com/dwl/mutcube/template/builtin/NotesTemplate.kt)
- [智能笔记页面](../app/src/main/assets/templates/notes/index.html)
- [训练记录声明](../template/builtin/src/main/java/com/dwl/mutcube/template/builtin/FitnessTemplate.kt)
- [训练记录页面](../app/src/main/assets/templates/fitness/index.html)
- [健身服务协议 2 契约](FITNESS_SERVICE_V2.md)
- [Manifest 解析器](../template/core/src/main/java/com/dwl/mutcube/template/core/TemplateManifest.kt)
- [统一 Action 引擎](../template/core/src/main/java/com/dwl/mutcube/template/core/TemplateAction.kt)
- [Schema 校验器](../template/core/src/main/java/com/dwl/mutcube/template/core/TemplateJsonContract.kt)
- [宿主 Action 适配](../template/runtime/src/main/java/com/dwl/mutcube/template/runtime/TemplateActions.kt)
- [模型运行时](../template/runtime/src/main/java/com/dwl/mutcube/template/runtime/TemplateRuntime.kt)
- [安全 WebView 与原生确认](../template/ui/src/main/java/com/dwl/mutcube/template/ui/TemplateRuntimePage.kt)
- [声明与聊天工具适配](../app/src/main/java/com/dwl/mutcube/storage/TemplateDataToolService.kt)
- [数据仓储](../core/database/src/main/java/com/dwl/mutcube/core/database/TemplateStorage.kt)
- [服务流程隔离测试](../app/src/androidTest/java/com/dwl/mutcube/storage/TemplateServiceFlowTest.kt)
- [模块与边界](TEMPLATE_MODULES.md)
- [协议 2 参考](BUILTIN_TEMPLATE_CONTRACT.md)
