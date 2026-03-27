package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.ApiResponse;
import com.annotation.satelliteannotationbackend.entity.Symbol;
import com.annotation.satelliteannotationbackend.repository.SymbolRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 符号控制器
 */
@RestController
@RequestMapping("/api/symbols")
@CrossOrigin(origins = "*")
public class SymbolController {

    private final SymbolRepository symbolRepository;

    private static final String UPLOAD_DIR = "uploads/symbols/";

    public SymbolController(SymbolRepository symbolRepository) {
        this.symbolRepository = symbolRepository;
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        List<Symbol> symbols = symbolRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(symbols));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return symbolRepository.findById(id)
                .map(symbol -> ResponseEntity.ok(ApiResponse.success(symbol)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Symbol symbol) {
        Symbol saved = symbolRepository.save(symbol);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadSymbol(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("category") String category) throws IOException {

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".svg")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("只支持 SVG 文件"));
        }

        String filename = UUID.randomUUID() + "_" + originalFilename;
        Path path = Paths.get(UPLOAD_DIR + filename);
        Files.createDirectories(path.getParent());
        Files.write(path, file.getBytes());

        Symbol symbol = new Symbol();
        symbol.setName(name);
        symbol.setCategory(category);
        symbol.setFilePath(UPLOAD_DIR + filename);
        symbol.setContent(new String(file.getBytes()));
        symbol.setSize((int) file.getSize());

        Symbol saved = symbolRepository.save(symbol);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Symbol symbol) {
        return symbolRepository.findById(id)
                .map(existing -> {
                    existing.setName(symbol.getName());
                    existing.setCategory(symbol.getCategory());
                    if (symbol.getContent() != null) {
                        existing.setContent(symbol.getContent());
                    }
                    Symbol saved = symbolRepository.save(existing);
                    return ResponseEntity.ok(ApiResponse.success(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        symbolRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
