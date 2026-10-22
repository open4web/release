// 构建时读取 app-config.json（由 workflow 从 apps/<key>/app.json 复制而来）。
const fs = require('fs');
const path = require('path');

const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'app-config.json'), 'utf8'));
const version = process.env.APP_VERSION || '0.0.1';

module.exports = {
  appId: cfg.windowsAppId,
  productName: cfg.name,
  executableName: cfg.slug,
  copyright: 'Copyright © open4web',
  extraMetadata: { name: cfg.slug, version, description: cfg.description },
  directories: { output: 'dist', buildResources: 'build' },
  files: ['main.js', 'preload.js', 'error.html', 'app-config.json', 'package.json'],
  asar: true,
  win: {
    icon: 'build/icon.ico',
    target: [
      { target: 'nsis', arch: ['x64'] },
      { target: 'portable', arch: ['x64'] },
    ],
    // 未做代码签名：首次运行 Windows SmartScreen 会提示“未知发布者”。
    signAndEditExecutable: true,
  },
  nsis: {
    oneClick: false,
    perMachine: false,
    allowToChangeInstallationDirectory: true,
    createDesktopShortcut: 'always',
    createStartMenuShortcut: true,
    shortcutName: cfg.name,
    artifactName: `${cfg.slug}-windows-setup.\${ext}`,
    installerIcon: 'build/icon.ico',
    uninstallerIcon: 'build/icon.ico',
    deleteAppDataOnUninstall: false,
  },
  portable: {
    artifactName: `${cfg.slug}-windows-portable.\${ext}`,
  },
};
