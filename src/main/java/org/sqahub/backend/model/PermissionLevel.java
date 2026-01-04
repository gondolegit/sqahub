package org.sqahub.backend.model;

/**
 * Enum yang mendefinisikan level hak akses dalam sebuah proyek.
 * OWNER: Punya semua hak (creator proyek).
 * ADMIN: Punya hak edit, view, delete, dan mengelola anggota.
 * CAN_EDIT: Dapat membuat/mengubah/menghapus fitur, test case, dll.
 * CAN_VIEW: Hanya dapat melihat data.
 */
public enum PermissionLevel {
    OWNER,
    ADMIN,
    CAN_EDIT,
    CAN_VIEW
}