package com.annotation.satelliteannotationbackend.service;

import com.annotation.satelliteannotationbackend.dto.DownloadNetworkRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Overpass API 服务
 * 用于从 OpenStreetMap 下载路网数据
 */
@Service
public class OverpassService {

    private static final String OVERPASS_API_URL = "https://overpass-api.de/api/interpreter";
    private static final String DATA_DIR = "data/road_networks/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OverpassService() {
        this.objectMapper = new ObjectMapper();

        // 配置 RestTemplate 超时
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);  // 60 秒连接超时
        factory.setReadTimeout(180000);    // 180 秒读取超时
        this.restTemplate = new RestTemplate(factory);

        System.out.println("[OverpassService] Initialized with timeouts: connect=60s, read=180s");
    }

    /**
     * 下载指定区域的道路网络数据
     * @param request 下载请求参数
     * @return GeoJSON 格式的道路网络数据
     */
    public String downloadRoadNetwork(DownloadNetworkRequest request) {
        System.out.println("[OverpassService] 开始下载路网数据：" + request.getName());

        // 构建 Overpass QL 查询
        String query = buildOverpassQuery(
            request.getMinLat(), request.getMinLon(),
            request.getMaxLat(), request.getMaxLon()
        );
        System.out.println("[OverpassService] 查询区域：" + request.getMinLat() + "," + request.getMinLon() + " to " + request.getMaxLat() + "," + request.getMaxLon());

        try {
            System.out.println("[OverpassService] 开始调用 Overpass API...");
            // 调用 Overpass API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<String> entity = new HttpEntity<>(query, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                OVERPASS_API_URL, entity, String.class
            );
            System.out.println("[OverpassService] Overpass API 响应状态码：" + response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK) {
                // 转换为 GeoJSON
                String geojson = convertToGeoJSON(response.getBody());
                System.out.println("[OverpassService] GeoJSON 转换完成，长度：" + (geojson != null ? geojson.length() : "null"));

                // 保存到文件
                String filename = saveGeoJSON(geojson, request.getName());
                System.out.println("[OverpassService] 文件已保存：" + filename);

                return filename;
            } else {
                throw new RuntimeException("Overpass API 请求失败：" + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("[OverpassService] 下载失败：" + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("下载路网数据失败：" + e.getMessage());
        }
    }

    /**
     * 构建 Overpass QL 查询
     */
    private String buildOverpassQuery(double minLat, double minLon, double maxLat, double maxLon) {
        // 查询道路类型：motorway, trunk, primary, secondary, tertiary, residential
        // 同时查询有限制信息的道路（maxheight, maxweight, maxwidth 等）
        return String.format("""
            [out:json][timeout:120];
            (
              way["highway"]["highway"~"motorway|trunk|primary|secondary|tertiary|residential"](%.6f,%.6f,%.6f,%.6f);
              way["highway"]["maxheight"](%.6f,%.6f,%.6f,%.6f);
              way["highway"]["maxweight"](%.6f,%.6f,%.6f,%.6f);
              way["highway"]["maxwidth"](%.6f,%.6f,%.6f,%.6f);
              way["highway"]["maxlength"](%.6f,%.6f,%.6f,%.6f);
              way["highway"]["motor_vehicle"]["motor_vehicle"="no"](%.6f,%.6f,%.6f,%.6f);
              way["highway"]["hgv"]["hgv"="no"](%.6f,%.6f,%.6f,%.6f);
            );
            out body;
            >;
            out skel qt;
            """,
            minLat, minLon, maxLat, maxLon,  // 道路
            minLat, minLon, maxLat, maxLon,  // 限高
            minLat, minLon, maxLat, maxLon,  // 限重
            minLat, minLon, maxLat, maxLon,  // 限宽
            minLat, minLon, maxLat, maxLon,  // 限长
            minLat, minLon, maxLat, maxLon,  // 禁止机动车
            minLat, minLon, maxLat, maxLon   // 禁止货车
        );
    }

    /**
     * 将 Overpass API 响应转换为 GeoJSON
     */
    private String convertToGeoJSON(String overpassResponse) throws IOException {
        JsonNode root = objectMapper.readTree(overpassResponse);
        JsonNode elements = root.path("elements");

        // 先构建节点 ID 到坐标的映射
        Map<Long, JsonNode> nodeMap = new HashMap<>();
        for (JsonNode element : elements) {
            if ("node".equals(element.path("type").asText())) {
                nodeMap.put(element.path("id").asLong(), element);
            }
        }

        Map<String, Object> geojson = new HashMap<>();
        geojson.put("type", "FeatureCollection");
        geojson.put("crs", Map.of("type", "name", "properties", Map.of("name", "EPSG:4326")));

        List<Map<String, Object>> features = new ArrayList<>();

        for (JsonNode element : elements) {
            String type = element.path("type").asText();

            if ("way".equals(type)) {
                Map<String, Object> feature = parseWay(element, nodeMap);
                if (feature != null) {
                    features.add(feature);
                }
            } else if ("relation".equals(type)) {
                // 处理关系（多段路组成的路线）
                Map<String, Object> feature = parseRelation(element, elements);
                if (feature != null) {
                    features.add(feature);
                }
            }
        }

        geojson.put("features", features);
        return objectMapper.writeValueAsString(geojson);
    }

    /**
     * 解析 Way（道路段）
     */
    private Map<String, Object> parseWay(JsonNode wayNode, Map<Long, JsonNode> nodeMap) {
        long id = wayNode.path("id").asLong();
        JsonNode tags = wayNode.path("tags");
        JsonNode nodes = wayNode.path("nodes");

        // 获取道路信息
        String highway = tags.path("highway").asText("");
        String name = tags.path("name").asText("");
        String maxheight = tags.path("maxheight").asText("");
        String maxweight = tags.path("maxweight").asText("");
        String maxwidth = tags.path("maxwidth").asText("");
        String maxlength = tags.path("maxlength").asText("");
        String motorVehicle = tags.path("motor_vehicle").asText("");
        String hgv = tags.path("hgv").asText("");

        // 构建几何坐标
        List<List<Double>> coordinates = new ArrayList<>();
        for (JsonNode nodeId : nodes) {
            long nid = nodeId.asLong();
            JsonNode node = nodeMap.get(nid);
            if (node != null) {
                double lon = node.path("lon").asDouble();
                double lat = node.path("lat").asDouble();
                coordinates.add(List.of(lon, lat));
            }
        }

        if (coordinates.isEmpty()) {
            return null;
        }

        // 创建 Feature
        Map<String, Object> feature = new HashMap<>();
        feature.put("type", "Feature");
        feature.put("id", id);

        Map<String, Object> geometry = new HashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coordinates);
        feature.put("geometry", geometry);

        Map<String, Object> properties = new HashMap<>();
        properties.put("osm_way_id", String.valueOf(id));
        properties.put("highway", highway);
        properties.put("name", name);
        properties.put("maxheight", parseValue(maxheight));
        properties.put("maxweight", parseValue(maxweight));
        properties.put("maxwidth", parseValue(maxwidth));
        properties.put("maxlength", parseValue(maxlength));
        properties.put("motor_vehicle", motorVehicle);
        properties.put("hgv", hgv);
        feature.put("properties", properties);

        return feature;
    }

    /**
     * 解析关系
     */
    private Map<String, Object> parseRelation(JsonNode relationNode, JsonNode allElements) {
        // 简化处理，关系可以包含多个 way
        // 实际实现需要递归查找成员 way 并合并坐标
        return null;
    }

    /**
     * 解析约束值（处理单位）
     */
    private Double parseValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            // 移除单位，保留数字
            String numStr = value.replaceAll("[^0-9.]", "");
            return Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 保存 GeoJSON 到文件
     */
    private String saveGeoJSON(String geojson, String name) throws IOException {
        // 创建目录
        Path dir = Paths.get(DATA_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        // 生成文件名
        String filename = System.currentTimeMillis() + "_" +
                         name.replaceAll("[^a-zA-Z0-9]", "_") + ".geojson";
        Path filePath = dir.resolve(filename);

        // 写入文件
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(geojson);
        }

        return filePath.toString();
    }

    /**
     * 从 GeoJSON 文件读取内容
     */
    public String readGeoJSON(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath));
    }
}
