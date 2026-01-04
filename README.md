<div align="center">

  # SQAHub Backend (BE)

  <p>
    <b>RESTful API Core for Testing Management System (TMS)</b>
  </p>

  ![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
  ![Spring Boot](https://img.shields.io/badge/spring%20boot-%236DB33F.svg?style=for-the-badge&logo=springboot&logoColor=white)
  ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-%23316192.svg?style=for-the-badge&logo=postgresql&logoColor=white)
  ![Maven](https://img.shields.io/badge/maven-%23C71A36.svg?style=for-the-badge&logo=apache-maven&logoColor=white)

  <br />
  <br />

  <p align="center">
    Repository ini berisi kode sumber Backend SQAHub, yang bertanggung jawab untuk semua logika bisnis, keamanan, dan persistensi data.
    <br />
    Ia menyediakan layanan RESTful API untuk dikonsumsi oleh aplikasi <strong>Frontend React</strong>.
  </p>
</div>

---

## 💡 Arsitektur & Teknologi

* **Framework:** Spring Boot 3 (Java)
* **Database:** PostgreSQL / MySQL (Pilih salah satu)
* **Keamanan:** Spring Security (JWT-based Authentication)
* **ORM:** Spring Data JPA / Hibernate
* **Build Tool:** Maven

## ⚙️ Fitur API Utama

* **API Authentication:** Endpoint untuk Login dan Register (JWT Token Generation).
* **User Management:** CRUD untuk mengelola pengguna dan peran (Roles).
* **Test Case CRUD:** Endpoint untuk membuat, membaca, memperbarui, dan menghapus test case.
* **Test Execution:** Logika untuk mencatat hasil test run.
* **Reporting:** API untuk menghasilkan laporan dan statistik pengujian.

## 📦 Prasyarat & Instalasi Lokal

### 1. Prasyarat
Pastikan Anda telah menginstal:
* Java Development Kit (JDK) 17 atau yang lebih baru.
* Maven.
* Database Server (PostgreSQL/MySQL).

### 2. Konfigurasi Database

1.  Buat database baru (misalnya, `sqahub_db`).
2.  Edit file `src/main/resources/application.properties` (atau `application.yml`) dan sesuaikan kredensial database Anda:

    ```properties
    spring.datasource.url=jdbc:postgresql://localhost:5432/sqahub_db
    spring.datasource.username=user_anda
    spring.datasource.password=password_anda
    # Konfigurasi JPA
    spring.jpa.hibernate.ddl-auto=update
    ```

### 3. Menjalankan Aplikasi

1.  **Clone repository ini:**
    ```bash
    git clone [https://github.com/username-anda/sqahub-be.git](https://github.com/username-anda/sqahub-be.git)
    cd sqahub-be
    ```
2.  **Compile dan Build menggunakan Maven:**
    ```bash
    mvn clean install
    ```
3.  **Jalankan aplikasi:**
    ```bash
    mvn spring-boot:run
    ```

Aplikasi akan berjalan di `http://localhost:8080` (default).

## 🔗 Dokumentasi API

Endpoint API dapat diakses melalui:
* **Swagger/OpenAPI:** `http://localhost:8080/swagger-ui.html` (Jika diaktifkan)

## 🔑 Security & CORS

* API ini diamankan menggunakan **JSON Web Tokens (JWT)**. Token harus disertakan dalam header `Authorization: Bearer <token>`.
* CORS diaktifkan secara global (atau spesifik) untuk mengizinkan *request* dari Frontend React (misalnya `http://localhost:5173`).

---
Dibuat dengan 💻 oleh Tim SQAHub Backend.
