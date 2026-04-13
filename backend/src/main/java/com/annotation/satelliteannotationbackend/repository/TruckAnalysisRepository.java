package com.annotation.satelliteannotationbackend.repository;

import com.annotation.satelliteannotationbackend.entity.TruckAnalysisRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 货车分析请求 Repository
 */
@Repository
public interface TruckAnalysisRepository extends JpaRepository<TruckAnalysisRequest, Long> {

    /**
     * 查询用户的分析历史
     */
    List<TruckAnalysisRequest> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    /**
     * 查询指定路网的分析记录
     */
    List<TruckAnalysisRequest> findByRoadNetworkIdOrderByCreatedAtDesc(Long roadNetworkId);
}
