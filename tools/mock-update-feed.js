#!/usr/bin/env node
// Local stand-in for the release channel: the update.json manifest plus the APK
// it points at. Used to walk through the in-app update flow on an emulator
// without publishing a tag. Not shipped: tools/ is outside the Android sources.
//
//   node tools/mock-update-feed.js 18082 app/build/outputs/apk/debug/app-debug.apk v9.9.9
//
// Then build with the feed injected:
//   ./gradlew :app:assembleDebug -Papp.updateFeedUrl=http://10.0.2.2:18082/latest/update.json

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const port = Number(process.argv[2] || 18082);
const apkPath = path.resolve(process.argv[3] || 'app/build/outputs/apk/debug/app-debug.apk');
const tag = process.argv[4] || 'v9.9.9';
// A wrong hash here exercises the download's checksum rejection path.
const forceSha256 = process.argv[5] || '';
const version = tag.replace(/^v/, '');
const host = `http://10.0.2.2:${port}`;

if (!fs.existsSync(apkPath)) {
  console.error(`APK not found: ${apkPath}`);
  process.exit(1);
}

const apk = fs.readFileSync(apkPath);
const apkSize = apk.length;
const apkSha256 = forceSha256 || crypto.createHash('sha256').update(apk).digest('hex');

const manifest = {
  versionName: version,
  versionCode: 999,
  tag,
  title: `周记 ${tag}`,
  notes: [
    '这是一个用于本地验证的模拟版本。',
    '',
    '### 新增',
    '- 应用内检查更新、下载与安装。',
    '- 设置页可手动检查更新并查看当前版本。',
    '',
    '### 修复',
    '- 离线队列在弱网下的重试顺序。',
  ].join('\n'),
  pageUrl: 'https://github.com/hmilyld/weekly-report-android/releases',
  apkUrl: `${host}/app-release.apk`,
  apkSize,
  apkSha256,
};

const server = http.createServer((req, res) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.url}`);
  if (req.url.startsWith('/latest/update.json') || req.url === '/latest') {
    const body = JSON.stringify(manifest, null, 2);
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
    // Send in chunks with a small delay so the progress bar is visible and the
    // download path is exercised the way a slow link would exercise it.
    const chunk = 256 * 1024;
    let offset = 0;
    const pump = () => {
      if (offset >= apkSize) {
        res.end();
        return;
      }
      const end = Math.min(offset + chunk, apkSize);
      res.write(apk.subarray(offset, end));
      offset = end;
      setTimeout(pump, 40);
    };
    pump();
    return;
  }
  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('not found');
});

server.listen(port, '0.0.0.0', () => {
  console.log(`mock release channel on ${host}/latest/update.json`);
  console.log(`version ${version}, apk ${apkSize} bytes, sha256 ${apkSha256}`);
});
