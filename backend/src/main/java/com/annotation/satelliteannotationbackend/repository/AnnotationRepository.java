package com.annotation.satelliteannotationbackend.repository;

import com.annotation.satelliteannotationbackend.entity.Annotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnotationRepository extends JpaRepository<Annotation, Long> {
    List<Annotation> findByUserId(Long userId);
    List<Annotation> findByProjectId(Long projectId);
    long countByProjectId(Long projectId);
}
