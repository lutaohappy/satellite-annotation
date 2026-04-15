package com.annotation.satelliteannotationbackend.repository;

import com.annotation.satelliteannotationbackend.entity.RoadNetworkDownloadTask;
import com.annotation.satelliteannotationbackend.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 路网下载任务 Repository
 */
@Repository
public interface RoadNetworkTaskRepository extends JpaRepository<RoadNetworkDownloadTask, Long> {

    /**
     * 按用户 ID 查询任务列表
     */
    List<RoadNetworkDownloadTask> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    /**
     * 按状态查询任务列表
     */
    List<RoadNetworkDownloadTask> findByStatusOrderByCreatedAtDesc(TaskStatus status);
}
