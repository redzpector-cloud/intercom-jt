# Jejak Teknisi Mesh Intercom V1.5 — Background / Screen Off

Fokus V1.5:
- Foreground Service khusus microphone.
- Interkom tetap berjalan saat aplikasi diminimalkan.
- `stopWithTask=false` agar service tidak ikut berhenti ketika task aplikasi ditutup dari recent apps.
- `START_STICKY` agar Android berusaha membuat ulang service setelah proses service dihentikan sistem.
- Notifikasi permanen menunjukkan status interkom.
- Tombol Putuskan tersedia dari notifikasi.
- Engine audio/network tetap memakai V1.4 fast reconnect.
- Wi-Fi lock dari engine dipertahankan untuk membantu koneksi saat layar mati.

Tes:
1. Build dan install V1.5 di kedua HP.
2. Hubungkan kedua HP ke Wi-Fi yang sama.
3. Izinkan Microphone dan Notification.
4. Tunggu audio tersambung.
5. Tekan Home/minimize aplikasi.
6. Pastikan suara tetap berjalan.
7. Matikan layar HP selama 10–30 detik.
8. Nyalakan layar lagi dan cek apakah koneksi masih ada.
9. Tes juga menutup aplikasi dari recent apps; service seharusnya tetap hidup.

Catatan:
Beberapa merek Android memiliki battery saver/aggressive background restriction. Jika service tetap dihentikan oleh sistem, set aplikasi ke Battery: Unrestricted / Don't optimize sesuai menu HP.
V1.5 belum multi-hop mesh. Setelah background stabil, lanjut multi-peer mesh relay dan internet fallback.
