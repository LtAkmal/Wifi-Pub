# WiFi PM Helper (CGK Terminal 3)

Aplikasi Android asisten teknisi **Preventive Maintenance (PM) & Corrective Maintenance (CM) WiFi Publik** untuk mempercepat pembuatan laporan harian dan dokumentasi foto.

## Fitur Utama

1. **Auto-Deteksi Speedtest dengan AI (`gemini-3.8-flash`)**
   - Membaca screenshot hasil Speedtest secara otomatis (baik dipilih dari Galeri maupun dikirim langsung melalui tombol **Share** dari aplikasi Speedtest).
   - Mengekstrak nilai **Upload (Mbps)** dan **Download (Mbps)** secara otomatis ke dalam format `REPORT DAILY ROUTINE PREVENTIVE MAINTENANCE`.
   - Dilengkapi daftar preset lokasi rutin **Terminal 3 CGK** dan pengisian tanggal otomatis.

2. **Kolase 4 Foto Jadi 1 (`< 1 MB`)**
   - Menggabungkan 4 foto dokumentasi kegiatan PM ke dalam susunan **Grid 2x2** lengkap dengan label header **Lokasi, Tanggal, dan Hasil Speedtest**.
   - Menggunakan kompresi JPEG adaptif yang menjamin ukuran file akhir **selalu di bawah 1 MB (`< 950 KB`)**, siap diunggah ke Form Perusahaan.

3. **Rekap Harian Otomatis (`*ICT OPERATION DAILY REPORT (CGK).`)**
   - Menyimpan progres harian hingga **10 lokasi PM Speedtest** secara lokal di perangkat (Room Database, tanpa perlu login).
   - Menyediakan form cepat untuk **Corrective Maintenance (CM)** serta pengaturan **Shift, Helpdesk, On Duty, Petugas EOS, dan Officer**.
   - Menghasilkan teks laporan lengkap siap salin (**1-Tap Copy**) atau kirim langsung ke **WhatsApp**.

4. **Asisten AI Teknisi & Studio Gambar AI**
   - **Chatbot Teknisi Multi-Model**: Mendukung `gemini-3.8-flash`, `gemini-4-argon`, dan `gemini-3.1-flash-lite`.
   - **Studio Buat & Edit Gambar (`gemini-nano-banana-2.1`)**: Membuat atau memperjelas foto dokumentasi dengan instruksi teks.

## Cara Build APK Secara Lokal

```bash
./gradlew assembleDebug
```

File APK akan tersedia di `app/build/outputs/apk/debug/app-debug.apk`.
