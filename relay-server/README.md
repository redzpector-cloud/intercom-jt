# Jejak Teknisi Internet Intercom Relay V2.0

Relay WebSocket sederhana untuk 2 HP. Server hanya meneruskan frame audio biner ke HP lain dalam kode ruang yang sama.

## Jalankan

```bash
npm install
npm start
```

Untuk produksi gunakan HTTPS/WSS melalui reverse proxy atau platform cloud yang menyediakan TLS. Contoh URL aplikasi: `wss://domain-anda/ws`.

Batas 1 ruang = maksimal 2 HP. Tidak ada penyimpanan audio di server.
