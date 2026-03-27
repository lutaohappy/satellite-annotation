package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.ApiResponse;
import com.annotation.satelliteannotationbackend.dto.ProjectDTO;
import com.annotation.satelliteannotationbackend.entity.Annotation;
import com.annotation.satelliteannotationbackend.entity.Project;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.repository.AnnotationRepository;
import com.annotation.satelliteannotationbackend.repository.ProjectRepository;
import com.annotation.satelliteannotationbackend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 项目控制器
 */
@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final AnnotationRepository annotationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ProjectController(ProjectRepository projectRepository,
                           AnnotationRepository annotationRepository,
                           UserRepository userRepository,
                           ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.annotationRepository = annotationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取当前用户的所有项目
     */
    @GetMapping
    public ResponseEntity<?> getProjects(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        List<Project> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<ProjectDTO> dtos = projects.stream().map(project -> {
            ProjectDTO dto = new ProjectDTO();
            dto.setId(project.getId());
            dto.setName(project.getName());
            dto.setDescription(project.getDescription());
            dto.setUserId(project.getUserId());
            dto.setCrs(project.getCrs());
            dto.setCreatedAt(project.getCreatedAt());
            dto.setUpdatedAt(project.getUpdatedAt());

            // 统计标注数量
            long count = annotationRepository.countByProjectId(project.getId());
            dto.setAnnotationCount(count);

            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    /**
     * 创建新项目
     */
    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Map<String, String> body,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        String name = body.get("name");
        String description = body.get("description");
        String crs = body.getOrDefault("crs", "EPSG:3857");

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("项目名称不能为空"));
        }

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Project project = new Project();
        project.setName(name);
        project.setDescription(description);
        project.setCrs(crs);
        project.setUserId(user.getId());

        Project saved = projectRepository.save(project);

        ProjectDTO dto = new ProjectDTO();
        dto.setId(saved.getId());
        dto.setName(saved.getName());
        dto.setDescription(saved.getDescription());
        dto.setUserId(saved.getUserId());
        dto.setCrs(saved.getCrs());
        dto.setCreatedAt(saved.getCreatedAt());
        dto.setUpdatedAt(saved.getUpdatedAt());
        dto.setAnnotationCount(0L);

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /**
     * 获取项目详情及标注数据
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(@PathVariable Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("项目不存在"));

        ProjectDTO dto = new ProjectDTO();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setUserId(project.getUserId());
        dto.setCrs(project.getCrs());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());

        // 获取项目的所有标注
        List<Annotation> annotations = annotationRepository.findByProjectId(id);

        Map<String, Object> result = new HashMap<>();
        result.put("project", dto);
        result.put("annotations", annotations);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 保存项目标注数据
     */
    @PostMapping("/{id}/annotations")
    public ResponseEntity<?> saveAnnotations(@PathVariable Long id,
                                             @RequestBody List<Map<String, Object>> annotations,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("项目不存在"));

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 删除项目原有的标注
        List<Annotation> existingAnnotations = annotationRepository.findByProjectId(id);
        annotationRepository.deleteAll(existingAnnotations);

        // 保存新标注
        List<Annotation> savedAnnotations = new ArrayList<>();
        for (Map<String, Object> annData : annotations) {
            Annotation annotation = new Annotation();
            annotation.setProjectId(id);
            annotation.setUser(user);

            String type = (String) annData.get("type");
            annotation.setType(type);

            // 存储 GeoJSON
            Object geometry = annData.get("geometry");
            if (geometry != null) {
                try {
                    annotation.setGeometry(objectMapper.writeValueAsString(geometry));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Object properties = annData.get("properties");
            if (properties != null) {
                try {
                    annotation.setProperties(objectMapper.writeValueAsString(properties));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 从 properties 中获取 category 和 symbolId
            if (properties instanceof Map) {
                Map<?, ?> props = (Map<?, ?>) properties;
                if (props.get("category") != null) {
                    annotation.setCategory(props.get("category").toString());
                }
                if (props.get("symbolId") != null) {
                    annotation.setSymbolId(props.get("symbolId").toString());
                }
            }

            savedAnnotations.add(annotationRepository.save(annotation));
        }

        // 更新项目更新时间
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);

        return ResponseEntity.ok(ApiResponse.success(savedAnnotations.size() + " 个标注已保存"));
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("项目不存在"));

        // 删除项目下的所有标注
        List<Annotation> annotations = annotationRepository.findByProjectId(id);
        annotationRepository.deleteAll(annotations);

        // 删除项目
        projectRepository.delete(project);

        return ResponseEntity.ok(ApiResponse.success("项目已删除"));
    }

    /**
     * 导出项目为 GeoJSON
     */
    @GetMapping("/{id}/export/geojson")
    public ResponseEntity<?> exportGeoJson(@PathVariable Long id) {
        List<Annotation> annotations = annotationRepository.findByProjectId(id);

        List<Map<String, Object>> features = new ArrayList<>();
        for (Annotation ann : annotations) {
            try {
                Map<String, Object> feature = new HashMap<>();
                feature.put("type", "Feature");

                // 解析 Geometry
                if (ann.getGeometry() != null) {
                    feature.put("geometry", objectMapper.readValue(ann.getGeometry(), Map.class));
                }

                // 解析 Properties
                Map<String, Object> props = new HashMap<>();
                if (ann.getProperties() != null) {
                    props = objectMapper.readValue(ann.getProperties(), Map.class);
                }
                props.put("id", ann.getId());
                props.put("type", ann.getType());
                props.put("category", ann.getCategory());
                props.put("symbolId", ann.getSymbolId());
                feature.put("properties", props);

                features.add(feature);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Map<String, Object> geojson = new HashMap<>();
        geojson.put("type", "FeatureCollection");
        geojson.put("features", features);

        return ResponseEntity.ok(ApiResponse.success(geojson));
    }

    /**
     * 导出项目为 Shapefile (Zip)
     */
    @GetMapping("/{id}/export/shapefile")
    public ResponseEntity<?> exportShapefile(@PathVariable Long id) throws IOException {
        List<Annotation> annotations = annotationRepository.findByProjectId(id);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        // 生成 SHP 内容（简化的 WKT 格式）
        StringBuilder shpContent = new StringBuilder();
        StringBuilder dbfContent = new StringBuilder();
        StringBuilder shxContent = new StringBuilder();

        // SHP 文件头（简化版）
        // 实际生产环境应该使用 GeoTools 库生成标准 Shapefile

        // 这里生成一个包含 WKT 几何的简单文本文件作为替代
        StringBuilder wktContent = new StringBuilder("id,type,category,wkt_geometry\n");

        for (Annotation ann : annotations) {
            try {
                // 直接使用存储的 GeoJSON 字符串作为 WKT 文件的几何
                String wkt = ann.getGeometry() != null ? ann.getGeometry() : "POINT EMPTY";

                String category = ann.getCategory() != null ? ann.getCategory() : "";
                wktContent.append(ann.getId()).append(",")
                        .append(ann.getType()).append(",")
                        .append(category).append(",\"")
                        .append(wkt.replace("\"", "\"\"")).append("\"\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 添加 WKT 文件到 Zip
        ZipEntry wktEntry = new ZipEntry("annotations.wkt");
        zos.putNextEntry(wktEntry);
        zos.write(wktContent.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        // 添加 GeoJSON 文件到 Zip
        ZipEntry jsonEntry = new ZipEntry("annotations.json");
        zos.putNextEntry(jsonEntry);
        zos.write(objectMapper.writeValueAsString(annotations).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();

        zos.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDispositionFormData("attachment", "project_" + id + "_shapefile.zip");

        return ResponseEntity.ok()
                .headers(headers)
                .body(baos.toByteArray());
    }

    /**
     * 导入 GeoJSON 到项目
     */
    @PostMapping("/{id}/import")
    public ResponseEntity<?> importGeoJson(@PathVariable Long id,
                                           @RequestParam("file") MultipartFile file,
                                           @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("项目不存在"));

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        Map<String, Object> geojson = objectMapper.readValue(content, Map.class);

        if (!"FeatureCollection".equals(geojson.get("type"))) {
            return ResponseEntity.badRequest().body(ApiResponse.error("无效的 GeoJSON 格式"));
        }

        List<Map<String, Object>> features = (List<Map<String, Object>>) geojson.get("features");
        int count = 0;

        for (Map<String, Object> feature : features) {
            try {
                Annotation annotation = new Annotation();
                annotation.setProjectId(id);
                annotation.setUser(user);

                Map<?, ?> geometry = (Map<?, ?>) feature.get("geometry");
                Map<?, ?> properties = (Map<?, ?>) feature.get("properties");

                String type = "Point";
                if (properties != null && properties.get("type") != null) {
                    type = properties.get("type").toString();
                }
                annotation.setType(type);
                annotation.setGeometry(objectMapper.writeValueAsString(geometry));
                annotation.setProperties(objectMapper.writeValueAsString(properties));

                if (properties != null) {
                    if (properties.get("category") != null) {
                        annotation.setCategory(properties.get("category").toString());
                    }
                    if (properties.get("symbolId") != null) {
                        annotation.setSymbolId(properties.get("symbolId").toString());
                    }
                }

                annotationRepository.save(annotation);
                count++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);

        return ResponseEntity.ok(ApiResponse.success("成功导入 " + count + " 个标注"));
    }
}
