# 下载页与下载设置 1.1.0.1 验证报告

## 根因与持久化

原下载实体没有作者、类型字段。页面每次创建时，metadata 都是空 Map，UI 先以未知作者/未知类型渲染；随后 LaunchedEffect 延迟 250ms，再按下载记录读取 info.json。页面重入会重建这个状态；下载记录列表、页面导入 revision、维护全局 revision 变化又会重新读取。ViewModel 的 loadAllDownloadedHanime 原先每调用一次都新增 Flow collector，也增加了重复处理。

本轮开始时现有轻量 loader、JSON parser、getDownloadVideoFolder 都是只读；getDownloadVideoFolder 仅构造 File，并不 mkdir。普通进入下载页的元数据读取没有写库或创建/修改本地文件。用户主动执行目录恢复/维护则原本就会更新数据库或相应文件，应与普通页面加载区分。

本次 Room v5→v6 只添加 author、genre、metadataResolved 三列。作者仍严格读取 artist.name，类型严格读取 artist.genre，不用 tags 猜测。ViewModel 首次对旧完成记录回填，等待 Room 发出已回填记录再展示，已保存记录直接展示，不再由 Screen 读取文件；普通重入不重复写元数据或重扫 JSON。缺失/损坏结果也记录识别状态以免重复读，显示未知。明确的目录恢复/维护通知允许重识别；读取失败保留先前已保存内容。

DAO 只对当前仍存在、Finished 且 URI 未变化的记录更新元数据列；普通回填不覆盖已识别记录，不会改下载进度、ID、分组、加入时间或复活已删除记录。目录导入同时保存已解析作者和类型，原有同质量记录的 ID/分组/时间保留。恢复现存记录使用带状态及元数据快照条件的局部更新，避免扫描期间覆盖新任务状态或刚写入的元数据；新建记录在事务中复查冲突，冲突计跳过。旧 Flow collector 会先取消再替换。同视频不同实际 URI 分别读取，避免旧目录失效影响另一质量；JSON 根结构非对象按损坏处理，不用空结果覆盖已保存信息。

## 四项进度定义

所有操作共用数量格式：总数、已完成（含失败及跳过）、失败、跳过；成功数 = 已完成 - 失败 - 跳过。每处理完一个候选项更新一次，在正常结束时已完成 = 总数 = 成功 + 失败 + 跳过；中止或取消时不把未结束项算成已完成，不伪造失败候选。

| 操作 | 总数与候选单位 | 成功 | 跳过 |
| --- | --- | --- | --- |
| restore | 扫描入口发现的直接候选项，主要是下载视频目录 | 成功导入或有效恢复的目录（无需变更的有效已存在目录也算成功处理） | 非目录、存在未完成任务等保护项 |
| clean | 根目录每个项，以及能够识别为应用下载目录的直接子项；发现子项时总数递增 | 删除成功的文件或私有空目录 | 数据库/任务引用、未知文件、SAF目录及其他保护项 |
| clearInvalid | 数据库记录快照的每条记录 | 明确无效且复查未变后删除的完成记录 | 未完成、有效、权限/I/O未知或已变化的记录 |
| repair | 数据库记录快照的每条记录 | 成功修复封面并安全更新记录 | 未完成、原封面有效或缺少可用本地视频/父目录等无法处理项 |

失败表示候选处理发生异常或明确的操作失败，具体分类沿用既有规则。例如恢复中非法目录名、缺少 JSON/视频也归入失败，并提供原因；不新增记录且不改坏既有记录。处理中与最终消息使用相同计数对象，不使用百分比。

## 界面修改与保护

列表/多列是 LIST/GRID 两种布局，grouped 独立控制是否生成分组头。两布局都渲染相同节点，网格分组头跨满列；搜索时沿用原来的平铺搜索结果。多选时卡片/复选框只切换选中状态，隐藏普通播放、删除、详情动作，底部统一显示操作栏；底栏支持换行并按真实高度为列表预留空间。空结果仍可退出多选，系统返回和左上返回也先退出多选。

设置 → 下载增加默认视图，列表/多列写入 SharedPreferences，页面进入时初始化；当前页的切换不改默认偏好。分组切换不强制变更布局。全选和批量动作只作用于当前可见项，过滤/折叠后剔除不可见选择。

顶部按可用宽度预留返回/标题区域和 48dp 触摸区，溢出时给更多菜单留槽。直接图标按以下优先级保留：布局、排序、类型、分组、多选、搜索、创建组、读取目录，溢出菜单包含其余动作，多选时仍可访问。

保留互斥锁、取消传播、未知个人文件保护、数据库所有状态引用保护、任务保护、删除前复查；清除无效记录只移除明确无效完成记录，不删文件；SAF目录保留避免递归删除；封面修复校验解码且新文件写入后复查更新。

## 更新与版本

页面链接及 Retrofit baseUrl 切到 kelaner/Han1meViewer。Release 实际请求为 https://api.github.com/repos/kelaner/Han1meViewer/releases/latest ；CI 实际请求为 https://api.github.com/repos/kelaner/Han1meViewer/actions/workflows/ci.yml/runs?event=push&status=success&per_page=1&branch=main 。支持四段版本比较；优先从 Release 选择 arm64 APK，避免误下载说明文件。发布前该仓库尚无 Release，latest 返回404，CI请求200但无成功运行，发布后需再次验证。

版本名固定 1.1.0.1，versionCode 沿用项目原 UTC yyMMddHH 规则；APK 使用 Han1meViewer-1.1.0.1-arm64-v8a.apk。沿用原 release 仅 arm64-v8a，不擅自扩增或削减播放器依赖；R8、资源压缩和 JNI ZIP 压缩用于减小产物。正式 `:app:assembleRelease` BUILD SUCCESSFUL（3m4s），release lintVital 通过，无配置的 Crashlytics mapping 上传正确跳过。APK 大小 **25,552,160 字节（25.552 MB / 24.368 MiB）**，低于严格十进制100,000,000字节限制。`aapt dump badging` 确认 applicationId=com.yenaly.han1meviewer、versionName=1.1.0.1、versionCode=26101816、minSdk=27、targetSdk=37、native-code=arm64-v8a；`apksigner verify --verbose --print-certs` 通过，v2签名有效。

APK SHA-256：`690dd399bcd60886c831e9887c740c88af7226310e68385fa22c337da95ad0bc`。

发布证书 SHA-256：`795dce2fe2e1bcc1dbaf373ed5699a319d33f71ab91362e0014253b1897deddb`，RSA3072。功能源码提交：`fea8c2e562eaed0916ef7b0f64dc9b01189c8ac2`。APK复制到 `artifacts/1.1.0.1/Han1meViewer-1.1.0.1-arm64-v8a.apk`，校验文件 `artifacts/1.1.0.1/SHA256SUMS.txt`。

按用户明确选择，在项目外生成并保留本地发布签名。路径 C:\Users\Ark\.android\han1meviewer-release\Han1meViewer-release.jks，配套签名配置 signing.json 在同目录，未上传。旧版本证书不同将无法直接覆盖安装；本次未卸载或清除任何旧应用数据。

## 验证结果

最终 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 不使用 init script，BUILD SUCCESSFUL（3m56s）。Lint 错误0，仍有355条现存警告，未用baseline或关闭检查绕过；修正了下载资源读取、缺失译文及旧设置入口API标注/兼容问题。`git diff --check` 通过。

已运行17项 JVM 单元测试，失败0、错误0。覆盖轻量artist字段解析、缺失/损坏元数据、2MiB上限、扫描350目录异常隔离/资源释放、未完成任务保护、导入字段持久化、已存在记录保护、混合逐项计数、取消不报完成、清理文件命名保护、宽度预算和四段版本比较。

设备回归测试已编译：列表/多列×分组/不分组多选、全选/清空/退出、空结果退出、320dp更多菜单动作，及 v5→v6 Room 迁移/数据库关闭重开持久化/任务保护。该测试使用独立临时数据库，不访问用户 download.db。临时真实 SQLite 对照当前 Room 生成 schema 的校验通过，旧记录13列原值保留，外键检查无误；这不等价于已执行 Android 设备测试。

构建开始时有两个ADB环境（x86_64 API32），随后连接消失，重连127.0.0.1:16384/16416均被拒绝，最终adb devices为空。未实际完成安装启动、重复进出页面、点击四项维护、真实SAF权限/删除失败/网络封面/视频帧生成/真机性能或覆盖升级验证；不能把编译通过写成设备验收通过。发布APK仅arm64，也不能直接在原x86_64环境验证其原生播放器。

## 构建复现

JDK：D:\Jdk21\unzip\jdk-21.0.12.1+1；Android SDK：D:\AndroidSdk。设置 JAVA_HOME、ANDROID_HOME 后运行 `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain`。正式构建从项目外签名配置向进程环境载入 KEYSTORE_FILE、KEYSTORE_PASSWORD、KEY_PASSWORD、KEY_ALIAS，再运行 `./gradlew.bat :app:assembleRelease --console=plain`；不需要 init script。日志在 artifacts/1.1.0.1-safety-validation.log 与 artifacts/1.1.0.1-release-build.log。签名配置不要提交或输出到日志。

缺少 google-services.json，按用户选择支持无Firebase配置：缺配置时跳过统计、崩溃上报、远端公告；GitHub更新和本地功能继续走原流程，CI开关用已有本地默认；有真实配置仍启用原服务。没有伪造配置。真实Firebase服务未验证。
