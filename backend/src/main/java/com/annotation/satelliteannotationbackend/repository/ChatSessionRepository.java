package com.annotation.satelliteannotationbackend.repository;

import com.annotation.satelliteannotationbackend.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI 聊天会话 Repository
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    // 根据设备 ID 查找所有会话
    List<ChatSession> findByDeviceIdOrderByUpdatedAtDesc(String deviceId);

    // 根据设备 ID 和会话 ID 查找
    Optional<ChatSession> findByIdAndDeviceId(String id, String deviceId);
}
