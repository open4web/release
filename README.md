# open4web/release

智点门店应用（浏览器外壳）的构建与发布仓库。应用只是一个「固定网址的浏览器」，网页功能随线上版本自动更新，
只有外壳本身（图标、名称、拦截规则等）变化时才需要发新版本。

| 应用 | 来源 | 固定打开 | Android 包名 |
| --- | --- | --- | --- |
| 智点收银（x6） | [open4web/x6](https://github.com/open4web/x6) | https://menu.huangliji.tech/#/ | `tech.huangliji.menu` |
| 智点出餐（x5） | open4web/x5 | https://pad.huangliji.tech/ | `tech.huangliji.pad` |

## 下载（固定地址，始终是最新版）

| 应用 | Android Pad | Windows 安装版 | Windows 免安装版 |
| --- | --- | --- | --- |
| 智点收银 | [zhidian-pos-android.apk](https://github.com/open4web/release/releases/download/x6-latest/zhidian-pos-android.apk) | [zhidian-pos-windows-setup.exe](https://github.com/open4web/release/releases/download/x6-latest/zhidian-pos-windows-setup.exe) | [zhidian-pos-windows-portable.exe](https://github.com/open4web/release/releases/download/x6-latest/zhidian-pos-windows-portable.exe) |
| 智点出餐 | [zhidian-kitchen-android.apk](https://github.com/open4web/release/releases/download/x5-latest/zhidian-kitchen-android.apk) | [zhidian-kitchen-windows-setup.exe](https://github.com/open4web/release/releases/download/x5-latest/zhidian-kitchen-windows-setup.exe) | [zhidian-kitchen-windows-portable.exe](https://github.com/open4web/release/releases/download/x5-latest/zhidian-kitchen-windows-portable.exe) |

每个版本另有带版本号的 Release（`x6-v1.0.0`、`x5-v1.0.0` …）。机器可读的版本清单在
[`apps/latest.json`](apps/latest.json)，官网 x8 的下载页在运行时读取它。

## 目录

```
apps/<key>/app.json        应用配置：名称、网址、允许的域名、包名、主题色
apps/<key>/icon-source.png （可选）正式图标源图，1024x1024；放入后运行 scripts/gen-icons.py
apps/<key>/icon.png|.ico   生成的图标；android-res/ 为 Android 启动图标
apps/latest.json           发布后由 workflow 自动更新
shell/apk/                 Android 外壳（原生 WebView，无第三方依赖）
shell/desktop/             Windows 外壳（Electron + electron-builder，NSIS 安装版 + 免安装版）
scripts/                   plan.py（版本计算）、update-manifest.py、gen-icons.py
.github/workflows/build-apps.yml
```

## 发布新版本

- 手动：Actions → **Build x5 / x6 apps** → Run workflow，选择应用，版本号留空则自动 +1。
- tag：`git tag x6-v1.0.1 && git push origin x6-v1.0.1`（x5 同理）。
- 从 x5 / x6 仓库：运行该仓库的 **App shell release** workflow（需要在 x5/x6 配置 `RELEASE_TOKEN`，见下）。

## 外壳行为

- 全屏（Android 沉浸式、横屏自适应；Windows 最大化，F11 全屏），屏幕常亮。
- 只允许访问 `allowedHosts` 中的域名，其他链接一律拦截并提示，不会跳到外部浏览器。
- 启用 JavaScript、本地存储、Cookie；提示音自动播放；摄像头（扫码）仅对允许的域名授权。
- `window.print()` / 打印小票弹窗：Android 走系统打印服务，Windows 走系统打印对话框。
- 断网或加载失败显示「重新加载 / 返回首页」。Android 返回键先后退，连按两次退出。
- 网页可通过 `navigator.userAgent` 中的 `ZhidianShell/<版本> (<app>; android|windows)` 识别外壳。

## 签名与密钥

- Android 使用专用发布证书签名（secrets：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、
  `ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`）。**证书一旦丢失，已安装的用户将无法覆盖升级**，请妥善备份。
- Windows 版本未做代码签名，首次运行 SmartScreen 会提示「未知发布者」，点「更多信息 → 仍要运行」。

## 本地构建

```bash
# Android（需要 JDK 17+、Android SDK、Gradle 8.11）
cd shell/apk && gradle assembleRelease -PappKey=x6 -PversionName=1.0.0

# Windows（在 Windows 上）
cd shell/desktop && cp ../../apps/x6/app.json app-config.json && mkdir -p build && cp ../../apps/x6/icon.ico build/
npm ci && APP_VERSION=1.0.0 npm run dist:win
```

旧的 `main.js`、`android-app/` 与 `android.yml` / `windows.yml` / `mac.yml` 是早期试验，已停用。
