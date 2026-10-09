# 央视频电视直播（Android TV）

用系统 WebView 全屏加载央视频电视直播页，伪装成电脑浏览器避免"下载 App"弹窗，
支持遥控器换台 + 语音搜索频道，把网页播放器钉成整屏。

## 功能对照

| 需求 | 实现 |
| --- | --- |
| 全屏 WebView | `MainActivity` + `layout/activity_main.xml`，无地址栏无浏览器控件 |
| 伪装电脑浏览器 | `UaHelper` 生成桌面 UA，Chrome 大版本号来自 `WebViewKernel` 实测设备内核，不写死 |
| 只留播放画面 | 注入 `assets/player_fix.js`：按"大小/位置"识别并固定播放器为全屏，几何规则隐藏小弹窗/右栏/横幅，**不依赖任何 class 名** |
| 48 个频道 | `assets/channels.json`：CCTV-1~17 + 31 个省级卫视频道 |
| 遥控器 | OK 键滑出左侧频道列表；▲▼ 换台/移动；BACK 收起列表；搜索键语音换台 |
| 正在播出节目 | JS 从页面侧栏按频道名抓取节目，`onChannels/onProgram` 回传刷新列表 |
| 换台不整页刷新 | 页面常驻，`CctvFix.playChannel()` 直接程序化点击页面内频道项让播放器原位重载 |
| 切换黑遮罩 | `overlay` 全屏黑底显示"频道号+频道名"，画面一出来立即撤掉 |
| 20 秒超时备用源 | 无画面 → 自动切 `tv.cctv.com/live/<key>/index.shtml`（CCTV 各台） |
| 内核版本检查 | 启动若 WebView 主版本 < 90，弹窗提示升级 Android System WebView |

## 语音搜索

- 遥控器 **搜索键 / 语音键 / MIC 键**（或长按部分机型的 ASSIST）触发系统语音识别
- 支持说法示例：`湖南卫视`、`芒果`、`cctv1`、`央视一套`、`中央新闻`、`体育`、`13套`、`上海`（→东方卫视）
- 匹配在 `VoiceSearch.java`，按"全名 → CCTV数字 → 别称 → 省份"顺序，识别不了会提示再试

## 目录结构

```
cctv-tv/
├─ app/src/main/
│  ├─ assets/channels.json        # 48 频道数据（可改）
│  ├─ assets/player_fix.js        # 页面注入脚本（播放器钉全屏/清浮层/抓节目/换台）
│  ├─ java/com/hamibot/cctvtv/
│  │  ├─ MainActivity.java        # 主界面/遥控器/换台/遮罩/超时
│  │  ├─ Channel.java / ChannelStore.java
│  │  ├─ UaHelper.java            # 桌面 UA（真实内核版本号）
│  │  ├─ WebViewKernel.java       # 获取 WebView 内核版本
│  │  ├─ JsBridge.java            # JS↔Android 桥
│  │  └─ VoiceSearch.java         # 语音频道路由
│  └─ res/                        # 布局/主题/图标
└─ build.gradle / settings.gradle
```

## 打包与签名（在任意电脑上用 Android Studio）

1. 安装 **Android Studio**（自带 JDK 和 SDK），Git 拉取或拷贝本项目后用 Studio 打开，等待 Gradle 同步。
2. 生成签名文件（命令行，路径随意）：
   ```
   keytool -genkeypair -v -keystore release.keystore -alias cctv -keyalg RSA -keysize 2048 -validity 36500
   ```
   把 `release.keystore` 放到 `app/` 目录（即本文件所在工程根目录）。默认口令 `cctv123456`，也可用环境变量覆盖（`CCTV_KEYSTORE`、`CCTV_KSPASS`、`CCTV_KEYALIAS`、`CCTV_KEYPASS`）。
3. 命令行打包（在工程根目录）：
   ```
   gradlew assembleRelease
   ```
   或 Android Studio：`Build → Generate Signed Bundle / APK → APK`，选择 `app/release.keystore`。
   产物在 `app/build/outputs/apk/release/app-release.apk`。

## 安装到电视

- **ADB**（电视开启"开发者选项→USB/网络调试"）：
  ```
  adb connect <电视IP>:5555
  adb install -r app\build\outputs\apk\release\app-release.apk
  ```
- **U 盘**：把 `app-release.apk` 拷入 U 盘，插电视用文件管理器安装（先允许"未知来源"）。
- 部分电视需先用助手工具（如甲壳虫/当贝）投装。

## 需要实机微调的点

- `player_fix.js` 的频道匹配、播放器识别规则写在文件顶部，若某台播不出画面，一般只需调整 `findPlayer()` 的尺寸阈值与 `findChannelEntries()` 的文本规则。
- 卫视频道的备用源当前走"回源央视频重试"，如央视网有对应直播页可自行在 `Channel.fallbackUrl()` 补充。
- 各频道 `pid` 字段留空，换台走页面内点击，无需人工填写；如页面结构变化导致识别失效，可在 `findChannelEntries` 中补充。

> 仅供个人学习使用，内容版权归中央广播电视总台及其合作方所有。