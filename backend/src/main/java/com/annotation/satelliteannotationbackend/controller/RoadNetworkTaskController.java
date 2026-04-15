package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.ApiResponse;
import com.annotation.satelliteannotationbackend.dto.DownloadNetworkRequest;
import com.annotation.satelliteannotationbackend.dto.RoadNetworkTaskDTO;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.service.RoadNetworkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 路网下载任务控制器
 */
@RestController
@RequestMapping("/api/road-network-tasks")
@CrossOrigin(origins = "*")
public class RoadNetworkTaskController {

    private final RoadNetworkService networkService;

    public RoadNetworkTaskController(RoadNetworkService networkService) {
        this.networkService = networkService;
    }

    /**
     * 创建下载任务
     */
    @PostMapping
    public ResponseEntity<?> createTask(
            @RequestBody DownloadNetworkRequest request,
            @AuthenticationPrincipal User currentUser) {
        try {
            // 创建任务
            var task = networkService.createDownloadTask(request, currentUser);

            // 异步执行任务
            networkService.executeDownloadTask(task.getId());

            return ResponseEntity.ok(ApiResponse.success(RoadNetworkTaskDTO.fromEntity(task)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("创建任务失败：" + e.getMessage()));
        }
    }

    /**
     * 获取用户任务列表
     */
    @GetMapping
    public ResponseEntity<?> listTasks(@AuthenticationPrincipal User currentUser) {
        try {
            List<RoadNetworkTaskDTO> tasks = networkService.getUserTasks(currentUser.getId()).stream()
                .map(RoadNetworkTaskDTO::fromEntity)
                .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success(tasks));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("获取任务列表失败：" + e.getMessage()));
        }
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTask(@PathVariable Long id) {
        try {
            var task = networkService.getTask(id);
            if (task == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ApiResponse.success(RoadNetworkTaskDTO.fromEntity(task)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("获取任务详情失败：" + e.getMessage()));
        }
    }

    /**
     * 重试失败任务
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryTask(@PathVariable Long id) {
        try {
            networkService.retryTask(id);
            return ResponseEntity.ok(ApiResponse.success("任务已重新执行"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("重试任务失败：" + e.getMessage()));
        }
    }

    /**
     * 取消任务
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelTask(@PathVariable Long id) {
        try {
            networkService.cancelTask(id);
            return ResponseEntity.ok(ApiResponse.success("任务已取消"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("取消任务失败：" + e.getMessage()));
        }
    }
}
