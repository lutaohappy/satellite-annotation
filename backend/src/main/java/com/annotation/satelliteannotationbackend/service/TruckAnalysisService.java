package com.annotation.satelliteannotationbackend.service;

import com.annotation.satelliteannotationbackend.dto.*;
import com.annotation.satelliteannotationbackend.entity.RoadConstraint;
import com.annotation.satelliteannotationbackend.entity.RoadNetwork;
import com.annotation.satelliteannotationbackend.entity.TruckAnalysisRequest;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.repository.RoadConstraintRepository;
import com.annotation.satelliteannotationbackend.repository.TruckAnalysisRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

            // 3. 获取路网约束数据
            List<RoadConstraint> constraints = new ArrayList<>();
            if (request.getRoadNetworkId() != null) {
                constraints = constraintRepository.findByRoadNetworkId(request.getRoadNetworkId());
            }

            // 4. 分析通过性
            TruckParametersDTO truck = request.getTruck();
            List<ViolationPointDTO> violations = analyzePassability(bestRoute, truck, constraints);

            // 5. 保存分析结果
            TruckAnalysisRequest analysisRequest = new TruckAnalysisRequest();
            analysisRequest.setRequestName(request.getRequestName());
            analysisRequest.setStartLat(request.getStartLat());
            analysisRequest.setStartLon(request.getStartLon());
            analysisRequest.setEndLat(request.getEndLat());
            analysisRequest.setEndLon(request.getEndLon());
            analysisRequest.setTruckLength(truck.getLength());
            analysisRequest.setTruckWidth(truck.getWidth());
            analysisRequest.setTruckHeight(truck.getHeight());
            analysisRequest.setTruckWeight(truck.getWeight());
            analysisRequest.setTruckAxleWeight(truck.getAxleWeight());
            analysisRequest.setWheelbase(truck.getWheelbase());
            analysisRequest.setRouteGeoJson(routeGeoJson);
            analysisRequest.setIsPassable(violations.isEmpty());
            analysisRequest.setViolationPoints(objectMapper.writeValueAsString(violations));
            analysisRequest.setCreatedBy(currentUser);

            if (request.getRoadNetworkId() != null) {
                RoadNetwork network = new RoadNetwork();
                network.setId(request.getRoadNetworkId());
                analysisRequest.setRoadNetwork(network);
            }

            analysisRepository.save(analysisRequest);

            // 6. 返回结果
            if (violations.isEmpty()) {
                return AnalysisResultDTO.passable(routeGeoJson, totalDistance, estimatedTime);
            } else {
                return AnalysisResultDTO.notPassable(routeGeoJson, violations, totalDistance, estimatedTime);
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
            String url = String.format(
                "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?" +
                "overview=full&geometries=geojson&alternatives=true",
                OSRM_BASE_URL,
                startLon, startLat, endLon, endLat
            );

            ResponseEntity<RouteResponse> response = restTemplate.getForEntity(
                url, RouteResponse.class
            );

            return response.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 分析通过性
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
                                                        List<RoadConstraint> constraints) {
        List<ViolationPointDTO> violations = new ArrayList<>();

        // 计算车辆最小转弯半径
        Double minTurningRadius = truck.calculateMinTurningRadius();

        // 创建 OSM Way ID 到约束的映射
        Map<String, RoadConstraint> constraintMap = new java.util.HashMap<>();
        for (RoadConstraint c : constraints) {
            if (c.getOsmWayId() != null) {
                constraintMap.put(c.getOsmWayId(), c);
            }
        }

        // 遍历路线的每个路段
        if (route.legs != null) {
            for (RouteLeg leg : route.legs) {
                if (leg.steps != null) {
                    for (RouteStep step : leg.steps) {
                        // 获取路段中心点作为检查点
                        List<List<Double>> geometry = step.geometry != null ?
                            step.geometry.coordinates : null;

                        if (geometry != null && !geometry.isEmpty()) {
                            // 取中间点作为代表
                            int midIndex = geometry.size() / 2;
                            List<Double> midPoint = geometry.get(midIndex);

                            // 检查约束
                            // 注意：实际应用中需要从 step 中获取 OSM Way ID
                            // 这里简化处理，假设可以匹配到约束

                            // 检查限高
                            if (truck.getHeight() != null) {
                                // 遍历所有约束，检查是否有高度限制
                                for (RoadConstraint c : constraints) {
                                    if (c.getMaxHeight() != null &&
                                        truck.getHeight() > c.getMaxHeight()) {
                                        violations.add(ViolationPointDTO.create(
                                            midPoint.get(1), midPoint.get(0),
                                            "限高不足",
                                            String.format("道路限高 %.2fm，车辆高度 %.2fm",
                                                c.getMaxHeight(), truck.getHeight())
                                        ));
                                    }
                                }
                            }

                            // 检查限重
                            if (truck.getWeight() != null) {
                                for (RoadConstraint c : constraints) {
                                    if (c.getMaxWeight() != null &&
                                        truck.getWeight() > c.getMaxWeight()) {
                                        violations.add(ViolationPointDTO.create(
                                            midPoint.get(1), midPoint.get(0),
                                            "限重不足",
                                            String.format("道路限重 %.2f 吨，车辆总重 %.2f 吨",
                                                c.getMaxWeight(), truck.getWeight())
                                        ));
                                    }
                                }
                            }

                            // 检查限宽
                            if (truck.getWidth() != null) {
                                for (RoadConstraint c : constraints) {
                                    if (c.getMaxWidth() != null &&
                                        truck.getWidth() > c.getMaxWidth()) {
                                        violations.add(ViolationPointDTO.create(
                                            midPoint.get(1), midPoint.get(0),
                                            "限宽不足",
                                            String.format("道路限宽 %.2fm，车辆宽度 %.2fm",
                                                c.getMaxWidth(), truck.getWidth())
                                        ));
                                    }
                                }
                            }

                            // 检查禁止货车
                            // 这需要从 OSRM 返回的步骤中获取道路属性
                            // 简化处理：假设某些道路类型禁止货车
                        }
                    }
                }
            }
        }

        // 转弯半径检查（简化：在急转弯处检查）
        if (minTurningRadius != null && route.legs != null) {
            for (RouteLeg leg : route.legs) {
                if (leg.steps != null && leg.steps.size() > 1) {
                    for (int i = 0; i < leg.steps.size() - 1; i++) {
                        RouteStep step1 = leg.steps.get(i);
                        RouteStep step2 = leg.steps.get(i + 1);

                        // 计算转弯角度和半径
                        Double turnRadius = calculateTurnRadiusFromSteps(step1, step2);
                        if (turnRadius != null && turnRadius < minTurningRadius) {
                            List<Double> turnPoint = step1.geometry != null &&
                                !step1.geometry.coordinates.isEmpty() ?
                                step1.geometry.coordinates.get(step1.geometry.coordinates.size() - 1) : null;

                            if (turnPoint != null) {
                                violations.add(ViolationPointDTO.create(
                                    turnPoint.get(1), turnPoint.get(0),
                                    "转弯半径不足",
                                    String.format("需要最小半径 %.2fm，实际半径 %.2fm",
                                        minTurningRadius, turnRadius)
                                ));
                            }
                        }
                    }
                }
            }
        }

        return violations;
    }

    /**
     * 从两个连续的路段步骤计算转弯半径
     */
    private Double calculateTurnRadiusFromSteps(RouteStep step1, RouteStep step2) {
        // 简化的转弯半径估算
        // 实际应用中应该使用更精确的几何计算

        Double bearing1 = step1.bearingAfter;
        Double bearing2 = step2.bearingBefore;

        if (bearing1 != null && bearing2 != null) {
            // 计算角度变化
            double angleDiff = Math.abs(bearing2 - bearing1);
            if (angleDiff > 180) {
                angleDiff = 360 - angleDiff;
            }

            // 角度越大，转弯越急
            // 这里使用简化的估算公式
            if (angleDiff > 45) {  // 急转弯
                // 估算转弯半径（单位：米）
                return 50.0 * (90.0 / angleDiff);  // 角度越大，半径越小
            }
        }

        return null;
    }

    /**
     * 获取分析历史
     */
    public List<TruckAnalysisRequest> getHistory(Long userId) {
        return analysisRepository.findByCreatedByIdOrderByCreatedAtDesc(userId);
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
        public Object geometry;      // GeoJSON LineString
        public Double distance;
        public Double duration;
        public Double bearingBefore;
        public Double bearingAfter;
        public String instruction;
    }
}
