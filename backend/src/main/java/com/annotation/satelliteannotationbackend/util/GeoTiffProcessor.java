package com.annotation.satelliteannotationbackend.util;

import java.awt.image.*;
import java.awt.Graphics2D;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * GeoTIFF 影像处理器
 * 用于处理 TFW 文件和影像调整
 */
public class GeoTiffProcessor {

    /**
     * 复制 GeoTIFF 文件并应用调整
     * @param inputFile 输入文件路径
     * @param outputFile 输出文件路径
     * @param brightness 亮度 (0.1-2.0)
     * @param contrast 对比度 (0.1-2.0)
     * @param gamma Gamma 值 (0.1-3.0)
     */
    public static void applyAdjustments(String inputFile, String outputFile,
                                        double brightness, double contrast, double gamma) throws Exception {
        File inputFileObj = new File(inputFile);
        File outputFileObj = new File(outputFile);

        System.out.println("[GeoTiffProcessor] 开始读取文件：" + inputFile);

        // 使用 ImageIO 读取 TIFF
        ImageInputStream iis = ImageIO.createImageInputStream(inputFileObj);
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("tiff");

        if (!readers.hasNext()) {
            iis.close();
            throw new RuntimeException("No TIFF ImageReader found");
        }

        ImageReader reader = readers.next();
        reader.setInput(iis);

        // 读取影像为 BufferedImage
        BufferedImage bufferedImage = reader.read(0);

        System.out.println("[GeoTiffProcessor] 影像尺寸：" + bufferedImage.getWidth() + "x" + bufferedImage.getHeight());
        System.out.println("[GeoTiffProcessor] 影像类型：" + bufferedImage.getType() + ", 色彩模型：" + bufferedImage.getColorModel());

        // 确保图像是 RGB 或 RGBA 格式
        if (bufferedImage.getType() != BufferedImage.TYPE_INT_RGB &&
            bufferedImage.getType() != BufferedImage.TYPE_INT_ARGB) {
            // 转换为 RGB 格式
            BufferedImage rgbImage = new BufferedImage(
                bufferedImage.getWidth(),
                bufferedImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = rgbImage.createGraphics();
            g.drawImage(bufferedImage, 0, 0, null);
            g.dispose();
            bufferedImage = rgbImage;
            System.out.println("[GeoTiffProcessor] 已转换为 RGB 格式");
        }

        System.out.println("[GeoTiffProcessor] 应用调整：brightness=" + brightness + ", contrast=" + contrast + ", gamma=" + gamma);

        // 应用亮度和对比度调整
        BufferedImage adjustedImage = applyBrightnessContrast(bufferedImage, brightness, contrast);

        // 应用 Gamma 调整
        if (gamma != 1.0) {
            adjustedImage = applyGamma(adjustedImage, gamma);
        }

        System.out.println("[GeoTiffProcessor] 调整完成，开始写入文件：" + outputFile);

        // 使用 ImageIO 写入 TIFF
        ImageOutputStream ios = ImageIO.createImageOutputStream(outputFileObj);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");

        if (!writers.hasNext()) {
            ios.close();
            throw new RuntimeException("No TIFF ImageWriter found");
        }

        ImageWriter writer = writers.next();
        writer.setOutput(ios);

        // 使用默认参数写入
        writer.write(adjustedImage);

        writer.dispose();
        ios.close();
        reader.dispose();
        iis.close();

        System.out.println("[GeoTiffProcessor] 文件写入完成");

        // 如果有 TFW 文件，也复制一份
        String inputTfw = inputFile.substring(0, inputFile.lastIndexOf('.')) + ".tfw";
        String outputTfw = outputFile.substring(0, outputFile.lastIndexOf('.')) + ".tfw";
        Path tfwSource = Paths.get(inputTfw);
        if (Files.exists(tfwSource)) {
            Files.copy(tfwSource, Paths.get(outputTfw), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[GeoTiffProcessor] TFW 文件已复制：" + outputTfw);
        }
    }

    /**
     * 应用亮度和对比度调整
     */
    private static BufferedImage applyBrightnessContrast(BufferedImage image,
                                                          double brightness,
                                                          double contrast) {
        BufferedImage result = new BufferedImage(
            image.getWidth(),
            image.getHeight(),
            image.getType()
        );

        // 计算缩放因子
        float scale = (float) (contrast * brightness);
        float offset = (float) ((1 - brightness) * 127);

        RescaleOp rescaleOp = new RescaleOp(scale, offset, null);
        rescaleOp.filter(image, result);

        return result;
    }

    /**
     * 应用 Gamma 调整
     */
    private static BufferedImage applyGamma(BufferedImage image, double gamma) {
        BufferedImage result = new BufferedImage(
            image.getWidth(),
            image.getHeight(),
            image.getType()
        );

        float gammaValue = (float) (1.0 / gamma);
        java.awt.image.LookupTable lookupTable = new java.awt.image.ByteLookupTable(0,
            createGammaTable(gammaValue));
        java.awt.image.LookupOp lookupOp = new java.awt.image.LookupOp(lookupTable, null);
        lookupOp.filter(image, result);

        return result;
    }

    /**
     * 创建 Gamma 查找表
     */
    private static byte[][] createGammaTable(float gamma) {
        byte[] table = new byte[256];
        for (int i = 0; i < 256; i++) {
            table[i] = (byte) (Math.pow(i / 255.0, gamma) * 255 + 0.5);
        }
        return new byte[][] { table, table, table };
    }

    /**
     * 创建白色 GeoTIFF 文件（用作占位影像）- 使用 GeoTools 创建真正的 GeoTIFF
     * @param outputPath 输出文件路径
     * @param minX 最小 X 坐标（EPSG:3857）
     * @param minY 最小 Y 坐标（EPSG:3857）
     * @param maxX 最大 X 坐标（EPSG:3857）
     * @param maxY 最大 Y 坐标（EPSG:3857）
     */
    public static void createTransparentGeoTiff(String outputPath,
                                                 double minX, double minY,
                                                 double maxX, double maxY) throws Exception {
        File outputFile = new File(outputPath);

        System.out.println("[GeoTiffProcessor] 开始创建白色占位 GeoTIFF：" + outputPath);
        System.out.println("[GeoTiffProcessor] 范围：minX=" + minX + ", minY=" + minY + ", maxX=" + maxX + ", maxY=" + maxY);

        // 使用 GeoTools 创建真正的 GeoTIFF
        int width = 256;
        int height = 256;

        // 创建覆盖范围
        org.geotools.coverage.grid.GridCoverage2D coverage = createCoverage(width, height, minX, minY, maxX, maxY);

        // 使用 GeoTiffWriter 写入真正的 GeoTIFF
        org.geotools.gce.geotiff.GeoTiffWriter writer = new org.geotools.gce.geotiff.GeoTiffWriter(outputFile);

        // 写入（使用默认参数）
        writer.write(coverage, null);
        writer.dispose();

        System.out.println("[GeoTiffProcessor] GeoTIFF 文件已写入：" + outputPath);
        System.out.println("[GeoTiffProcessor] 白色占位 GeoTIFF 创建完成");
    }

    /**
     * 创建 GridCoverage2D 覆盖
     */
    private static org.geotools.coverage.grid.GridCoverage2D createCoverage(
            int width, int height, double minX, double minY, double maxX, double maxY) throws Exception {

        // 创建白色图像
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();

        // 创建 envelope
        org.locationtech.jts.geom.Envelope envelope = new org.locationtech.jts.geom.Envelope(minX, maxX, minY, maxY);
        org.opengis.referencing.crs.CoordinateReferenceSystem crs = org.geotools.referencing.CRS.decode("EPSG:3857");
        org.opengis.geometry.Envelope ggEnvelope = new org.geotools.geometry.jts.ReferencedEnvelope(envelope, crs);

        // 创建 GridCoverage2D
        org.geotools.coverage.grid.GridCoverageFactory factory = new org.geotools.coverage.grid.GridCoverageFactory();
        return factory.create("white_placeholder", image, ggEnvelope);
    }

    /**
     * 创建 TFW 文件（TIFF World File）
     * @param tfwPath TFW 文件路径
     * @param minX 最小 X 坐标
     * @param minY 最小 Y 坐标
     * @param maxX 最大 X 坐标
     * @param maxY 最大 Y 坐标
     * @param width 影像宽度
     * @param height 影像高度
     */
    private static void createTfwFile(String tfwPath, double minX, double minY,
                                       double maxX, double maxY, int width, int height) throws IOException {
        // 计算像素大小
        double pixelSizeX = (maxX - minX) / width;
        double pixelSizeY = (maxY - minY) / height;  // 这个值通常是负的（因为图像坐标 Y 向下为正）

        // 左上角坐标
        double topLeftX = minX;
        double topLeftY = maxY;

        // TFW 文件格式：
        // Line 1: A - X 方向像素大小
        // Line 2: D - Y 轴旋转（通常为 0）
        // Line 3: B - X 轴旋转（通常为 0）
        // Line 4: E - Y 方向像素大小（通常为负）
        // Line 5: C - 左上角 X 坐标
        // Line 6: F - 左上角 Y 坐标

        StringBuilder sb = new StringBuilder();
        sb.append(pixelSizeX).append("\n");  // A
        sb.append("0.0\n");                   // D
        sb.append("0.0\n");                   // B
        sb.append(-pixelSizeY).append("\n");  // E (负值，因为地图 Y 向上，图像 Y 向下)
        sb.append(topLeftX).append("\n");     // C
        sb.append(topLeftY).append("\n");     // F

        Files.write(Paths.get(tfwPath), sb.toString().getBytes());

        System.out.println("[GeoTiffProcessor] TFW 参数：pixelSizeX=" + pixelSizeX +
            ", pixelSizeY=" + pixelSizeY + ", topLeftX=" + topLeftX + ", topLeftY=" + topLeftY);
    }

    /**
     * 读取 TFW 文件获取地理参考信息
     * @param tiffFilePath TIFF 文件路径
     * @return 包含地理参考信息的数组 [minX, minY, maxX, maxY]，如果读取失败返回 null
     */
    public static double[] readTfwFile(String tiffFilePath) {
        String tfwPath = tiffFilePath.substring(0, tiffFilePath.lastIndexOf('.')) + ".tfw";
        File tfwFile = new File(tfwPath);

        if (!tfwFile.exists()) {
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(tfwFile.toPath());
            if (lines.size() < 6) {
                return null;
            }

            double A = Double.parseDouble(lines.get(0).trim()); // X 像素大小
            double E = Double.parseDouble(lines.get(3).trim()); // Y 像素大小（通常为负）
            double C = Double.parseDouble(lines.get(4).trim()); // 左上角 X
            double F = Double.parseDouble(lines.get(5).trim()); // 左上角 Y

            // 使用 ImageReader 获取影像尺寸
            File tiffFile = new File(tiffFilePath);
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
            reader.dispose();
            iis.close();

            // 计算边界坐标
            double minX = C;
            double maxY = F;
            double maxX = C + (width * A);
            double minY = F + (height * E); // E 通常为负值

            return new double[] { minX, minY, maxX, maxY };

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
