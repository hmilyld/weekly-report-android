#!/usr/bin/env node
// Local stand-in for GitHub's "latest release" endpoint plus an APK download.
// Used to walk through the in-app update flow on an emulator without publishing
// a tag. Not shipped: tools/ is outside the Android source sets.
//
//   node tools/mock-update-feed.js 18082 app/build/outputs/apk/debug/app-debug.apk
//
// Then build with the feed injected:
//   ./gradlew :app:assembleDebug -Papp.updateFeedUrl=http://10.0.2.2:18082/latest

const http = require('http');
const fs = require('fs');
const path = require('path');

const port = Number(process.argv[2] || 18082);
const apkPath = path.resolve(process.argv[3] || 'app/build/outputs/apk/debug/app-debug.apk');
const tag = process.argv[4] || 'v9.9.9';
const version = tag.replace(/^v/, '');
const host = `http://10.0.2.2:${port}`;

if (!fs.existsSync(apkPath)) {
  console.error(`APK not found: ${apkPath}`);
  process.exit(1);
}
const apkSize = fs.statSync(apkPath).size;

const release = {
  tag_name: tag,
  name: `周记 ${tag}`,
  html_url: 'https://github.com/hmilyld/weekly-report-android/releases',
  body: [
    '这是一个用于本地验证的模拟版本。',
    '',
    '### 新增',
    '- 应用内检查更新、下载与安装。',
    '- 设置页可手动检查更新并查看当前版本。',
    '',
    '### 修复',
    '- 离线队列在弱网下的重试顺序。',
  ].join('\n'),
  assets: [
    {
      name: 'app-release.apk',
      size: apkSize,
      browser_download_url: `${host}/app-release.apk`,
    },
  ],
};

const server = http.createServer((req, res) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.url}`);
  if (req.url.startsWith('/latest')) {
    const body = JSON.stringify(release);
    res.writeHead(200, {
      'Content-Type': 'application/json; charset=utf-8',
      'Content-Length': Buffer.byteLength(body),
    });
    res.end(body);
    return;
  }
  if (req.url.startsWith('/app-release.apk')) {
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': apkSize,
    });
    // Trickle the file out so the progress bar is visible for a moment.
    const stream = fs.createReadStream(apkPath, { highWaterMark: 64 * 1024 });
    stream.pipe(res);
    return;
  }
  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('not found');
});

server.listen(port, '0.0.0.0', () => {
  console.log(`mock update feed on ${host} (version ${version}, apk ${apkSize} bytes)`);
});
