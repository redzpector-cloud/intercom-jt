# Jejak Teknisi Mesh Intercom V1.4 — Fast Reconnect

Perbaikan fokus V1.4:
- Reconnect ke IP/port terakhir dimulai segera.
- Retry cepat: 0 / 250 / 500 / 750 / 1000 / 1500 / 2000 ms.
- Hanya satu reconnect loop aktif agar tidak saling berebut.
- Setelah retry cepat, tetap mencoba setiap 2 detik sambil NSD mencari alamat baru.
- Dead connection detection dipercepat dari 7 detik menjadi 4 detik.
- Wi-Fi lock dan audio engine dari V1.3 tetap dipertahankan.

Tes:
1. Build dan install V1.4 di kedua HP.
2. Sambungkan kedua HP ke Wi-Fi yang sama.
3. Pastikan suara sudah tersambung.
4. Matikan Wi-Fi HP 2 selama 3 detik, lalu nyalakan lagi.
5. Jangan tekan CARI ULANG.
6. Ukur apakah koneksi kembali dalam beberapa detik.

Catatan:
V1.4 masih koneksi 2 HP. Setelah reconnect cukup cepat dan stabil, tahap berikutnya adalah multi-peer mesh relay.
