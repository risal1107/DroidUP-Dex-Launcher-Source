# DroidUP Dex Launcher (Samsung DeX Edition)

Source code proyek Android untuk **`Payload/DroidUP-Dex-Launcher.apk`** yang telah dimodifikasi dengan antarmuka **Samsung DeX Taskbar One UI**.

## 🎨 Perubahan Tampilan Taskbar (Samsung DeX UI)
1. **Sisi Kiri**:
   - **Apps Drawer Button**: Ikon 9 titik (`:::`) khas Samsung DeX.
   - **Search Button**: Ikon pencarian (🔍) cepat.
   - **3 Tombol Navigasi**:
     - Recent Apps (`|||`)
     - Home (`○`)
     - Back (`<`)
2. **Sisi Tengah**:
   - Ikon aplikasi aktif/running dengan indikator baris aktif (*active indicator line*) putih halus di bagian bawah.
   - Long-click pada ikon untuk kontrol jendela (*Focus/Open, Maximize, Minimize, Close App*).
3. **Sisi Kanan (DeX System Tray)**:
   - Badge Bahasa `[ID]`
   - Ikon Quick Action: Keyboard (`⌨`), Screenshot (`📷`), Volume (`🔇`), Notifikasi (`💬`)
   - Status Cluster Pill: Wi-Fi (`📶`), Persentase Baterai Real-time (`🔋`), dan TextClock 2 baris (`HH:mm` dan `EEE, d/M`).
4. **Desain Fisik**:
   - Translucent Acrylic Dark Bar docked penuh sepanjang dasar layar.

---

## 🛠 Cara Build Menjadi APK

### Cara 1: Menggunakan Android Studio (Paling Mudah)
1. Buka software **Android Studio**.
2. Pilih **Open** -> Arahkan ke folder:
   ```
   c:\Users\risal\Downloads\DroidUP-Dex-v1.6-win-x64\DroidUP-Dex-Launcher-Source
   ```
3. Tunggu hingga Gradle selesai melakukan sinkronisasi.
4. Klik menu **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.
5. Setelah build sukses, file APK akan berada di:
   ```
   app/build/outputs/apk/release/app-release.apk
   (atau app/build/outputs/apk/debug/app-debug.apk)
   ```
6. Salin file APK tersebut ke folder Payload dan ganti nama menjadi:
   ```
   ..\Payload\DroidUP-Dex-Launcher.apk
   ```

### Cara 2: Menggunakan Terminal / Command Line (Gradle)
Jika Anda sudah memiliki JDK/Gradle:
```bash
cd DroidUP-Dex-Launcher-Source
gradlew assembleRelease
```
Lalu salin file `app-release.apk` atau `app-debug.apk` ke `..\Payload\DroidUP-Dex-Launcher.apk`.

---

## 🚀 Pengujian ke Ponsel
Saat Anda menjalankan `DroidUPDex.exe` dan menyambungkan ponsel, DroidUP Dex akan otomatis mendeteksi dan menginstal `DroidUP-Dex-Launcher.apk` terbaru ke ponsel Anda.
