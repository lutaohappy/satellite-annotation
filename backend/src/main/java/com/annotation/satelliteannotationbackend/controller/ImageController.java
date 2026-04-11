package com.annotation.satelliteannotationbackend.controller;

import com.annotation.satelliteannotationbackend.dto.ApiResponse;
import com.annotation.satelliteannotationbackend.dto.AdjustmentParams;
import com.annotation.satelliteannotationbackend.entity.Image;
import com.annotation.satelliteannotationbackend.entity.UploadBatch;
import com.annotation.satelliteannotationbackend.entity.User;
import com.annotation.satelliteannotationbackend.entity.Project;
import com.annotation.satelliteannotationbackend.repository.ImageRepository;
import com.annotation.satelliteannotationbackend.repository.UploadBatchRepository;
import com.annotation.satelliteannotationbackend.repository.UserRepository;
import com.annotation.satelliteannotationbackend.repository.ProjectRepository;
import com.annotation.satelliteannotationbackend.util.GeoTiffProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 卫星影像控制器
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final ProjectRepository projectRepository;

    @Value("${image.upload-dir:uploads/images/}")
    private String uploadDir;

    public ImageController(ImageRepository imageRepository,
                          UserRepository userRepository,
                          UploadBatchRepository uploadBatchRepository,
                          ProjectRepository projectRepository) {
        this.imageRepository = imageRepository;
        this.userRepository = userRepository;
        this.uploadBatchRepository = uploadBatchRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * 获取所有影像列表
     */
    @GetMapping
    public ResponseEntity<?> listImages(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "batchId", required = false) String batchId
    ) {
        List<Image> images;
        if (batchId != null) {
            images = imageRepository.findByBatchId(batchId);
        } else if (projectId != null) {
            images = imageRepository.findByProjectId(projectId);
        } else {
            images = imageRepository.findAll();
        }
        return ResponseEntity.ok(ApiResponse.success(images));
    }

    /**
     * 获取影像详情（带智能加载逻辑）
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getImage(@PathVariable Long id) {
        return imageRepository.findById(id)
                .map(image -> {
                    boolean needReextract = false;

                    // 检查是否需要重新提取地理参考信息
                    if (image.getHasGeoreference() == null || !image.getHasGeoreference()) {
                        needReextract = true;
                    } else if (image.getMinX() != null && image.getMaxX() != null &&
                               image.getMinY() != null && image.getMaxY() != null) {
                        // 检查坐标范围是否异常（范围太小可能是 CRS 未转换）
                        double xRange = image.getMaxX() - image.getMinX();
                        double yRange = image.getMaxY() - image.getMinY();
                        // 如果范围小于 1 度（约 111km），可能是经纬度坐标而非投影坐标
                        if (xRange < 1.0 && yRange < 1.0 && (xRange > 0.001 || yRange > 0.001)) {
                            System.out.println("[ImageController] 检测到可能的 CRS 问题，xRange=" + xRange +
                                ", yRange=" + yRange + "，需要重新提取");
                            needReextract = true;
                        }
                    }

                    if (needReextract) {
                        System.out.println("[ImageController] 开始重新提取地理参考信息，imageId=" + id);
                        GeoTiffMetadata metadata = extractMetadata(image.getFilePath());
                        if (metadata.getMinX() != 0.0 || metadata.getMinY() != 0.0) {
                            // 更新数据库
                            image.setMinX(metadata.getMinX());
                            image.setMinY(metadata.getMinY());
                            image.setMaxX(metadata.getMaxX());
                            image.setMaxY(metadata.getMaxY());
                            image.setHasGeoreference(true);
                            imageRepository.save(image);
                            System.out.println("[ImageController] 地理参考信息已更新：minX=" + metadata.getMinX() +
                                ", minY=" + metadata.getMinY() + ", maxX=" + metadata.getMaxX() +
                                ", maxY=" + metadata.getMaxY());
                        } else {
                            System.out.println("[ImageController] 无法提取地理参考信息，使用默认值");
                        }
                    }
                    return ResponseEntity.ok(ApiResponse.success(image));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 上传 GeoTIFF 影像
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "tfwFile", required = false) MultipartFile tfwFile
    ) {
        try {
            // 验证文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null ||
                !(originalFilename.toLowerCase().endsWith(".tif") ||
                  originalFilename.toLowerCase().endsWith(".tiff"))) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("只支持 GeoTIFF 文件 (.tif, .tiff)"));
            }

            // 生成唯一文件名
            String filename = UUID.randomUUID() + "_" + originalFilename;
            Path path = Paths.get(uploadDir + filename);
            Files.createDirectories(path.getParent());
            Files.write(path, file.getBytes());

            // 如果有上传 TFW 文件，保存到同名位置
            if (tfwFile != null && !tfwFile.isEmpty()) {
                String tfwFilename = filename.substring(0, filename.lastIndexOf('.')) + ".tfw";
                Path tfwPath = Paths.get(uploadDir + tfwFilename);
                Files.write(tfwPath, tfwFile.getBytes());
                System.out.println("[ImageController] TFW 文件已保存：" + tfwPath);
            }

            // 提取 GeoTIFF 元数据
            GeoTiffMetadata metadata = extractMetadata(path.toString());

            // 保存到数据库
            Image image = new Image();
            image.setName(name != null ? name : originalFilename);
            image.setFilePath(uploadDir + filename);
            image.setCrs(metadata.getCrs());
            image.setMinX(metadata.getMinX());
            image.setMinY(metadata.getMinY());
            image.setMaxX(metadata.getMaxX());
            image.setMaxY(metadata.getMaxY());
            image.setFileSize(file.getSize());
            image.setWidth(metadata.getWidth());
            image.setHeight(metadata.getHeight());
            image.setNumBands(metadata.getNumBands());

            // 设置当前用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                userRepository.findByUsername(auth.getName())
                        .ifPresent(image::setUser);
            }

            if (projectId != null) {
                image.setProjectId(projectId);
            }

            Image saved = imageRepository.save(image);
            return ResponseEntity.ok(ApiResponse.success(saved));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("上传失败：" + e.getMessage()));
        }
    }

    /**
     * 批量上传 GeoTIFF 影像
     */
    @PostMapping("/upload/batch")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "tfwFiles", required = false) List<MultipartFile> tfwFiles,
            @RequestParam(value = "batchUuid", required = false) String batchUuid,
            @RequestParam(value = "batchName", required = false) String batchName
    ) {
        try {
            // 获取当前用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = null;
            if (auth != null && auth.getName() != null) {
                currentUser = userRepository.findByUsername(auth.getName()).orElse(null);
            }

            UploadBatch batch;
            boolean isNewBatch = false;

            // 如果指定了批次 UUID，使用已有批次；否则创建新批次
            if (batchUuid != null && !batchUuid.isEmpty()) {
                batch = uploadBatchRepository.findByBatchUuid(batchUuid)
                        .orElseThrow(() -> new RuntimeException("批次不存在"));
                // 如果提供了新名称，更新批次名称
                if (batchName != null && !batchName.isEmpty()) {
                    batch.setName(batchName);
                }
            } else {
                // 创建新批次
                batchUuid = UUID.randomUUID().toString();
                batch = new UploadBatch();
                batch.setBatchUuid(batchUuid);
                batch.setUser(currentUser);
                batch.setName(batchName); // 可以为 null
                isNewBatch = true;
            }

            // 更新文件数量（累加）
            if (batch.getFileCount() == null) {
                batch.setFileCount(0);
            }
            batch.setFileCount(batch.getFileCount() + files.size());
            if (projectId != null) {
                batch.setProjectId(projectId);
            }
            uploadBatchRepository.save(batch);

            // 保存所有文件
            List<Image> savedImages = new ArrayList<>();
            for (MultipartFile file : files) {
                try {
                    String originalFilename = file.getOriginalFilename();
                    if (originalFilename == null ||
                        !(originalFilename.toLowerCase().endsWith(".tif") ||
                          originalFilename.toLowerCase().endsWith(".tiff"))) {
                        continue; // 跳过无效文件
                    }

                    // 生成唯一文件名 - 使用 UUID + 原始扩展名，避免中文乱码问题
                    String extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
                    String filename = UUID.randomUUID() + extension;
                    Path path = Paths.get(uploadDir + filename);
                    Files.createDirectories(path.getParent());
                    Files.write(path, file.getBytes());

                    // 提取 GeoTIFF 元数据
                    GeoTiffMetadata metadata = extractMetadata(path.toString());

                    // 保存到数据库 - 原始文件名仅用于显示，存储在 name 字段
                    Image image = new Image();
                    image.setName(originalFilename);
                    image.setFilePath(uploadDir + filename);
                    image.setBatchId(batchUuid);
                    image.setProjectId(projectId);
                    image.setUser(currentUser);
                    image.setCrs(metadata.getCrs());
                    image.setMinX(metadata.getMinX());
                    image.setMinY(metadata.getMinY());
                    image.setMaxX(metadata.getMaxX());
                    image.setMaxY(metadata.getMaxY());
                    image.setFileSize(file.getSize());
                    image.setWidth(metadata.getWidth());
                    image.setHeight(metadata.getHeight());
                    image.setNumBands(metadata.getNumBands());
                    image.setHasGeoreference(metadata.getMinX() != 0.0 || metadata.getMinY() != 0.0);

                    Image saved = imageRepository.save(image);
                    savedImages.add(saved);
                } catch (Exception e) {
                    e.printStackTrace();
                    // 单个文件失败不影响其他文件
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("batchUuid", batchUuid);
            result.put("fileCount", savedImages.size());
            result.put("images", savedImages);
            result.put("isNewBatch", isNewBatch);
            result.put("batch", batch);
            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("批量上传失败：" + e.getMessage()));
        }
    }

    /**
     * 修复文件路径（本地开发环境适配生产路径）
     */
    private String fixFilePath(String originalPath) {
        System.out.println("[PathFix] 原始路径：" + originalPath);
        // 如果文件存在，直接返回原路径
        if (Files.exists(Paths.get(originalPath))) {
            System.out.println("[PathFix] 文件存在，使用原路径");
            return originalPath;
        }
        // 处理相对路径 uploads/images/xxx -> 使用配置的 uploadDir
        if (originalPath.startsWith("uploads/images/")) {
            String fixedPath = uploadDir + originalPath.substring("uploads/images/".length());
            if (Files.exists(Paths.get(fixedPath))) {
                System.out.println("[PathFix] 使用配置路径：" + fixedPath);
                return fixedPath;
            }
        }
        // 如果数据库存储的是 /opt/uploads/images/ 但实际文件在 uploadDir
        if (originalPath.startsWith("/opt/uploads/images/")) {
            String fixedPath = uploadDir + originalPath.substring("/opt/uploads/images/".length());
            if (Files.exists(Paths.get(fixedPath))) {
                System.out.println("[PathFix] 使用配置路径（生产转本地）: " + fixedPath);
                return fixedPath;
            }
            // 服务器上，直接使用 /opt/uploads/images/ 路径
            if (Files.exists(Paths.get(originalPath))) {
                System.out.println("[PathFix] 使用生产路径：" + originalPath);
                return originalPath;
            }
        }
        System.out.println("[PathFix] 未找到文件，返回原路径：" + originalPath);
        return originalPath;
    }

    /**
     * 保存影像调整（覆盖原文件）
     */
    @PostMapping("/{id}/adjust")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> saveAdjustment(
            @PathVariable Long id,
            @RequestBody AdjustmentParams params
    ) {
        try {
            Image image = imageRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Image not found"));

            double brightness = params.getBrightness() != null ? params.getBrightness() : 1.0;
            double contrast = params.getContrast() != null ? params.getContrast() : 1.0;
            double gamma = params.getGamma() != null ? params.getGamma() : 1.0;

            // 修复文件路径（本地开发适配生产路径）
            String originalFilePath = fixFilePath(image.getFilePath());

            // 获取原文件信息
            Path originalPath = Paths.get(originalFilePath);
            long originalFileSize = Files.size(originalPath);
            String originalFileTime = Files.getLastModifiedTime(originalPath).toString();

            // 创建临时文件
            String tempFilename = "temp_" + UUID.randomUUID() + ".tif";
            Path tempPath = Paths.get(uploadDir + tempFilename);

            System.out.println("[saveAdjustment] ========== 开始保存调整 ==========");
            System.out.println("[saveAdjustment] 影像 ID: " + id);
            System.out.println("[saveAdjustment] 影像名称：" + image.getName());
            System.out.println("[saveAdjustment] 调整参数：brightness=" + brightness + ", contrast=" + contrast + ", gamma=" + gamma);
            System.out.println("[saveAdjustment] 原文件路径：" + originalFilePath);
            System.out.println("[saveAdjustment] 原文件大小：" + originalFileSize + " bytes");
            System.out.println("[saveAdjustment] 原文件时间：" + originalFileTime);

            // 应用调整到临时文件
            GeoTiffProcessor.applyAdjustments(
                originalFilePath,
                tempPath.toString(),
                brightness,
                contrast,
                gamma
            );

            // 获取临时文件信息
            long tempFileSize = Files.size(tempPath);
            System.out.println("[saveAdjustment] 调整后临时文件大小：" + tempFileSize + " bytes");

            // 备份原文件
            String backupFilename = "backup_" + UUID.randomUUID() + ".tif";
            Path backupPath = Paths.get(uploadDir + backupFilename);
            Files.copy(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            // 复制 TFW 备份（如果有）
            String originalTfw = originalFilePath.substring(0,
                    originalFilePath.lastIndexOf('.')) + ".tfw";
            String backupTfw = backupPath.toString().substring(0,
                    backupPath.toString().lastIndexOf('.')) + ".tfw";
            Path originalTfwPath = Paths.get(originalTfw);
            if (Files.exists(originalTfwPath)) {
                Files.copy(originalTfwPath, Paths.get(backupTfw), StandardCopyOption.REPLACE_EXISTING);
            }

            // 用临时文件覆盖原文件
            Files.copy(tempPath, originalPath, StandardCopyOption.REPLACE_EXISTING);

            // 复制 TFW 文件（如果有）
            String tempTfw = tempPath.toString().substring(0,
                    tempPath.toString().lastIndexOf('.')) + ".tfw";
            Path tempTfwPath = Paths.get(tempTfw);
            if (Files.exists(tempTfwPath)) {
                Files.copy(tempTfwPath, Paths.get(originalTfw), StandardCopyOption.REPLACE_EXISTING);
            }

            // 获取新文件信息
            long newFileSize = Files.size(originalPath);
            String newFileTime = Files.getLastModifiedTime(originalPath).toString();
            System.out.println("[saveAdjustment] 新文件大小：" + newFileSize + " bytes");
            System.out.println("[saveAdjustment] 新文件时间：" + newFileTime);
            System.out.println("[saveAdjustment] 存储位置：" + originalFilePath);

            // 删除临时文件
            Files.deleteIfExists(tempPath);
            Files.deleteIfExists(tempTfwPath);

            // 删除备份文件（成功覆盖后）
            Files.deleteIfExists(backupPath);
            Files.deleteIfExists(Paths.get(backupTfw));

            // 更新数据库：清除调整参数（因为已经应用到文件）
            image.setAdjustmentParams(null);
            imageRepository.save(image);

            System.out.println("[saveAdjustment] ========== 保存完成 ==========");

            return ResponseEntity.ok(ApiResponse.success(image));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("保存调整影像失败：" + e.getMessage()));
        }
    }

    /**
     * 保存调整后的影像（另存为新影像）
     */
    @PostMapping("/{id}/save-adjusted")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> saveAdjusted(
            @PathVariable Long id,
            @RequestBody AdjustmentParams params
    ) {
        try {
            Image originalImage = imageRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Image not found"));

            double brightness = params.getBrightness() != null ? params.getBrightness() : 1.0;
            double contrast = params.getContrast() != null ? params.getContrast() : 1.0;
            double gamma = params.getGamma() != null ? params.getGamma() : 1.0;
            String newName = params.getNewName();

            // 修复文件路径（本地开发适配生产路径）
            String originalFilePath = fixFilePath(originalImage.getFilePath());

            // 获取原文件信息
            Path originalPathObj = Paths.get(originalFilePath);
            long originalFileSize = Files.size(originalPathObj);
            String originalFileModTime = Files.getLastModifiedTime(originalPathObj).toString();

            // 应用调整并保存新文件
            String adjustedFilename = "adjusted_" + UUID.randomUUID() + ".tif";
            Path adjustedPath = Paths.get(uploadDir + adjustedFilename);

            System.out.println("[saveAdjusted] ========== 开始另存为新影像 ==========");
            System.out.println("[saveAdjusted] 原始影像 ID: " + id);
            System.out.println("[saveAdjusted] 原始影像名称：" + originalImage.getName());
            System.out.println("[saveAdjusted] 原始影像文件路径（数据库）: " + originalImage.getFilePath());
            System.out.println("[saveAdjusted] 原始影像实际文件路径：" + originalFilePath);
            System.out.println("[saveAdjusted] 原始影像文件大小：" + originalFileSize + " bytes");
            System.out.println("[saveAdjusted] 原始影像文件时间：" + originalFileModTime);
            System.out.println("[saveAdjusted] 调整参数：brightness=" + brightness + ", contrast=" + contrast + ", gamma=" + gamma);
            System.out.println("[saveAdjusted] 新影像文件名：" + adjustedFilename);
            System.out.println("[saveAdjusted] 新影像完整路径：" + adjustedPath.toString());
            System.out.println("[saveAdjusted] 新影像名称：" + (newName != null ? newName : ("Adjusted_" + originalImage.getName())));

            // 应用调整
            GeoTiffProcessor.applyAdjustments(
                originalFilePath,
                adjustedPath.toString(),
                brightness,
                contrast,
                gamma
            );

            // 验证新文件
            if (!Files.exists(adjustedPath)) {
                System.out.println("[saveAdjusted] 错误：新文件创建失败！");
                throw new RuntimeException("新文件创建失败");
            }
            long newFileSize = Files.size(adjustedPath);
            String newFileModTime = Files.getLastModifiedTime(adjustedPath).toString();
            System.out.println("[saveAdjusted] 新文件大小：" + newFileSize + " bytes");
            System.out.println("[saveAdjusted] 新文件时间：" + newFileModTime);

            // 如果有 TFW 文件，复制一份
            String originalTfw = originalImage.getFilePath().substring(0,
                    originalImage.getFilePath().lastIndexOf('.')) + ".tfw";
            String adjustedTfw = adjustedPath.toString().substring(0,
                    adjustedPath.toString().lastIndexOf('.')) + ".tfw";
            Path originalTfwPath = Paths.get(originalTfw);
            if (Files.exists(originalTfwPath)) {
                Files.copy(originalTfwPath, Paths.get(adjustedTfw), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("[saveAdjusted] TFW 文件已复制：" + adjustedTfw);
            }

            // 创建新的 Image 记录
            Image newImage = new Image();
            newImage.setName(newName != null && !newName.isEmpty() ? newName : ("Adjusted_" + originalImage.getName()));
            // 注意：数据库存储路径使用 uploadDir + filename，与原始影像一致
            newImage.setFilePath(uploadDir + adjustedFilename);
            newImage.setBatchId(originalImage.getBatchId());
            newImage.setProjectId(originalImage.getProjectId());
            newImage.setUser(originalImage.getUser());
            newImage.setCrs(originalImage.getCrs());
            newImage.setMinX(originalImage.getMinX());
            newImage.setMinY(originalImage.getMinY());
            newImage.setMaxX(originalImage.getMaxX());
            newImage.setMaxY(originalImage.getMaxY());
            newImage.setWidth(originalImage.getWidth());
            newImage.setHeight(originalImage.getHeight());
            newImage.setNumBands(originalImage.getNumBands());
            newImage.setHasGeoreference(originalImage.getHasGeoreference());
            newImage.setFileSize(newFileSize);

            Image saved = imageRepository.save(newImage);
            System.out.println("[saveAdjusted] 新影像已保存到数据库：");
            System.out.println("[saveAdjusted]   - ID: " + saved.getId());
            System.out.println("[saveAdjusted]   - Name: " + saved.getName());
            System.out.println("[saveAdjusted]   - FilePath: " + saved.getFilePath());
            System.out.println("[saveAdjusted]   - FileSize: " + saved.getFileSize() + " bytes");
            System.out.println("[saveAdjusted]   - BatchId: " + saved.getBatchId());

            // 更新批次的文件数量
            if (originalImage.getBatchId() != null) {
                uploadBatchRepository.findByBatchUuid(originalImage.getBatchId())
                    .ifPresent(batch -> {
                        batch.setFileCount(batch.getFileCount() + 1);
                        uploadBatchRepository.save(batch);
                        System.out.println("[saveAdjusted] 批次文件数量已更新：" + batch.getFileCount());
                    });
            }

            System.out.println("[saveAdjusted] ========== 保存完成 ==========");

            return ResponseEntity.ok(ApiResponse.success(saved));

        } catch (Exception e) {
            System.out.println("[saveAdjusted] 异常：" + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("保存调整影像失败：" + e.getMessage()));
        }
    }

    /**
     * 获取用户的所有上传批次
     */
    @GetMapping("/batches")
    public ResponseEntity<?> getBatches() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
                // 未认证用户返回空列表而不是 404
                return ResponseEntity.ok(ApiResponse.success(List.of()));
            }

            User user = userRepository.findByUsername(auth.getName()).orElse(null);
            if (user == null) {
                return ResponseEntity.ok(ApiResponse.success(List.of()));
            }

            List<UploadBatch> batches = uploadBatchRepository.findByUserId(user.getId());
            return ResponseEntity.ok(ApiResponse.success(batches));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("获取批次列表失败：" + e.getMessage()));
        }
    }

    /**
     * 更新批次名称
     */
    @PutMapping("/batches/{batchUuid}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> updateBatch(
            @PathVariable String batchUuid,
            @RequestBody Map<String, String> body
    ) {
        try {
            UploadBatch batch = uploadBatchRepository.findByBatchUuid(batchUuid)
                    .orElseThrow(() -> new RuntimeException("批次不存在"));

            String newName = body.get("name");
            if (newName != null && !newName.isEmpty()) {
                batch.setName(newName);
                uploadBatchRepository.save(batch);
            }

            return ResponseEntity.ok(ApiResponse.success(batch));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("更新批次失败：" + e.getMessage()));
        }
    }

    /**
     * 删除批次及其关联的影像文件
     */
    @DeleteMapping("/batches/{batchUuid}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> deleteBatch(@PathVariable String batchUuid) {
        try {
            UploadBatch batch = uploadBatchRepository.findByBatchUuid(batchUuid)
                    .orElseThrow(() -> new RuntimeException("批次不存在"));

            // 获取批次中的所有影像
            List<Image> images = imageRepository.findByBatchId(batchUuid);

            // 删除文件
            for (Image image : images) {
                try {
                    Path path = Paths.get(image.getFilePath());
                    if (Files.exists(path)) {
                        Files.delete(path);
                    }
                    // 删除关联的 TFW 文件
                    String tfwPath = image.getFilePath().substring(0,
                            image.getFilePath().lastIndexOf('.')) + ".tfw";
                    Path tfw = Paths.get(tfwPath);
                    if (Files.exists(tfw)) {
                        Files.delete(tfw);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // 删除影像记录
            imageRepository.deleteAll(images);

            // 删除批次记录
            uploadBatchRepository.delete(batch);

            return ResponseEntity.ok(ApiResponse.success("批次已删除"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("删除批次失败：" + e.getMessage()));
        }
    }

    /**
     * 删除影像
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteImage(@PathVariable Long id) throws IOException {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        // 保存批次 ID 以便后续更新
        String batchId = image.getBatchId();

        // 删除文件
        Path path = Paths.get(image.getFilePath());
        if (Files.exists(path)) {
            Files.delete(path);
        }

        // 删除关联的 TFW 文件
        String tfwPath = image.getFilePath().substring(0,
                image.getFilePath().lastIndexOf('.')) + ".tfw";
        Path tfw = Paths.get(tfwPath);
        if (Files.exists(tfw)) {
            Files.delete(tfw);
        }

        imageRepository.delete(image);

        // 更新批次的文件数量
        if (batchId != null && !batchId.isEmpty()) {
            uploadBatchRepository.findByBatchUuid(batchId).ifPresent(batch -> {
                int newCount = Math.max(0, batch.getFileCount() - 1);
                batch.setFileCount(newCount);
                uploadBatchRepository.save(batch);
            });
        }

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 为矢量标注创建透明占位影像
     * 当项目只有矢量标注没有影像时，创建一个覆盖所有标注范围的透明 GeoTIFF
     * 这样可以确保图层顺序正确，矢量不会被后续加载的影像覆盖
     */
    @PostMapping("/create-placeholder")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> createPlaceholder(
            @RequestBody Map<String, Object> params
    ) {
        try {
            // 获取标注范围
            Double minX = getDoubleParam(params, "minX");
            Double minY = getDoubleParam(params, "minY");
            Double maxX = getDoubleParam(params, "maxX");
            Double maxY = getDoubleParam(params, "maxY");
            Long projectId = params.get("projectId") != null ? Long.valueOf(params.get("projectId").toString()) : null;

            if (minX == null || minY == null || maxX == null || maxY == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("缺少必要的范围参数"));
            }

            // 获取当前用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = null;
            if (auth != null && auth.getName() != null) {
                currentUser = userRepository.findByUsername(auth.getName()).orElse(null);
            }

            // 生成透明 GeoTIFF 文件
            String filename = "placeholder_" + UUID.randomUUID() + ".tif";
            Path filePath = Paths.get(uploadDir + filename);
            Files.createDirectories(filePath.getParent());

            System.out.println("[CreatePlaceholder] 开始创建透明占位影像");
            System.out.println("[CreatePlaceholder] 范围：minX=" + minX + ", minY=" + minY + ", maxX=" + maxX + ", maxY=" + maxY);
            System.out.println("[CreatePlaceholder] 文件路径：" + filePath);

            // 使用 GeoTiffProcessor 创建透明 GeoTIFF
            GeoTiffProcessor.createTransparentGeoTiff(
                filePath.toString(),
                minX, minY, maxX, maxY
            );

            // 验证文件是否真的是透明的
            Path createdFile = Paths.get(filePath.toString());
            if (Files.exists(createdFile)) {
                long fileSize = Files.size(createdFile);
                System.out.println("[CreatePlaceholder] 文件创建成功，大小：" + fileSize + " bytes");
                // 透明 256x256 RGBA TIFF 应该非常小（接近空文件），如果有内容说明不是完全透明
                if (fileSize > 10000) {
                    System.out.println("[CreatePlaceholder] 警告：文件大小异常 (" + fileSize + " bytes)，可能不是完全透明");
                } else {
                    System.out.println("[CreatePlaceholder] 文件大小正常，应该是透明的");
                }
            }

            // 计算影像尺寸（用于元数据）
            int width = 256;  // 占位影像使用较小尺寸
            int height = 256;

            // 创建 Image 实体
            Image image = new Image();
            image.setName("Transparent Placeholder");
            image.setFilePath(uploadDir + filename);
            image.setCrs("EPSG:3857");
            image.setMinX(minX);
            image.setMinY(minY);
            image.setMaxX(maxX);
            image.setMaxY(maxY);
            image.setWidth(width);
            image.setHeight(height);
            image.setNumBands(4);  // RGBA
            image.setFileSize(Files.size(filePath));
            image.setHasGeoreference(true);
            image.setProjectId(projectId);
            image.setUser(currentUser);

            // 保存到数据库
            Image saved = imageRepository.save(image);

            // 如果指定了项目 ID，更新项目的 baseImageId
            if (projectId != null) {
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new RuntimeException("项目不存在"));
                project.setBaseImageId(saved.getId());
                projectRepository.save(project);
                System.out.println("[CreatePlaceholder] 项目 " + projectId + " 的 baseImageId 已更新为 " + saved.getId());
            }

            System.out.println("[CreatePlaceholder] 透明占位影像创建成功，ID=" + saved.getId());

            return ResponseEntity.ok(ApiResponse.success(saved));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("创建占位影像失败：" + e.getMessage()));
        }
    }

    /**
     * 从 Map 中提取 double 值
     */
    private Double getDoubleParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.valueOf((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 下载影像文件（原始 TIFF 文件）
     */
    @GetMapping("/{id}/file")
    public void downloadImage(@PathVariable Long id, HttpServletResponse response)
            throws IOException {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        Path path = Paths.get(image.getFilePath());
        if (!Files.exists(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType("image/tiff");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + image.getName() + "\"");
        response.setContentLengthLong(image.getFileSize());

        try (InputStream is = Files.newInputStream(path);
             OutputStream os = response.getOutputStream()) {
            is.transferTo(os);
        }
    }

    /**
     * 获取影像预览图（PNG 格式，用于浏览器显示）
     */
    @GetMapping("/{id}/preview")
    public void getImagePreview(@PathVariable Long id, HttpServletResponse response)
            throws IOException {
        Image image = imageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        // 修复文件路径（本地开发适配生产路径）
        String filePath = fixFilePath(image.getFilePath());
        System.out.println("[getImagePreview] ========== 开始生成预览 ==========");
        System.out.println("[getImagePreview] 影像 ID: " + id);
        System.out.println("[getImagePreview] 影像名称：" + image.getName());
        System.out.println("[getImagePreview] 数据库文件路径：" + image.getFilePath());
        System.out.println("[getImagePreview] 实际文件路径：" + filePath);

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            System.out.println("[getImagePreview] 文件不存在：" + filePath);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        long fileSize = Files.size(path);
        String fileModTime = Files.getLastModifiedTime(path).toString();
        System.out.println("[getImagePreview] 文件大小：" + fileSize + " bytes");
        System.out.println("[getImagePreview] 文件时间：" + fileModTime);

        try {
            // 读取 TIFF 文件
            java.io.File tiffFile = new java.io.File(path.toString());
            javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(tiffFile);
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReadersByFormatName("tiff");
            if (!readers.hasNext()) {
                iis.close();
                System.out.println("[getImagePreview] 找不到 TIFF 读取器");
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            javax.imageio.ImageReader reader = readers.next();
            reader.setInput(iis);

            // 读取影像
            java.awt.image.RenderedImage rendered = reader.read(0);
            System.out.println("[getImagePreview] 读取影像成功：" + rendered.getWidth() + "x" + rendered.getHeight());

            // 转换为 PNG 并输出
            response.setContentType("image/png");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");

            try (OutputStream os = response.getOutputStream()) {
                javax.imageio.ImageIO.write(rendered, "PNG", os);
                System.out.println("[getImagePreview] PNG 已输出到响应");
            }

            reader.dispose();
            iis.close();
            System.out.println("[getImagePreview] ========== 预览生成完成 ==========");

        } catch (Exception e) {
            System.out.println("[getImagePreview] 异常：" + e.getMessage());
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 提取 GeoTIFF 元数据
     * 使用 GeoTools 库读取 GeoTIFF 文件信息，并转换坐标到 EPSG:3857
     */
    private GeoTiffMetadata extractMetadata(String filePath) {
        GeoTiffMetadata metadata = new GeoTiffMetadata();

        try {
            // 先尝试读取 .tfw 文件获取地理信息
            GeoTiffMetadata tfwMetadata = readTfwFile(filePath);
            if (tfwMetadata != null) {
                metadata.setCrs(tfwMetadata.getCrs());
                metadata.setMinX(tfwMetadata.getMinX());
                metadata.setMinY(tfwMetadata.getMinY());
                metadata.setMaxX(tfwMetadata.getMaxX());
                metadata.setMaxY(tfwMetadata.getMaxY());
                metadata.setWidth(tfwMetadata.getWidth());
                metadata.setHeight(tfwMetadata.getHeight());
                metadata.setNumBands(tfwMetadata.getNumBands());
                return metadata;
            }
        } catch (Exception e) {
            System.out.println("[ImageController] TFW 文件读取失败：" + e.getMessage());
        }

        try {
            // 使用 GeoTools 读取 GeoTIFF
            org.geotools.gce.geotiff.GeoTiffReader reader =
                new org.geotools.gce.geotiff.GeoTiffReader(new java.io.File(filePath));

            org.geotools.coverage.grid.GridCoverage2D coverage = reader.read(null);
            org.opengis.coverage.grid.GridEnvelope gridEnvelope = coverage.getGridGeometry().getGridRange();

            // 获取影像尺寸
            metadata.setWidth(gridEnvelope.getHigh(0));
            metadata.setHeight(gridEnvelope.getHigh(1));

            // 获取波段数
            metadata.setNumBands(coverage.getRenderedImage().getSampleModel().getNumBands());

            // 获取坐标范围和 CRS
            org.opengis.referencing.crs.CoordinateReferenceSystem crs =
                coverage.getGridGeometry().getCoordinateReferenceSystem();

            String crsCode = "EPSG:3857";
            if (crs != null) {
                var identifiers = crs.getIdentifiers();
                if (identifiers != null && identifiers.iterator().hasNext()) {
                    crsCode = identifiers.iterator().next().toString();
                }
            }
            metadata.setCrs(crsCode);

            // 获取边界坐标（在原始 CRS 下）
            org.opengis.geometry.Envelope envelope = coverage.getGridGeometry().getEnvelope();
            if (envelope != null) {
                double minX = envelope.getMinimum(0);
                double minY = envelope.getMinimum(1);
                double maxX = envelope.getMaximum(0);
                double maxY = envelope.getMaximum(1);

                System.out.println("[ImageController] 原始 CRS: " + crsCode +
                    ", 原始坐标：minX=" + minX + ", minY=" + minY + ", maxX=" + maxX + ", maxY=" + maxY);

                // 如果 CRS 不是 EPSG:3857，需要转换坐标
                if (!crsCode.equals("EPSG:3857") && crs != null) {
                    try {
                        // 定义目标 CRS (EPSG:3857)
                        org.opengis.referencing.crs.CoordinateReferenceSystem targetCRS =
                            org.geotools.referencing.CRS.decode("EPSG:3857");

                        // 创建坐标转换
                        org.opengis.referencing.operation.MathTransform transform =
                            org.geotools.referencing.CRS.findMathTransform(crs, targetCRS);

                        // 转换四个角点
                        double[] p1 = transformPoint(transform, minX, minY);
                        double[] p2 = transformPoint(transform, maxX, maxY);

                        metadata.setMinX(p1[0]);
                        metadata.setMinY(p1[1]);
                        metadata.setMaxX(p2[0]);
                        metadata.setMaxY(p2[1]);

                        System.out.println("[ImageController] 转换后坐标 (EPSG:3857): minX=" + metadata.getMinX() +
                            ", minY=" + metadata.getMinY() + ", maxX=" + metadata.getMaxX() + ", maxY=" + metadata.getMaxY());
                    } catch (Exception e) {
                        System.out.println("[ImageController] CRS 转换失败：" + e.getMessage());
                        // 转换失败，使用原始坐标
                        metadata.setMinX(minX);
                        metadata.setMinY(minY);
                        metadata.setMaxX(maxX);
                        metadata.setMaxY(maxY);
                    }
                } else {
                    // 已经是 EPSG:3857，直接使用
                    metadata.setMinX(minX);
                    metadata.setMinY(minY);
                    metadata.setMaxX(maxX);
                    metadata.setMaxY(maxY);
                }
            }

            reader.dispose();

        } catch (Exception e) {
            // 如果 GeoTools 读取失败，使用默认值
            e.printStackTrace();
            metadata.setCrs("EPSG:3857");
            metadata.setWidth(0);
            metadata.setHeight(0);
            metadata.setNumBands(3);
            metadata.setMinX(0.0);
            metadata.setMinY(0.0);
            metadata.setMaxX(0.0);
            metadata.setMaxY(0.0);
        }

        return metadata;
    }

    /**
     * 转换单个点的坐标
     */
    private double[] transformPoint(org.opengis.referencing.operation.MathTransform transform,
                                    double x, double y) throws Exception {
        double[] srcPts = new double[] { x, y };
        double[] dstPts = new double[2];
        transform.transform(srcPts, 0, dstPts, 0, 1);
        return dstPts;
    }

    /**
     * 读取 TFW 文件（TIFF World File）获取地理参考信息
     * TFW 文件包含 6 行参数：
     * 1. A - X 方向像素大小
     * 2. D - 旋转参数（通常为 0）
     * 3. B - 旋转参数（通常为 0）
     * 4. E - Y 方向像素大小（通常为负值）
     * 5. C - 左上角 X 坐标
     * 6. F - 左上角 Y 坐标
     */
    private GeoTiffMetadata readTfwFile(String tiffFilePath) {
        String tfwPath = tiffFilePath.substring(0, tiffFilePath.lastIndexOf('.')) + ".tfw";
        java.io.File tfwFile = new java.io.File(tfwPath);

        if (!tfwFile.exists()) {
            return null;
        }

        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(tfwFile.toPath());
            if (lines.size() < 6) {
                return null;
            }

            double A = Double.parseDouble(lines.get(0).trim()); // X 像素大小
            double D = Double.parseDouble(lines.get(1).trim()); // 旋转
            double B = Double.parseDouble(lines.get(2).trim()); // 旋转
            double E = Double.parseDouble(lines.get(3).trim()); // Y 像素大小（通常为负）
            double C = Double.parseDouble(lines.get(4).trim()); // 左上角 X
            double F = Double.parseDouble(lines.get(5).trim()); // 左上角 Y

            // 使用 ImageReader 获取影像尺寸
            java.io.File tiffFile = new java.io.File(tiffFilePath);
            javax.imageio.stream.ImageInputStream iis = javax.imageio.ImageIO.createImageInputStream(tiffFile);
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReadersByFormatName("tiff");
            if (!readers.hasNext()) {
                iis.close();
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            reader.setInput(iis);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            // 获取波段数（从 SampleModel）
            java.awt.image.RenderedImage rendered = reader.read(0);
            int numBands = rendered.getSampleModel().getNumBands();
            reader.dispose();
            iis.close();

            GeoTiffMetadata metadata = new GeoTiffMetadata();
            metadata.setCrs("EPSG:3857");
            metadata.setWidth(width);
            metadata.setHeight(height);
            metadata.setNumBands(numBands);

            // 计算边界坐标
            metadata.setMinX(C);
            metadata.setMaxY(F);
            metadata.setMaxX(C + (width * A));
            metadata.setMinY(F + (height * E)); // E 通常为负值

            System.out.println("[ImageController] TFW 文件读取成功：minX=" + metadata.getMinX() +
                ", minY=" + metadata.getMinY() + ", maxX=" + metadata.getMaxX() +
                ", maxY=" + metadata.getMaxY() + ", width=" + width + ", height=" + height);

            return metadata;

        } catch (Exception e) {
            System.out.println("[ImageController] TFW 文件解析失败：" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * GeoTIFF 元数据内部类
     */
    private static class GeoTiffMetadata {
        private String crs;
        private double minX, minY, maxX, maxY;
        private int width, height, numBands;

        public String getCrs() { return crs; }
        public void setCrs(String crs) { this.crs = crs; }
        public double getMinX() { return minX; }
        public void setMinX(double minX) { this.minX = minX; }
        public double getMinY() { return minY; }
        public void setMinY(double minY) { this.minY = minY; }
        public double getMaxX() { return maxX; }
        public void setMaxX(double maxX) { this.maxX = maxX; }
        public double getMaxY() { return maxY; }
        public void setMaxY(double maxY) { this.maxY = maxY; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        public int getNumBands() { return numBands; }
        public void setNumBands(int numBands) { this.numBands = numBands; }
    }
}
