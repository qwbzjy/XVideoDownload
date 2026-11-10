# X 内容下载 / Markdown 存档（Android）

粘贴 / 分享 X（Twitter）推文链接，把**正文文字、图片、视频**按日期归档到同一个目录：

- 每天一个 `yyyy-MM-dd.md`，同一天的帖子**追加**到同一个文件，帖子之间用 `---` 分隔
- 图片用 `![](文件名)` **内嵌进 md**，视频用链接引用
- 图片 / 视频文件与 md **同目录**，所以 md 里的相对引用直接生效
- 存档目录可在设置里通过系统文件夹选择器（SAF）指定，权限持久化；未指定时默认 `Download/X存档/`

## 构建

```bash
# 需要 JDK 17+（已在 gradle.properties 里指定本机 JDK 21 路径）
./gradlew :app:testDebugUnitTest   # 36 个本地单测
./gradlew :app:lintDebug           # 静态检查（无 error）
./gradlew :app:assembleDebug       # 产出 app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

本机环境：macOS 13 / Android SDK platform 36.1 / Gradle 8.13 / AGP 8.13.2 / Kotlin 2.3.21 / Compose BOM 2026.02.00（compose 1.10.3 + material3 1.4.0）。

> 依赖版本刻意选了与 AGP 8.13.2 + compileSdk 36.1 兼容的一档：更新的 Compose BOM 2026.09.00 / core-ktx 1.19.1 / lifecycle 2.11.0 要求 compileSdk 37 与 AGP 9.1，本机工具链不满足。
> `gradle.properties` 里显式指定了 JDK 21（本机 `JAVA_HOME` 指向已卸载的 JDK 8）；`gradlew` 也做了兼容处理，`JAVA_HOME` 失效时会自动回退到 PATH 上的 java。

## 功能

- **粘贴 / 分享**：输入框 + 「粘贴」按钮，或从 X 的分享菜单直接 `分享 → X 内容下载`（自动填入并跳回下载页）
- **按天 Markdown 存档**：每条帖子生成一段 `## @作者 · 时间` + 正文 + 原链接 + 推文 ID，追加到当天 md，用 `---` 分隔
- **图片内嵌**：`media.photos[]` 全部下载到存档目录，并在 md 里写成 `![图片 1 · 1378x917](xxx.jpg)`
- **视频引用**：视频下载到同目录，md 里写成 `🎬 [视频 1 · 1280x720](xxx.mp4)`
- **画质选择**：视频列出所有 mp4 清晰度（360p / 720p / 1080p / 4K…），默认取不超过设置上限的最高码率
- **全部保存**：把图片 + 视频 + Markdown 一起加入队列；Markdown 任务排最后，引用的是媒体落盘后的**真实文件名**（含系统自动加的 ` (1)` 后缀）
- **指定目录**：设置里选任意文件夹（SAF tree URI，权限持久化）；也可恢复默认 `Download/X存档/`
- **日期口径**：默认按「保存当天」，可切换为按「推文发布日期」（X 时间戳按 UTC 换算，避免时区漂移）
- **封面预览 / 后台下载 / 通知 / 保存历史 / 设置**：沿用 v0.3 能力；历史按「视频 / 图片 / Markdown」标注，旧版记录兼容
- 支持 `x.com` / `twitter.com` / `mobile.twitter.com` / `/i/status/` / fxtwitter / fixupx / vxtwitter 变体、混在分享文本里的链接、`t.co` 短链跟随跳转，以及直接粘贴 `video.twimg.com` mp4 直链

## 保存位置

| 内容 | 文件 | 说明 |
|---|---|---|
| Markdown | `<存档目录>/2026-11-10.md` | 同一天追加，帖子间 `---` 分隔 |
| 图片 | `<存档目录>/X_作者_推文ID_img_宽x高.jpg` | 原图（`?name=orig`），被 md 内嵌引用 |
| 视频 | `<存档目录>/X_作者_推文ID_宽x高.mp4` | 被 md 以链接引用 |

默认存档目录：`Download/X存档/`（MediaStore.Downloads，API 29+；API ≤28 写公共 Download 目录）。
在设置里选了 SAF 目录后，三者全部写到该目录。

## Markdown 样例

```markdown
# X 推文存档 · 2026-11-10

## @jack · Tue Mar 21 20:50:14 +0000 2006

just setting up my twttr

- 原链接：https://x.com/jack/status/20
- 推文 ID：20

---

## @NatGeo · Mon Nov 09 20:00:00 +0000 2026

The Antarctic climate may be brutal, but this huddle of emperor penguins makes it look like a cozy affair.

- 原链接：https://x.com/NatGeo/status/2119882719270162776
- 推文 ID：2119882719270162776

![图片 1 · 1378x917](X_NatGeo_2119882719270162776_img_1378x917.jpg)
```

## 原理

```
链接 → 提取推文 ID → GET https://api.fxtwitter.com/2/status/{id}
     → status.text / raw_text.text            → 正文
     → status.media.photos[].url              → 原图直链
     → status.media.videos[].formats[]        → 各档 mp4 直链
     → 队列顺序执行：图片 → 视频 → Markdown 追加
```

- 纯文字推文 `media` 是空对象但仍能解析出正文；纯图推文有 `media.photos[]`；三者都提取，不再因「没有视频」报错。
- `MarkdownEntry`（纯 JVM 可测）负责生成标题、片段、`---` 分隔、图片/视频引用和追加拼接。
- `DownloadService` 顺序执行 `DownloadCenter` 队列；同一条帖子的任务共享 `groupId`，Markdown 任务执行时从同组已完成任务里收集真实文件名再写引用。
- `ArchiveStorage` 统一出口，三条路径：SAF tree URI → MediaStore.Downloads（API 29+）→ 公共 Download 目录（API ≤28）。md 用「读出已有全文 → 拼接 → 整体覆写」而不是 `wa` 追加，兼容所有 SAF provider。
- 依赖 `androidx.documentfile:documentfile:1.0.0` 做 SAF 目录读写；SAF 选目录后调用 `takePersistableUriPermission` 持久化权限。
- 落盘文件**永远是 `.md`**：md 用 `text/markdown` MIME 写入（不会被 MediaProvider 追加 `.txt`）；`find()` 优先精确匹配 `.md`，若命中早期构建留下的 `.md.txt` 会自动把内容搬到同名 `.md` 并删除旧文件，保证之后一直追加到 markdown 文件。
- 解析责任链：主地址 `/2/status/{id}` → 主地址 `/i/status/{id}` → 设置里配置的备用地址，自动降级。

## 网络要求

- 解析需要能访问 `api.fxtwitter.com`；下载需要能访问 `video.twimg.com`（视频）与 `pbs.twimg.com`（图片 / 缩略图）。
- 在中国大陆网络环境下通常需要系统代理 / VPN；本 App 未内置代理。

## 已知限制

- 只处理**公开推文**；仅登录可见 / 私密 / NSFW / 年龄限制内容会明确报错，不做绕过。
- 不处理 m3u8（当前 API 直接给 mp4，无需分片合并）。
- 同一条帖子重复保存会重复追加到 md（未做去重）。
- 选了 SAF 目录后，图片/视频写在该目录下，不一定会出现在系统相册的 Pictures/Movies 分类里。
- 保存队列保存在内存中，App 进程被系统杀死后队列会丢失；已落盘的文件与历史不受影响。
- 依赖公共解析服务，挂了可在设置里加备用地址或指向自建 FxEmbed 实例。

## 合规说明

仅用于个人保存自己有权保存的公开内容，请勿用于批量抓取、转载他人作品或绕过访问控制。

## 目录

```
app/src/main/java/com/example/xvideodownload/
  MainActivity.kt / MainScreen.kt / MainViewModel.kt / UiState.kt / XVideoTheme.kt
  core/       LinkParser, ParsedLink, Models, MarkdownEntry, ResolverConfig,
             SettingsStore, TweetResolver, FxResponseParser, ThumbnailLoader
  download/   ArchiveStorage, MediaDownloader, DownloadCenter,
             DownloadService, DownloadNotifier, HistoryRepository
  ui/         DownloadTab, HistoryTab, SettingsTab, UiComponents
app/src/test/  LinkParserTest, FxResponseParserTest, RealResponseTest,
              MarkdownEntryTest, ArchiveNameTest
app/src/test/resources/
  video_tweet_v2.json / photo_tweet.json / text_tweet.json   # 线上真实抓取的响应样本
```

## 验证状态

- ✅ `clean` 后全量构建通过，产出可安装 APK（约 11 MB），v0.5
- ✅ 36 个 JVM 单测通过：`MarkdownEntryTest`（首次写入、`---` 分隔、图片内嵌、视频链接、日期口径）、`ArchiveNameTest`（追加时匹配 provider 改过的文件名、优先 `.md`）、以及真实视频 / 图片 / 纯文字响应回归
- ✅ `lintDebug` 无 error
- ✅ v0.2 / v0.3 真机验证过：视频解析与画质选择、图片原图保存、文字保存、历史、设置持久化、分享入口切 Tab、按 Home 切后台后前台服务继续完成下载
- ✅ v0.5 真机完整验证（Android 14 / API 34）：
  - **文件就是 markdown**：默认模式保存后生成 `Download/X存档/2026-11-10.md`，目录里没有任何 `.txt`；早期 bug 留下的 `2026-11-10.md.txt` 被自动把内容搬进 `.md` 后删除
  - 同日追加：连续保存 3 条后同一个 `2026-11-10.md` 里出现 3 段，段间用 `---` 分隔；新增段的图片引用是带 ` (4)` 后缀的真实落盘文件名
  - 同目录：md 与 jpg 在同一个目录，相对引用可直接打开
  - 按发布日期归档：开启后生成 `2026-11-09.md`，内容含标题、`## @作者`、正文、原链接、推文 ID 与 `![图片 1 · 1378x917](相对文件名)`
  - SAF 指定目录：用系统文件夹选择器新建并选中 `XArchiveTest`，权限持久化后，`.md` 与图片都写进了该目录，设置页正确显示「当前：XArchiveTest」
- ✅ 真机暴露并修复了四个 bug：
  1. `text/plain` + `xxx.md` 被 MediaProvider 改名成 `xxx.md.txt`，导致 `find()` 每次都找不到、重复新建文件 → 改用 `text/markdown` MIME + 前缀兜底匹配
  2. 图片引用拿到的是 `IS_PENDING` 阶段的旧文件名，重名时指向不存在的 `xxx.jpg` → 改为 `publish()` 之后回查真实文件名
  3. `find()` 用 `relative_path='Download/X存档'` 等值查询，但 MediaProvider 实际存的是带尾斜杠的 `Download/X存档/`，永远查不到 → 改成 `LIKE 'Download/X存档%'`
  4. 早期构建留下的 `xxx.md.txt` 会被继续追加、导致存档格式仍是 txt → `find()` 优先只认 `.md`，命中 `.txt` 时自动把内容迁移成同名 `.md` 并删除旧文件；md 写入统一用 `text/markdown` MIME，打开时先试 `text/markdown` 再回退 `text/plain`
- ✅ 设备上的 `.txt` 残留已在最后验证后清理，`Download/X存档/` 现在只有 `.md` 与图片
