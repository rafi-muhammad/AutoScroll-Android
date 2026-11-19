# Auto Scroll (Android Kotlin & Jetpack Compose)

**Auto Scroll** adalah aplikasi Android native berbasis **Kotlin** dan **Jetpack Compose (Material 3)** untuk melakukan scroll layar secara otomatis ketika Anda membuka aplikasi lain seperti Shopee, Tokopedia, Lazada, TikTok, atau Instagram. Gerakan scroll dijalankan langsung di atas layar aplikasi target memakai `AccessibilityService.dispatchGesture()` tanpa akses root.

---

## Fitur Utama

1. **Start / Stop Layanan**
   Tombol utama berukuran besar di halaman dashboard untuk memulai dan menghentikan auto scroll dengan cepat.
2. **Pilihan Interval Scroll**
   Tersedia preset **30 Detik**, **1 Menit**, **2 Menit**, **3 Menit**, **5 Menit**, serta mode **Custom** dalam satuan detik atau menit (mulai dari 2 detik sampai 60 menit).
3. **Arah Scroll**
   Pengguna dapat memilih arah scroll **Ke Bawah** (sapuan jari ke atas) atau **Ke Atas** (sapuan jari ke bawah).
4. **Kecepatan & Jarak Scroll**
   Tersedia pilihan **Lambat**, **Sedang**, **Cepat**, dan **Sangat Cepat** untuk mengatur seberapa jauh layar bergeser setiap kali gesture dijalankan.
5. **Durasi Gerakan Scroll**
   Pengaturan durasi sapuan dari `200 ms` hingga `1500 ms` agar pergerakan scroll terasa halus sesuai kebutuhan aplikasi.
6. **Pemilihan Aplikasi Target**
   Secara default mengarah ke `com.shopee.id`, namun Anda bebas memilih aplikasi lain seperti Shopee MY/SG, Tokopedia, Lazada, TikTok, Instagram, atau aplikasi lain yang terpasang di HP. Layanan akan otomatis berhenti sejenak saat Anda keluar dari aplikasi target dan berjalan lagi begitu aplikasi target dibuka kembali.
7. **Floating Control (`SYSTEM_ALERT_WINDOW`)**
   Kontrol melayang berukuran ringkas yang bisa digeser ke mana saja di atas aplikasi lain, dilengkapi hitung mundur, jumlah scroll, serta tombol **Pause / Resume** dan **Stop**.
8. **Informasi Status & Jumlah Scroll**
   Menampilkan kondisi layanan (**Aktif**, **Nonaktif**, **Menunggu Aplikasi Target**, **Dijeda**, atau **Izin Diperlukan**), interval yang sedang dipakai, dan total scroll yang sudah berjalan beserta tombol reset.

---

## Struktur Project

```text
AutoScroll/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── drawable/ic_launcher_foreground.xml
        │   │   ├── mipmap-anydpi-v26/
        │   │   │   ├── ic_launcher.xml
        │   │   │   └── ic_launcher_round.xml
        │   │   ├── values/
        │   │   │   ├── colors.xml
        │   │   │   ├── strings.xml
        │   │   │   └── themes.xml
        │   │   └── xml/accessibility_service_config.xml
        │   └── java/com/autoscroll/app/
        │       ├── MainActivity.kt
        │       ├── data/
        │       │   ├── model/ScrollConfig.kt
        │       │   └── repository/ScrollEngineStateHolder.kt
        │       ├── service/
        │       │   ├── AutoScrollAccessibilityService.kt
        │       │   └── FloatingControlOverlay.kt
        │       ├── ui/
        │       │   ├── components/
        │       │   │   ├── IntervalSelectorCard.kt
        │       │   │   ├── ScrollSettingsCard.kt
        │       │   │   └── TargetAppCard.kt
        │       │   ├── screen/
        │       │   │   ├── DashboardScreen.kt
        │       │   │   └── PermissionsScreen.kt
        │       │   ├── theme/Theme.kt
        │       │   └── viewmodel/MainViewModel.kt
        │       └── util/
        │           ├── GestureCalculator.kt
        │           └── PermissionHelper.kt
        └── test/
            └── java/com/autoscroll/app/ScrollConfigTest.kt
```

---

## Kebutuhan Sistem & Izin

- **Minimum Android**: Android 8.0 Oreo (`minSdk = 26`)
- **Target SDK**: Android 15 (`targetSdk = 35`)
- **JDK**: Java 17
- **Tanpa Root**: Semua fitur berjalan menggunakan API resmi Android.
- **Izin yang Dipakai**:
  - `BIND_ACCESSIBILITY_SERVICE`: Menjalankan `dispatchGesture()` dan mengecek aplikasi yang sedang dibuka di layar depan.
  - `SYSTEM_ALERT_WINDOW`: Menampilkan Floating Control (Pause, Resume, Stop) di atas aplikasi target.
  - `<queries>` pada `AndroidManifest.xml`: Membaca daftar aplikasi target pada Android 11 ke atas tanpa harus meminta izin `QUERY_ALL_PACKAGES`.

---

## Cara Build & Install ke HP Android

### 1. Menggunakan Android Studio
1. Buka **Android Studio** (versi Koala, Ladybug, Meerkat, atau yang lebih baru).
2. Pilih **Open**, lalu arahkan ke folder project ini.
3. Tunggu **Gradle Sync** selesai sampai semua library Compose terunduh.
4. **Install langsung lewat kabel USB**:
   - Aktifkan **Developer options** dan **USB debugging** di pengaturan HP Anda.
   - Sambungkan HP ke komputer lewat kabel data lalu izinkan prompt USB debugging di layar HP.
   - Pilih perangkat Anda di toolbar Android Studio, kemudian klik **Run**.
5. **Atau buat file `.apk`**:
   - Buka menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   - Setelah selesai, ambil file APK di `app/build/outputs/apk/debug/app-debug.apk`, pindahkan ke HP, lalu tap untuk memasangnya.

### 2. Menggunakan Command Line (Gradle + Android SDK)
Jika Anda ingin build langsung dari terminal (memerlukan Gradle 8.9+ dan Android SDK):

```bash
gradle assembleDebug
```

Hasil build akan tersimpan di:
```text
app/build/outputs/apk/debug/app-debug.apk
```

Untuk langsung memasang ke HP yang terhubung lewat `adb`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Cara Pakai di HP

1. Buka aplikasi **Auto Scroll**, lalu masuk ke halaman **Pengaturan Izin** lewat ikon pengaturan di kanan atas.
2. Aktifkan **Layanan Aksesibilitas**, cari **Auto Scroll**, lalu nyalakan.
   - *Catatan untuk Android 13 ke atas (jika install dari file `.apk`):* Bila tombol Aksesibilitas tidak bisa diklik karena muncul keterangan *Restricted setting*, buka **Settings > Apps > Auto Scroll**, ketuk ikon titik tiga di pojok kanan atas, pilih **Allow restricted settings**, lalu kembali ke menu Aksesibilitas untuk menyalakannya.
3. Kembali ke aplikasi dan aktifkan izin **Tampil di Atas Aplikasi Lain (Overlay)**.
4. Di Dashboard, pilih **Aplikasi Target**, tentukan **Interval Scroll**, **Arah**, **Kecepatan**, serta **Durasi** sesuai kebutuhan.
5. Tekan tombol **Mulai Auto Scroll**.
6. Buka aplikasi target Anda di HP. Panel Floating Control akan muncul di layar dan auto scroll mulai berjalan sesuai jadwal.

---

## Menjalankan Unit Test

Project ini sudah menyertakan unit test JUnit4 di `app/src/test/java/com/autoscroll/app/ScrollConfigTest.kt` untuk memastikan perhitungan interval dan koordinat gesture berjalan benar:

```bash
gradle testDebugUnitTest
```
