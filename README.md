# Jejak Teknisi Mesh Intercom V1.1

V1.1 adalah tes nyata 2 HP melalui jaringan Wi-Fi yang sama.

Fitur:
- mDNS/NSD auto discovery
- TCP auto connection
- Full duplex PCM audio 16 kHz mono
- Mic ke speaker kedua arah
- Auto reconnect setelah socket putus

Cara tes:
1. Install APK pada HP A dan HP B.
2. Sambungkan kedua HP ke jaringan Wi-Fi yang sama (router/hotspot).
3. Buka aplikasi pada kedua HP dan izinkan microphone.
4. Tunggu status menjadi TERHUBUNG.
5. Bicara dari HP A dan dengarkan HP B, lalu sebaliknya.
6. Untuk mencegah feedback, gunakan earphone saat tes awal.

Catatan:
V1.1 belum merupakan multi-hop mesh. Ini adalah fondasi koneksi nyata 2 HP. Setelah ini stabil, tahap berikutnya adalah multi-peer/relay, auto route, lalu internet fallback.
