#!/usr/bin/env node
// Writes the update.json manifest that the release workflow attaches to a
// GitHub release. Kept as a script (not inline YAML) so it can be run and
// checked locally with the same arguments the workflow passes.
//
//   node tools/write-update-manifest.js \
//     --out update.json \
//     --version 1.1.0 --code 2 --tag v1.1.0 \
//     --apk app/build/outputs/apk/release/app-release.apk \
//     --notes release-notes.md \
//     --repo hmilyld/weekly-report-android

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (!key.startsWith('--')) throw new Error(`unexpected argument: ${key}`);
    out[key.slice(2)] = argv[i + 1];
    i += 1;
  }
  return out;
}

const args = parseArgs(process.argv.slice(2));
const required = ['out', 'version', 'code', 'tag', 'apk', 'notes', 'repo'];
for (const key of required) {
  if (!args[key]) {
    console.error(`missing --${key}`);
    process.exit(1);
  }
}

const apkPath = path.resolve(args.apk);
if (!fs.existsSync(apkPath)) {
  console.error(`APK not found: ${apkPath}`);
  process.exit(1);
}
if (!fs.existsSync(args.notes)) {
  console.error(`release notes not found: ${args.notes}`);
  process.exit(1);
}

const versionCode = Number(args.code);
if (!Number.isInteger(versionCode) || versionCode <= 0) {
  console.error(`invalid --code: ${args.code}`);
  process.exit(1);
}

const apk = fs.readFileSync(apkPath);
const apkName = path.basename(apkPath);
const tag = args.tag;
const manifest = {
  versionName: args.version,
  versionCode,
  tag,
  title: `周记 ${tag}`,
  notes: fs.readFileSync(args.notes, 'utf8'),
  pageUrl: `https://github.com/${args.repo}/releases/tag/${tag}`,
  // Pinned to this tag on purpose: accepting 1.1.0 must download 1.1.0 even if
  // a newer release appears before the user taps 下载.
  apkUrl: `https://github.com/${args.repo}/releases/download/${tag}/${apkName}`,
  apkSize: apk.length,
  apkSha256: crypto.createHash('sha256').update(apk).digest('hex'),
};

fs.writeFileSync(args.out, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`wrote ${args.out}`);
console.log(JSON.stringify({ ...manifest, notes: `<${manifest.notes.length} chars>` }, null, 2));
