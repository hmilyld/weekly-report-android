#!/usr/bin/env node
// Minimal stand-in for the weekly-report backend, enough to sign in and reach
// the Settings screen on an emulator. It is not a backend implementation: only
// auth and users/me answer with data, everything else returns an empty result.
//
//   node tools/mock-backend.js 18083
//
// Then in the app: server address http://10.0.2.2:18083, any user/password.

const http = require('http');

const port = Number(process.argv[2] || 18083);
const token = 'mock-token';

const user = {
  id: 1,
  username: 'demo',
  role: 'admin',
  created_at: '2026-01-01T00:00:00Z',
};

function json(res, code, body) {
  const payload = JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  const path = url.pathname.replace(/^\/api\/v1/, '');
  console.log(`${new Date().toISOString()} ${req.method} ${path}`);

  if (path === '/health') return json(res, 200, { status: 'ok' });
  if (path === '/auth/login' && req.method === 'POST') {
    return json(res, 200, { access_token: token });
  }
  if (path === '/auth/logout') return json(res, 200, {});
  if (path === '/users/me') return json(res, 200, user);
  if (path.startsWith('/daily')) {
    return json(res, 200, { week_start: url.searchParams.get('week_start') || '', days: [] });
  }
  if (path.startsWith('/tasks')) return json(res, 200, { tasks: [], total: 0 });
  if (path.startsWith('/weekly') || path.startsWith('/monthly')) {
    return json(res, 404, { detail: '未生成' });
  }
  return json(res, 404, { detail: 'not found' });
});

server.listen(port, '0.0.0.0', () => {
  console.log(`mock backend on http://10.0.2.2:${port}/api/v1`);
});
