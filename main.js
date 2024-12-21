const { app, BrowserWindow } = require('electron');

let mainWindow;

app.on('ready', () => {
    // 或者
    mainWindow = new BrowserWindow({
        kiosk: true
        // icon: 'assets/favicon.ico'
    });

    // Load the web URL
    mainWindow.loadURL('https://menu.huangliji.tech/');
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});