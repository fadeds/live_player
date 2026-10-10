# Live Player

A fullscreen live-stream player for Android TV and phones. It loads the desktop version of a live site in
a system WebView while masquerading as a desktop browser, then pins the web player fullscreen.
Supports remote-control channel switching, voice search, and phone touch interaction.

## Features

| Feature | Implementation |
| --- | --- |
| Fullscreen WebView | `MainActivity` + `layout/activity_main.xml`, no address bar or browser UI |
| Desktop UA spoofing | `UaHelper` builds a desktop UA; Chrome major version comes from the real device kernel via `WebViewKernel` |
| Player-only view | Injects `assets/player_fix.js`: finds the player by size/position and pins it fullscreen; hides popups/sidebars/banners by geometry, **not by class names** |
| Channel list | `assets/channels.json`, easy to edit |
| Remote control | OK opens the left channel drawer; ▲▼ switch/move; BACK closes; search key does voice search |
| Phone touch | ≡ button (bottom-right) opens/closes the drawer; tap a channel to switch; BACK closes |
| Now playing | script scrapes current program from the page sidebar, reported back via `onChannels/onProgram` |
| Switch without reload | page stays alive; `PlayerFix.playChannel()` clicks the in-page channel item to reload the player in place |
| Switch overlay | fullscreen black overlay showing "channel number + name", removed as soon as video appears |
| 20s watchdog fallback | no video → automatically loads configured `fallbackUrl()` |
| Kernel check | shows a dialog on startup if WebView major version < 90 |

## Voice Search

- Triggered by remote **search / voice / MIC** keys (or long-press ASSIST on some devices)
- Examples: `湖南卫视`, `芒果`, `1套`, `新闻`, `体育`, `13套`, `上海`（→东方卫视）
- Matching logic lives in `VoiceSearch.java`: full name → number → alias → province. Unmatched input prompts a retry.

## Structure

```
app/src/main/
├─ assets/channels.json         # channel data (editable)
├─ assets/player_fix.js         # injected script (pin player / hide overlays / scrape / switch)
├─ java/com/hamibot/cctvtv/
│  ├─ MainActivity.java         # UI / remote keys / switching / overlay / watchdog
│  ├─ Channel.java / ChannelStore.java
│  ├─ UaHelper.java             # desktop UA
│  ├─ WebViewKernel.java        # WebView kernel version
│  ├─ JsBridge.java             # JS↔Android bridge
│  └─ VoiceSearch.java          # voice → channel routing
└─ res/                         # layouts / theme / icons
```

## Local Build

1. Install **Android Studio**, open the project, wait for Gradle sync.
2. Generate a signing key:
   ```
   keytool -genkeypair -v -keystore release.keystore -alias live -keyalg RSA -keysize 2048 -validity 36500
   ```
   Put `release.keystore` under `app/`. Default password `cctv123456`, overridable via env vars
   (`CCTV_KEYSTORE`, `CCTV_KSPASS`, `CCTV_KEYALIAS`, `CCTV_KEYPASS`).
3. Build:
   ```
   gradlew assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk`.

## GitHub Actions

Everything runs automatically after pushing to GitHub:

- **Push `master`**: builds the APK, uploaded to the run's **Artifacts** (`LivePlayer-v<version>.apk`)
- **Push a tag** (e.g. `git tag v0.0.5`): creates a **GitHub Release** with the APK attached
- **Versioning**: versionName comes from the tag (`v0.0.5` → `0.0.5`); versionCode uses the CI run number
- **Signing**: for a stable signing key add repository Secrets:
  - `KEYSTORE_BASE64` — base64 of `release.keystore`
  - `KEYSTORE_PASSWORD` — password (e.g. `cctv123456`)
  - `KEY_ALIAS` — alias (e.g. `live`)
  - If not set, CI generates a throwaway key (old installs cannot be updated)

## Install

- **ADB**:
  ```
  adb connect <device-ip>:5555
  adb install -r app-release.apk
  ```
- **Phone / USB**: install the APK directly, allow "unknown sources" first.

## Device Tuning

- Player/channel matching rules live at the top of `player_fix.js`. If a channel won't play, adjust the size
  thresholds in `findPlayer()` or the text rules in `findChannelEntries()`.
- Channel `pid` is left empty; switching works by in-page clicks. If the page structure changes, extend
  `findChannelEntries`.

> For personal study only. Content copyright belongs to the original site and its partners.