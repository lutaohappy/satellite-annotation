package com.annotation.satelliteannotationbackend.service;

import com.annotation.satelliteannotationbackend.dto.DownloadNetworkRequest;
import com.annotation.satelliteannotationbackend.dto.RoadNetworkDTO;
import com.annotation.satelliteannotationbackend.entity.RoadNetwork;
import com.annotation.satelliteannotationbackend.entity.RoadNetworkDownloadTask;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.enums.TaskStatus;
import com.annotation.satelliteannotationbackend.repository.RoadNetworkRepository;
import com.annotation.satelliteannotationbackend.repository.RoadNetworkTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 路网数据服务
 */
@Service
public class RoadNetworkService {

    private final RoadNetworkRepository networkRepository;
    private final OverpassService overpassService;
    private final RoadNetworkTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public RoadNetworkService(
            RoadNetworkRepository networkRepository,
            OverpassService overpassService,
            RoadNetworkTaskRepository taskRepository) {
        this.networkRepository = networkRepository;
        this.overpassService = overpassService;
        this.taskRepository = taskRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 下载并保存路网数据
     */
    @Transactional
    public RoadNetworkDTO downloadNetwork(DownloadNetworkRequest request, User currentUser) {
        try {
            // 1. 调用 Overpass API 下载数据
            String geojsonPath = overpassService.downloadRoadNetwork(request);

            // 2. 创建路网记录
            RoadNetwork network = new RoadNetwork();
            network.setName(request.getName());
            network.setRegion(request.getRegion());
            network.setMinLat(request.getMinLat());
            network.setMinLon(request.getMinLon());
            network.setMaxLat(request.getMaxLat());
            network.setMaxLon(request.getMaxLon());
            network.setGeojsonPath(geojsonPath);
            network.setDownloadDate(LocalDateTime.now());
            network.setDownloadedBy(currentUser);

            // 3. 解析 GeoJSON 统计道路数量
            int roadCount = countRoads(geojsonPath);
            network.setTotalRoads(roadCount);

            // 4. 保存
            networkRepository.save(network);

            return RoadNetworkDTO.fromEntity(network);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("下载路网数据失败：" + e.getMessage());
        }
    }

    /**
     * 统计 GeoJSON 中的道路数量
     */
    private int countRoads(String geojsonPath) {
        try {
            String content = Files.readString(Paths.get(geojsonPath));
            var root = objectMapper.readTree(content);
            var features = root.path("features");
            return features.size();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 获取所有路网
     */
    public List<RoadNetworkDTO> findAll() {
        return networkRepository.findAll().stream()
            .map(RoadNetworkDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * 按 ID 获取路网
     */
    public RoadNetworkDTO findById(Long id) {
        return networkRepository.findById(id)
            .map(RoadNetworkDTO::fromEntity)
            .orElse(null);
    }

    /**
     * 按区域查询路网
     */
    public List<RoadNetworkDTO> findByRegion(String region) {
        return networkRepository.findByRegionOrderByDownloadDateDesc(region).stream()
            .map(RoadNetworkDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * 获取用户的路网列表
     */
    public List<RoadNetworkDTO> findByUser(Long userId) {
        return networkRepository.findByDownloadedByIdOrderByDownloadDateDesc(userId).stream()
            .map(RoadNetworkDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * 删除路网
     */
    @Transactional
    public void delete(Long id) {
        networkRepository.deleteById(id);
        // 注意：实际应用中需要同时删除关联的 GeoJSON 文件和约束数据
    }

    /**
     * 获取路网的 GeoJSON
     */
    public String findGeoJsonById(Long id) {
        return networkRepository.findById(id)
            .map(RoadNetwork::getGeojsonPath)
            .filter(path -> Files.exists(Paths.get(path)))
            .map(path -> {
                try {
                    return Files.readString(Paths.get(path));
                } catch (IOException e) {
                    return null;
                }
            })
            .orElse(null);
    }

    /**
     * 获取路网的 GeoJSON 文件路径
     */
    public String findGeoJsonPathById(Long id) {
        return networkRepository.findById(id)
            .map(RoadNetwork::getGeojsonPath)
            .orElse(null);
    }

    /**
     * 创建下载任务
     */
    @Transactional
    public RoadNetworkDownloadTask createDownloadTask(DownloadNetworkRequest request, User user) {
        RoadNetworkDownloadTask task = new RoadNetworkDownloadTask();
        task.setTaskName(request.getName());
        task.setRegion(request.getRegion());
        task.setMinLat(request.getMinLat());
        task.setMinLon(request.getMinLon());
        task.setMaxLat(request.getMaxLat());
        task.setMaxLon(request.getMaxLon());
        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setCreatedBy(user);
        return taskRepository.save(task);
    }

    /**
     * 异步执行下载任务
     */
    @Async("taskExecutor")
    public void executeDownloadTask(Long taskId) {
        System.out.println("[AsyncTask] 开始执行任务：" + taskId);
        RoadNetworkDownloadTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("任务不存在：" + taskId));

        try {
            // 更新状态为下载中
            task.setStatus(TaskStatus.DOWNLOADING);
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(10);
            taskRepository.save(task);
            System.out.println("[AsyncTask] 任务 " + taskId + " 状态更新为 DOWNLOADING");

            // 1. 调用 Overpass API 下载数据
            DownloadNetworkRequest request = new DownloadNetworkRequest();
            request.setName(task.getTaskName());
            request.setRegion(task.getRegion());
            request.setMinLat(task.getMinLat());
            request.setMinLon(task.getMinLon());
            request.setMaxLat(task.getMaxLat());
            request.setMaxLon(task.getMaxLon());

            System.out.println("[AsyncTask] 任务 " + taskId + " 开始下载路网数据");
            String geojsonPath = overpassService.downloadRoadNetwork(request);
            task.setProgress(50);
            taskRepository.save(task);
            System.out.println("[AsyncTask] 任务 " + taskId + " 路网数据下载完成：" + geojsonPath);

            // 2. 创建路网记录
            RoadNetwork network = new RoadNetwork();
            network.setName(task.getTaskName());
            network.setRegion(task.getRegion());
            network.setMinLat(task.getMinLat());
            network.setMinLon(task.getMinLon());
            network.setMaxLat(task.getMaxLat());
            network.setMaxLon(task.getMaxLon());
            network.setGeojsonPath(geojsonPath);
            network.setDownloadDate(LocalDateTime.now());
            network.setDownloadedBy(task.getCreatedBy());

            // 3. 解析 GeoJSON 统计道路数量
            task.setStatus(TaskStatus.PROCESSING);
            task.setProgress(80);
            taskRepository.save(task);

            int roadCount = countRoads(geojsonPath);
            network.setTotalRoads(roadCount);

            // 4. 保存路网
            networkRepository.save(network);
            task.setRoadNetworkId(network.getId());

            // 5. 完成任务
            task.setStatus(TaskStatus.COMPLETED);
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

        } catch (Exception e) {
            e.printStackTrace();
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("下载失败：" + e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    /**
     * 获取用户任务列表
     */
    public List<RoadNetworkDownloadTask> getUserTasks(Long userId) {
        return taskRepository.findByCreatedByIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取任务详情
     */
    public RoadNetworkDownloadTask getTask(Long taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }

    /**
     * 重试失败任务
     */
    @Transactional
    public void retryTask(Long taskId) {
        RoadNetworkDownloadTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("任务不存在：" + taskId));

        if (task.getStatus() != TaskStatus.FAILED && task.getStatus() != TaskStatus.CANCELLED) {
            throw new IllegalStateException("只能重试失败或已取消的任务");
        }

        task.setStatus(TaskStatus.PENDING);
        task.setProgress(0);
        task.setErrorMessage(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);
        taskRepository.save(task);

        // 重新执行
        executeDownloadTask(taskId);
    }

    /**
     * 取消任务
     */
    @Transactional
    public void cancelTask(Long taskId) {
        RoadNetworkDownloadTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("任务不存在：" + taskId));

        if (task.getStatus() == TaskStatus.DOWNLOADING || task.getStatus() == TaskStatus.PROCESSING) {
            throw new IllegalStateException("正在执行中的任务无法取消");
        }

        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
    }
}
