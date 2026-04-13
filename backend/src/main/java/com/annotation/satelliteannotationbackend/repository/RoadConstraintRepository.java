package com.annotation.satelliteannotationbackend.repository;

import com.annotation.satelliteannotationbackend.entity.RoadConstraint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 道路约束 Repository
 */
@Repository
public interface RoadConstraintRepository extends JpaRepository<RoadConstraint, Long> {

    /**
     * 按路网 ID 查询约束
     */
    List<RoadConstraint> findByRoadNetworkId(Long roadNetworkId);

    /**
     * 按 OSM Way ID 查询约束
     */
    RoadConstraint findByOsmWayId(String osmWayId);

    /**
     * 查询有特定限制类型的约束
     */
    List<RoadConstraint> findByRoadNetworkIdAndRestrictionType(Long roadNetworkId, String restrictionType);
}
