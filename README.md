# Auto Scroll (Android Kotlin & Jetpack Compose)

**Auto Scroll** adalah aplikasi Android native berbasis **Kotlin** dan **Jetpack Compose (Material 3)** untuk melakukan scroll layar otomatis sekaligus merekam dan memutar ulang rangkaian gesture (Macro) ketika Anda membuka aplikasi lain seperti Shopee, Tokopedia, Lazada, TikTok, atau Instagram. Seluruh gerakan dijalankan langsung di atas aplikasi target menggunakan `AccessibilityService.dispatchGesture()` tanpa akses root.

---

## Fitur Utama

### 1. Auto Scroll Otomatis
1. **Start / Stop Layanan**
   Tombol utama berukuran besar di halaman dashboard untuk memulai dan menghentikan auto scroll dengan cepat.
2. **Pilihan Interval Scroll**
   Tersedia preset **30 Detik**, **1 Menit**, **2 Menit**, **3 Menit**, **5 Menit**, serta mode **Custom** dalam satuan detik atau menit (mulai dari 2 detik sampai 60 menit).
3. **Arah, Kecepatan, dan Durasi Scroll**
   Pengguna dapat mengatur arah scroll (**Ke Bawah** atau **Ke Atas**), jarak sapuan (**Lambat**, **Sedang**, **Cepat**, **Sangat Cepat**), serta durasi gesture (`200 ms` sampai `1500 ms`).
4. **Pemilihan Aplikasi Target**
   Secara default mengarah ke `com.shopee.id`, namun Anda bebas memilih aplikasi lain yang terpasang di HP. Auto Scroll hanya berjalan saat aplikasi target berada di layar depan.

### 2. Gesture Recorder / Macro Recorder
1. **Rekam Interaksi dari Floating Control**
   Mulai perekaman dari tab **Rekam**, lalu berpindah ke aplikasi target. Ketuk, tekan lama, swipe, atau scroll langsung di layar; Floating Control akan menampilkan badge `REC`, durasi `MM:SS`, jumlah gesture terekam, serta tombol **Stop Recording**.
2. **Jenis Gesture yang Didukung**
   Mendukung perekaman **Tap**, **Double Tap**, **Long Press**, **Swipe**, **Scroll**, dan **Jeda (Delay)** antar gerakan lengkap dengan koordinat awal dan akhir, durasi, serta urutan aksi.
3. **Catatan Teknis Android Non-Root**
   Pada Android tanpa akses root, aplikasi lain tidak diizinkan membaca koordinat sentuhan mentah secara pasif di layar aplikasi target. Karena itu, mode **Overlay Capture + Forward** menangkap sentuhan pada lapisan overlay transparan (`TouchCaptureOverlay`) lalu langsung meneruskannya ke aplikasi target lewat `dispatchGesture()`. Layar sensitif yang memakai `FLAG_SECURE` tetap dilindungi oleh sistem Android.

### 3. My Macros (Room Database)
Seluruh rekaman disimpan secara lokal di **Room Database** sehingga tetap aman saat aplikasi ditutup atau HP direstart. Di tab **My Macros**, Anda dapat:
- Memilih dan mengaktifkan macro ke Floating Control lewat tombol **Use Macro**.
- Mengganti nama macro.
- Mengedit urutan gerakan (naik atau turunkan posisi aksi), menambah aksi baru, menghapus aksi tertentu, serta mengubah tipe aksi, durasi (`ms`), dan jeda (`ms`).
- Mengatur jumlah pengulangan (**Repeat**, 1 sampai 999 kali).
- Menghapus rekaman disertai dialog konfirmasi.

### 4. Dynamic Floating Control (1 Window untuk 3 Mode)
Aplikasi memakai satu kontrol melayang (`SYSTEM_ALERT_WINDOW`) yang isinya berubah otomatis mengikuti mode aktif:
- **Mode Auto Scroll**: menampilkan status aktif, interval atau hitung mundur, hitungan scroll, serta tombol **Start**, **Pause / Resume**, dan **Stop**.
- **Mode Recording**: menampilkan badge **REC**, durasi perekaman, jumlah gesture terekam, dan tombol **Stop Recording**.
- **Mode Macro Playback**: menampilkan nama macro terpilih, progress langkah (misalnya `7/12 Aksi`), status playback, serta tombol **Play / Play Ulang**, **Pause / Resume**, dan **Stop**. Setelah seluruh aksi selesai dijalankan, status berubah menjadi **Completed** dan tidak berulang otomatis kecuali opsi **Repeat** diatur lebih dari 1 kali.

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
        │       │   ├── local/
        │       │   │   ├── AutoScrollDatabase.kt
        │       │   │   ├── MacroDao.kt
        │       │   │   └── MacroEntities.kt
        │       │   ├── model/
        │       │   │   ├── MacroModels.kt
        │       │   │   └── ScrollConfig.kt
        │       │   └── repository/
        │       │       ├── MacroRepository.kt
        │       │       └── ScrollEngineStateHolder.kt
        │       ├── service/
        │       │   ├── AutoScrollAccessibilityService.kt
        │       │   ├── FloatingControlOverlay.kt
        │       │   └── TouchCaptureOverlay.kt
        │       ├── ui/
        │       │   ├── components/
        │       │   │   ├── IntervalSelectorCard.kt
        │       │   │   ├── ScrollSettingsCard.kt
        │       │   │   └── TargetAppCard.kt
        │       │   ├── screen/
        │       │   │   ├── DashboardScreen.kt
        │       │   │   ├── MyMacrosScreen.kt
        │       │   │   ├── PermissionsScreen.kt
        │       │   │   └── RecorderScreen.kt
        │       │   ├── theme/Theme.kt
        │       │   └── viewmodel/MainViewModel.kt
        │       └── util/
        │           ├── GestureCalculator.kt
        │           ├── GestureClassifier.kt
        │           └── PermissionHelper.kt
        └── test/
            └── java/com/autoscroll/app/ScrollConfigTest.kt
```

---

## Kebutuhan Sistem & Izin

- **Minimum Android**: Android 8.0 Oreo (`minSdk = 26`)
- **Target SDK**: Android 15 (`targetSdk = 35`)
- **JDK**: Java 17
- **Database Lokal**: Room + KSP
- **Tanpa Root**: Semua fitur berjalan memakai API resmi Android.
- **Izin yang Dipakai**:
  - `BIND_ACCESSIBILITY_SERVICE`: Menjalankan `dispatchGesture()` untuk Auto Scroll maupun pemutaran Macro dan membaca aplikasi aktif di layar depan.
  - `SYSTEM_ALERT_WINDOW`: Menampilkan Floating Control dinamis dan overlay perekaman sentuhan.
  - `<queries>` pada `AndroidManifest.xml`: Membaca daftar aplikasi target pada Android 11 ke atas tanpa izin `QUERY_ALL_PACKAGES`.

---

## Cara Build & Install ke HP Android

### 1. Menggunakan Android Studio
1. Buka **Android Studio** (versi Koala, Ladybug, Meerkat, atau yang lebih baru).
2. Pilih **Open**, lalu arahkan ke folder project ini.
3. Tunggu **Gradle Sync** selesai sampai dependency Compose dan Room terunduh.
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

### Menjalankan Auto Scroll
1. Buka **Auto Scroll**, masuk ke **Pengaturan Izin** lewat ikon roda gigi di pojok kanan atas.
2. Aktifkan **Layanan Aksesibilitas** untuk Auto Scroll dan izin **Tampil di Atas Aplikasi Lain (Overlay)**.
   - *Catatan Android 13 ke atas (install manual `.apk`):* Bila tombol Aksesibilitas terkunci oleh *Restricted setting*, buka **Settings > Apps > Auto Scroll**, tap menu titik tiga di kanan atas, pilih **Allow restricted settings**, lalu aktifkan kembali layanan di menu Aksesibilitas.
3. Di tab **Auto Scroll**, pilih aplikasi target, interval, arah, kecepatan, serta durasi scroll.
4. Tekan **Mulai Auto Scroll**, lalu buka aplikasi target Anda.

### Merekam & Memutar Macro
1. Buka tab **Rekam**, lalu tekan **Mulai Rekam (Mode REC)**.
2. Pindah ke aplikasi target (misalnya Shopee), lakukan sentuhan atau scroll seperti biasa. Floating Control akan menghitung durasi dan jumlah gesture.
3. Setelah selesai, tekan **Stop Recording** pada Floating Control, beri nama macro, atur jumlah pengulangan bila perlu, lalu simpan ke **My Macros**.
4. Buka tab **My Macros**, pilih rekaman Anda, lalu tekan **Use Macro**.
5. Pindah ke aplikasi target, lalu tekan **Play** pada Floating Control untuk menjalankan seluruh urutan gerakan.

---

## Menjalankan Unit Test

Project ini menyertakan unit test JUnit4 di `app/src/test/java/com/autoscroll/app/ScrollConfigTest.kt` untuk menguji perhitungan interval, koordinat scroll, klasifikasi gesture macro, dan pengurutan aksi:

```bash
gradle testDebugUnitTest
```
