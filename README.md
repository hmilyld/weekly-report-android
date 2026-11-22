# 周记 · Android

自托管「周记」系统的原生 Android 客户端。用 Kotlin 与 Jetpack Compose 编写，对接 [weekly-report](https://github.com/hmilyld/weekly-report) 后端的 `/api/v1` 接口。

它把网页端已有的日报、待办、周报/月报和账号设置带到手机上，并补上网页做不到的四件事：

- **主屏小部件**：显示今天写了什么，一点直达当天编辑；
- **本地到期提醒**：待办逾期或三天内到期时由手机通知；
- **系统分享**：复制周报/月报后直接拉起系统分享面板；
- **生物识别解锁**：已保存的会话需要指纹或面容才能打开。

## 功能

### 日报
- 周一为周首的周视图，今天高亮，显示本周已记天数；
- 上一周 / 下一周 / 回到本周；
- 底部编辑抽屉：空记录自动以 `1. ` 开头，回车续号，空条目回车结束列表；
- 中文输入法组合输入期间不会误触发续号；
- 未保存离开时二次确认；保存、删除；
- 离线可写，联网后按顺序补发。

### 待办
- 新建、勾选完成 / 恢复、行内编辑、删除；
- 逾期与三天内到期标记；
- 已完成列表分页加载；
- 系统通知权限引导与本地提醒。

### 周报 / 月报
- 周报 / 月报切换与周期导航；
- 未生成时（404）展示空状态，生成中显示忙碌态；
- 生成后可编辑保存、复制、复制并分享；
- 结果缓存在本地，断网时仍可阅读。

### 设置
- 服务器地址：首次启动必填，之后可修改（修改后重新登录）；
- 登录 / 退出、当前用户；首次登录后自动创建长期 API Token，之后免每天登录；
- Token 模式下账号信息与修改密码在网页端处理，App 内提供「吊销本机 Token」；
- 主题跟随系统 / 浅色 / 深色；
- 生物识别开关、通知权限入口、小部件添加、离线队列状态；
- 更新：自动检查开关、手动检查、当前版本与发布渠道；
- 版本号与安全说明。

## 自动更新

Release 版本通过 GitHub Releases 更新，不依赖 Google Play 或应用商店：

- 启动时最多每天检查一次最新发布，24 小时内不重复请求；设置页可随时手动检查，手动检查不受节流影响。
- 检查优先读取 `releases/latest/download/update.json`（发布流水线随每个版本上传的静态文件），不消耗 GitHub API 配额；该文件缺失时才回退到 `/repos/.../releases/latest` 接口。
- `update.json` 里的 `apkUrl` 固定到具体 tag，所以接受 1.1.0 的用户即使期间发布了 1.2.0，下载到的仍是 1.1.0 的包。
- 只有成功拿到响应才记录检查时间：网络不通不会让下一次检查被压后 24 小时；被限流（403/429）则退避一天，避免每次启动都去撞墙。
- 发现新版本时弹窗展示版本号与发布说明，发布说明按 Markdown 简化渲染（标题、列表、加粗、行内代码、链接）。「稍后」忽略该版本（记住版本号，直到出现更新的版本才再提示）；「取消」或返回键只关闭弹窗，不记录忽略。
- 版本比较以语义版本为准，`versionCode` 只作为同名版本的次级判断：只提升构建号的热修复也算更新，而构建号永远不会让一个更低的版本号「变成」更新。API 回退路径拿不到构建号，此时只比版本号。
- 「下载」把 APK 流式写入 `cache/updates` 并显示进度；下载使用独立的 OkHttp 客户端，只限制单次读超时，慢速链路上不会因整体超时被截断。若 `update.json` 提供了 `apkSha256`，下载完成后校验，不匹配则删除并提示重新下载。每次新下载会先清掉上一次残留的安装包。
- 安装时把 APK 通过 `content://` 交给系统安装器后**不能立刻删除文件**：安装器要等用户点确认才真正读取它。因此交接后只清理其它残留，当前包保留到下一次下载。
- 首次安装需要用户允许「安装未知应用」；授权后回到应用会自动刷新按钮状态。APK 通过 `FileProvider`（`${applicationId}.updates`）交给系统安装器。
- 覆盖安装要求签名一致：CI 使用仓库 secrets 里的 keystore 构建 Release，见 `AGENTS.md` 的发布流程。未签名的构建不会发布 `update.json`，客户端因此回退到 API，只提供「在浏览器中打开」。
- 网络失败、限流或解析失败都不打扰用户，错误只在更新弹窗或手动检查时提示。
- `UPDATE_FEED_URL` 只是 Debug 的测试接缝：Release 构建在 `app/build.gradle.kts` 里被硬编码为空，即使误传 `-Papp.updateFeedUrl` 也只会走真实发布渠道。

本地验证整条链路（无需发布新版本）：

```bash
node tools/mock-update-feed.js 18082 app/build/outputs/apk/debug/app-debug.apk v9.9.9
./gradlew :app:assembleDebug -Papp.updateFeedUrl=http://10.0.2.2:18082/latest/update.json
```

给 `mock-update-feed.js` 传第 5 个参数可以伪造一个错误的 sha256，用来验证校验失败路径：

```bash
node tools/mock-update-feed.js 18082 app/build/outputs/apk/debug/app-debug.apk v9.9.9 deadbeef...
```

`-Papp.updateFeedUrl` 会写入 `BuildConfig.UPDATE_FEED_URL`；不传该参数时始终使用真实的发布渠道。Release 构建会忽略它。

## 免每天登录

后端 JWT 默认 24 小时过期。为避免每天重新登录，客户端在首次账号密码登录成功后会：

1. 用刚拿到的 JWT 调用 `POST /api/v1/tokens` 创建一个名为「Android 客户端」的长期 API Token；
2. 只把 Token 存进 Android Keystore，后续数据请求统一带 `X-API-Token`；
3. 再次登录时尝试吊销上一次创建的 Token，避免服务端累积。

Token 模式下 `/users/me` 与改密接口只接受 JWT，所以 App 不再内置改密：账号信息与密码修改请在网页端完成。设置页仍提供「吊销本机 Token」，需要输入当前密码换取 JWT 后调用 `DELETE /api/v1/tokens/{id}`。

旧后端没有 `/tokens` 时自动回退 JWT，设置页会提示 24 小时后需要重新登录。

## 离线与冲突

所有写操作先保存到本机 SQLite，再进入按顺序发送的 `outbox`：

- 断网、进程被杀或后端暂时不可用时，内容不会丢；
- 同一日期多次保存只保留最后一次；
- 恢复网络后由 WorkManager 补发；
- 重放前会读取服务器当前值：如果服务器在该日期上已被其他设备更新，**不会静默覆盖**，而是提示冲突，让你选择“保留服务器版本”或“用本地覆盖”。

## 安全

- Release 构建只允许 HTTPS；明文 HTTP 仅在 Debug 构建中用于局域网联调。
- 明文密码不落盘；JWT 与长期 API Token 都使用 Android Keystore 的 AES-GCM 密钥加密保存。
- 首次账号密码登录后，客户端用 JWT 创建长期 API Token，数据请求改用 `X-API-Token`；JWT 仍只有 24 小时有效期，但不再影响日常使用。
- 409 `encryption_key_missing` / `decryption_failed` 与响应中的 `_decryption_failed` 表示服务端内容密钥问题；客户端保留会话并提示，不渲染空白内容，也不会用重新登录来“修复”。
- 只有 401 才会话失效、回登录页；修改密码 `402` 表示当前密码错误（会话保留）。
- Token 模式下 `/users/me` 与改密接口只接受 JWT，因此 App 把账号管理与改密放到网页端；退出登录只清本机 Token，服务端 Token 可通过设置页「吊销本机 Token」或网页端删除。

## 环境要求

- Android 8.0（API 26）及以上；
- 一个可访问的 `weekly-report` 后端（`/api/v1`，JWT + API Token；建议 1.2.0 及以上）；
- Release 包要求 HTTPS 地址；局域网 HTTP 仅用于 Debug 联调。

## 安装

从源码构建后安装：

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

首次启动填写服务器地址并登录一次；客户端会创建长期 API Token 并加密保存，之后不再受 JWT 24 小时过期影响。服务器需要事先通过后端的 `POST /api/v1/auth/setup` 或管理端创建账号。

## 构建

已验证工具链：

| 组件 | 版本 |
| --- | --- |
| JDK | 21 |
| Android SDK | platform 35 / build-tools 35 |
| Gradle | 8.11.1（wrapper） |
| AGP / Kotlin | 8.9.1 / 2.1.10 |
| minSdk / targetSdk | 26 / 35 |

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

### 签名 Release

仓库不提交签名私钥。把 `keystore.properties.example` 复制为 `keystore.properties`，填入自己的 keystore 路径和密码，再构建 Release：

```properties
storeFile=app/keystore/release.jks
storePassword=change-me
keyAlias=weeklyreport
keyPassword=change-me
```

`keystore.properties` 与 `*.jks` 已在 `.gitignore` 中。没有签名配置时 Release 仍可构建，但产物未签名，不能直接分发。

## 本地后端

优先使用 `weekly-report` 仓库的 Docker 镜像/Compose。本仓库提供了 `docker-compose.local.yml`，以 `../weekly-report` 为构建上下文，独立端口 `18081`、独立数据目录 `.local-backend/docker-data`：

```bash
export JWT_SECRET_KEY="$(openssl rand -hex 32)"
export ENCRYPTION_MASTER_KEY="$(openssl rand -base64 32)"
docker compose -f docker-compose.local.yml up --build
```

没有 Docker 时，也可以直接运行同源码的后端；步骤见 `AGENTS.md`。

`tools/mock-llm.js` 是一个 OpenAI-compatible 的本地 mock，只用于验证“生成请求已发出 / 404 / 200 / 忙碌态”的契约，不是真实 LLM，生成质量不代表真实模型。

## 项目结构

```
app/src/main/java/com/hmilyld/weeklyreport/
├── AppContainer.kt          手动依赖容器
├── MainActivity.kt          单 Activity，小部件/通知深链
├── data/
│   ├── SessionManager.kt    登录、长期 Token、401 登出
│   ├── local/AppDatabase.kt SQLite 缓存、outbox、冲突
│   ├── prefs/               服务器地址、主题、生物识别等设置
│   ├── secure/              Keystore 加密 token
│   ├── repo/                日报 / 待办 / 报告仓库
│   ├── sync/                补发引擎、WorkManager、冲突规则
│   └── update/              GitHub Releases 检查、APK 下载与安装
├── network/                 OkHttp + kotlinx.serialization
├── notifications/           本地到期提醒
├── widget/                  今日日报小部件
├── ui/                      Compose 主题、组件与页面（含更新弹窗）
└── util/                    日期、列表输入、URL 规则、版本比较
```

## 文档

- `AGENTS.md`：给 AI 代理和维护者的项目说明、架构约束、命令与验收清单。
- `docker-compose.local.yml`：独立本地后端编排。
- `tools/mock-llm.js`：契约验证用 mock。
- `tools/mock-update-feed.js`：模拟 GitHub 发布源，用于走查应用内更新。
- `tools/mock-backend.js`：最小后端，用于登录后进入设置页验证。

## 开发状态

核心功能、离线队列、冲突处理、密钥缓存恢复和四个原生能力已实现，并在模拟器上完成主要走查；Release 已使用本地演示密钥签名。真机上的完整逐项走查、真实 LLM 生成质量、以及使用 docker-compose 的完整后端验收仍需在相应环境中补充。

应用内更新已在模拟器上验证：更新弹窗、下载进度、`FileProvider` 交接系统安装器、授权跳转与返回刷新、「稍后」忽略同一版本。真实发布链路（推送标签 → CI 签名构建 → GitHub Release → 客户端覆盖升级）需要在下一个版本发布时验证。
