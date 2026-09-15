const http = require('http');
const WebSocket = require('ws');

const port = Number(process.env.PORT || 8080);
const host = process.env.HOST || '0.0.0.0';
const server = http.createServer((req,res)=>{
  if (req.url === '/health' || req.url === '/') {
    res.writeHead(200, {'Content-Type':'text/plain; charset=utf-8'});
    res.end('Jejak Teknisi Internet Intercom relay OK\n');
    return;
  }
  res.writeHead(404, {'Content-Type':'text/plain; charset=utf-8'});
  res.end('Not found\n');
});
const wss = new WebSocket.Server({server, path:'/ws'});
const rooms = new Map();

function leave(ws){
  if(!ws.room) return;
  const set=rooms.get(ws.room); if(!set) return;
  set.delete(ws);
  for(const peer of set) if(peer.readyState===WebSocket.OPEN) peer.send(JSON.stringify({type:'peer',count:set.size}));
  if(set.size===0) rooms.delete(ws.room);
  ws.room=null;
}

wss.on('connection',(ws)=>{
  ws.on('message',(data,isBinary)=>{
    if(!ws.room){
      if(isBinary) return;
      let msg; try{msg=JSON.parse(data.toString())}catch(e){return;}
      if(msg.type!=='join' || !msg.room) return ws.close(1008,'room required');
      const room=String(msg.room).toUpperCase().slice(0,32);
      if(!rooms.has(room)) rooms.set(room,new Set());
      const set=rooms.get(room);
      if(set.size>=2) return ws.send(JSON.stringify({type:'full'}));
      ws.room=room; set.add(ws);
      ws.send(JSON.stringify({type:'joined',count:set.size}));
      for(const peer of set) if(peer!==ws && peer.readyState===WebSocket.OPEN) peer.send(JSON.stringify({type:'peer',count:set.size}));
      if(set.size===1) ws.send(JSON.stringify({type:'waiting'}));
      return;
    }
    const set=rooms.get(ws.room); if(!set) return;
    for(const peer of set){ if(peer!==ws && peer.readyState===WebSocket.OPEN) peer.send(data,{binary:isBinary}); }
  });
  ws.on('close',()=>leave(ws));
  ws.on('error',()=>leave(ws));
});
server.listen(port, host, ()=>console.log(`Relay listening on ${host}:${port}`));
