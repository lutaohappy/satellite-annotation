package com.annotation.mcpserver.tool;

import com.annotation.mcpserver.dto.McpTool;
import com.annotation.satelliteannotationbackend.repository.RoadNetworkRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路网列表查询工具 - 查询数据库中所有路网记录
 */
@Component
public class RoadNetworkListTool implements McpToolProvider {

    private final RoadNetworkRepository roadNetworkRepository;

    public RoadNetworkListTool(RoadNetworkRepository roadNetworkRepository) {
        this.roadNetworkRepository = roadNetworkRepository;
    }

    @Override
    public McpTool.ToolDefinition getDefinition() {
        return McpTool.ToolDefinition.builder()
            .name("list_road_networks")
            .description("查询数据库中所有保存的路网数据列表，包含路网名称、区域、道路数量等信息")
            .inputSchema(McpTool.InputSchema.builder()
                .type("object")
                .properties(Map.of(
                    "region", McpTool.PropertySchema.builder()
                        .type("string")
                        .description("按区域名称筛选（可选，不传则返回全部）")
                        .build()
                ))
                .required(List.of())
                .build()
            )
            .build();
    }

    @Override
    public McpTool.ToolResult execute(Map<String, Object> args) {
        String region = args.containsKey("region") ? (String) args.get("region") : null;

        List<Map<String, Object>> networks = new ArrayList<>();

        if (region != null && !region.isEmpty()) {
            roadNetworkRepository.findByRegionOrderByDownloadDateDesc(region).forEach(n -> {
                networks.add(buildNetworkInfo(n));
            });
        } else {
            roadNetworkRepository.findAll().forEach(n -> {
                networks.add(buildNetworkInfo(n));
            });
        }

        return McpTool.ToolResult.builder()
            .isSuccess(true)
            .content(Map.of(
                "count", networks.size(),
                "networks", networks
            ))
            .build();
    }

    private Map<String, Object> buildNetworkInfo(com.annotation.satelliteannotationbackend.entity.RoadNetwork n) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", n.getId());
        info.put("name", n.getName());
        info.put("region", n.getRegion());
        info.put("totalRoads", n.getTotalRoads());
        info.put("downloadDate", n.getDownloadDate() != null ? n.getDownloadDate().toString() : null);
        return info;
    }
}
