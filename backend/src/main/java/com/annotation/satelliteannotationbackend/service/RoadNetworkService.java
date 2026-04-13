package com.annotation.satelliteannotationbackend.service;

import com.annotation.satelliteannotationbackend.dto.DownloadNetworkRequest;
import com.annotation.satelliteannotationbackend.dto.RoadNetworkDTO;
import com.annotation.satelliteannotationbackend.entity.RoadNetwork;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.repository.RoadNetworkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public RoadNetworkService(
            RoadNetworkRepository networkRepository,
            OverpassService overpassService) {
        this.networkRepository = networkRepository;
        this.overpassService = overpassService;
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
}
