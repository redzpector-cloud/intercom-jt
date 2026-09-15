export class IntercomRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
  }

  async fetch(request) {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("Intercom room OK");
    }

    const url = new URL(request.url);
    const room = url.searchParams.get("room")?.trim();

    if (!room) {
      return new Response("Missing room", { status: 400 });
    }

    if (this.state.getWebSockets().length >= 2) {
      return new Response("Room is full", { status: 409 });
    }

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];

    this.state.acceptWebSocket(server);
    server.serializeAttachment({ room });

    return new Response(null, {
      status: 101,
      webSocket: client
    });
  }

  webSocketMessage(ws, message) {
    for (const peer of this.state.getWebSockets()) {
      if (peer !== ws) {
        try {
          peer.send(message);
        } catch (_) {}
      }
    }
  }

  webSocketClose(ws) {
    try { ws.close(); } catch (_) {}
  }

  webSocketError(ws) {
    try { ws.close(); } catch (_) {}
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return new Response("Jejak Teknisi Intercom Relay OK");
    }

    if (url.pathname === "/ws") {
      if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
        return new Response("WebSocket endpoint", { status: 426 });
      }

      const room = url.searchParams.get("room")?.trim();
      if (!room) {
        return new Response("Missing room", { status: 400 });
      }

      const id = env.INTERCOM_ROOM.idFromName(room);
      return env.INTERCOM_ROOM.get(id).fetch(request);
    }

    return new Response("Jejak Teknisi Internet Intercom Relay OK");
  }
};
