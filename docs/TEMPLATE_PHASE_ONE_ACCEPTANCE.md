# 内置模板第一阶段验收记录

> 历史验收：以下结果对应已移除的旧协议及旧模板，不作为协议 2 的验证结果。当前实现见 TEMPLATE_MODULES.md，最新验证见 PROGRESS.md。

日期：2026-09-16；设备：c53df088（24122RKC7C）；业务实现提交：d5d5916。

## 已执行

| 验证范围 | 证据与结果 |
| --- | --- |
| 构建与静态检查 | Debug APK、app/database 仪器测试 APK 编译通过；相关 JVM 测试与 app Lint 通过 |
| 运行单元回归 | template:runtime 二十一个测试通过；覆盖契约、上下文、摘要和运行控制流 |
| 真机真实 SQL | database AndroidJUnitRunner 全量执行 OK (25 tests)，包含迁移、会话仓储与模板权限用例 |
| 隔离备份恢复 | TemplateBackupTest 真机执行 OK (1 test)，恢复后事实/快照/缓存保留，授权清除 |
| 模板实际 AI | MiMo main、title 独立交互成功并保存；重新进入恢复输入与结果 |
| 语义摘要 | 超过八条近期完整结果后，真实运行快照 historySummary.status=GENERATED，来源键及摘要指纹保存，主交互成功 |
| 进程中断 | 生成途中 force-stop，重启并进入模板显示 INTERRUPTED / Application restarted，保留旧成功结果 |
| 聊天受控读取 | 普通项目聊天实际调用 template_data_list；停用保留数据，重新仅授权读取后仍可调用 |
| 主题与返回 | 真机检查深浅主题；深色运行页、运行列表/详情、数据授权页和数据详情可读；返回关闭当前弹窗/页面，模板切回正常聊天后可展开侧边栏 |
| 较多数据查看 | 二十四条记录时显示更多可展开最后四条，实际打开此前隐藏的历史结果成功 |

数据库测试在测试 APK 的内存或迁移测试数据库中执行；备份恢复使用 app 私有缓存下单独创建的测试目录，不恢复到用户真实数据库。真实 AI 测试只追加“测试”项目的冒烟记录，不覆盖旧业务事实。

## 执行命令

```sh
adb -s c53df088 shell am instrument -w com.dwl.mutcube.core.database.test/androidx.test.runner.AndroidJUnitRunner
adb -s c53df088 shell am instrument -w -e class com.dwl.mutcube.storage.TemplateBackupTest com.dwl.mutcube.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## 验证范围边界

- 已检查模板相关正常页面的深浅主题和返回操作；不代表所有设备、系统版本及无障碍/极端字号组合均完成兼容验收。
- 摘要缓存复用、网络失败和超时降级有 JVM 测试，未在真机逐项注入故障；撤销提交边界已用真实 SQL 验证，不宣称覆盖所有线程交错。
- 摘要只处理有界历史窗口，不代表全部历史长期汇总；详见 BUILTIN_TEMPLATE_CONTRACT.md。
- 可选 AI 写入提案未开放，第三方安装、签名、市场和完整 SDK 不属于本次验收。

结论：第一阶段内置模板最小运行闭环已完成本记录范围内的验收，可以作为后续模板接入的基础；不扩展为第三方模板 SDK、可选写入提案或全部设备兼容性完成。
