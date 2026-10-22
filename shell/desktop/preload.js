// 只暴露只读的应用信息，网页无法调用 Node / Electron 能力。
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('ZhidianDesktop', {
  info: () => ipcRenderer.sendSync('zd:info'),
  reload: () => ipcRenderer.send('zd:reload'),
  home: () => ipcRenderer.send('zd:home'),
});
