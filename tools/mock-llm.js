// Local OpenAI-compatible mock used only to verify the app's 200-path contract.
// It is not an LLM; report quality is explicitly marked as unverified.
const http = require('http')

const server = http.createServer((req, res) => {
  if (req.method !== 'POST') {
    res.writeHead(404)
    res.end('not found')
    return
  }
  let body = ''
  req.on('data', (chunk) => (body += chunk))
  req.on('end', () => {
    setTimeout(() => {
      const content = [
        '### 本周工作',
        '1. 完成周记 Android 客户端的日报与离线队列。',
        '2. 联调待办与周报/月报接口。',
        '### 下周计划',
        '1. 补齐真机演示与边界场景。',
        '2. 继续验证加密会话恢复路径。',
      ].join('\n')
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(
        JSON.stringify({
          id: 'mock-completion',
          object: 'chat.completion',
          created: Math.floor(Date.now() / 1000),
          model: 'mock-demo',
          choices: [
            { index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' },
          ],
          usage: { prompt_tokens: 10, completion_tokens: 20, total_tokens: 30 },
        }),
      )
    }, 2500)
  })
})

server.listen(18082, '0.0.0.0', () => console.log('mock llm on 18082'))
