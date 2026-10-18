# HANi 1.1.0.1 共存安装修复

## 原因

截图安装器显示“软件包与现有软件包存在冲突”，并提示开发者签名异常。上一发布包的 applicationId 仍是 com.yenaly.han1meviewer，但使用本机构建的新发布签名；若设备已安装同包名、不同签名的软件，Android 将拒绝覆盖。截图与此原因一致；未连接该设备，未直接提取已安装证书确认。

用户要求保留原软件并共存，故新应用独立使用 applicationId=com.kelaner.hani，正式显示名 HANi，版本仍为用户指定的1.1.0.1。源码 namespace 保留 com.yenaly.han1meviewer，避免无必要的源码包重构；Android 的应用身份由 applicationId 决定，不由类命名空间决定。

## 修复

- 新包名隔离私有数据库、登录、设置、目录授权及私有存储；不会覆盖或清除原应用，不会自动迁移旧应用数据。
- 默认文件目录名由 Han1meViewer 改为 HANi，避免旧公共下载目录默认共享。用户手动给两应用授权同一SAF目录时，文件仍属于共享资源，应自行决定是否共享。
- FileProvider、AndroidX Startup、Firebase Init、CrashX provider 以及AndroidX自定义signature权限均从applicationId生成，随新包独立，无安装级名称冲突。
- 启动别名仍使用原namespace的完整组件类名；ComponentName(context, className) 的package来自当前新应用，能定位新包中的对应入口。无需改原应用或卸载。
- Widget/PiP自定义广播action采用BuildConfig.APPLICATION_ID前缀；widget manifest同步占位符，避免与原包广播标识混用。
- APK命名HANi-1.1.0.1-arm64-v8a.apk；CI匹配新名字，HANi更新仅选HANi APK，避免误选旧包名附件。
- 已有下载页、元数据持久化、四项维护数量进度、多选与设置实现保持。

## 验证

`:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 不使用 init script，BUILD SUCCESSFUL（3m52s）。17项JVM测试通过，失败0；设备回归测试APK编译通过但未执行。Lint错误0，现存警告保留（实际数量见报告）。`git diff --check` 通过。

正式 `:app:assembleRelease` BUILD SUCCESSFUL（3m21s），APK 25,552,200字节，低于100MB；`apksigner verify --verbose --print-certs`通过，v2签名有效，证书SHA-256与上一轮本地发布签名一致（795dce2fe2e1bcc1dbaf373ed5699a319d33f71ab91362e0014253b1897deddb）。最终 `aapt dump badging` 确认显示名HANi、包名com.kelaner.hani、versionName=1.1.0.1、versionCode=26101816、minSdk=27、native-code=arm64-v8a。Release合并清单所有provider authority及自定义权限均使用com.kelaner.hani前缀，无旧包冲突。

APK SHA-256：`7c538361cc45fe70879786511d8a755c165719d40c61a46c66ce38b6188190c6`。产物 `artifacts/HANi-1.1.0.1/HANi-1.1.0.1-arm64-v8a.apk`；源码提交 `34a7a1cbc7f4879c8a90c70f409f12666f498fde`；发布标签 `v1.1.0.1-hani`（标签后缀用于区别旧包，APK版本仍1.1.0.1）。

已对debug合并Manifest核验：package=com.kelaner.hani.debug，全部provider/custom permission独立，组件全名可解析，aapt显示HANi [Debug]。正式包需单独核验。

当前adb devices为空，不能在截图设备上实际双应用安装验收。没有卸载、清除原软件数据或修改设备。相同网页/视频深链保留，系统可能提供选择器或按默认应用处理；新包的自动App Links验证还取决于网站配置新包名及证书，不保证自动接管链接。

签名沿用上一轮已保存的项目外本地发布签名，后续HANi更新应持续使用该签名。缺Firebase配置继续沿用已授权的无配置兼容方案，有真实配置时必须为新applicationId提供对应配置。
