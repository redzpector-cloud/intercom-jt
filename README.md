# Jejak Teknisi Mesh Intercom V1.3 — Stable Reconnect

Perbaikan V1.3:
- Wi-Fi lock saat interkom aktif agar koneksi lokal tidak mudah tidur.
- Menyimpan endpoint peer terakhir.
- Reconnect otomatis ke peer terakhir saat socket putus.
- Backoff reconnect 0,5–5 detik.
- Tetap memakai discovery untuk menemukan peer setelah perubahan jaringan.
- Audio 2 arah tetap memakai PCM 16 kHz mono dengan framing.

Tes:
1. Install di 2 HP.
2. Keduanya di Wi-Fi yang sama.
3. Izinkan Microphone.
4. Tunggu TERHUBUNG.
5. Tes audio dengan earphone.
6. Matikan Wi-Fi salah satu HP selama 3–5 detik lalu nyalakan lagi.
7. Target: aplikasi kembali TERHUBUNG tanpa menekan CARI ULANG.

V1.3 masih fokus stabilitas 2 HP. Setelah stabil, baru lanjut multi-peer mesh relay dan internet fallback.
