# 模板 UI 组件试点

日期：2026-09-27。目标是在不改变 Template Package v1 和 Action 协议 2 的前提下，为开发者提供一个可复用的移动端页面起点。

本文为历史试点记录。自 2026-10-08 起，官方开发样板与 CLI 默认入口改为 [英语学习模板](../template-sdk/examples/english-reader/README.md)，Todo/Vant Todo 仅保留作回归材料，不再推荐为开发起点，也不制定统一模板 UI。

## 技术边界

- 官方样板选用 Vue 3 + Vant 4。Vant 是模板开发的推荐选项，不是安装协议的必选依赖；宿主原生页面仍使用 Jetpack Compose。
- Todo 样板源码在 `template-sdk/examples/vant-todo/`。`npm run dev` 在浏览器用 Mock Host；`npm run build` 生成可校验、可打包的静态目录 `dist/`。
- 健身模板曾在基础资料的“直接填写”页试用 Vant；真机视觉评估后恢复原布局。实验源码保留在 `template-ui/fitness-profile/` 供比较，不进入 APK，也不随第三方健身示例导出。
- 第三方模板的 WebView 不开放外网脚本。Vant、Vue 与 CSS 均在构建时打包；宿主色值通过 CSS 变量映射，不向模板泄露完整原生主题对象。

## 开发与验收

```bash
cd template-sdk/examples/vant-todo
npm ci && npm run dev
npm run build
cd ../../..
python3 scripts/mutcube-template validate template-sdk/examples/vant-todo/dist

node scripts/test-fitness-service.cjs
python3 scripts/mutcube-template example-fitness build/fitness-example
python3 scripts/mutcube-template validate build/fitness-example
```

验收应覆盖：Todo 新增、勾选、删除及刷新后读取；健身原表单的资料填写、草稿保留、切换 AI 交流及正式保存；浅深主题、键盘与滚动；浏览器 Mock Host 与 Android 宿主；本地包大小和真实 WebView 对 Todo 静态脚本/CSS 的加载。`npm run build` 产生的 Todo 资源要与源码一同更新，不能只改 Vue 源码。真机连接不可用时，不能将浏览器验证写成真机验收。

本轮在真机用隔离数据运行 `FitnessWebViewTest`，并将 Todo 打包文件放入测试应用的外部文件目录后，通过 `VantTodoPackageTest` 验证第三方安装、资源加载、Action 新增和 Room 持久化。该测试只有传入 `packageFile` 仪器测试参数时才执行；常规 CI 不依赖设备上预置的包。

Vant 与 Vue 均采用 MIT 许可证。重新分发时保留其许可证声明和依赖清单；不要把 UI 组件的许可误写成第三方模板作者身份认证。对外 SDK 仍保持框架无关。
