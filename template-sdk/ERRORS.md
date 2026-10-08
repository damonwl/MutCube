# 模板 SDK 错误与重试

SDK 抛出 `TemplateHostError`，可读取 `error.code`。以下是当前可识别的代码，不应通过匹配中文提示文案判断错误。

| 代码 | 来源 | 处理建议 |
| --- | --- | --- |
| `NO_HOST` | SDK | 不在宿主或 Mock Host 中；检查桥接初始化。 |
| `DUPLICATE_REQUEST` | SDK | 同一个客户端中重复使用未完成的 request ID。 |
| `TIMEOUT` | SDK | 未收到宿主响应；普通调用约 220 秒，语音调用约 600 秒。**写入可能已经成功**，先查询状态，不要用新 ID 盲目重试。 |
| `INVALID_REQUEST` | 宿主 | 输入、Action 或参数不符合声明；检查 Manifest、Schema、调用参数。 |
| `PERMISSION_DENIED` | 宿主 | 能力未授权、数据权限不足或麦克风权限被拒绝；引导用户查看权限设置。 |
| `VERSION_CONFLICT` | 宿主 | 乐观锁版本变化；重新查询记录或当前版本，再让用户确认修改。 |
| `SERVICE_NOT_CONFIGURED` | 宿主 | 语音 Provider、模型或凭据尚未配置。 |
| `BUSY` | 宿主 | 已有语音任务运行；等待完成或停止当前任务。 |
| `SPEECH_ERROR` | 宿主 | TTS/ASR 服务或设备失败；查看用户可见提示与脱敏运行记录。 |
| `HOST_ERROR` | SDK/宿主 | 其他宿主错误；查看脱敏运行记录，不向用户暴露内部错误细节。 |

`TIMEOUT` 并不等于事务回滚。写入类 Action 尽量使用稳定的 request ID；重新发起前先查询对应记录/执行状态。`VERSION_CONFLICT` 不能自动覆盖新版本。安装、升级和权限校验错误发生在 SDK 启动前，由宿主界面显示，不属于此表。
