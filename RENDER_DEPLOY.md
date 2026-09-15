# LANGKAH AKTIFKAN SERVER INTERNET INTERCOM

1. Upload project ini ke repository GitHub.
2. Buka Render dan pilih **New → Blueprint**.
3. Pilih repository tersebut.
4. Render membaca `render.yaml` otomatis.
5. Tunggu deploy sampai status **Live**.
6. Catat URL service, contoh:
   `https://jejak-teknisi-intercom-relay.onrender.com`
7. URL WebSocket yang dipakai aplikasi adalah:
   `wss://jejak-teknisi-intercom-relay.onrender.com/ws`
8. Tes dulu URL HTTPS di browser. Jika muncul tulisan `Jejak Teknisi Internet Intercom relay OK`, server sudah hidup.
9. Setelah URL nyata didapat, URL tersebut perlu dimasukkan ke APK. Jangan lagi memakai `wss://YOUR-SERVER.example/ws`.

## Penting

Build APK hijau tidak otomatis berarti server internet sudah aktif. APK dan relay server adalah dua bagian berbeda:

**HP 1 → Internet → Relay Server → Internet → HP 2**

Tanpa relay server yang aktif, dua HP tidak dapat saling mengirim audio.
