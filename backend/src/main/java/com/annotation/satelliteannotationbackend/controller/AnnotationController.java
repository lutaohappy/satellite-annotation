package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.ApiResponse;
import com.annotation.satelliteannotationbackend.entity.Annotation;
import com.annotation.satelliteannotationbackend.repository.AnnotationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标注控制器
 */
@RestController
@RequestMapping("/api/annotations")
@CrossOrigin(origins = "*")
public class AnnotationController {

    private final AnnotationRepository annotationRepository;

    public AnnotationController(AnnotationRepository annotationRepository) {
        this.annotationRepository = annotationRepository;
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        List<Annotation> annotations = annotationRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(annotations));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return annotationRepository.findById(id)
                .map(annotation -> ResponseEntity.ok(ApiResponse.success(annotation)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Annotation annotation) {
        Annotation saved = annotationRepository.save(annotation);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Annotation annotation) {
        return annotationRepository.findById(id)
                .map(existing -> {
                    existing.setType(annotation.getType());
                    existing.setGeometry(annotation.getGeometry());
                    existing.setProperties(annotation.getProperties());
                    existing.setCategory(annotation.getCategory());
                    existing.setSymbolId(annotation.getSymbolId());
                    Annotation saved = annotationRepository.save(existing);
                    return ResponseEntity.ok(ApiResponse.success(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        annotationRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
