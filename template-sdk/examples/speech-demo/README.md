# 语音宿主能力示例

此模板用原生 DOM 和同一套 Template SDK 演示朗读、停止、语音转写及保存文字，不依赖 Vant。

在仓库根目录执行：

```bash
python3 scripts/mutcube-template validate template-sdk/examples/speech-demo
python3 scripts/mutcube-template pack template-sdk/examples/speech-demo build/speech-demo.mutcube-template
```

打包器会注入 `assets/mutcube-sdk.js` 与 `assets/mock-host.js`。浏览器预览可将生成的包解压到临时目录后通过本地 HTTP 服务打开 `templates/speech/index.html`；Mock Host 会用输入框模拟转写，不访问麦克风。真机导入包后才会出现原生录音界面。页面可选择“沿用全局设置 / 自动识别 / 英语 / 中文”，演示按次指定 ASR 语言。录音每次由用户触发，模板只收到文字，不能取得音频、密钥或自行切换 Provider。示例不会自动保存转写结果；点击“保存文字”后才写入项目数据。
