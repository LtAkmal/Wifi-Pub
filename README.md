# WiFi PM Helper v2.0.0 (CGK)

## 📲 Download APK Terbaru (v2.0.0)
- **Lewat Menu Releases**: Klik bagian [**Releases (`v2.0.0`)**](https://github.com/LtAkmal/Wifi-Pub/releases/tag/v2.0.0) di sebelah kanan halaman GitHub, lalu unduh **`WiFi-PM-Helper-v2.0.0.apk`**.

---

## Riwayat Pembaruan (Changelog)
- **v2.0.0**:
  - **Deteksi Foto / Screenshot Corrective Maintenance (CM) by AI**: Menambahkan tombol **"Deteksi Foto by AI"** di dalam dialog *Tambah / Edit Corrective Maintenance*. Cukup pilih screenshot laporan CM yang dikirim ke Grup WhatsApp atau foto kegiatan CM, maka AI otomatis mendeteksi dan mengisikan ke-7 kolom CM (`Judul Kegiatan 🛠️`, `Lokasi`, `Nama Peralatan`, `Kendala`, `Issue`, `Action per baris`, dan `Status per baris`).
  - **Update Label Bandara CGK (T1–T3)**: Pembaruan label header aplikasi menjadi `CGK • <Tanggal>` untuk mencakup seluruh area Terminal 1–3 Bandara Soekarno-Hatta (CGK).
- **v1.9.0**:
  - **Upload 4 foto PM terpisah ke NAS + riwayat di https://akmal-satrio-n.work.gd/pm/**: Mendukung pengiriman otomatis maupun manual 4 foto dokumentasi PM terpisah (`photo1..photo4` resolusi asli/JPEG quality 92 + `thumb1..thumb4` 480px quality 80) beserta metadata JSON lengkap ke server NAS, dilengkapi dialog **Pengaturan Upload NAS** (URL Server, Token Bearer, Tes Koneksi `/api/auth/check`, toggle Auto-Upload), chip status per laporan PM (`✓ NAS PM-XXXX`, `Mengunggah…`, `Gagal, ketuk untuk upload ulang`, `Upload ke NAS`), serta fitur *auto-retry* saat aplikasi dibuka.
- **v1.8.0**:
  - **Fitur Klik Detail Access Point di WiFi Analyzer (Persis Original)**: Mengetuk salah satu WiFi / SSID pada daftar WiFi Analyzer menampilkan *bottom sheet* detail lengkap: **Gauge Lingkaran Cyan (`-dBm`)**, status `• Connected`, **Band (`5 GHz`)**, **Channel (`149`)**, **Width (`20 MHz`)**, **Frequency (`5745 MHz`)**, **IP address (`172.18.108.140`)**, **Security (`[ESS]`)**, **Vendor Lengkap (`Hewlett Packard Enterprise`)**, **Distance (`2m`)**, **BSSID**, serta fitur **`Rename`** — dan otomatis memperbarui screenshot **Foto 4**.
- **v1.7.0**:
  - **Fix Sinkronisasi Lokasi pada Kolase (`Lokasi: ...`)**: Lokasi yang diketik di *Input PM* maupun *Judul Lokasi pada Kolase* (contoh: `Home (Test)`) langsung tersinkronisasi otomatis ke banner kolase tanpa tertimpa `Terminal 3 CGK`.
  - **Fix Watermark Bawah Kolase (`DD/MM/YY • HH:mm:ss`)**: Watermark di bagian paling bawah gambar kolase 4 foto kini menampilkan tanggal beserta Jam:Menit:Detik (`dd/MM/yy • HH:mm:ss`), `App By Akmal Satrio N`, dan `Visit https://akmal-satrio-n.work.gd`.
  - **Registrasi Resmi `Test ID` Speedtest.net (`https://www.speedtest.net/result/<Test_ID>`)**: Hasil Speedtest didaftarkan ke endpoint resmi `https://www.speedtest.net/api/api.php` sehingga `Test ID` dapat dicari dan dicek langsung di situs resmi Speedtest.net.
- **v1.6.0**:
  - UI/UX Speedtest & WiFi Analyzer 100% persis original beserta auto-screenshot ke Foto 3 & Foto 4.
- **v1.5.0**:
  - Kamera langsung di aplikasi (Foto 1-2), Auto-Save Lokasi PM, Edit Corrective Maintenance (CM) dengan teks bayangan (*placeholder*), dan tombol ganti Shift (`Pagi (SH1)` / `Malam (SH2)`).
- **v1.4.0**:
  - Kolase 4 Foto 100% Utuh (Tanpa Terpotong) `< 1 MB`, Audit UI/UX 3x, dan Smart Studio Foto Dokumentasi PM.
