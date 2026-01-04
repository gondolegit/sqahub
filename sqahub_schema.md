# Final Database Schema for **SQAHUB.ORG**

Dokumen ini berisi skema database final untuk aplikasi **SQAHUB.ORG**, diformat ulang agar rapi, konsisten, dan valid sebagai file Markdown (`.md`).

---

## 1. `user` — Manajemen Pengguna

| Field Name | Data Type | Length | Constraint / Index                  | Notes                                     |
| ---------- | --------- | ------ | ----------------------------------- | ----------------------------------------- |
| id         | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT         | Identifier unik pengguna                  |
| username   | VARCHAR   | 100    | UNIQUE, NOT NULL                    | Nama pengguna untuk login                 |
| password   | VARCHAR   | 255    | NOT NULL                            | Password yang sudah di-hash               |
| email      | VARCHAR   | 255    | UNIQUE, NOT NULL                    | Alamat email                              |
| name       | VARCHAR   | 255    | NOT NULL                            | Nama lengkap                              |
| role       | VARCHAR   | 50     | NOT NULL                            | Peran pengguna (admin, tester, developer) |
| status     | VARCHAR   | 50     | NOT NULL                            | Status akun (active, inactive, suspended) |
| created_at | TIMESTAMP | -      | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Waktu pembuatan record                    |
| updated_at | TIMESTAMP | -      | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Waktu pembaruan record terakhir           |

---

## 2. `project` — Manajemen Proyek

| Field Name  | Data Type | Length | Constraint / Index              | Notes                                     |
| ----------- | --------- | ------ | ------------------------------- | ----------------------------------------- |
| id          | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT     | Identifier unik proyek                    |
| name        | VARCHAR   | 255    | NOT NULL                        | Nama proyek                               |
| description | TEXT      | -      | -                               | Deskripsi detail proyek                   |
| type        | VARCHAR   | 50     | NOT NULL                        | Tipe proyek (web, mobile, API)            |
| status      | VARCHAR   | 50     | NOT NULL                        | Status proyek (active, on hold, archived) |
| created_by  | BIGINT    | -      | FOREIGN KEY → user.id, NOT NULL | User pembuat proyek                       |
| created_at  | TIMESTAMP | -      | NOT NULL                        | Waktu pembuatan                           |
| updated_at  | TIMESTAMP | -      | NOT NULL                        | Waktu pembaruan                           |

---

## 3. `feature` — Manajemen Fitur

| Field Name  | Data Type | Length | Constraint / Index                 | Notes                         |
| ----------- | --------- | ------ | ---------------------------------- | ----------------------------- |
| id          | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT        | Identifier unik fitur         |
| id_project  | BIGINT    | -      | FOREIGN KEY → project.id, NOT NULL | Proyek induk                  |
| name        | VARCHAR   | 255    | NOT NULL                           | Nama fitur                    |
| type        | VARCHAR   | 50     | -                                  | Tipe fitur (new, enhancement) |
| description | TEXT      | -      | -                                  | Deskripsi fitur               |
| status      | VARCHAR   | 50     | NOT NULL                           | Status fitur                  |
| created_by  | BIGINT    | -      | FOREIGN KEY → user.id, NOT NULL    | User pembuat fitur            |
| created_at  | TIMESTAMP | -      | NOT NULL                           | Waktu pembuatan               |
| updated_at  | TIMESTAMP | -      | NOT NULL                           | Waktu pembaruan               |

---

## 4. `test_case` — Definisi Kasus Uji

| Field Name      | Data Type | Length | Constraint / Index                 | Notes                      |
| --------------- | --------- | ------ | ---------------------------------- | -------------------------- |
| id              | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT        | Identifier unik test case  |
| id_feature      | BIGINT    | -      | FOREIGN KEY → feature.id, NOT NULL | Fitur yang diuji           |
| name            | VARCHAR   | 255    | NOT NULL                           | Judul test case            |
| description     | TEXT      | -      | -                                  | Deskripsi singkat          |
| type            | VARCHAR   | 50     | NOT NULL                           | Jenis pengujian            |
| tag             | VARCHAR   | 255    | -                                  | Tag (P1, Smoke, Login)     |
| pre_condition   | TEXT      | -      | -                                  | Prasyarat sebelum eksekusi |
| test_steps      | TEXT      | -      | NOT NULL                           | Langkah pengujian          |
| test_data       | TEXT      | -      | -                                  | Data uji (JSON/string)     |
| post_condition  | TEXT      | -      | -                                  | Kondisi setelah eksekusi   |
| expected_result | TEXT      | -      | NOT NULL                           | Hasil yang diharapkan      |
| created_by      | BIGINT    | -      | FOREIGN KEY → user.id, NOT NULL    | Pembuat test case          |
| created_at      | TIMESTAMP | -      | NOT NULL                           | Waktu pembuatan            |
| updated_at      | TIMESTAMP | -      | NOT NULL                           | Waktu pembaruan            |

---

## 5. `test_suite` — Ringkasan Eksekusi

| Field Name           | Data Type | Length | Constraint / Index              | Notes                 |
| -------------------- | --------- | ------ | ------------------------------- | --------------------- |
| id                   | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT     | Identifier test suite |
| name                 | VARCHAR   | 255    | NOT NULL                        | Nama test suite       |
| description          | TEXT      | -      | -                               | Deskripsi             |
| tag                  | VARCHAR   | 255    | -                               | Tag suite             |
| test_stage           | VARCHAR   | 50     | NOT NULL                        | Tahap pengujian       |
| test_environment     | VARCHAR   | 100    | NOT NULL                        | Environment pengujian |
| hostname             | VARCHAR   | 255    | -                               | Host eksekusi         |
| os                   | VARCHAR   | 100    | -                               | Sistem operasi        |
| version              | VARCHAR   | 50     | -                               | Versi aplikasi/tools  |
| browser              | VARCHAR   | 100    | -                               | Browser               |
| status_total_passed  | INT       | -      | NOT NULL, DEFAULT 0             | Total passed          |
| status_total_failed  | INT       | -      | NOT NULL, DEFAULT 0             | Total failed          |
| status_total_error   | INT       | -      | NOT NULL, DEFAULT 0             | Total error           |
| status_total_skipped | INT       | -      | NOT NULL, DEFAULT 0             | Total skipped         |
| start_date           | TIMESTAMP | -      | NOT NULL                        | Waktu mulai           |
| end_date             | TIMESTAMP | -      | -                               | Waktu selesai         |
| elapsed_time         | BIGINT    | -      | NOT NULL, DEFAULT 0             | Durasi (ms)           |
| executed_by          | BIGINT    | -      | FOREIGN KEY → user.id, NOT NULL | Executor              |
| created_by           | BIGINT    | -      | FOREIGN KEY → user.id, NOT NULL | Pembuat suite         |
| created_at           | TIMESTAMP | -      | NOT NULL                        | Waktu pembuatan       |
| updated_at           | TIMESTAMP | -      | NOT NULL                        | Waktu pembaruan       |

---

## 6. `test_suite_run_detail` — Detail Hasil Eksekusi

| Field Name    | Data Type | Length | Constraint / Index                    | Notes                             |
| ------------- | --------- | ------ | ------------------------------------- | --------------------------------- |
| id            | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT           | Identifier detail                 |
| id_test_suite | BIGINT    | -      | FOREIGN KEY → test_suite.id, NOT NULL | Test suite                        |
| id_test_case  | BIGINT    | -      | FOREIGN KEY → test_case.id, NOT NULL  | Test case                         |
| status        | VARCHAR   | 50     | NOT NULL                              | PASSED / FAILED / ERROR / SKIPPED |
| actual_result | TEXT      | -      | -                                     | Hasil aktual                      |
| remarks       | TEXT      | -      | -                                     | Catatan / log                     |
| start_date    | TIMESTAMP | -      | NOT NULL                              | Mulai eksekusi                    |
| end_date      | TIMESTAMP | -      | -                                     | Selesai eksekusi                  |
| elapsed_time  | INT       | -      | NOT NULL, DEFAULT 0                   | Durasi                            |
| executed_by   | BIGINT    | -      | FOREIGN KEY → user.id, NOT NULL       | Executor                          |
| created_at    | TIMESTAMP | -      | NOT NULL                              | Waktu pembuatan                   |
| updated_at    | TIMESTAMP | -      | NOT NULL                              | Waktu pembaruan                   |

**Index Tambahan:** `INDEX (id_test_suite, id_test_case)`

---

## 7. `test_evidence` — Bukti Pengujian

| Field Name    | Data Type | Length | Constraint / Index                               | Notes            |
| ------------- | --------- | ------ | ------------------------------------------------ | ---------------- |
| id            | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT                      | Identifier bukti |
| id_run_detail | BIGINT    | -      | FOREIGN KEY → test_suite_run_detail.id, NOT NULL | Detail eksekusi  |
| file_path_url | VARCHAR   | 1024   | NOT NULL                                         | Path / URL file  |
| mime_type     | VARCHAR   | 100    | -                                                | Tipe file        |
| description   | VARCHAR   | 255    | -                                                | Deskripsi bukti  |
| step_number   | INT       | -      | -                                                | Step ke berapa   |
| uploaded_at   | TIMESTAMP | -      | NOT NULL, DEFAULT CURRENT_TIMESTAMP              | Waktu upload     |

---

## 8. `api_key` — Integrasi Eksternal & Otomatisasi

| Field Name   | Data Type | Length | Constraint / Index              | Notes              |
| ------------ | --------- | ------ | ------------------------------- | ------------------ |
| id           | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT     | Identifier API key |
| id_user      | BIGINT    | -      | FOREIGN KEY → user.id, NOT NULL | Pemilik key        |
| key_hash     | VARCHAR   | 255    | UNIQUE, NOT NULL                | Hash key           |
| name         | VARCHAR   | 255    | NOT NULL                        | Nama key           |
| status       | VARCHAR   | 50     | NOT NULL                        | active / revoked   |
| expires_at   | TIMESTAMP | -      | -                               | Kadaluarsa         |
| last_used_at | TIMESTAMP | -      | -                               | Terakhir digunakan |
| revoked_by   | BIGINT    | -      | FOREIGN KEY → user.id           | User pencabut      |
| created_at   | TIMESTAMP | -      | NOT NULL                        | Waktu pembuatan    |
| updated_at   | TIMESTAMP | -      | NOT NULL                        | Waktu pembaruan    |

---

## 9. `activity_log` — Audit Sistem & Keamanan

| Field Name  | Data Type | Length | Constraint / Index                  | Notes            |
| ----------- | --------- | ------ | ----------------------------------- | ---------------- |
| id          | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT         | Identifier log   |
| id_user     | BIGINT    | -      | FOREIGN KEY → user.id               | User pelaku      |
| action      | VARCHAR   | 100    | NOT NULL                            | Jenis aksi       |
| entity_type | VARCHAR   | 100    | -                                   | Tipe entitas     |
| entity_id   | BIGINT    | -      | -                                   | ID entitas       |
| details     | JSON      | -      | -                                   | Detail perubahan |
| ip_address  | VARCHAR   | 50     | -                                   | Alamat IP        |
| created_at  | TIMESTAMP | -      | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Waktu kejadian   |

---

## 10. `project_members` — Manajemen Anggota Proyek

| Field Name       | Data Type | Length | Constraint / Index                  | Notes                                    |
| ---------------- | --------- | ------ | ----------------------------------- | ---------------------------------------- |
| id               | BIGINT    | -      | PRIMARY KEY, AUTO_INCREMENT         | Identifier                               |
| id_project       | BIGINT    | -      | FOREIGN KEY → project.id, NOT NULL  | Proyek                                   |
| id_user          | BIGINT    | -      | FOREIGN KEY → user.id, NOT NULL     | User                                     |
| permission_level | VARCHAR   | 50     | NOT NULL                            | CAN_VIEW / CAN_EDIT / CAN_DELETE / ADMIN |
| joined_at        | TIMESTAMP | -      | NOT NULL, DEFAULT CURRENT_TIMESTAMP | Waktu bergabung                          |
| created_at       | TIMESTAMP | -      | NOT NULL                            | Waktu pembuatan                          |
| updated_at       | TIMESTAMP | -      | NOT NULL                            | Waktu pembaruan                          |

**Index Tambahan:** `UNIQUE (id_project, id_user)`
