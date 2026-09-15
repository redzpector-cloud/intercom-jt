# Jejak Teknisi Internet Intercom — Relay Server

Relay WebSocket untuk **Jejak Teknisi Internet Intercom V2.x**. Server ini meneruskan audio PCM antar maksimal 2 HP yang memakai kode ruang yang sama.

## Deploy ke Render

Project utama sudah menyediakan `render.yaml` di folder root. Di Render pilih **New → Blueprint** lalu pilih repository GitHub project ini.

Konfigurasi yang dipakai:
- Service: Web Service
- Root Directory: `relay-server`
- Build Command: `npm install`
- Start Command: `npm start`
- Health Check: `/health`
- Runtime: Node.js

Render memberikan alamat publik `https://NAMA-SERVICE.onrender.com`. Untuk aplikasi Android, alamat WebSocket harus memakai **WSS**:

`wss://NAMA-SERVICE.onrender.com/ws`

Render memang mendukung koneksi WebSocket publik pada Web Service dan menyarankan `wss` untuk koneksi internet. citehttps://render.com/docs/websocket

## Tes server

Buka alamat HTTPS service di browser. Harus muncul:

`Jejak Teknisi Internet Intercom relay OK`

Endpoint health:

`https://NAMA-SERVICE.onrender.com/health`

WebSocket:

`wss://NAMA-SERVICE.onrender.com/ws`

## Catatan

- Satu room maksimal 2 perangkat.
- Server tidak menyimpan rekaman audio.
- Kedua HP harus memasukkan kode ruang yang sama.
- Free web service Render dapat sleep setelah tidak aktif dan akan aktif kembali saat menerima request/koneksi baru; ini dapat menambah waktu tunggu koneksi pertama. citehttps://render.com/docs/your-first-deploy
