package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.ApiResponse;
import com.annotation.satelliteannotationbackend.dto.DownloadNetworkRequest;
import com.annotation.satelliteannotationbackend.dto.RoadNetworkDTO;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.security.JwtAuthenticationFilter;
import com.annotation.satelliteannotationbackend.service.RoadNetworkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 路网数据管理控制器
 */
@RestController
@RequestMapping("/api/road-networks")
@CrossOrigin(origins = "*")
public class RoadNetworkController {

    private final RoadNetworkService networkService;

    public RoadNetworkController(RoadNetworkService networkService) {
        this.networkService = networkService;
    }

    /**
     * 下载指定区域的路网数据
     */
    @PostMapping("/download")
    public ResponseEntity<?> downloadNetwork(
            @RequestBody DownloadNetworkRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            RoadNetworkDTO result = networkService.downloadNetwork(request, currentUser);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("下载路网数据失败：" + e.getMessage()));
        }
    }

    /**
     * 获取路网列表
     */
    @GetMapping
    public ResponseEntity<?> listNetworks() {
        try {
            List<RoadNetworkDTO> networks = networkService.findAll();
            return ResponseEntity.ok(ApiResponse.success(networks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("获取路网列表失败：" + e.getMessage()));
        }
    }

    /**
     * 获取路网详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getNetwork(@PathVariable Long id) {
        RoadNetworkDTO network = networkService.findById(id);
        if (network == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(network));
    }

    /**
     * 按区域查询路网
     */
    @GetMapping("/region/{region}")
    public ResponseEntity<?> listByRegion(@PathVariable String region) {
        try {
            List<RoadNetworkDTO> networks = networkService.findByRegion(region);
            return ResponseEntity.ok(ApiResponse.success(networks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("查询失败：" + e.getMessage()));
        }
    }

    /**
     * 删除路网
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNetwork(@PathVariable Long id) {
        try {
            networkService.delete(id);
            return ResponseEntity.ok(ApiResponse.success("删除成功"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("删除失败：" + e.getMessage()));
        }
    }
}
