# Jejak Teknisi Mesh Intercom V1.2 — Stable 2 HP

Perbaikan utama dari V1.1:
- Nama service unik untuk setiap HP.
- Aturan client/server deterministik agar dua HP tidak saling membuka dua koneksi.
- TCP_NODELAY + keepAlive.
- Framing audio agar data PCM tidak tercampur dengan pembacaan stream.
- Deteksi koneksi mati dan auto reconnect.
- Status koneksi berdasarkan socket nyata, bukan simulasi UI.

Tes:
1. Build APK.
2. Install di 2 HP.
3. Kedua HP harus berada pada Wi-Fi/LAN yang sama.
4. Izinkan Microphone.
5. Buka kedua aplikasi dan tunggu status TERHUBUNG.
6. Tes suara dengan earphone terlebih dahulu.

Catatan:
V1.2 masih 1 peer langsung (2 HP). Setelah koneksi stabil, tahap berikutnya adalah multi-peer mesh relay dan kemudian internet fallback.
