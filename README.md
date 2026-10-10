# Live Player

Android TV / 手机通用的全屏直播播放器：用系统 WebView 全屏加载直播网站桌面版页面，伪装成电脑浏览器，
把网页播放器钉成整屏。支持遥控器换台、语音搜索频道和手机触屏操作。

## 功能对照

| 需求 | 实现 |
| --- | --- |
| 全屏 WebView | `MainActivity` + `layout/activity_main.xml`，无地址栏无浏览器控件 |
| 伪装电脑浏览器 | `UaHelper` 生成桌面 UA，Chrome 大版本号来自 `WebViewKernel` 实测设备内核，不写死 |
| 只留播放画面 | 注入 `assets/player_fix.js`：按"大小/位置"识别并固定播放器为全屏，几何规则隐藏小弹窗/右栏/横幅，**不依赖任何 class 名** |
| 频道列表 | `assets/channels.json` 可随时增删改 |
| 遥控器 | OK 键滑出左侧频道列表；▲▼ 换台/移动；BACK 收起列表；搜索键语音换台 |
| 手机触屏 | 右下角 ≡ 按钮打开/收起列表，点频道直接换台，返回键收起 |
| 正在播出节目 | JS 从页面侧栏抓取节目，`onChannels/onProgram` 回传刷新列表 |
| 换台不整页刷新 | 页面常驻，`PlayerFix.playChannel()` 程序化点击页面内频道项让播放器原位重载 |
| 切换黑遮罩 | 全屏黑底显示"频道号+频道名"，画面一出来立即撤掉 |
| 20 秒超时备用源 | 无画面 → 自动切换备用源（按频道配置的 `fallbackUrl()`） |
| 内核版本检查 | 启动若 WebView 主版本 < 90，弹窗提示升级 Android System WebView |

## 语音搜索

- 遥控器**搜索键 / 语音键 / MIC 键**（或长按部分机型的 ASSIST）触发系统语音识别
- 支持说法示例：`湖南卫视`、`芒果`、`1套`、`新闻`、`体育`、`13套`、`上海`（→东方卫视）
- 匹配在 `VoiceSearch.java`，按"全名 → 数字 → 别称 → 省份"顺序，识别不了会提示再试

## 目录结构

```
app/src/main/
├─ assets/channels.json        # 频道数据（可改）
├─ assets/player_fix.js        # 页面注入脚本（播放器钉全屏/清浮层/抓节目/换台）
├─ java/com/hamibot/cctvtv/
│  ├─ MainActivity.java        # 主界面/遥控器/换台/遮罩/超时
│  ├─ Channel.java / ChannelStore.java
│  ├─ UaHelper.java            # 桌面 UA（真实内核版本号）
│  ├─ WebViewKernel.java       # 获取 WebView 内核版本
│  ├─ JsBridge.java            # JS↔Android 桥
│  └─ VoiceSearch.java         # 语音频道路由
└─ res/                        # 布局/主题/图标
```

## 本地打包

1. 安装 **Android Studio**（自带 JDK 和 SDK），Git 拉取本项目后用 Studio 打开，等待 Gradle 同步。
2. 生成签名文件（命令路径随意）：
   ```
   keytool -genkeypair -v -keystore release.keystore -alias live -keyalg RSA -keysize 2048 -validity 36500
   ```
   把 `release.keystore` 放到 `app/` 目录。默认口令 `cctv123456`，可用环境变量覆盖（`CCTV_KEYSTORE`、`CCTV_KSPASS`、`CCTV_KEYALIAS`、`CCTV_KEYPASS`）。
3. 打包：
   ```
   gradlew assembleRelease
   ```
   产物在 `app/build/outputs/apk/release/app-release.apk`。

## GitHub Actions 自动打包

推送到 GitHub 后全自动：

- **推送 `master`**：自动构建，APK 在本次运行的 **Artifacts** 里（`LivePlayer-v<版本>.apk`）
- **打标签发版**（如 `git tag v0.0.5`）：自动生成 **GitHub Release**，APK 直接可下载
- **版本号联动**：versionName 取标签（去掉前缀 `v`），versionCode 用 CI 构建序号保证递增
- **签名**：如需固定签名，在仓库 Settings → Secrets 添加
  - `KEYSTORE_BASE64`：签名文件 `release.keystore` 的 base64 内容
  - `KEYSTORE_PASSWORD`：口令（如 `cctv123456`）
  - `KEY_ALIAS`：别名（如 `live`）
  - 不设置则 CI 每次生成临时密钥（无法覆盖安装旧版本）

## 安装

- **ADB**（电视开启开发者调试）：
  ```
  adb connect <设备IP>:5555
  adb install -r app-release.apk
  ```
- **手机/U 盘**：直接把 APK 拷入安装，先允许"未知来源"。

## 需要实机微调的点

- `player_fix.js` 的频道匹配、播放器识别规则写在文件顶部，若某台播不出画面，一般只需调整 `findPlayer()`
  的尺寸阈值与 `findChannelEntries()` 的文本规则。
- 频道 `pid` 字段留空，换台走页面内点击；如页面结构变化导致识别失效，可在 `findChannelEntries` 中补充。

> 仅供个人学习使用，内容版权归原站点及其合作方所有。