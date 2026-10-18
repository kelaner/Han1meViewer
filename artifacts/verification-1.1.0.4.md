# HANi 1.1.0.4 界面变更验证

## 实现

- 完整操作序列共九项：切换视图、排序、类型分类、分组、多选、搜索、创建分组、一键更新、读取下载目录。更新是列表内第八项；工具栏和溢出菜单都从同一序列按屏宽切分，非菜单展示为纯图标。下载中页的更新入口同样改为图标。
- 共用 `DownloadedBadge` 观察既有 `DatabaseRepo.HanimeDownload.downloadedVideoCodes`，由 Room 完成记录驱动。没有视频编号或无完成记录时不显示。常规封面内距 6dp，小型封面内距 2dp。
- 标准卡片下载图标位于封面左上角，已播放标记保持右上角。播放蒙层不遮盖下载图标。独立卡片使用相同状态组件；创作中心审核角标移至右上角。

## 非下载页逐项覆盖审查

| 页面/入口 | 实际视频卡片实现 | 验证依据 |
| --- | --- | --- |
| 首页分类 | CategoryRow → VideoCardItem | 调用链审查，共享卡片与真实 Room 仪器测试 |
| 首页轮播、队列 | BannerCarousel / HeroQueue | 两类实际组件的 Room 完成记录新增/删除仪器测试 |
| 订阅 | SubscriptionContent → VideoCardItem | 调用链审查，共享卡片与真实 Room 仪器测试 |
| 喜爱、稍后再看 | VideoGridContent → VideoCardItem | 两条路由审查，共享卡片与真实 Room 仪器测试 |
| 搜索 | SearchResultsGrid → VideoCardItem | 调用链审查，共享卡片与真实 Room 仪器测试 |
| 在线历史 | OnlineWatchHistoryGrid → VideoCardItem | 调用链审查，共享卡片与真实 Room 仪器测试 |
| 本地历史 | WatchHistoryCard | 封面叠层和状态组件接入静态审查 |
| 我的播放清单内视频 | MyplayListBottomSheet → VideoCardItem | 调用链审查，共享卡片与真实 Room 仪器测试 |
| 创作中心已上传 | CreatorUploadedPage → VideoCardItem | 调用链审查，共享卡片与真实 Room 仪器测试 |
| 创作中心上传中 | CreatorUploadingCard | 实际组件 Room 完成记录新增/删除仪器测试 |
| 视频详情系列、相关推荐、宽屏侧栏 | PlaylistSection / RelatedVideosSection → VideoCardItem | 调用链审查，共享卡片与真实 Room 仪器测试 |
| 视频详情全部播放列表弹窗 | 私有 PlaylistBottomSheet 的逐视频封面 | 封面叠层和状态组件接入静态审查 |
| 月份预览 | PreviewTourRow / PreviewInfoCard | 两类实际组件 Room 完成记录新增/删除仪器测试 |
| 打卡视频记录、历史选择项 | ExistingRecordItem / WatchHistoryItem | 两类实际组件 Room 完成记录新增/删除仪器测试 |

下载页不在本次角标变更范围内。播放列表聚合图、月份/商品预览头图、图片查看器、头像及播放器海报不属于单视频卡片，不添加下载标记。

## 验证方法与边界

- Android API 35 x86_64 模拟器执行 Compose 仪器测试；菜单测试使用宽屏显示配置验证完整九图标与窄屏溢出顺序；160dp 标准卡片验证已下载/已播放同时显示、左右分离，切换下载状态前后已播放文字边界完全一致。
- 真实数据库测试不显式传 `isDownloaded`，检验暂停记录不显示、同视频另一画质完成后显示、删除完成画质后消失、暂停画质变完成后显示、删除视频后消失。
- 七类独立卡片参数化测试分别使用真实组件、唯一视频编号，验证完成记录新增显示和删除消失。测试后清理临时记录，图片为空地址，不依赖远程媒体响应。
- 页面覆盖审查与组件仪器测试相结合；未逐个登录线上账号浏览真实站点，不能将上述检查描述为所有线上页面的端到端人工验收。
- 当前“已下载”语义为数据库存在完成记录，与下载库保持一致；本次没有增加媒体文件完整性扫描或修复下载引擎。

## 执行记录

环境：JDK 21，Android SDK `D:/AndroidSdk`，Gradle Wrapper。

```powershell
$env:JAVA_HOME='D:\Jdk21\unzip\jdk-21.0.12.1+1'
$env:ANDROID_HOME='D:\AndroidSdk'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --offline --console=plain
```

结果：`BUILD SUCCESSFUL`。Debug APK 构建通过；23 项单元测试全部通过；15 项 API 35 模拟器仪器测试全部通过，失败 0、跳过 0。测试 Activity 使用应用的 Android 主题，保证矢量图标的主题属性得到解析；使用 Compose v2 同步规则，移除测试图片联网依赖。为提取截图又直接执行 13 项界面测试，全部通过。已查看截图，九个工具栏图标均可见，更新为第八个；窄屏菜单更新为倒数第二项；160dp 卡片左上下载图标与右上已播放文字清楚分离。`git diff --check` 通过。测试输出保存在本地 `artifacts/ui-1.1.0.4-final-tests.log`，截图在本地 `artifacts/device-validation/ui-1.1.0.4/`。

完整 `:app:lintDebug` 已执行，报告 2 个既有错误、355 个警告。错误均为 `values/strings.xml` 内 `sort_by_upload_ascending` 和 `sort_by_upload_descending` 缺少英文/日文翻译；经 `git grep HEAD` 核实提交前已经存在且本次未改动相关资源。未压低检查级别、删除规则或添加基线。本次保留该既有问题并明确记录，不能将完整 Lint 声称为通过。

发布目标：`kelaner/Han1meViewer`，新标签 `v1.1.0.4`，标题 `HANi 1.1.0.4`；保留 `v1.1.0.3`。使用项目既有发布签名，不生成新密钥。
