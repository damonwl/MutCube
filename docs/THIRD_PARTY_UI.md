# UI 依赖评估

## Lobe Icons 静态品牌图标

- GitHub：https://github.com/lobehub/lobe-icons
- 固定资源包：`@lobehub/icons-static-png` 1.97.0，MIT
- 用途：模型服务列表和新增服务弹窗的品牌标识；同时打包浅色和深色图标，离线可用
- 许可：`app/src/main/assets/licenses/lobe-icons.txt`，应用内“第三方开源许可”可查看
- 未收录图标的自定义服务显示名称缩写；品牌商标仍归各权利人所有

## Reorderable

- GitHub：https://github.com/Calvin-LL/Reorderable
- 固定版本：3.1.0，Apache-2.0
- 用途：模型服务列表的整卡跟手拖动、相邻项让位动画及边缘自动滚动
- 业务顺序仍由 MutCube 的 Provider 配置保存；组件只负责手势和呈现

## Compose Unstyled

- GitHub：https://github.com/composablehorizons/compose-unstyled
- 评估版本：2.9.2
- 许可证：MIT
- 用途：高度自定义且具备无障碍语义的底部弹层
- 引入方式：标准依赖
- 使用范围：仅限 UI 基础设施层，不向业务状态暴露第三方类型
- 替换边界：`AttachmentSheet` 组件
- 审核日期：2026-09-14

## AndroidX Compose Material 3

- GitHub：https://github.com/androidx/androidx
- 许可证：Apache-2.0
- 用途：Android 官方 Compose 基础控件、主题与布局
- 引入方式：标准依赖
- 使用范围：应用 UI 层
- 替换边界：由 MutCube 组件封装颜色、尺寸和交互状态
- 审核日期：2026-09-14

## 暂不采用 Stream Chat Android

该项目使用 Stream 专有 Source Code License，并将消息模型与 Stream 服务绑定，不符合 MutCube 的本地会话和模型网关边界，因此不采用。
