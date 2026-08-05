// Minimal STOMP client over a SockJS raw WebSocket connection.
//
// The backend registers a SockJS endpoint at /ws (see WebSocketConfig). The
// SockJS `websocket` transport is a raw WebSocket at /ws/websocket: STOMP
// frames are written directly on the socket, each frame terminated by \x00.
//
// This helper negotiates heart-beat:0,0 so no heartbeat frames pollute the
// message stream. The JWT is sent as an `Authorization: Bearer ...` native
// header on CONNECT, which JwtChannelInterceptor validates.
import { WebSocket } from 'k6/websockets';

const FRAME_END = '\x00';

export function openStomp(url, token) {
  const conn = {
    ws: new WebSocket(url),
    connected: false,
    onConnected: null, // called(headers) after CONNECTED
    onFrame: null, // called(headers, body) for each MESSAGE
    onError: null, // called(headers, body) for STOMP ERROR frames
  };

  conn.ws.addEventListener('open', () => {
    const frame = [
      'CONNECT',
      'accept-version:1.2',
      'heart-beat:0,0',
      `Authorization: Bearer ${token}`,
      '',
      '',
    ].join('\n') + FRAME_END;
    conn.ws.send(frame);
  });

  conn.ws.addEventListener('message', (event) => {
    const data = String(event.data);
    const chunks = data.split(FRAME_END).filter((c) => c.trim().length > 0);
    for (const chunk of chunks) {
      const { command, headers, body } = parseFrame(chunk);
      if (command === 'CONNECTED') {
        conn.connected = true;
        if (conn.onConnected) conn.onConnected(headers);
      } else if (command === 'MESSAGE') {
        if (conn.onFrame) conn.onFrame(headers, body);
      } else if (command === 'ERROR') {
        if (conn.onError) conn.onError(headers, body);
      }
    }
  });

  conn.send = (destination, jsonBody) => {
    const frame = [
      'SEND',
      `destination:${destination}`,
      'content-type:application/json',
      '',
      jsonBody,
    ].join('\n') + FRAME_END;
    conn.ws.send(frame);
  };

  conn.subscribe = (destination) => {
    const frame = [
      'SUBSCRIBE',
      `id:sub-${Math.floor(Math.random() * 1e9)}`,
      `destination:${destination}`,
      '',
      '',
    ].join('\n') + FRAME_END;
    conn.ws.send(frame);
  };

  conn.close = () => conn.ws.close();

  return conn;
}

// Calls `then` as soon as `cond()` returns true, polling on a timer.
// Falls back to `onTimeout` after `timeoutMs`. Uses timers (not sleep) so it
// never blocks the k6 global event loop.
export function when(cond, then, onTimeout, timeoutMs = 15000) {
  let done = false;
  const poll = setInterval(() => {
    if (cond()) {
      done = true;
      clearInterval(poll);
      then();
    }
  }, 50);
  setTimeout(() => {
    if (!done) {
      clearInterval(poll);
      onTimeout();
    }
  }, timeoutMs);
}

function parseFrame(raw) {
  const lines = raw.split('\n');
  const command = lines[0];
  const headers = {};
  let i = 1;
  while (i < lines.length && lines[i] !== '') {
    const idx = lines[i].indexOf(':');
    if (idx > 0) headers[lines[i].slice(0, idx).trim()] = lines[i].slice(idx + 1).trim();
    i += 1;
  }
  const body = lines.slice(i + 1).join('\n');
  return { command, headers, body };
}
