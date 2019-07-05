const packager = require('electron-packager')

const options = {
  "dir": ".",
  "name": "panaeolus",
  "out": "dist/",
  "icon": "resources/public/icons/panaeolus.ico",
  "overwrite": true,
  "ignore": [
   ".shadow-cljs",
   "node_modules/higlight.js",
   "resources/public/js/cljs-runtime",
   "windows_installer.js",
   /\.pfx/g,
   "scripts",
   "src",
   "yarn.lock",
   "shell.nix"
  ]
};

async function bundleElectronApp(options) {
  const appPaths = await packager(options)
  console.log(`Electron app bundles created:\n${appPaths.join("\n")}`)
}

bundleElectronApp(options);