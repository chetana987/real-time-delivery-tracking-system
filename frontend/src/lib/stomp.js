import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs.min.js';

/**
 * Creates a STOMP client that connects to the backend /ws endpoint via SockJS.
 * The JWT is sent as a STOMP CONNECT header so the server-side
 * JwtChannelInterceptor can authenticate the session.
 */
export function createStompClient(token) {
  return new Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {},
  });
}
