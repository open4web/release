// 智点 Windows 浏览器外壳：只允许访问 app-config.json 里 allowedHosts 内的域名。
const { app, BrowserWindow, Menu, dialog, ipcMain, session } = require('electron');
const path = require('path');
const fs = require('fs');

const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'app-config.json'), 'utf8'));
const START_URL = cfg.url;
const ALLOWED = new Set((cfg.allowedHosts || []).map((h) => h.toLowerCase()));
const ERROR_PAGE = path.join(__dirname, 'error.html');

app.setAppUserModelId(cfg.windowsAppId);

let win = null;
let lastGoodUrl = START_URL;

function isAllowed(raw) {
  try {
    const u = new URL(raw);
    if (u.protocol === 'about:' || u.protocol === 'blob:') return true;
    return u.protocol === 'https:' && ALLOWED.has(u.hostname.toLowerCase());
  } catch {
    return false;
  }
}

function isErrorPage(raw) {
  return raw.startsWith('file:') && raw.includes('error.html');
}

function blocked(url) {
  if (!win) return;
  dialog.showMessageBox(win, {
    type: 'info',
    title: cfg.name,
    message: '已拦截外部链接',
    detail: `此应用只能访问 ${[...ALLOWED].join('、')}。\n\n被拦截的地址：${url}`,
    buttons: ['知道了'],
  });
}

function showError(msg) {
  if (!win) return;
  win.loadFile(ERROR_PAGE, { query: { msg } });
}

function goHome() {
  if (win) win.loadURL(START_URL);
}

function reload() {
  if (!win) return;
  const cur = win.webContents.getURL();
  if (!cur || isErrorPage(cur)) win.loadURL(lastGoodUrl || START_URL);
  else win.webContents.reloadIgnoringCache();
}

function buildMenu() {
  // 菜单栏默认隐藏，按 Alt 显示；快捷键始终可用。
  const zoom = (d) => () => {
    if (!win) return;
    const wc = win.webContents;
    wc.setZoomLevel(d === 0 ? 0 : wc.getZoomLevel() + d);
  };
  return Menu.buildFromTemplate([
    {
      label: '页面',
      submenu: [
        { label: '首页', accelerator: 'Alt+Home', click: goHome },
        { label: '后退', accelerator: 'Alt+Left', click: () => win && win.webContents.navigationHistory.canGoBack() && win.webContents.navigationHistory.goBack() },
        { label: '刷新', accelerator: 'F5', click: reload },
        { label: '强制刷新', accelerator: 'CmdOrCtrl+R', click: reload, visible: false },
        { type: 'separator' },
        { label: '全屏 / 退出全屏', accelerator: 'F11', click: () => win && win.setFullScreen(!win.isFullScreen()) },
        { label: '放大', accelerator: 'CmdOrCtrl+=', click: zoom(0.5) },
        { label: '缩小', accelerator: 'CmdOrCtrl+-', click: zoom(-0.5) },
        { label: '实际大小', accelerator: 'CmdOrCtrl+0', click: zoom(0) },
        { label: '打印', accelerator: 'CmdOrCtrl+P', click: () => win && win.webContents.print() },
        { type: 'separator' },
        { label: '退出', role: 'quit' },
      ],
    },
    {
      label: '帮助',
      submenu: [
        { label: '开发者工具', accelerator: 'CmdOrCtrl+Shift+I', click: () => win && win.webContents.toggleDevTools() },
        {
          label: '关于',
          click: () =>
            dialog.showMessageBox(win, {
              title: cfg.name,
              message: `${cfg.name} ${app.getVersion()}`,
              detail: `${cfg.description}\n\n访问地址：${START_URL}`,
            }),
        },
      ],
    },
  ]);
}

function hardenContents(contents) {
  contents.on('will-navigate', (e, url) => {
    if (!isAllowed(url) && !isErrorPage(url)) {
      e.preventDefault();
      blocked(url);
    }
  });
  contents.on('will-redirect', (e, url) => {
    if (!isAllowed(url)) {
      e.preventDefault();
      blocked(url);
    }
  });
  contents.on('will-attach-webview', (e) => e.preventDefault());
  contents.setWindowOpenHandler(({ url }) => {
    // window.open('') + document.write 用于打印小票：允许在子窗口打开空白页。
    if (!url || url === 'about:blank') {
      return {
        action: 'allow',
        overrideBrowserWindowOptions: {
          parent: win || undefined,
          width: 520,
          height: 720,
          autoHideMenuBar: true,
          title: cfg.name,
          webPreferences: { contextIsolation: true, sandbox: true, nodeIntegration: false },
        },
      };
    }
    if (isAllowed(url)) {
      win && win.loadURL(url);
    } else {
      blocked(url);
    }
    return { action: 'deny' };
  });
}

function createWindow() {
  win = new BrowserWindow({
    width: 1366,
    height: 860,
    minWidth: 960,
    minHeight: 600,
    title: cfg.name,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: '#ffffff',
    icon: path.join(__dirname, 'build', 'icon.ico'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
      webviewTag: false,
      spellcheck: false,
    },
  });
  win.once('ready-to-show', () => {
    win.maximize();
    win.show();
  });
  win.on('page-title-updated', (e) => {
    e.preventDefault();
    win.setTitle(cfg.name);
  });

  const wc = win.webContents;
  wc.setUserAgent(`${wc.getUserAgent()} ZhidianShell/${app.getVersion()} (${cfg.key}; windows)`);
  wc.on('did-navigate', (_e, url) => {
    if (isAllowed(url)) lastGoodUrl = url;
  });
  wc.on('did-navigate-in-page', (_e, url) => {
    if (isAllowed(url)) lastGoodUrl = url;
  });
  wc.on('did-fail-load', (_e, code, desc, url, isMainFrame) => {
    if (!isMainFrame || code === -3 /* ABORTED */ || isErrorPage(url)) return;
    showError(code === -106 ? '网络未连接，请检查网络后重试。' : `页面加载失败（${desc}），请稍后重试。`);
  });
  wc.on('render-process-gone', () => showError('页面异常退出，请重新加载。'));

  win.loadURL(START_URL);
  win.on('closed', () => {
    win = null;
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      if (win.isMinimized()) win.restore();
      win.focus();
    }
  });

  app.on('web-contents-created', (_e, contents) => hardenContents(contents));

  ipcMain.on('zd:info', (e) => {
    e.returnValue = { app: cfg.key, name: cfg.name, version: app.getVersion(), platform: 'windows', url: START_URL };
  });
  ipcMain.on('zd:reload', reload);
  ipcMain.on('zd:home', goHome);

  app.whenReady().then(() => {
    const allowPerm = new Set(['media', 'notifications', 'clipboard-sanitized-write', 'fullscreen']);
    session.defaultSession.setPermissionRequestHandler((wc, permission, cb, details) => {
      const origin = details.requestingUrl || wc.getURL();
      cb(isAllowed(origin) && allowPerm.has(permission));
    });
    session.defaultSession.setPermissionCheckHandler((_wc, permission, origin) =>
      isAllowed(origin) && allowPerm.has(permission));
    session.defaultSession.on('will-download', (e) => {
      e.preventDefault();
    });
    Menu.setApplicationMenu(buildMenu());
    createWindow();
  });

  app.on('window-all-closed', () => app.quit());
}

// 不使用 shell.openExternal：外部链接一律拦截，避免切换到错误的页面。
