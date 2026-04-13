package com.annotation.satelliteannotationbackend.repository;

import com.annotation.satelliteannotationbackend.entity.RoadNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 路网数据 Repository
 */
@Repository
public interface RoadNetworkRepository extends JpaRepository<RoadNetwork, Long> {

    /**
     * 按区域查询路网
     */
    List<RoadNetwork> findByRegionOrderByDownloadDateDesc(String region);

    /**
     * 查询用户下载的所有路网
     */
    List<RoadNetwork> findByDownloadedByIdOrderByDownloadDateDesc(Long userId);
}
