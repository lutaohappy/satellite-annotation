package com.annotation.satelliteannotationbackend.service;

import com.annotation.satelliteannotationbackend.dto.*;
import com.annotation.satelliteannotationbackend.entity.RoadConstraint;
import com.annotation.satelliteannotationbackend.entity.RoadNetwork;
import com.annotation.satelliteannotationbackend.entity.TruckAnalysisRequest;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.repository.RoadConstraintRepository;
import com.annotation.satelliteannotationbackend.repository.TruckAnalysisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 货车通过性分析服务
 */
@Service
public class TruckAnalysisService {

    private static final String OSRM_BASE_URL = "http://localhost:5000";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RoadConstraintRepository constraintRepository;
    private final TruckAnalysisRepository analysisRepository;

    public TruckAnalysisService(
            RoadConstraintRepository constraintRepository,
            TruckAnalysisRepository analysisRepository) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.constraintRepository = constraintRepository;
        this.analysisRepository = analysisRepository;
    }

    /**
     * 执行通过性分析
     */
    @Transactional
    public AnalysisResultDTO analyze(TruckAnalysisRequestDTO request, User currentUser) {
        try {
            // 1. 计算路线（使用 OSRM）
            RouteResponse routeResponse = calculateRoute(
                request.getStartLat(), request.getStartLon(),
                request.getEndLat(), request.getEndLon()
            );

            if (routeResponse == null || routeResponse.routes == null || routeResponse.routes.isEmpty()) {
                throw new RuntimeException("无法计算路线");
            }

            RouteRoute bestRoute = routeResponse.routes.get(0);
            String routeGeoJson = objectMapper.writeValueAsString(bestRoute.geometry);

            // 2. 计算总距离和预计时间
            double totalDistance = bestRoute.distance / 1000.0;  // 转换为公里
            double estimatedTime = bestRoute.duration / 60.0;    // 转换为分钟

            // 2.5. 计算所有转弯点
            List<TurnPointDTO> turnPoints = calculateTurnPoints(bestRoute);

            // 2.6. 构建路段列表
            List<RoadSegmentDTO> roadSegments = buildRoadSegments(bestRoute);

            // 3. 获取路网约束数据
            List<RoadConstraint> constraints = new ArrayList<>();
            if (request.getRoadNetworkId() != null) {
                constraints = constraintRepository.findByRoadNetworkId(request.getRoadNetworkId());
            }

            // 4. 分析通过性
            TruckParametersDTO truck = request.getTruck();
            List<ViolationPointDTO> violations = analyzePassability(bestRoute, truck, constraints, turnPoints);

            // 5. 返回结果（不再自动保存，由前端决定是否保存）
            if (violations.isEmpty()) {
                return AnalysisResultDTO.passable(routeGeoJson, totalDistance, estimatedTime, turnPoints, roadSegments);
            } else {
                return AnalysisResultDTO.notPassable(routeGeoJson, violations, totalDistance, estimatedTime, turnPoints, roadSegments);
            }

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException("分析失败：" + e.getMessage());
        }
    }

    /**
     * 计算路线（调用 OSRM API）
     */
    private RouteResponse calculateRoute(double startLat, double startLon,
                                          double endLat, double endLon) {
        try {
            // OSRM Route API: http://localhost:5000/route/v1/driving/{lon},{lat};{lon},{lat}
            // 添加 annotations=true 获取详细路段信息
            // 添加 steps=true 获取转弯指示
            String url = String.format(
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?" +
                "overview=full&geometries=geojson&alternatives=true&" +
                "steps=true&annotations=true",
                OSRM_BASE_URL,
                startLon, startLat, endLon, endLat
            );

            System.out.println("[TruckAnalysis] 调用 OSRM URL: " + url);
            System.out.println("[TruckAnalysis] 起点：" + startLat + "," + startLon);
            System.out.println("[TruckAnalysis] 终点：" + endLat + "," + endLon);

            ResponseEntity<RouteResponse> response = restTemplate.getForEntity(
                url, RouteResponse.class
            );

            System.out.println("[TruckAnalysis] OSRM 响应码：" + response.getStatusCode());
            System.out.println("[TruckAnalysis] OSRM 响应体：" + response.getBody());

            return response.getBody();
        } catch (Exception e) {
            System.err.println("[TruckAnalysis] OSRM 调用失败：" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 分析通过性（改进版）
     * 检查：
     * 1. 限高：truck.height <= road.maxHeight
     * 2. 限重：truck.weight <= road.maxWeight
     * 3. 限宽：truck.width <= road.maxWidth
     * 4. 限长：truck.length <= road.maxLength
     * 5. 禁止货车：hgv=no 或 motor_vehicle=no
     * 6. 转弯半径：R = wheelbase / sin(35°)
     */
    private List<ViolationPointDTO> analyzePassability(RouteRoute route,
                                                        TruckParametersDTO truck,
                                                        List<RoadConstraint> constraints,
                                                        List<TurnPointDTO> turnPoints) {
        List<ViolationPointDTO> violations = new ArrayList<>();

        // 计算车辆最小转弯半径
        Double minTurningRadius = truck.calculateMinTurningRadius();
        System.out.println("[TruckAnalysis] 车辆最小转弯半径：" + minTurningRadius + "m");

        // 如果没有约束数据，只进行转弯半径检查
        if (constraints == null || constraints.isEmpty()) {
            System.out.println("[TruckAnalysis] 无路网约束数据，仅进行转弯半径检查");
        } else {
            System.out.println("[TruckAnalysis] 路网约束数量：" + constraints.size());
        }

        // 遍历路线的每个路段，检查约束
        if (route.legs != null) {
            for (RouteLeg leg : route.legs) {
                if (leg.steps != null) {
                    int stepIndex = 0;
                    for (RouteStep step : leg.steps) {
                        // 获取路段中心点作为检查点
                        List<List<Double>> geometryCoords = parseGeometryCoordinates(step.geometry);

                        if (geometryCoords != null && geometryCoords.size() >= 2) {
                            // 取中间点作为代表
                            int midIndex = geometryCoords.size() / 2;
                            List<Double> midPoint = geometryCoords.get(midIndex);
                            double lat = midPoint.get(1);
                            double lon = midPoint.get(0);

                            System.out.println("[TruckAnalysis] 检查路段 #" + stepIndex +
                                " 位置：" + String.format("%.6f,%.6f", lat, lon));

                            // 从约束列表中查找可能的限制
                            // 注意：由于无法精确匹配 OSM way_id，这里使用启发式方法
                            // 检查所有约束，如果车辆参数超过任何约束则报告
                            if (constraints != null) {
                                for (RoadConstraint c : constraints) {
                                    // 检查限高
                                    if (truck.getHeight() != null && c.getMaxHeight() != null &&
                                        truck.getHeight() > c.getMaxHeight()) {
                                        System.out.println("[TruckAnalysis] 发现限高违规：车辆高度 " +
                                            truck.getHeight() + "m > 道路限高 " + c.getMaxHeight() + "m");
                                        violations.add(ViolationPointDTO.create(
                                            lat, lon,
                                            "限高不足",
                                            String.format("道路限高 %.2fm，车辆高度 %.2fm，超限 %.2fm",
                                                c.getMaxHeight(), truck.getHeight(),
                                                truck.getHeight() - c.getMaxHeight())
                                        ));
                                    }

                                    // 检查限重
                                    if (truck.getWeight() != null && c.getMaxWeight() != null &&
                                        truck.getWeight() > c.getMaxWeight()) {
                                        System.out.println("[TruckAnalysis] 发现限重违规：车辆总重 " +
                                            truck.getWeight() + "t > 道路限重 " + c.getMaxWeight() + "t");
                                        violations.add(ViolationPointDTO.create(
                                            lat, lon,
                                            "限重不足",
                                            String.format("道路限重 %.2f 吨，车辆总重 %.2f 吨，超限 %.2f 吨",
                                                c.getMaxWeight(), truck.getWeight(),
                                                truck.getWeight() - c.getMaxWeight())
                                        ));
                                    }

                                    // 检查限宽
                                    if (truck.getWidth() != null && c.getMaxWidth() != null &&
                                        truck.getWidth() > c.getMaxWidth()) {
                                        System.out.println("[TruckAnalysis] 发现限宽违规：车辆宽度 " +
                                            truck.getWidth() + "m > 道路限宽 " + c.getMaxWidth() + "m");
                                        violations.add(ViolationPointDTO.create(
                                            lat, lon,
                                            "限宽不足",
                                            String.format("道路限宽 %.2fm，车辆宽度 %.2fm，超限 %.2fm",
                                                c.getMaxWidth(), truck.getWidth(),
                                                truck.getWidth() - c.getMaxWidth())
                                        ));
                                    }

                                    // 检查限长
                                    if (truck.getLength() != null && c.getMaxLength() != null &&
                                        truck.getLength() > c.getMaxLength()) {
                                        System.out.println("[TruckAnalysis] 发现限长违规：车辆长度 " +
                                            truck.getLength() + "m > 道路限长 " + c.getMaxLength() + "m");
                                        violations.add(ViolationPointDTO.create(
                                            lat, lon,
                                            "限长不足",
                                            String.format("道路限长 %.2fm，车辆长度 %.2fm，超限 %.2fm",
                                                c.getMaxLength(), truck.getLength(),
                                                truck.getLength() - c.getMaxLength())
                                        ));
                                    }

                                    // 检查禁止货车标记
                                    if (c.getRestrictionType() != null) {
                                        if ("hgv_no".equals(c.getRestrictionType()) ||
                                            "motor_vehicle_no".equals(c.getRestrictionType())) {
                                            System.out.println("[TruckAnalysis] 发现禁行标志：" + c.getRestrictionType());
                                            violations.add(ViolationPointDTO.create(
                                                lat, lon,
                                                "道路禁行",
                                                "该路段禁止货车通行（" + c.getRestrictionType() + "）"
                                            ));
                                        }
                                    }
                                }
                            }

                            // 检查 OSRM 返回的道路模式（如轮渡）
                            if (step.mode != null && "ferry".equals(step.mode)) {
                                System.out.println("[TruckAnalysis] 发现轮渡路段");
                                violations.add(ViolationPointDTO.create(
                                    lat, lon,
                                    "轮渡路段",
                                    "该路段为轮渡，请确认货车是否可以搭乘"
                                ));
                            }
                        }
                        stepIndex++;
                    }
                }
            }
        }

        // 转弯半径检查：使用计算出的转弯点
        if (minTurningRadius != null && turnPoints != null) {
            System.out.println("[TruckAnalysis] 检查 " + turnPoints.size() + " 个转弯点");
            for (TurnPointDTO turnPoint : turnPoints) {
                if (turnPoint.getTurnRadius() != null && turnPoint.getTurnRadius() < minTurningRadius) {
                    System.out.println("[TruckAnalysis] 发现转弯半径不足：需要 " + minTurningRadius +
                        "m，实际 " + turnPoint.getTurnRadius() + "m");
                    violations.add(ViolationPointDTO.createTurnRadiusViolation(
                        turnPoint.getLat(),
                        turnPoint.getLon(),
                        String.format("需要最小半径 %.2fm，实际半径 %.2fm，车辆轴距 %.2fm",
                            minTurningRadius, turnPoint.getTurnRadius(), truck.getWheelbase()),
                        turnPoint.getTurnAngle(),
                        turnPoint.getTurnRadius()
                    ));
                }
            }
        }

        System.out.println("[TruckAnalysis] 总共发现 " + violations.size() + " 个违规点");
        return violations;
    }

    /**
     * 计算所有转弯点
     */
    private List<TurnPointDTO> calculateTurnPoints(RouteRoute route) {
        List<TurnPointDTO> turnPoints = new ArrayList<>();
        int sequence = 1;

        System.out.println("[TruckAnalysis] 开始计算转弯点");
        if (route.legs == null) {
            System.out.println("[TruckAnalysis] route.legs 为 null");
            return turnPoints;
        }
        System.out.println("[TruckAnalysis] route.legs 数量：" + route.legs.size());

        for (RouteLeg leg : route.legs) {
            if (leg.steps == null) {
                System.out.println("[TruckAnalysis] leg.steps 为 null");
                continue;
            }
            System.out.println("[TruckAnalysis] leg.steps 数量：" + leg.steps.size());

            if (leg.steps.size() > 1) {
                for (int i = 0; i < leg.steps.size() - 1; i++) {
                    RouteStep step1 = leg.steps.get(i);
                    RouteStep step2 = leg.steps.get(i + 1);

                    Double bearing1 = (step1.maneuver != null) ? step1.maneuver.bearingAfter : null;
                    Double bearing2 = (step2.maneuver != null) ? step2.maneuver.bearingBefore : null;

                    System.out.println("[TruckAnalysis] Step " + i + ": bearing1=" + bearing1 + ", bearing2=" + bearing2);
                    System.out.println("[TruckAnalysis] Step " + i + " geometry: " + (step1.geometry != null ? "not null" : "null") +
                        ", maneuver.bearingBefore=" + (step1.maneuver != null ? step1.maneuver.bearingBefore : null) +
                        ", maneuver.bearingAfter=" + (step1.maneuver != null ? step1.maneuver.bearingAfter : null));

                    if (bearing1 != null && bearing2 != null) {
                        // 计算角度变化
                        double angleDiff = Math.abs(bearing2 - bearing1);
                        if (angleDiff > 180) {
                            angleDiff = 360 - angleDiff;
                        }

                        // 获取转弯点坐标（step1 的终点）
                        List<Double> turnPoint = getStepEndPoint(step1);
                        System.out.println("[TruckAnalysis] turnPoint=" + turnPoint);

                        if (turnPoint != null) {
                            // 计算转弯半径（估算）
                            Double turnRadius = calculateTurnRadius(turnPoint, step1, step2);

                            TurnPointDTO dto = TurnPointDTO.create(
                                turnPoint.get(1),  // lat
                                turnPoint.get(0),  // lon
                                angleDiff,
                                turnRadius,
                                bearing1,
                                bearing2,
                                sequence++
                            );
                            turnPoints.add(dto);
                            System.out.println("[TruckAnalysis] 添加转弯点 #" + (sequence-1) + ": 角度=" + angleDiff + "度，半径=" + turnRadius + "m");
                        }
                    }
                }
            }
        }

        System.out.println("[TruckAnalysis] 返回转弯点数量：" + turnPoints.size());
        return turnPoints;
    }

    /**
     * 构建路段列表
     */
    private List<RoadSegmentDTO> buildRoadSegments(RouteRoute route) {
        List<RoadSegmentDTO> roadSegments = new ArrayList<>();
        int sequence = 1;

        System.out.println("[TruckAnalysis] 开始构建路段列表");
        if (route.legs == null) {
            System.out.println("[TruckAnalysis] buildRoadSegments: route.legs 为 null");
            return roadSegments;
        }

        for (RouteLeg leg : route.legs) {
            if (leg.steps == null) {
                continue;
            }

            for (RouteStep step : leg.steps) {
                RoadSegmentDTO segment = new RoadSegmentDTO();
                segment.setSequence(sequence++);

                // 路段名称（从 instruction 中提取道路名称）
                if (step.instruction != null) {
                    // 提取道路名称（例如："Turn right onto Main Street" -> "Main Street"）
                    String name = extractRoadName(step.instruction);
                    segment.setName(name);
                    segment.setInstruction(step.instruction);
                }

                segment.setDistance(step.distance);  // 米
                segment.setDuration(step.duration);  // 秒
                segment.setRoadMode(step.mode);
                segment.setModifier(step.modifier);
                segment.setManeuverType(step.maneuverType);

                if (step.maneuver != null) {
                    segment.setBearingBefore(step.maneuver.bearingBefore);
                    segment.setBearingAfter(step.maneuver.bearingAfter);

                    // 获取起点和终点坐标
                    List<List<Double>> coords = parseGeometryCoordinates(step.geometry);
                    if (coords != null && !coords.isEmpty()) {
                        List<Double> startPoint = coords.get(0);
                        List<Double> endPoint = coords.get(coords.size() - 1);
                        segment.setStartLon(startPoint.get(0));
                        segment.setStartLat(startPoint.get(1));
                        segment.setEndLon(endPoint.get(0));
                        segment.setEndLat(endPoint.get(1));
                    }
                }

                // 限制信息（根据道路模式判断）
                if ("ferry".equals(step.mode)) {
                    segment.setRestrictions("轮渡路段，请确认货车是否可以搭乘");
                }

                roadSegments.add(segment);
            }
        }

        System.out.println("[TruckAnalysis] 返回路段数量：" + roadSegments.size());
        return roadSegments;
    }

    /**
     * 从导航指示中提取道路名称
     */
    private String extractRoadName(String instruction) {
        if (instruction == null || instruction.isEmpty()) {
            return "";
        }

        // 常见模式：
        // "Turn left onto [road name]"
        // "Turn right onto [road name]"
        // "Continue onto [road name]"
        // "Go straight onto [road name]"

        String[] ontoKeywords = {"onto", "on", "along", "toward", "towards"};
        for (String keyword : ontoKeywords) {
            int index = instruction.toLowerCase().indexOf(keyword);
            if (index != -1) {
                String afterKeyword = instruction.substring(index + keyword.length()).trim();
                // 移除句号等标点
                afterKeyword = afterKeyword.replaceAll("[.,!?;:]$", "");
                return afterKeyword.isEmpty() ? instruction : afterKeyword;
            }
        }

        // 如果没有找到关键词，返回完整指示
        return instruction;
    }

    /**
     * 计算转弯半径（基于距离和角度）
     */
    private Double calculateTurnRadius(List<Double> turnPoint, RouteStep step1, RouteStep step2) {
        // 简化估算：基于转弯角度和典型车辆速度
        // 更精确的计算需要道路曲率半径

        Double bearing1 = (step1.maneuver != null) ? step1.maneuver.bearingAfter : null;
        Double bearing2 = (step2.maneuver != null) ? step2.maneuver.bearingBefore : null;

        if (bearing1 != null && bearing2 != null) {
            double angleDiff = Math.abs(bearing2 - bearing1);
            if (angleDiff > 180) {
                angleDiff = 360 - angleDiff;
            }

            // 角度越大，转弯越急，半径越小
            // 使用经验公式估算
            if (angleDiff > 5) {  // 忽略微小角度变化
                // R = v^2 / (g * tan(theta)) 的简化版本
                // 这里使用经验公式：角度 90 度时半径约 10 米，角度 45 度时半径约 25 米
                return 900.0 / (angleDiff + 10);  // 单位：米
            }
        }

        return null;
    }

    /**
     * 解析 GeoJSON LineString 的坐标数组
     */
    private List<List<Double>> parseGeometryCoordinates(JsonNode geometryNode) {
        if (geometryNode == null) {
            System.out.println("[TruckAnalysis] parseGeometryCoordinates: geometryNode 为 null");
            return null;
        }

        // GeoJSON 格式：{"type": "LineString", "coordinates": [[lon,lat], ...]}
        // 需要先获取 coordinates 字段
        JsonNode coordsNode = geometryNode.has("coordinates") ? geometryNode.get("coordinates") : geometryNode;

        if (!coordsNode.isArray()) {
            System.out.println("[TruckAnalysis] parseGeometryCoordinates: coordinates 不是数组，type=" + coordsNode.getNodeType());
            return null;
        }

        List<List<Double>> coordinates = new ArrayList<>();
        for (JsonNode coordNode : coordsNode) {
            if (coordNode.isArray() && coordNode.size() >= 2) {
                List<Double> coord = new ArrayList<>();
                coord.add(coordNode.get(0).asDouble());
                coord.add(coordNode.get(1).asDouble());
                coordinates.add(coord);
            }
        }
        System.out.println("[TruckAnalysis] parseGeometryCoordinates: 解析出 " + coordinates.size() + " 个坐标点");
        return coordinates.isEmpty() ? null : coordinates;
    }

    /**
     * 获取路段的终点坐标
     */
    private List<Double> getStepEndPoint(RouteStep step) {
        if (step.geometry == null) {
            System.out.println("[TruckAnalysis] getStepEndPoint: geometry 为 null");
            return null;
        }
        List<List<Double>> coords = parseGeometryCoordinates(step.geometry);
        if (coords == null || coords.isEmpty()) {
            System.out.println("[TruckAnalysis] getStepEndPoint: coords 为空");
            return null;
        }
        List<Double> endPoint = coords.get(coords.size() - 1);
        System.out.println("[TruckAnalysis] getStepEndPoint: 返回终点 " + endPoint);
        return endPoint;
    }

    /**
     * 获取分析历史
     */
    public List<TruckAnalysisRequest> getHistory(Long userId) {
        return analysisRepository.findByCreatedByIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 根据 ID 获取分析请求
     */
    public TruckAnalysisRequest getById(Long id) {
        return analysisRepository.findById(id).orElse(null);
    }

    /**
     * 保存分析结果（支持新增和更新）
     */
    @Transactional
    public TruckAnalysisRequest saveAnalysis(SavedAnalysisRequestDTO request, User currentUser) {
        // 如果有 ID，表示更新操作
        if (request.getId() != null) {
            System.out.println("[TruckAnalysisService] 更新记录 ID: " + request.getId());
            return analysisRepository.findById(request.getId())
                .map(entity -> {
                    // 更新现有记录
                    SavedAnalysisDTO.updateEntity(entity, request);
                    entity.setCreatedBy(currentUser);
                    return analysisRepository.save(entity);
                })
                .orElseThrow(() -> new RuntimeException("记录不存在：" + request.getId()));
        } else {
            // 新增记录
            System.out.println("[TruckAnalysisService] 创建新记录");
            TruckAnalysisRequest entity = SavedAnalysisDTO.toEntity(request, currentUser.getId());
            entity.setCreatedBy(currentUser);
            return analysisRepository.save(entity);
        }
    }

    /**
     * 获取已保存的分析列表
     */
    public List<SavedAnalysisDTO> getSavedList(Long userId) {
        List<TruckAnalysisRequest> requests = analysisRepository.findByCreatedByIdOrderByCreatedAtDesc(userId);
        return requests.stream()
            .map(SavedAnalysisDTO::fromEntity)
            .toList();
    }

    /**
     * 获取已保存的分析详情
     */
    public SavedAnalysisDTO getSavedAnalysis(Long id) {
        return analysisRepository.findById(id)
            .map(SavedAnalysisDTO::fromEntity)
            .orElse(null);
    }

    /**
     * 删除已保存的分析
     */
    @Transactional
    public void deleteAnalysis(Long id) {
        analysisRepository.deleteById(id);
    }

    // ==================== OSRM 响应 DTO ====================

    public static class RouteResponse {
        public List<RouteRoute> routes;
        public String code;
    }

    public static class RouteRoute {
        public Object geometry;      // GeoJSON
        public Double distance;      // 米
        public Double duration;      // 秒
        public List<RouteLeg> legs;
    }

    public static class RouteLeg {
        public Double distance;
        public Double duration;
        public List<RouteStep> steps;
    }

    public static class RouteStep {
        public JsonNode geometry;      // GeoJSON LineString
        public Double distance;
        public Double duration;
        public StepManeuver maneuver;  // 包含 bearing_before 和 bearing_after
        public String instruction;
        public String mode;            // 道路模式（road, ferry, etc.）
        public String modifier;        // 转弯方向（left, right, slight, sharp, etc.）
        public String maneuverType;    // 操作类型（turn, roundabout, etc.）
        public JsonNode intersections; // 交叉口信息（包含 lane 信息）
    }

    public static class StepManeuver {
        @JsonProperty("bearing_before")
        public Double bearingBefore;
        @JsonProperty("bearing_after")
        public Double bearingAfter;
        public Double[] location;
        public String modifier;
        public String type;
    }
}
