# WiFi PM & Daily Report Helper — Asisten Teknisi Preventative Maintenance

Aplikasi Android khusus teknisi jaringan untuk mengotomatisasi pembacaan screenshot Speedtest dengan AI, menggabungkan 4 foto dokumentasi menjadi 1 gambar kolase di bawah 1 MB, serta menyusun rekap laporan harian (10 titik PM & Corrective Maintenance) yang siap disalin ke WhatsApp atau aplikasi chat kerja.

### User Review & Critical Decisions

> [!IMPORTANT]
> Berikut adalah keputusan utama yang telah dikonfirmasi dari jawaban Anda, ditambah fleksibilitas kustomisasi format laporan:

- **Keputusan Terkonfirmasi 1 — Kolase 4 Foto (< 1 MB)**: Menggunakan susunan **Grid 2x2** otomatis dengan overlay header/label **Nama Lokasi & Tanggal/Waktu**, serta kompresi JPEG adaptif yang menjamin ukuran file akhir selalu **di bawah 1 MB** (ideal untuk diunggah ke Form Perusahaan).
- **Keputusan Terkonfirmasi 2 — Ekstraksi AI Speedtest**: Menggunakan **Gemini Multimodal (`gemini-3.8-flash`)** untuk membaca gambar hasil Speedtest dan secara spesifik mengekstrak angka **Download (Mbps)** dan **Upload (Mbps)**, lalu otomatis menggabungkannya dengan **Judul/Lokasi** dan **Tanggal Otomatis**.
- **Keputusan Terkonfirmasi 3 — Output Laporan Siap Salin**: Seluruh laporan satuan (Daily PM) maupun rekap harian (**Today Report** untuk target 10 lokasi PM + Corrective Maintenance) diformat menjadi **teks rapi siap salin (1-Tap Copy)** dan **Share langsung ke WhatsApp/Chat**.
- **Keputusan Terkonfirmasi 4 — Penyimpanan Lokal Tanpa Login**: Seluruh data laporan harian, template teks, dan daftar 10 lokasi PM disimpan secara **lokal di memori HP (Room Database)** sehingga cepat, privat, dan langsung bisa dipakai tanpa login.
- **Fleksibilitas Template Laporan (Bisa Diedit Kapan Saja)**: Karena setiap perusahaan memiliki gaya penulisan teks laporan yang khas, aplikasi dilengkapi **Template Editor** dengan variabel dinamis (`{tanggal}`, `{judul}`, `{download}`, `{upload}`, `{daftar_pm}`, `{daftar_cm}`). Anda juga dapat mengirimkan contoh format teks laporan PM dan Today Report Anda kapan saja untuk dijadikan format bawaan.

---

### 1. Overview & Core Concept

- **What It Does**:
  1. **Auto-Deteksi Speedtest & Generator Laporan PM**: Pilih atau kirim screenshot Speedtest ke aplikasi -> AI otomatis membaca nilai **Download** & **Upload** -> mengisi **Judul/Lokasi** dan **Tanggal Otomatis** ke dalam format laporan PM.
  2. **4-in-1 Photo Collage Compressor (< 1 MB)**: Pilih 4 foto kegiatan PM -> aplikasi menyusunnya menjadi kolase Grid 2x2 lengkap dengan label lokasi & timestamp -> mengompresi secara cerdas hingga ukuran file dijamin `< 1 MB` (misal 650–850 KB) siap unggah ke form perusahaan.
  3. **Today Report Compiler (10 PM + Corrective Maintenance)**: Melacak progres harian (indikator `0/10 PM Selesai`), mengumpulkan hasil Download/Upload dari ke-10 titik PM ditambah catatan Corrective Maintenance (CM) jika ada, lalu menghasilkan satu teks **Today Report** utuh dalam sekali klik.
  4. **AI Visual & Chat Assistant**: Dilengkapi fitur **Edit/Buat Gambar Dokumentasi AI** (`gemini-nano-banana-2.1`) serta **Asisten Chat Teknisi Multi-Model** (`gemini-3.8-flash`, `gemini-4-argon`, `gemini-3.5-flash-lite`) untuk tanya-jawab troubleshooting jaringan atau merapikan kalimat laporan.
- **Target Audience / Persona**: Teknisi lapangan / Engineer Preventative Maintenance (PM) WiFi yang setiap hari harus mengunjungi hingga 10 titik lokasi, mengunggah foto bukti `< 1 MB` ke form perusahaan, dan mengirim laporan harian ke grup kerja.
- **Key Value**: Memangkas waktu administrasi harian dari puluhan menit (ketik angka speedtest manual, gabung & kompres foto di aplikasi terpisah, rekap 10 lokasi satu per satu) menjadi hitungan detik.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Alur Cepat Tambah PM (Speedtest + 4 Foto)**:
     - Teknisi menekan tombol **"+ Tambah Lokasi PM (Ke-1 s/d 10)"**.
     - Mengisi/memilih **Judul Lokasi** (tanggal terisi otomatis hari ini).
     - Memilih **Screenshot Speedtest** -> menekan **"Scan Speedtest (AI)"** -> angka **Download** dan **Upload** langsung terisi otomatis ke form dan pratinjau teks laporan.
     - Memilih **4 Foto Dokumentasi** -> aplikasi menampilkan pratinjau **Grid 2x2 dengan Label Lokasi & Waktu** beserta indikator ukuran file real-time (contoh: `Ukuran Akhir: 742 KB ✓ < 1 MB`) -> tombol **"Simpan Kolase ke Galeri / Bagikan"**.
     - Menekan **"Simpan & Salin Teks PM"** -> data masuk ke progres harian (`1/10 PM`) dan teks laporan langsung tersalin ke clipboard.
  2. **Alur Rekap Today Report (PM + Corrective Maintenance)**:
     - Pada tab **Today Report**, teknisi melihat daftar PM yang sudah dikerjakan hari ini (target 10 item) beserta nilai Download/Upload masing-masing.
     - Jika ada pekerjaan perbaikan insidental, teknisi dapat menambahkan item di bagian **Corrective Maintenance (CM)**.
     - Aplikasi otomatis merangkai seluruh item PM dan CM ke dalam **Format Today Report** (yang templatenya bisa disesuaikan).
     - Teknisi menekan **"Salin Today Report"** atau **"Kirim ke WhatsApp"**.
  3. **Alur Kustomisasi Format Laporan**:
     - Teknisi membuka menu **Atur Format Teks** untuk menyesuaikan susunan kata laporan PM satuan maupun Today Report agar 100% identik dengan format baku perusahaan.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Tactical Field Utility** — Desain bersih, kontras tinggi agar mudah dibaca di lapangan/luar ruangan, dengan kartu berstruktur tegas dan indikator status progres yang jelas.
  - *Color Palette & Mood*:
    - `--background`: Slate Dark / Crisp Daylight adaptive (`#0F172A` pada mode gelap, `#F8FAFC` pada mode terang)
    - `--primary`: Signal Teal (`#0D9488` / `#14B8A6`) — merepresentasikan konektivitas WiFi dan sinyal optimal
    - `--accent`: Speed Amber (`#F59E0B`) untuk highlight aksi cepat & indikator ukuran file `< 1 MB`
    - `--success`: Emerald (`#10B981`) untuk indikator target 10/10 PM tercapai
  - *Typography & Hierarchy*:
    - Heading & Angka Speedtest: **Space Grotesk** (tegas dan teknis untuk menampilkan angka Mbps dan counter `8/10 PM`).
    - Body & Preview Laporan: **Plus Jakarta Sans** untuk keterbacaan UI, dipadukan dengan **JetBrains Mono** pada kotak pratinjau teks laporan WhatsApp.
  - *Component Styling & Layout*: Material Design 3 dengan `NavigationBar` 4 tab utama (**PM Harian**, **Kolase 4 Foto**, **Today Report**, **Asisten AI & Template**), tombol aksi besar minimal `48dp` untuk penggunaan satu tangan di lapangan.

- **Interactive Feedback & Motion**:
  - Animasi *progress bar* harian dari `1/10` hingga `10/10` dengan lencana "Target PM Harian Selesai!".
  - Indikator badge hijau otomatis saat hasil kompresi kolase berada di bawah `1.000 KB (< 1 MB)`.
  - Snackbar konfirmasi instan saat teks laporan berhasil disalin ke clipboard.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Ekstraksi Speedtest Terstruktur dengan `Schema.obj` pada Firebase AI Logic**
  - *Chosen Approach*: Menggunakan `gemini-3.8-flash` dengan `responseMimeType = "application/json"` dan skema terstruktur `downloadMbps` serta `uploadMbps`.
  - *Why*: Menjamin hasil pembacaan screenshot aplikasi Speedtest (Ookla, Fast.com, nPerf, dll.) selalu berupa angka bersih tanpa teks tambahan yang merusak format laporan, namun tetap mengizinkan teknisi mengedit manual jika ingin melakukan koreksi.
- **Decision 2: Algoritma Kompresi Kolase 2x2 Adaptif Berbasis Bitmap & ByteArrayOutputStream**
  - *Chosen Approach*: Menggambar 4 foto pada satu `Canvas` resolusi optimal (misal `1600x1600` px) lengkap dengan banner judul & tanggal, lalu menjalankan kompresi JPEG berjenjang (mulai kualitas 88% turun bertahap jika perlu) hingga ukuran array byte dipastikan `< 950 KB` (`< 1 MB`).
  - *Why*: Form perusahaan sering menolak unggahan di atas 1 MB. Pendekatan ini menjamin foto tetap tajam dan teks lokasi terbaca jelas, tetapi 100% lolos batas ukuran 1 MB.
- **Decision 3: Sistem Template Laporan Berbasis Variabel Placeholder**
  - *Chosen Approach*: Menyediakan format bawaan untuk **Daily PM Report** dan **Today Report (PM + CM)** yang mendukung tag otomatis seperti `{tanggal}`, `{judul}`, `{no}`, `{download}`, `{upload}`, dan `{catatan_cm}`, serta bisa diedit langsung oleh pengguna di dalam aplikasi.
  - *Why*: Memastikan aplikasi langsung siap pakai sejak hari pertama dan dapat mengikuti perubahan format laporan perusahaan kapan saja tanpa perlu update kode.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:
```
┌───────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (Edge-to-Edge)                        │
│         configureAppCheck(this, intent) -> Material 3 Scaffold            │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │
        ┌─────────────────────────────┼─────────────────────────────┐
        ▼                             ▼                             ▼
┌───────────────────┐       ┌───────────────────┐       ┌───────────────────┐
│  PM & Speedtest   │       │  4-Photo Collage  │       │   Today Report    │
│    Screen / VM    │       │   Engine (<1MB)   │       │  & AI Studio Tab  │
└─────────┬─────────┘       └─────────┬─────────┘       └─────────┬─────────┘
          │                           │                           │
          ▼                           ▼                           ▼
┌───────────────────┐       ┌───────────────────┐       ┌───────────────────┐
│ Firebase AI Logic │       │  Android Canvas   │       │  Room Local DB    │
│ gemini-3.8-flash  │       │ 2x2 Grid + Label  │       │ (PM Entry, CM, &  │
│ (Download/Upload) │       │ Adaptive <1MB JPG │       │ Report Templates) │
└───────────────────┘       └───────────────────┘       └───────────────────┘
```

- **Data Model & State**:
  - `PmReportEntity` (Room): `id`, `dateString` (YYYY-MM-DD), `displayDate`, `locationTitle`, `downloadMbps`, `uploadMbps`, `speedtestImageUri`, `collageImageUri`, `collageSizeBytes`, `notes`, `createdAt`.
  - `CorrectiveMaintenanceEntity` (Room): `id`, `dateString`, `locationTitle`, `issueDescription`, `actionTaken`, `status`, `createdAt`.
  - `ReportTemplatePreferences`: Menyimpan template teks *Daily PM* dan *Today Report* secara persisten.

- **Interactive Component & State Mapping**:
  - **Speedtest Scanner Handler**: Memilih gambar via `PickVisualMedia` -> mengubah ke `Bitmap` -> memanggil `gemini-3.8-flash` dengan `withTimeout(60_000L)` -> mengisi state `downloadMbps` & `uploadMbps` -> merender ulang pratinjau laporan secara reaktif.
  - **4-Photo Collage Handler**: Memilih hingga 4 foto via `PickMultipleVisualMedia(4)` -> mengatur urutan atau mengganti foto -> merender `Bitmap` Grid 2x2 dengan header Lokasi & Waktu -> menjalankan loop kompresi `< 1_000_000` bytes -> menyimpan ke `MediaStore` (Galeri HP) dan menampilkan tombol Share.
  - **Today Report Compiler Handler**: Mengambil seluruh `PmReportEntity` dan `CorrectiveMaintenanceEntity` pada tanggal terpilih -> menyusun daftar bernomor 1 s/d 10 beserta hasil Speedtest (`Download / Upload`) dan daftar Corrective Maintenance -> menyalin ke `ClipboardManager` atau mengirim via `Intent.ACTION_SEND`.
  - **AI Image & Chatbot Handler**: Tab khusus yang mengintegrasikan generator/editor gambar (`gemini-nano-banana-2.1`) dan asisten chat multi-turn (`gemini-3.8-flash` / `gemini-4-argon` / `gemini-3.5-flash-lite`) dengan riwayat percakapan.
