# Jejak Teknisi Internet Intercom V2.0

Versi ini mengganti Wi-Fi Direct / NSD dengan **Internet Intercom**.

## Cara kerja

HP A → Wi-Fi/Data Seluler → WebSocket Relay → Wi-Fi/Data Seluler → HP B

Kedua HP tidak perlu berada di Wi-Fi yang sama. Cukup internet aktif dan menggunakan **kode ruang yang sama**.

## Server wajib

Aplikasi membutuhkan WebSocket relay. Source server tersedia di `relay-server/`.

1. Deploy `relay-server` ke VPS/cloud yang menyediakan Node.js.
2. Gunakan TLS/WSS untuk produksi.
3. Masukkan alamat seperti `wss://domain-anda/ws` di aplikasi kedua HP.
4. Kedua HP masukkan kode ruang yang sama, misalnya `JT1234`.

## Catatan audio

V2.0 menggunakan PCM 16 kHz mono melalui WebSocket agar implementasi sederhana dan mudah diuji. Ini membutuhkan bandwidth lebih besar daripada codec Opus. Jika koneksi sudah stabil, tahap berikutnya sebaiknya memakai Opus/WebRTC untuk menurunkan bandwidth dan latency.

## Background

Foreground microphone service tetap dipakai agar interkom dapat berjalan ketika aplikasi diminimalkan. Perilaku battery saver tiap merek HP dapat berbeda.
