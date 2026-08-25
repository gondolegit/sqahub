<div align="center">

  # SQAHub Backend (BE)

  <p>
    <b>RESTful API Core for Testing Management System (TMS)</b>
  </p>

  ![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
  ![Spring Boot](https://img.shields.io/badge/spring%20boot-%236DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white)
  ![MySQL](https://img.shields.io/badge/MySQL-%2300f.svg?style=for-the-badge&logo=mysql&logoColor=white)
  ![Maven](https://img.shields.io/badge/maven-%23C71A36.svg?style=for-the-badge&logo=apache-maven&logoColor=white)
  ![Docker](https://img.shields.io/badge/docker-%232496ED.svg?style=for-the-badge&logo=docker&logoColor=white)

  <br />
  <br />

  <p align="center">
    Repository ini berisi kode sumber Backend SQAHub, yang bertanggung jawab untuk semua logika bisnis, keamanan, dan persistensi data.
    <br />
    Ia menyediakan layanan RESTful API untuk dikonsumsi oleh aplikasi <strong>Frontend React</strong> (<code>sqahub-fe</code>).
  </p>
</div>

---

## 💡 Arsitektur & Teknologi

* **Framework:** Spring Boot 3.5, Java 21
* **Database:** MySQL 8 (produksi), H2 in-memory (profil `test`, tidak butuh setup apapun)
* **Keamanan:** Spring Security - JWT (login manual) + Google OAuth2 (opsional), API Key untuk integrasi eksternal (Katalon/Jenkins), rate limiting anti brute-force, token blacklist untuk logout
* **ORM:** Spring Data JPA / Hibernate
* **Build Tool:** Maven
* **Dokumentasi API:** springdoc-openapi (Swagger UI)
* **Observability:** Spring Boot Actuator
* **Deploy:** Docker multi-stage build + docker-compose, GitHub Actions CI

## ⚙️ Fitur API Utama

* **Autentikasi:** Register, login (JWT), logout (blacklist token), forgot/reset password (email via Gmail SMTP), login Google (OAuth2, opsional).
* **Project & Project Member:** CRUD proyek, manajemen anggota + level akses (OWNER/ADMIN/CAN_EDIT/CAN_VIEW).
* **Feature & Test Case:** CRUD hierarkis Project → Feature → Test Case.
* **Test Suite Run:** Mencatat eksekusi test (ringkasan + detail per test case), rekalkulasi status otomatis.
* **Test Evidence:** Upload file bukti tes fisik (screenshot/log/video) atau metadata + URL eksternal.
* **API Key:** Kunci API untuk integrasi automation eksternal (hash SHA-256, expiry, revoke).
* **Activity Log:** Audit trail semua aksi penting (khusus ADMIN).
* **Deploy Decision Engine:** Evaluasi otomatis kelayakan deploy dari pass rate Test Suite vs ambang batas.
* **Export Excel:** Laporan Test Suite (ringkasan + detail) sebagai file `.xlsx`.

## 📦 Prasyarat & Instalasi Lokal

### Opsi A: Docker (paling cepat)

```bash
cp .env.example .env
# isi minimal JWT_SECRET (wajib) di .env, lihat komentar di dalamnya
docker compose up --build
```

Aplikasi + MySQL akan jalan otomatis, lihat `docker-compose.yml` untuk detail environment variable.

### Opsi B: Manual

**Prasyarat:** JDK 21, Maven, MySQL 8 (atau lewati DB sepenuhnya - lihat bagian Testing di bawah).

1. Buat database `sqahub_db` di MySQL lokal Anda (kredensial default: `root` tanpa password, lihat `application.properties` - override lewat env var `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` kalau beda).
2. Jalankan:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

Aplikasi berjalan di `http://localhost:8080`.

### Environment variable penting

| Variable | Wajib? | Keterangan |
|---|---|---|
| `JWT_SECRET` | Disarankan (ada default dev) | Kunci penandatanganan JWT. **Wajib diganti untuk produksi** - default di repo sudah tercatat di git history. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Tidak (ada default) | Override koneksi MySQL. |
| `CORS_ALLOWED_ORIGINS` | Tidak | Daftar origin frontend, dipisah koma. |
| `MAIL_USERNAME`, `MAIL_APP_PASSWORD` | Untuk fitur forgot-password | Gmail App Password (bukan password akun biasa), lihat `application.properties` bagian EMAIL. |
| `APP_OAUTH2_GOOGLE_CLIENT_ID`, `APP_OAUTH2_GOOGLE_CLIENT_SECRET` | Untuk fitur login Google | Dari Google Cloud Console. Redirect URI: `http://localhost:8080/login/oauth2/code/google`. Tanpa ini, fitur nonaktif otomatis (aplikasi tetap jalan normal). |
| `DEPLOY_PASS_RATE_THRESHOLD` | Tidak (default 95.0) | Ambang batas pass rate (%) untuk Deploy Decision Engine. |
| `EVIDENCE_STORAGE_DIR` | Tidak (default `./evidence-storage`) | Direktori penyimpanan file upload Test Evidence. |

## 🧪 Testing

```bash
mvn test
```

Seluruh test suite (unit + integrasi, termasuk satu suite end-to-end yang menembak SEMUA
endpoint lewat HTTP sungguhan seperti koleksi Postman) berjalan memakai profil `test` (H2
in-memory) - **tidak butuh MySQL sama sekali**.

Laporan HTML pass/fail/error per test:
```bash
mvn surefire-report:report-only
# buka target/reports/surefire.html
```

## 🔗 Dokumentasi API

* **Swagger UI (interaktif):** `http://localhost:8080/swagger-ui.html`
* **OpenAPI spec (raw JSON):** `http://localhost:8080/v3/api-docs`
* **Health check:** `http://localhost:8080/actuator/health`

## 🔑 Security & CORS

* JWT di header `Authorization: Bearer <token>`. Registrasi publik tidak bisa memilih role ADMIN (dicegah di server).
* Rate limiting di `/auth/authenticate` dan `/auth/register` (default 10 percobaan/menit per IP).
* Logout membuat token langsung tidak valid (blacklist), walau masa berlakunya belum habis.
* CORS dikonfigurasi lewat `app.cors.allowed-origins` / env var `CORS_ALLOWED_ORIGINS`.

---
Dibuat dengan 💻 oleh Tim SQAHub Backend.
