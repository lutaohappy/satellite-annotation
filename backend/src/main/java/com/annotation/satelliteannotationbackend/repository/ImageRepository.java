package com.annotation.satelliteannotationbackend.repository;

import com.annotation.satelliteannotationbackend.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
    List<Image> findByProjectId(Long projectId);
    List<Image> findByUserId(Long userId);
    List<Image> findByBatchId(String batchId);
}
