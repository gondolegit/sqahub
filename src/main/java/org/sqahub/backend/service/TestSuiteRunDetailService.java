package org.sqahub.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.sqahub.backend.model.TestSuiteRunDetail;
import org.sqahub.backend.repository.TestSuiteRunDetailRepository;

import java.util.List;
import java.util.Optional;

/**
 * Layanan untuk mengelola data eksekusi Test Suite (Run Detail).
 * Menyediakan operasi CRUD dasar: save, getById, getAll, dan delete.
 */
@Service
public class TestSuiteRunDetailService {

    @Autowired
    private TestSuiteRunDetailRepository runDetailRepository;

    /**
     * Membuat atau memperbarui detail eksekusi tes.
     * Digunakan oleh POST (buat baru) dan PUT (update).
     * @param runDetail Objek detail run yang akan disimpan.
     * @return Run Detail yang telah disimpan (dengan ID terisi).
     */
    public TestSuiteRunDetail saveRunDetail(TestSuiteRunDetail runDetail) {
        return runDetailRepository.save(runDetail);
    }

    /**
     * Mendapatkan Run Detail berdasarkan ID.
     */
    public Optional<TestSuiteRunDetail> getRunDetailById(Long id) {
        return runDetailRepository.findById(id);
    }

    /**
     * Mendapatkan semua Run Details.
     * @return Daftar semua Run Details.
     */
    public List<TestSuiteRunDetail> getAllRunDetails() {
        return runDetailRepository.findAll();
    }

    /**
     * Menghapus Run Detail berdasarkan ID.
     */
    public void deleteRunDetail(Long id) {
        runDetailRepository.deleteById(id);
    }
}