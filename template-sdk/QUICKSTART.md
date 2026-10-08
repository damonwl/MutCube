# 第三方模板：从空目录到真机安装

本教程只依赖仓库公开的 CLI、SDK 和包协议，不需要修改 MutCube 宿主源码。需要 Python 3、可运行的 MutCube 0.1.3+ App；浏览器预览不需要 Android。以下命令从仓库根目录运行。

## 1. 创建模板

```bash
python3 scripts/mutcube-template init build/my-first-template --id org.example.my-english --developer "你的开发者名称"
```

生成的是英语学习样板，包含目标设置、阅读、词典与 AI 解读、生词本、复习和朗读。`--id` 设置你的全局唯一模板 ID；若省略，样板 ID 是 `example.english-reader`，请在打包前手动修改。再编辑 `build/my-first-template/manifest.json` 中的模板名。不要把 API Key、用户隐私或未经授权的内容放进模板包。`template.version` 从 `1.0.0` 开始；同 ID 升级必须提高版本号。原创文章不是官方真题；保留包内 WordNet 许可与在线词典来源。

页面位于 `templates/english/`。它通过 `assets/mutcube-sdk.js` 调用声明过的 Action；浏览器预览使用 Mock Host，正式 App 的权限和数据由宿主重新校验。直接双击 `index.html` 打开 `file://` 可能阻止 ES Module 导入，请运行本地静态服务器：

```bash
python3 -m http.server 8765 --bind 127.0.0.1 --directory build/my-first-template
```

然后打开 `http://127.0.0.1:8765/templates/english/`，测试学习目标设置、阅读点词、模拟 AI 解读、加入生词本与复习。Mock Host 的示例记录保存在当前页面内存中，刷新会清空；网络、AI 和朗读采用模拟响应，不能用来证明真实服务已可用。在 App 中验证 AI 与朗读时，还需配置对应 Provider、模型、凭据并授权模板。

## 2. 校验、生成类型、打包

在另一个终端执行：

```bash
python3 scripts/mutcube-template validate build/my-first-template
python3 scripts/mutcube-template types build/my-first-template build/my-first-template-actions.d.ts
python3 scripts/mutcube-template pack build/my-first-template build/my-first-template.mutcube-template
```

`types` 生成 Action 输入和记录输出的 TypeScript 类型；AI `GENERATE` 的输出仍须由模板在运行时校验。CLI 会注入本地 SDK 资源并生成 Hash。开发校验只能提前发现常见问题，App 安装器是最终校验者；不要依赖 Mock Host 证明某项权限已经获批。打包不会覆盖已有文件。

## 3. 在 App 中安装

把 `.mutcube-template` 文件复制到手机。在 MutCube 的“模板管理”选择“从本地导入模板包”，检查模板 ID、开发者、版本、数据集合、敏感权限和域名，再确认安装。安装本身不授权：打开模板时还需选择项目并确认授权。首次写入、重新打开和升级后读取都应在这个项目内验证。

模板卸载默认保留宿主中的用户记录；选择永久删除时须输入完整模板 ID。重装同 ID 不会自动恢复授权。备份恢复后也需要重新授权。集合 Schema 或策略改变不能仅靠提高版本号完成迁移，v1 安装器会拒绝静默变更。

遇到错误时先看 [错误码](ERRORS.md)；协议和权限边界见 [包规范](../docs/TEMPLATE_PACKAGE_V1.md)。当前 SDK 随仓库分发，尚未发布 npm；网页与安装包均加载本地打包的 SDK 文件，不依赖 CDN。
