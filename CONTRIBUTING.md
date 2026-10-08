# 贡献指南 / Contributing

MutCube 是开发中的 Android AI 客户端与模板宿主。请先阅读 README、`docs/ARCHITECTURE.md`、
`docs/ORIGIN_POLICY.md` 和 `THIRD_PARTY_NOTICES.md`。SDK 和开发文档直接随源码维护，不要求发布 npm 包。

## 环境与检查

- JDK 17、Android SDK 37；使用仓库内 Gradle Wrapper，不需要 Firebase 配置。
- SDK/模板工具测试需要 Node.js 22 或更新版本，以及 Python 3。
- Gradle 默认堆上限为 4 GiB、最多 2 个 worker，避免大型编译与 R8 在较小堆内溢出；建议开发机至少 8 GiB 内存。
- 真机或模拟器测试无需真实 API Key；带 `Live` 的测试须明确选择，并可能调用收费服务。

```bash
./gradlew testDebugUnitTest :app:assembleDebug :app:lintDebug --no-configuration-cache
./gradlew :app:assembleRelease --no-configuration-cache
node --test template-sdk/test/*.test.js
npm exec --yes --package=typescript@5.9.3 -- tsc --strict --noEmit --lib ES2022,DOM --module NodeNext --moduleResolution NodeNext template-sdk/test/types.test.ts
node scripts/test-template-service.cjs
node scripts/test-fitness-service.cjs
git diff --check

# 数据库测试使用独立数据库；多设备时显式指定设备序列号
ANDROID_SERIAL=<device-serial> ./gradlew :core:database:connectedDebugAndroidTest
```

Release 默认不配置发布签名。不要提交签名文件、API Key、`.env`、私人日志、用户数据或备份。
发布 APK 的签名与上传由维护者单独处理，CI 不发布 APK。

## 修改边界

- UI 负责展示与交互；业务流程放在服务中，数据一致性和授权检查放在 Repository/runtime。
- `AppContainer` 仅负责依赖组装；核心模块不得反向依赖 app。
- 模板必须通过 SDK 访问授权宿主能力，不可访问 Room、任意文件路径或 Provider 密钥。
- 更改数据结构必须提供 Room 迁移与测试；不能靠清空数据库解决升级问题。
- Bug 修复应有回归测试。异步流程覆盖取消、并发、错误及资源释放，而不仅是成功路径。
- Kotlin 使用 4 空格；文档以中文维护，影响用户入口的内容同时更新英文 README。
- 复用 MIT/Apache-2.0/BSD 等组件时保留版权、许可及适用 NOTICE；不得复制 AGPL 参考应用的实现。

## 提交与评审

先提交 Issue 描述可复现步骤、预期和实际结果；较大架构调整先说明职责边界与迁移方案。
Pull Request 写明影响范围、测试命令和实际结果。不要把未运行的真机验收描述为通过。
涉及删除或恢复用户数据的测试必须在隔离目录/数据库执行，不得自动覆盖真实应用数据。

## English summary

Use JDK 17, Android SDK 37 and the checked-in Gradle wrapper. Node.js 22+ and Python 3 are required for
template SDK/tool tests. The commands above run without provider credentials. Keep business rules outside UI,
add regression tests and database migrations, and retain third-party license notices. Never commit credentials,
signing keys, private logs or backups. Release signing and publication are maintainer-controlled, not CI actions.
Report reproducible bugs and include verified test results in your pull request.
