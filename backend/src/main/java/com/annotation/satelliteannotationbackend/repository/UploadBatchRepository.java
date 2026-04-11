package com.annotation.satelliteannotationbackend.repository;

import com.annotation.satelliteannotationbackend.entity.UploadBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadBatchRepository extends JpaRepository<UploadBatch, Long> {
    Optional<UploadBatch> findByBatchUuid(String batchUuid);
    List<UploadBatch> findByUserId(Long userId);
}
