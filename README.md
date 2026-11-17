# termOS

Launcher bergaya terminal untuk HP Android: DeepSeek AI yang bisa mengambil data live,
kabar otomatis (gempa, cuaca, kurs, berita), cek macet, CCTV publik, grafik kripto,
toolkit keamanan (vault AES-256, PIN darurat, 2FA, scanner APK, database ancaman),
alat dokumen (PDF ke Word, dll), dan peluncur aplikasi.

Satu repo GitHub menghasilkan dua hal sekaligus, otomatis setiap kali kamu update:
- **Web app** di GitHub Pages (`https://USERNAME.github.io/NAMA-REPO/`)
- **APK launcher** di GitHub Releases (bisa jadi layar utama / tema bawaan)

termOS di HP mengecek versi baru sendiri dan menampilkan **PATCH NOTES** + tombol pasang.

---

## Pasang pertama kali (sekali saja, sekitar 10 menit, dari PC)

### 1. Buat repository
github.com > **+** > **New repository** > nama `termos` > pilih **Public** > **Create repository**.
(Public diperlukan supaya HP bisa mengunduh APK & cek update tanpa login.
Tidak ada data pribadimu di repo; semua data tersimpan di HP.)

### 2. Buat password kunci APK (WAJIB sebelum upload)
Repo > **Settings** > **Secrets and variables** > **Actions** > **New repository secret**
- Name: `KEYSTORE_PASSWORD`
- Secret: password acak yang panjang (mis. hasil perintah `passgen 32 -n` di termOS)

Kunci tanda tangan APK disimpan di repo dalam keadaan terkunci password ini.
Tanpa password ini, build ditolak demi keamanan.

### 3. Aktifkan GitHub Pages
Repo > **Settings** > **Pages** > *Source*: pilih **GitHub Actions**.

### 4. Upload semua file
Repo > **Add file** > **Upload files** > seret **seluruh isi** folder termos (termasuk folder
`.github` dan `android`) > **Commit changes**.

### 5. Tunggu 5-10 menit
Tab **Actions** > proses **Rilis termOS** sampai centang hijau. Hasilnya:
- web app aktif di `https://USERNAME.github.io/termos/`
- APK ada di halaman **Releases** (kanan repo) > `termOS-v3.3.0.apk`

### 6. Pasang di HP
- **APK (disarankan)**: buka halaman Releases di Chrome HP > ketuk `termOS-vX.apk` > buka file >
  izinkan "instal dari sumber ini" **khusus kali ini** > Instal. Kalau Play Protect bertanya,
  pilih *Detail lainnya > Tetap instal* (wajar untuk aplikasi buatan sendiri).
- **Web saja**: buka `https://USERNAME.github.io/termos/` di Chrome > menu titik tiga > **Instal aplikasi**.

### 7. Jadikan layar utama (tema bawaan, khusus APK)
Tekan tombol **Home** > pilih **termOS** > **Selalu**. Atau ketik `launcher` di termOS.
Mau kembali? Pengaturan > Aplikasi > Aplikasi default > Aplikasi layar utama.

---

## Update berikutnya
1. Ganti file yang berubah di repo (Add file > Upload files, timpa yang lama).
   Untuk versi baru, naikkan `version` di `version.json` dan tambahkan catatannya di `changelog`.
2. GitHub Actions otomatis menerbitkan web + APK baru.
3. Di HP muncul jendela **PATCH NOTES**:
   - web: tombol **MUAT ULANG SEKARANG**
   - APK: tombol **UNDUH & PASANG APK** (pasang di atas versi lama, data tetap aman)

Cek manual kapan saja: perintah `update`. Riwayat: `update log`.

> Catatan: kalau dulu kamu sudah pernah build dengan workflow versi lama (repo privat, tanpa secret),
> hapus file `android/termos.keystore` dari repo sebelum memakai secret, lalu uninstall APK lama sekali
> (backup dulu dengan perintah `backup`).

---

## Setelah terpasang
1. `pin set` - kunci termOS dengan PIN.
2. Beranda > **BUAT VAULT** - semua data pribadi & API key dienkripsi di sini.
3. Kartu **DEEPSEEK AI** > tempel API key dari platform.deepseek.com.
4. Kartu **PROTEKSI** > **UNDUH DB ANCAMAN** (sekali, ~7.6 MB).
5. `kota Bandung` (ganti kotamu), lalu izinkan lokasi saat diminta.
6. Opsional, key gratis:
   - **TomTom** (macet live): developer.tomtom.com > `macet key`
   - **Windy Webcams** (kamera publik live): api.windy.com/keys > `cctv key`

## Ngobrol dengan AI di terminal
- `chat` - mode kolaborasi. `!perintah` menjalankan perintah, `exit` keluar.
- `?pertanyaan` - tanya cepat. Kalimat biasa juga otomatis diteruskan ke AI.
- AI bisa mengambil data live (cuaca, gempa, kripto, kurs, berita, macet, grafik, dll)
  dan mengusulkan perintah dengan tombol **JALANKAN**.

## Sumber data
CoinGecko, Open-Meteo, BMKG, USGS, open.er-api.com, Wikipedia, Nager.Date, Antara & CNN Indonesia (RSS),
OSRM/Nominatim (OpenStreetMap), TomTom (opsional), Windy Webcams (opsional),
portal CCTV resmi (Jasa Marga, Bandung, Yogyakarta, Malang, Palembang),
dan 2.400+ API di [public-apis/public-apis](https://github.com/public-apis/public-apis).

## Privasi
Tidak ada server termOS. Semua data pribadi tersimpan di HP-mu (vault terenkripsi).
Yang keluar dari HP hanya permintaan ke sumber data di atas, pesan AI ke api.deepseek.com,
dan unduhan database ancaman / library / update dari GitHub dan jsDelivr.
CCTV: hanya kamera yang resmi dibuka untuk publik.
