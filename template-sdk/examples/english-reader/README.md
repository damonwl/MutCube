# 英语阅读与生词 · 官方开发样板

这是独立的 MutCube Template SDK 包，也是 `mutcube-template init` 的默认样板。首次开发请按 [QUICKSTART](../../QUICKSTART.md) 从空目录创建、预览、校验和打包。流程：设置通用英语／四级／六级／雅思／托福目标 → 阅读原创模拟文章 → 点词查看词典 → 调用宿主 TTS 与 AI → 用户主动加入生词本 → 根据自评复习或请 AI 出题。

## 内容来源与边界

- 三篇文章及理解题是 MutCube 原创示例，**不是历年真题、官方样题或杂志转载**。考试目标只用于练习推荐与 AI 解读。
- `templates/english/wordnet.js` 从 [Princeton WordNet 3.0](https://wordnet.princeton.edu/)官方数据库裁出 145 个相关词，不是完整词典；多义词只保留部分通用义项，不保证符合当前语境。原始版权和许可文本完整保存在 `assets/WORDNET_LICENSE.txt`，必须随包分发。这份数据不按 MIT 许可重新授权。
- 点击词后通过宿主受控网络能力查询 [Wiktionary](https://www.wiktionary.org/)；只发送所点词语到 `en.wiktionary.org`、`zh.wiktionary.org`，不发送整篇文章、生词本或学习目标。界面将在线英英／英汉释义与 AI 解读分开展示，并提供词条及 [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) 来源。中文词条结构各异，部分词可能无法提取释义；联网失败不影响离线释义。
- AI 输出不声称出自词典或考试机构；本包不内置未经授权的四六级、托福、雅思真题。未来题库应单独管理来源、授权、版本与署名。
- TTS 由宿主处理；模板不读取密钥或音频。未配置 TTS Provider 时仍可阅读，朗读操作会提示不可用。

## 安装与预览

在仓库根目录运行：

```bash
python3 scripts/mutcube-template validate template-sdk/examples/english-reader
python3 scripts/mutcube-template pack template-sdk/examples/english-reader build/english-reader.mutcube-template
```

在 MutCube 模板管理中导入生成的包并绑定项目。浏览器预览可把包解压到临时目录，通过本地 HTTP 服务打开 `templates/english/index.html`；无 Android 宿主时使用 Mock Host，AI 与网络结果为模拟值。若需重新生成词典摘录，执行 `python3 template-sdk/scripts/build-wordnet-subset.py`；脚本从 Princeton 官方站下载 WordNet 3.0。

## 当前限制

- 只有三篇原创文章和部分词语的离线释义，不是完整考试课程或完整词典。
- 复习间隔是 10 分钟、1 天、逐步翻倍的简单规则，不等同于成熟的间隔重复算法；仅由用户显式自评修改。
- 项目数据最多读取 20 页；大量生词时仍需完善分页 UI。
- 当前宿主没有授权真题内容源、词典离线批量更新、文章导入与编辑协议，本示例不绕过 SDK 访问数据库。
