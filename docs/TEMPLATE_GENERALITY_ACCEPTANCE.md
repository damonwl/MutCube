# 模板系统通用性验收

日期：2026-09-17

## 结论

内置模板范围内，通用运行架构验证通过。已有笔记模板复用与健身相同的动作引擎、项目作用域、工具桥接、结果定位、WebView 和持久层，本轮无需修改宿主业务代码。此结论不等于外部模板安装、安全、升级与 SDK 已验收。

## 验证范围与结果

真机 Xiaomi c53df088，Debug 应用；新测试仅使用独立内存 Room 数据库、测试凭据和模型替身。未读取用户 Key，未访问实际 Provider，不写入用户项目、聊天、笔记或训练数据。

| 项目 | 验证结果 |
| --- | --- |
| 聊天工具链 | 实际 ChatGenerationService 先执行 notes.list / document.current，再执行 document.generate，最后持久化回复及三组配对工具轨迹 |
| 实时状态 | 工具调用前出现 RUNNING；完成轨迹和 ToolResult 保存在聊天消息 |
| 候选边界 | 生成后 documents 有候选，当前版本为空；打开定位事件不会启用 |
| 精确定位 | 使用真实工具结果 presentation 初始化真实安全 WebView，自动展示对应文稿候选 |
| 原生确认模式 | 关闭宿主可信 GUI 选项时，取消原生确认不启用；确认后 revision 为 1 |
| 实际内置宿主模式 | 开启与 MutCubeApp 相同的可信 GUI 选项，展示候选后用户点击模板按钮即可启用，不弹第二次确认 |
| 重开与主题 | 重建深色 WebView 后读取持久化的当前文稿，主题前景色改变；项目聊天入口回调生效，未增加持久化聊天 |
| 项目隔离 | A 聊天和页面只看到 A 笔记；不能在 B 启用 A 候选，B 无文稿、笔记仍保留 |
| 解绑重绑 | A 解绑后能力消失且调用失败，B 不受影响；A 重新授权后恢复查询，原笔记与已启用版本保留 |
| 通用服务回归 | 既有 CRUD、重复提交、过期 revision、确认取消、聊天写入拒绝、开发者重置作用域测试通过 |

构建及 JVM 回归：app、feature:chat、template:core、template:runtime、template:builtin 的测试任务通过；Android 测试 APK 构建通过，两套模板 Node 页面脚本通过，git diff --check 通过。

真机最终运行：NotesTemplateAcceptanceTest 两项 + TemplateServiceFlowTest 一项 + TemplateChatUiTest 一项，OK (4 tests)，6.381 秒。模型输出与工具选择由替身确定，真实模型是否正确选择动作、生成质量与网络延迟不属于本轮结论。

## 架构检查

- TemplateRuntime / TemplateActions 根据 ActionMode、CollectionPolicy、schema 和 bindings 执行，不识别训练部位、重量、次数或 RIR。
- TemplateDataToolService 遍历 manifest 生成工具与能力面板，CHAT 权限统一检查，结果定位元数据由宿主构建，不需要健身/笔记分支。
- 通用结果卡片只处理动作状态、配对结果、摘要和定位信息；具体候选页面与展示属于模板 HTML。
- 宿主默认选中的 mutcube.notes 只是初始目录项，已有目录回退逻辑；不是笔记业务执行分支。

## 两处非阻塞完善项

1. TemplateRuntimePage 的删除确认标题固定为“确认删除此笔记”，应改为通用“确认删除此记录”，或由受控展示元数据提供名称。当前不影响权限和作用域，但其他模板删除时语义不准确。
2. 笔记模板按钮写“预览并确认”，实际内置宿主开启可信 GUI 时点击即启用；建议改成“启用文稿”，因为正文已在模板中展示。关闭可信选项时保留真正的原生预览确认。

本轮仅记录建议，未修改生产操作逻辑。TEMPLATE_CHAT_INTERACTION.md 已修正此前“笔记始终保留原生确认”的不准确描述。
