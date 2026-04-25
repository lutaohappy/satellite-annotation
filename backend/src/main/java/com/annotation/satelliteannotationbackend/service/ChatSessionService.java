package com.annotation.satelliteannotationbackend.service;

import com.annotation.satelliteannotationbackend.dto.ChatSessionDTO;
import com.annotation.satelliteannotationbackend.entity.ChatSession;
import com.annotation.satelliteannotationbackend.repository.ChatSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 聊天会话 Service
 */
@Service
public class ChatSessionService {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 保存会话
     */
    @Transactional
    public ChatSessionDTO saveSession(ChatSessionDTO dto, String deviceId) {
        ChatSession session = new ChatSession();
        session.setId(dto.getId());
        session.setTitle(dto.getTitle());
        session.setDeviceId(deviceId);

        try {
            session.setMessages(objectMapper.writeValueAsString(dto.getMessages()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("消息序列化失败", e);
        }

        chatSessionRepository.save(session);

        ChatSessionDTO result = new ChatSessionDTO();
        result.setId(session.getId());
        result.setTitle(session.getTitle());
        result.setMessages(dto.getMessages());
        result.setDeviceId(session.getDeviceId());
        result.setCreatedAt(session.getCreatedAt());
        result.setUpdatedAt(session.getUpdatedAt());
        result.setSynced(true);

        return result;
    }

    /**
     * 获取会话列表
     */
    public List<ChatSessionDTO> getSessions(String deviceId) {
        List<ChatSession> sessions = chatSessionRepository.findByDeviceIdOrderByUpdatedAtDesc(deviceId);
        List<ChatSessionDTO> result = new ArrayList<>();

        for (ChatSession session : sessions) {
            ChatSessionDTO dto = new ChatSessionDTO();
            dto.setId(session.getId());
            dto.setTitle(session.getTitle());
            dto.setDeviceId(session.getDeviceId());
            dto.setCreatedAt(session.getCreatedAt());
            dto.setUpdatedAt(session.getUpdatedAt());
            dto.setSynced(true);

            try {
                List<ChatSessionDTO.ChatMessageDTO> messages = objectMapper.readValue(
                    session.getMessages(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ChatSessionDTO.ChatMessageDTO>>() {}
                );
                dto.setMessages(messages);
            } catch (JsonProcessingException e) {
                dto.setMessages(new ArrayList<>());
            }

            result.add(dto);
        }

        return result;
    }

    /**
     * 获取单个会话
     */
    public Optional<ChatSessionDTO> getSession(String sessionId, String deviceId) {
        return chatSessionRepository.findByIdAndDeviceId(sessionId, deviceId)
            .map(session -> {
                ChatSessionDTO dto = new ChatSessionDTO();
                dto.setId(session.getId());
                dto.setTitle(session.getTitle());
                dto.setDeviceId(session.getDeviceId());
                dto.setCreatedAt(session.getCreatedAt());
                dto.setUpdatedAt(session.getUpdatedAt());
                dto.setSynced(true);

                try {
                    List<ChatSessionDTO.ChatMessageDTO> messages = objectMapper.readValue(
                        session.getMessages(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<ChatSessionDTO.ChatMessageDTO>>() {}
                    );
                    dto.setMessages(messages);
                } catch (JsonProcessingException e) {
                    dto.setMessages(new ArrayList<>());
                }

                return dto;
            });
    }

    /**
     * 删除会话
     */
    @Transactional
    public boolean deleteSession(String sessionId, String deviceId) {
        Optional<ChatSession> sessionOpt = chatSessionRepository.findByIdAndDeviceId(sessionId, deviceId);
        if (sessionOpt.isPresent()) {
            chatSessionRepository.delete(sessionOpt.get());
            return true;
        }
        return false;
    }

    /**
     * 生成设备 ID（简单实现，生产环境可用更复杂的指纹）
     */
    public String generateDeviceId(String userAgent) {
        return "device_" + Math.abs(userAgent.hashCode());
    }
}
