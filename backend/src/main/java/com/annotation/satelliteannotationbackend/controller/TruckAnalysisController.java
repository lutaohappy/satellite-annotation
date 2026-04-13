package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.AnalysisResultDTO;
import com.annotation.satelliteannotationbackend.dto.ApiResponse;
import com.annotation.satelliteannotationbackend.dto.TruckAnalysisRequestDTO;
import com.annotation.satelliteannotationbackend.entity.TruckAnalysisRequest;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.service.TruckAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 货车通过性分析控制器
 */
@RestController
@RequestMapping("/api/truck-analysis")
@CrossOrigin(origins = "*")
public class TruckAnalysisController {

    private final TruckAnalysisService analysisService;

    public TruckAnalysisController(TruckAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 执行通过性分析
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(
            @RequestBody TruckAnalysisRequestDTO request,
            @AuthenticationPrincipal User currentUser) {
        try {
            AnalysisResultDTO result = analysisService.analyze(request, currentUser);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("分析失败：" + e.getMessage()));
        }
    }

    /**
     * 获取分析历史
     */
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(@AuthenticationPrincipal User currentUser) {
        try {
            List<TruckAnalysisRequest> history = analysisService.getHistory(currentUser.getId());
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("获取历史记录失败：" + e.getMessage()));
        }
    }

    /**
     * 获取分析详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAnalysis(@PathVariable Long id) {
        // 实际应用中需要添加权限检查
        return ResponseEntity.ok(ApiResponse.success(analysisService.getHistory(null)));
    }
}
