# AGENTS.md

本文档给 AI 代理和维护者阅读。面向用户的软件介绍在 `README.md`；两者冲突时，以本文件的工程约束为准。

## 1. 项目定位

- 原生 Android 客户端，包名 `com.hmilyld.weeklyreport`；Debug 后缀 `.debug`。
- Kotlin + Jetpack Compose，单模块 `:app`，手动依赖注入（`AppContainer`），不使用 Hilt/Room/Navigation。
- 后端是仓库外的 `weekly-report`（FastAPI，`/api/v1`，JWT）。**不得修改后端仓库的任何文件**；接口缺能力时先停下报告。
- 所有改动限于本仓库。

## 2. 环境与命令

本机工具链（可按实际环境调整）：

```powershell
$env:JAVA_HOME = 'E:\Androiddev\jdk21'
$env:ANDROID_HOME = 'E:\Androiddev\sdk'
$env:ANDROID_SDK_ROOT = 'E:\Androiddev\sdk'
$env:GRADLE_USER_HOME = 'E:\Androiddev\gradle-home'
```

验证命令（每次改动后至少跑 Debug 这一组）：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

约束：

- `minSdk 26`、`targetSdk 35`。
- Lint 必须 0 errors；当前约 50 条 warning（多为版本目录里的依赖升级提示，其次是 `AppDatabase.kt` 的 `SQLiteDatabase.transaction` 建议），新增 error 不允许。
- Release 使用 R8；新增反射/序列化模型时检查 `app/build/outputs/mapping/release/seeds.txt` 中 `$$serializer` 是否保留。
- 不提交 `keystore.properties`、`*.jks`、`dist/`、`evidence/`、`local.properties`、`build/`、`.gradle/`、`.kotlin/`。

## 3. 架构与数据流

```
MainActivity / Compose
        │
        ▼
ViewModels (ui/screens/*)
        │
        ▼
Repositories (data/repo)
        │                 ┌──────────────┐
        ├── ApiClient ────│ weekly-report │ /api/v1
        │                 └──────────────┘
        ▼
AppDatabase (SQLite) ──► outbox ──► SyncEngine ──► ApiClient
        ▲                                  │
        └──────── WorkManager ─────────────┘
```

关键文件：

| 文件 | 职责 |
| --- | --- |
| `network/ApiClient.kt` | OkHttp、JWT、错误映射、`_decryption_failed` 检测 |
| `network/ApiException.kt` | 401/402/网络/解析错误分类 |
| `data/SessionManager.kt` | 登录、锁定、401/解密失败强制登出 |
| `data/secure/SecureTokenStore.kt` | Keystore AES-GCM 加密 JWT |
| `data/local/AppDatabase.kt` | 日报/待办缓存、outbox、冲突、报告缓存 |
| `data/sync/SyncEngine.kt` | 按序补发、冲突检测、任务 revision 处理 |
| `data/sync/SyncScheduler.kt` | WorkManager 唯一队列（`APPEND_OR_REPLACE`） |
| `data/repo/*` | 面向 ViewModel 的写入/读取入口 |
| `ui/AppRoot.kt` | 登录/锁定/四页导航、全局队列横幅与冲突对话框 |
| `ui/components/Common.kt` | `ScreenHeader`、卡片、Banner、确认框等 |

## 4. 必须保持的不变量

### 4.1 写入必须经过本地队列

- UI/ViewModel 不得直接调用 `ApiClient` 保存日报或待办。
- 所有写入：先写 SQLite（`daily_cache` / `tasks_cache`），再写 `outbox`，然后 `SyncScheduler.requestDrain()`。
- 同一日期/同一待办只保留一个待发送实体，新的修改覆盖旧的 payload。
- 任务在“创建仍在队列中”时继续编辑，必须更新 CREATE 的 payload，而不是追加 UPDATE。

### 4.2 冲突不得静默覆盖

日报重放前必须读取服务器当前值，用 `ReplayRules.dailyDecision` 判断：

- 服务器值 == 本地值 → 已同步；
- 服务器值 == 本地基线 → 可发送；
- 其他 → `OutboxState.CONFLICT`，弹窗让用户选择；
- 用户选择“用本地覆盖”后，先把冲突时的服务器快照写回本地基线，再重发；否则会再次冲突。
- 删除同样要在服务器值相对基线发生变化时进入冲突。

### 4.3 401 与 `_decryption_failed`

- 任意响应（包括 200）嵌套 `_decryption_failed: true` → `SessionProblem.DECRYPTION_FAILED`。
- 401 → `SessionProblem.UNAUTHORIZED`。
- 两者都必须：清空内存 token、清空 Keystore token、回登录页、显示原因；**不得渲染空内容**。
- 登录接口自身的 401 不触发全局登出；修改密码 402 保留会话，401 才登出。
- 会话处于 `Locked` 时不得发送网络请求；`SyncEngine` 必须检查 `tokenOrNull()`。

### 4.4 Release 安全

- Release：`usesCleartextTraffic=false`，`network_security_config` 禁止明文，`UrlRules` 再拒绝 `http://`。
- Debug：允许明文，仅用于局域网联调。
- 用户密码绝不落盘；JWT 只能进 Keystore 加密存储；`allowBackup=false`。

### 4.5 UI 一致性

- 页面标题统一使用 `ui/components/Common.kt` 的 `ScreenHeader`。它的 kicker 行固定最小高度 `48dp`，以确保有/无右侧操作按钮的页面标题对齐；不要改回自适应高度。
- 设计语言：暖纸色 + 墨色 + 朱红强调色；标题衬线体，正文无衬线；间距使用 `Spacing`。
- 设置页的多个操作按钮使用 `FlowRow`（每行最多 2 个）并让文字单行，避免窄屏竖排断字。
- 所有面向上架界面的中文文案放在 `res/values/strings.xml`，Compose 用 `stringResource`、非 Compose 用 `context.getString`；不要在 Kotlin 里新增中文字面量（`util/`、`network/`、`data/` 的领域错误文本与 ViewModel 的 `_messages` 仍是例外，改动时同步迁移）。
- 圆角统一取 `MaterialTheme.shapes`（`small`=12dp、`medium`=16dp、`large`=20dp），不要在页面里临时写 `RoundedCornerShape(...)`；颜色只取 `colorScheme` 槽位，Snackbar/Switch 等组件依赖 `inverseSurface`、`surfaceContainerHighest` 等已补齐的槽位。
- 通知权限入口统一走 `NotificationPermission.openSettings`（系统通知设置页），不要改回 `RequestPermission`：部分 MIUI/HyperOS 运行时弹窗会直接失败或关闭应用；返回时通过 `ON_RESUME` 重新检查权限。
- 报告页动作分层：编辑/重新生成放在正文上方的文档工具栏，复制/复制并分享留在正文底部；生成状态紧邻工具栏，避免点了重新生成还要向上找提示。
- 所有可点击元素至少 48dp；破坏性操作必须二次确认。

### 4.6 列表输入与中文 IME

- 空日报以 `1. ` 开头；回车续号；空条目回车结束列表；保存时去掉末尾空编号条目。
- 只有“单个换行插入”且 `TextFieldValue.composition == null` 时才可改写文本；组合输入期间必须原样接受。
- 这条规则在 `util/ListInput.kt` 与 `DailyScreen` 的 `handleValueChange` 中，修改时补单测。

### 4.7 永久失败与 outbox 阻塞

- 重放时只有认证错误（401 / `_decryption_failed`）与可重试错误（网络、5xx、429）会停止整条队列。
- 其余 4xx（典型场景：离线建了截止日期为过去的待办，第二天联网时后端返回 400/422）以及本地 payload 无法解码，只把该条置为 `OutboxState.FAILED`，然后继续处理后续项；否则一条坏数据会永久堵住所有离线写入。
- 错误分类集中在 `data/sync/SyncErrorPolicy.kt`，改动分类时同步更新 `SyncErrorPolicyTest`。
- `FAILED` 项保留在本机，`outboxCounts` 单独计数，`AppRoot` 横幅提示，设置页可看到失败条数；`requeueFailed(owner)` 把它们放回 `PENDING` 之后才会再次发送。
- 同一实体再次保存/编辑会替换该实体上残留的 `FAILED` 行，等于用户显式重试。
- 不要改成“永久失败就删除该条”：删除等于静默丢用户数据。

## 5. 后端契约

Base URL 由用户填写，自动补 `/api/v1/`。主要端点：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `auth/login` | `{username,password}` → `{access_token}` |
| POST | `auth/logout` | 清理服务端 key cache |
| POST | `auth/change-password` | 旧密码错返回 402；成功返回新 token |
| GET | `users/me` | 当前用户 |
| GET | `daily/week/{weekStart}` | 周日报（周一） |
| GET | `daily?start_date=&end_date=` | 日期范围，重放冲突检测用 |
| POST | `daily` | `{date,content}` upsert |
| DELETE | `daily/{date}` | 删除 |
| GET | `tasks` | 全部待办 |
| GET | `tasks/completed?offset=&limit=` | 已完成分页 |
| POST/PUT/DELETE | `tasks` / `tasks/{id}` | 待办 CRUD |
| GET/POST/PUT | `weekly/{weekStart}` | 读取（404=未生成）/生成/编辑 |
| GET/POST/PUT | `monthly/{year}/{month}` | 同上 |

注意：

- 服务端时间戳是无时区的 UTC 字符串，解析时要按 UTC 处理（`Dates.parseServerInstant`）。
- 后端拒绝“过去日期”的任务 deadline；更新任务时如果 deadline 已过期且用户没有改它，应省略该字段，避免 400。
- `kotlinx.serialization` 的 `explicitNulls = false` 会省略 null，任务 deadline 的“清空”必须手工构造 JSON（`ApiClient.updateTask`）。
- 生成接口可能耗时几十秒，读超时为 180s；UI 必须有忙碌态。

## 6. 本地后端验证

优先使用 `weekly-report` 的 Docker Compose。本仓库的 `docker-compose.local.yml`：

```bash
export JWT_SECRET_KEY="$(openssl rand -hex 32)"
docker compose -f docker-compose.local.yml up --build
```

没有 Docker 时，用同源码原生长跑（独立端口/数据目录，禁止写入后端仓库）：

```powershell
uv venv .local-backend/venv --python 3.12
uv pip install --python .local-backend/venv/Scripts/python.exe -r ..\weekly-report\backend\requirements.txt

$env:DATABASE_URL = 'sqlite:///<android-repo>/.local-backend/data/weekly_report.db'
$env:JWT_SECRET_KEY = '<random>'
$env:PYTHONIOENCODING = 'utf-8'
.local-backend/venv/Scripts/python.exe -m uvicorn main:app `
  --app-dir ..\weekly-report\backend --host 0.0.0.0 --port 18081
```

不要修改后端文件；用 `PYTHONDONTWRITEBYTECODE=1` 或 `PYTHONPYCACHEPREFIX` 避免在后端目录生成 `__pycache__`。

## 7. 测试

已有单测覆盖：

- 日期/周首/截止状态/UTC 解析；
- 有序列表续号、重编号、末尾空条目、IME 组合态；
- URL 校验（Release 拒绝 HTTP，Debug 允许）；
- 重放冲突规则；
- API 错误 detail 与 `_decryption_failed` 检测。

新增纯逻辑时优先放 `util/` 或 `data/sync/`，补 JUnit 测试；不要用需要 Android 运行时的类。测试数量变化时更新 README 中的数字。

## 8. 真机/模拟器验收

- 模拟器：`E:\Androiddev\sdk\emulator\emulator.exe -avd Pixel_8_API_35 -no-window -gpu swiftshader_indirect`。
- 真机：无线调试需先 `adb pair IP:PAIR_PORT CODE`，再 `adb connect IP:CONNECT_PORT`（两个端口不同，连接端口在「无线调试」主页面）。
- 验收重点：登录 → 日报保存 → 杀进程重开 → 删除 → 翻周；待办全流程；周报 404/生成/编辑/分享；离线写→联网补发；冲突提示；后端重启后强制重新登录；小部件、通知、分享、生物识别。
- `evidence/` 只保留在本地，**不要提交**：真机截图可能包含个人通知、头像、内网地址等隐私。
- 生物识别需要用户在真机上实际按压指纹；模拟器可用 `adb emu finger touch 1`。

## 9. 常见陷阱

1. `mergeWeekFromServer` 在本地有 pending 时，只有服务器内容未变才更新基线；否则保留旧基线以便冲突检测。
2. 补发过程中用户继续编辑：用 outbox `revision` 避免误删新 payload；任务 CREATE 成功时若 revision 已变，转成 UPDATE。
3. WorkManager 队列用 `APPEND_OR_REPLACE`，避免在 drain 结束时丢掉刚到的请求。
4. 会话锁定（生物识别）时没有 token，不能补发，否则 401 会把用户登出。
5. 修改服务器地址会退出登录；这是预期行为，不要保留旧 token 发到新服务器。
6. 小部件读取的是本地 `daily_cache`，保存/同步后要刷新小部件。
7. 不要把 `evidence/`、`dist/`、签名密钥提交到 Git。

## 10. 完成标准

- Debug/Release 构建成功；
- Debug/Release 单测通过；
- lint 0 errors；
- Release 已签名且只允许 HTTPS；
- 新增/修改的离线、冲突、认证逻辑有测试或明确的真机验证记录；
- 不修改 `weekly-report` 仓库，不提交任何密钥或个人数据。
