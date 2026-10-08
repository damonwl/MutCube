# Vue 3 + Vant 历史试点

本目录仅保留为历史试点和回归材料，不再作为官方开发样板；当前入口为 [英语学习样板](../english-reader/README.md)。Template Package v1 不强制 UI 框架。此试点包不从 CDN 下载脚本或样式，Vite 将 Vue、Vant 和页面代码打包成本地资源。

```bash
cd template-sdk/examples/vant-todo
npm ci
npm run dev
npm run build
cd ../../..
python3 scripts/mutcube-template validate template-sdk/examples/vant-todo/dist
python3 scripts/mutcube-template pack template-sdk/examples/vant-todo/dist /tmp/vant-todo.mutcube-template
```

浏览器 `npm run dev` 会使用 Mock Host，右下角可切换权限和深浅主题；导入 App 后则使用真实宿主。每个数据操作仍须经过 Manifest 声明的 Action。Vant 只处理 UI，不直接触碰数据库或 API Key。
