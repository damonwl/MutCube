# 聊天 Markdown 表格横向滚动

普通聊天回复中的顶层 GFM 表格采用局部横向滚动，不再等宽挤进消息可见宽度。普通正文、列表和代码块继续使用现有 Markdown 渲染组件。

## 实现

- 复用已有的 JetBrains Markdown 0.7.5 GFM AST，按源码位置拆分顶层表格与其他正文；不通过正则识别表格，不修改消息原文、复制或导出内容。
- 每列按表头和单元格内容的文本测量结果分配宽度，含内边距限制在 88–320 dp。短字段较窄，长说明较宽；达到上限后正常换行，不无限扩展单列。
- 表头、所有行和分隔线使用同一列宽数组，在一个横向滚动容器内，避免各行单独滚动错位。短表格不足屏宽时均分剩余空间，无须横滑。
- 单元格复用现有 Markdown 组件，保留常用粗体、代码和链接；色彩使用 Material 主题变量。
- 表格源码起点作为 Compose 稳定 key，追加行时保留 ScrollState。字号/密度参与列宽计算。
- 引用块/列表中的嵌套表格暂由原 Markdown 组件渲染，未替换其内部布局。本次不扩展 Markdown 语法或新增 WebView。

## 依赖依据

- 当前 Markdown 库的 [MarkdownTable](https://github.com/boswelja/compose-markdown/blob/main/core/src/commonMain/kotlin/com/boswelja/markdown/components/MarkdownTable.kt) 使用等权重列，TableStyle 只开放内边距，因此不能仅通过样式参数开启横向滚动。
- 显式声明与原渲染器已有传递依赖相同的 `org.jetbrains:markdown:0.7.5`，用于直接调用解析器，不更换解析引擎。
- Compose UI 测试依赖仅加入 androidTest，不进入正式 APK。
