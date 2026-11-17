// Minimal local mail catcher so the stack runs without Docker or Mailpit.
// SMTP on 1025, and a tiny Mailpit style HTTP API on 8025:
//   GET /api/v1/messages        list of captured messages
//   DELETE /api/v1/messages     clear
import net from 'node:net';
import http from 'node:http';

const messages = [];

const smtp = net.createServer((socket) => {
  let buf = '';
  let data = false;
  let from = '';
  let to = [];
  let body = '';
  const send = (line) => socket.write(line + '\r\n');
  send('220 mail-sink ready');
  socket.on('data', (chunk) => {
    buf += chunk.toString('utf8');
    let idx;
    while ((idx = buf.indexOf('\r\n')) !== -1) {
      const line = buf.slice(0, idx);
      buf = buf.slice(idx + 2);
      if (data) {
        if (line === '.') {
          data = false;
          const raw = body;
          const subject = /^Subject: (.*)$/im.exec(raw)?.[1] ?? '';
          messages.unshift({ ID: String(Date.now()) + Math.random().toString(16).slice(2, 6), From: { Address: from }, To: to.map((Address) => ({ Address })), Subject: decodeURIComponent(escape(subject)), Created: new Date().toISOString(), Raw: raw });
          body = '';
          send('250 queued');
        } else {
          body += (line.startsWith('..') ? line.slice(1) : line) + '\r\n';
        }
        continue;
      }
      const cmd = line.toUpperCase();
      if (cmd.startsWith('EHLO') || cmd.startsWith('HELO')) send('250 mail-sink');
      else if (cmd.startsWith('MAIL FROM')) {
        from = /<([^>]*)>/.exec(line)?.[1] ?? '';
        to = [];
        send('250 ok');
      } else if (cmd.startsWith('RCPT TO')) {
        to.push(/<([^>]*)>/.exec(line)?.[1] ?? '');
        send('250 ok');
      } else if (cmd === 'DATA') {
        data = true;
        send('354 end with .');
      } else if (cmd === 'QUIT') {
        send('221 bye');
        socket.end();
      } else send('250 ok');
    }
  });
  socket.on('error', () => {});
});

http
  .createServer((req, res) => {
    res.setHeader('Content-Type', 'application/json');
    if (req.url?.startsWith('/api/v1/messages') && req.method === 'DELETE') {
      messages.length = 0;
      res.end('{}');
    } else if (req.url?.startsWith('/api/v1/messages')) {
      res.end(JSON.stringify({ total: messages.length, messages: messages.map(({ Raw, ...m }) => m) }));
    } else {
      res.setHeader('Content-Type', 'text/html');
      res.end(`<h1>Mail sink</h1><ul>${messages.map((m) => `<li>${m.To.map((t) => t.Address).join(', ')}: ${m.Subject}</li>`).join('')}</ul>`);
    }
  })
  .listen(8025, () => console.log('mail sink: SMTP 1025, HTTP 8025'));
smtp.listen(1025);
