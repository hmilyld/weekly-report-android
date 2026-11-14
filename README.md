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
- 登录 / 退出、当前用户、修改密码；
- 主题跟随系统 / 浅色 / 深色；
- 生物识别开关、通知权限入口、小部件添加、离线队列状态；
- 版本号与安全说明。

## 离线与冲突

所有写操作先保存到本机 SQLite，再进入按顺序发送的 `outbox`：

- 断网、进程被杀或后端暂时不可用时，内容不会丢；
- 同一日期多次保存只保留最后一次；
- 恢复网络后由 WorkManager 补发；
- 重放前会读取服务器当前值：如果服务器在该日期上已被其他设备更新，**不会静默覆盖**，而是提示冲突，让你选择“保留服务器版本”或“用本地覆盖”。

## 安全

- Release 构建只允许 HTTPS；明文 HTTP 仅在 Debug 构建中用于局域网联调。
- 明文密码不落盘；JWT 使用 Android Keystore 的 AES-GCM 密钥加密保存。
- 后端因重启丢失密钥缓存时，接口会返回 `_decryption_failed`。客户端会立即回到登录页并说明原因，而不是显示空白内容；重新登录后内容恢复。
- 修改密码时严格区分：`402` 表示当前密码错误（会话保留）；`401` 才会话失效、回登录页。

## 环境要求

- Android 8.0（API 26）及以上；
- 一个可访问的 `weekly-report` 后端（`/api/v1`，JWT）；
- Release 包要求 HTTPS 地址；局域网 HTTP 仅用于 Debug 联调。

## 安装

从源码构建后安装：

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

首次启动填写服务器地址并登录。服务器需要事先通过后端的 `POST /api/v1/auth/setup` 或管理端创建账号。

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
│   ├── SessionManager.kt    401 / _decryption_failed 恢复
│   ├── local/AppDatabase.kt SQLite 缓存、outbox、冲突
│   ├── prefs/               服务器地址、主题、生物识别等设置
│   ├── secure/              Keystore 加密 token
│   ├── repo/                日报 / 待办 / 报告仓库
│   └── sync/                补发引擎、WorkManager、冲突规则
├── network/                 OkHttp + kotlinx.serialization
├── notifications/           本地到期提醒
├── widget/                  今日日报小部件
├── ui/                      Compose 主题、组件与页面
└── util/                    日期、列表输入、URL 规则
```

## 文档

- `AGENTS.md`：给 AI 代理和维护者的项目说明、架构约束、命令与验收清单。
- `docker-compose.local.yml`：独立本地后端编排。
- `tools/mock-llm.js`：契约验证用 mock。

## 开发状态

核心功能、离线队列、冲突处理、密钥缓存恢复和四个原生能力已实现，并在模拟器上完成主要走查；Release 已使用本地演示密钥签名。真机上的完整逐项走查、真实 LLM 生成质量、以及使用 docker-compose 的完整后端验收仍需在相应环境中补充。
