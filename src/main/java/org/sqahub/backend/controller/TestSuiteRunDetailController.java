package org.sqahub.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.service.TestSuiteRunDetailService;

import java.util.List;
import java.util.Optional;

/**
 * Controller untuk mengelola endpoint TestSuiteRunDetail (Metrik Tes).
 * Mapping utama: /api/v1/run-details
 * Menyediakan endpoint untuk POST, GET, PUT, dan DELETE.
 */
@RestController
@RequestMapping("/api/v1/run-details")
public class TestSuiteRunDetailController {

    @Autowired
    private TestSuiteRunDetailService runDetailService;

    /**
     * Endpoint [POST] untuk membuat Run Detail baru.
     * @param runDetail Data metrik tes.
     * @return Run Detail yang telah disimpan, termasuk ID-nya.
     */
    @PostMapping
    public ResponseEntity<TestSuiteRunDetail> createRunDetail(@RequestBody TestSuiteRunDetail runDetail) {
        TestSuiteRunDetail savedDetail = runDetailService.saveRunDetail(runDetail);
        // Mengembalikan CREATED status code (201)
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDetail);
    }

    /**
     * Endpoint [GET] untuk mendapatkan Run Detail berdasarkan ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TestSuiteRunDetail> getRunDetail(@PathVariable Long id) {
        Optional<TestSuiteRunDetail> detail = runDetailService.getRunDetailById(id);
        return detail.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Endpoint [GET] untuk mendapatkan semua Run Details.
     */
    @GetMapping
    public ResponseEntity<List<TestSuiteRunDetail>> getAllRunDetails() {
        return ResponseEntity.ok(runDetailService.getAllRunDetails());
    }

    /**
     * Endpoint [PUT] untuk memperbarui Run Detail yang sudah ada.
     */
    @PutMapping("/{id}")
    public ResponseEntity<TestSuiteRunDetail> updateRunDetail(@PathVariable Long id, @RequestBody TestSuiteRunDetail runDetail) {
        // Cek apakah entitas ada sebelum mencoba update
        if (runDetailService.getRunDetailById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Set ID dari yang sudah ada untuk memastikan update
        runDetail.setId(id);
        TestSuiteRunDetail updatedDetail = runDetailService.saveRunDetail(runDetail);
        return ResponseEntity.ok(updatedDetail);
    }

    /**
     * Endpoint [DELETE] untuk menghapus Run Detail berdasarkan ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRunDetail(@PathVariable Long id) {
        if (runDetailService.getRunDetailById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        runDetailService.deleteRunDetail(id);
        return ResponseEntity.noContent().build();
    }
}