const { app, BrowserWindow } = require('electron');

let mainWindow;

const { app, BrowserWindow } = require('electron');

let mainWindow;

app.on('ready', () => {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        kiosk: true,
        autoHideMenuBar: true,
        webPreferences: {
            nodeIntegration: false,
        }
    });

    mainWindow.loadURL('https://menu.huangliji.tech/');
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});