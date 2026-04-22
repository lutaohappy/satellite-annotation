package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.*;
import com.annotation.satelliteannotationbackend.entity.TruckAnalysisRequest;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.service.TruckAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            System.out.println("[TruckAnalysisController] 收到分析请求：" + request.getRequestName());
            System.out.println("[TruckAnalysisController] 起点：" + request.getStartLat() + "," + request.getStartLon());
            System.out.println("[TruckAnalysisController] 终点：" + request.getEndLat() + "," + request.getEndLon());
            System.out.println("[TruckAnalysisController] 路网 ID: " + request.getRoadNetworkId());

            if (request.getTruck() != null) {
                System.out.println("[TruckAnalysisController] 货车参数：长=" + request.getTruck().getLength() +
                    "m, 宽=" + request.getTruck().getWidth() +
                    "m, 高=" + request.getTruck().getHeight() +
                    "m, 重=" + request.getTruck().getWeight() +
                    "t, 轴距=" + request.getTruck().getWheelbase() + "m");
            }

            AnalysisResultDTO result = analysisService.analyze(request, currentUser);
            System.out.println("[TruckAnalysisController] 分析完成，可通过：" + result.getIsPassable() +
                ", 违规点数量：" + (result.getViolations() != null ? result.getViolations().size() : 0));
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            System.err.println("[TruckAnalysisController] 分析失败：" + e.getMessage());
            e.printStackTrace();
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
        try {
            TruckAnalysisRequest request = analysisService.getById(id);
            if (request == null) {
                return ResponseEntity.notFound().build();
            }

            // 构建分析结果 DTO
            AnalysisResultDTO result = new AnalysisResultDTO();
            result.setIsPassable(request.getIsPassable());
            result.setRouteGeoJson(request.getRouteGeoJson());

            // 解析禁行点 JSON
            ObjectMapper mapper = new ObjectMapper();
            try {
                List<ViolationPointDTO> violations = mapper.readValue(
                    request.getViolationPoints(),
                    mapper.getTypeFactory().constructCollectionType(List.class, ViolationPointDTO.class)
                );
                result.setViolations(violations);
            } catch (Exception e) {
                result.setViolations(List.of());
            }

            // 解析转弯点 JSON
            try {
                List<TurnPointDTO> turnPoints = mapper.readValue(
                    request.getTurnPoints(),
                    mapper.getTypeFactory().constructCollectionType(List.class, TurnPointDTO.class)
                );
                result.setTurnPoints(turnPoints);
            } catch (Exception e) {
                result.setTurnPoints(List.of());
            }

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("获取分析详情失败：" + e.getMessage()));
        }
    }

    /**
     * 保存分析结果为 GeoJSON
     */
    @PostMapping("/save-geojson")
    public ResponseEntity<?> saveAnalysis(
            @RequestBody SavedAnalysisRequestDTO request,
            @AuthenticationPrincipal User currentUser) {
        try {
            System.out.println("[TruckAnalysisController] 保存分析结果：" + request.getName());

            TruckAnalysisRequest saved = analysisService.saveAnalysis(request, currentUser);
            SavedAnalysisDTO result = SavedAnalysisDTO.fromEntity(saved);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            System.err.println("[TruckAnalysisController] 保存失败：" + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("保存失败：" + e.getMessage()));
        }
    }

    /**
     * 获取已保存的分析列表
     */
    @GetMapping("/saved-list")
    public ResponseEntity<?> getSavedList(@AuthenticationPrincipal User currentUser) {
        try {
            List<SavedAnalysisDTO> savedList = analysisService.getSavedList(currentUser.getId());
            return ResponseEntity.ok(ApiResponse.success(savedList));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("获取列表失败：" + e.getMessage()));
        }
    }

    /**
     * 获取已保存的分析详情
     */
    @GetMapping("/saved/{id}")
    public ResponseEntity<?> getSavedAnalysis(@PathVariable Long id) {
        try {
            SavedAnalysisDTO result = analysisService.getSavedAnalysis(id);
            if (result == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("获取详情失败：" + e.getMessage()));
        }
    }

    /**
     * 保存分析记录
     */
    @PostMapping("/save-record")
    public ResponseEntity<?> saveRecord(
            @RequestBody SavedAnalysisRequestDTO request,
            @AuthenticationPrincipal User currentUser) {
        try {
            System.out.println("[TruckAnalysisController] 保存分析记录：" + request.getName());

            TruckAnalysisRequest saved = analysisService.saveAnalysis(request, currentUser);
            SavedAnalysisDTO result = SavedAnalysisDTO.fromEntity(saved);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            System.err.println("[TruckAnalysisController] 保存失败：" + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("保存失败：" + e.getMessage()));
        }
    }

    /**
     * 删除已保存的分析
     */
    @DeleteMapping("/geojson/{id}")
    public ResponseEntity<?> deleteAnalysis(@PathVariable Long id) {
        try {
            analysisService.deleteAnalysis(id);
            return ResponseEntity.ok(ApiResponse.success("删除成功"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("删除失败：" + e.getMessage()));
        }
    }
}
