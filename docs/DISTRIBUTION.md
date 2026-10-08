# APK 分发与签名

更新日期：2026-10-08。

## 当前源码与构建

- 源码为开发版 `0.1.3`，`versionCode=4`，最低支持 Android 8.0。
- 本轮 Debug 已覆盖安装真机，保留原数据；APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
- `app/build/outputs/apk/release/app-release-unsigned.apk` 已通过 R8/资源压缩构建，但**尚未签名、未正式分发**，不能直接当作可安装的发布包。
- `alpha` 使用独立的 `com.dwl.mutcube.alpha` 包名和开发签名，仅用于本地验收。
- GitHub CI 只验证和保存测试报告，不签名、不上传安装包；SDK 不发布 npm。

## 已记录的正式签名包

此前完成的本机安装包为 `dist/MutCube-0.1.2-release.apk`，包名 `com.dwl.mutcube`，`versionCode=3`，最低支持 Android 8.0。
它不包含本轮 0.1.3 的全部能力和修复。`dist/` 不纳入 Git；历史包是否仍可下载，应以实际 Release 发布为准。
本轮没有创建新的公开 Release，不将本机已有文件等同于公开发行。

0.1.2 使用软件默认头像中的 MutCube Logo 作为启动图标：浅色圆形底、双层倾斜圆角轮廓，图案轻微下移。普通启动图标与圆形启动图标共用 `app/src/main/res/drawable/mutcube_icon_foreground.xml`，背景色定义在 `app/src/main/res/values/icon_colors.xml`。未使用用户自定义头像图片。

正式签名证书保存在本机仓库的 `.signing/mutcube-release.p12`，密码保存在 macOS 钥匙串：服务 `com.dwl.mutcube.release-signing`、账户 `mutcube`。两者均不纳入 Git。后续发布同包名更新时必须使用同一证书，并递增 `app/build.gradle.kts` 中的 `versionCode`；丢失证书或密码将无法覆盖安装旧版本。请将证书和密码分别做安全备份，不要把它们附在 APK、提交到仓库或发给安装者。

生成流程：先运行 `./gradlew :app:assembleRelease`，再对 `app/build/outputs/apk/release/app-release-unsigned.apk` 执行 `zipalign` 和 `apksigner sign`。签名前从钥匙串读取密码，避免写入 Gradle 文件或命令脚本。0.1.2 的 Release 构建成功，`apksigner` 已确认 v2/v3 签名有效，证书指纹与 0.1.1 相同；`aapt` 已确认包名、版本号、应用名称和最低系统版本。按本次要求，未安装到设备做启动验证；不要将签名校验等同于设备验收。

开发版使用 `com.dwl.mutcube.debug` 包名和开发签名，可与正式版并存；开发版的数据不会自动迁移到正式版。
