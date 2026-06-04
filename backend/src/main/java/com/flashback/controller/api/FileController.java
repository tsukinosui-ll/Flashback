package com.flashback.controller.api;

import com.flashback.common.response.ApiResponse;
import com.flashback.config.AppUploadProperties;
import com.flashback.security.auth.AuthUser;
import com.flashback.security.auth.CurrentUser;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final AppUploadProperties uploadProperties;

    private static final Map<String, byte[]> ALLOWED_MAGIC_NUMBERS = Map.ofEntries(
        Map.entry("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        Map.entry("jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        Map.entry("png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}),
        Map.entry("gif", new byte[]{0x47, 0x49, 0x46, 0x38})
    );

    public FileController(AppUploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @CurrentUser AuthUser authUser,
            @RequestParam("file") MultipartFile file) {

        // 1. 空文件检查
        if (file.isEmpty()) {
            return ApiResponse.fail(400, "文件不能为空");
        }

        // 2. 提取并校验扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }

        if (extension.isEmpty() || !uploadProperties.getAllowedExtensions().contains(extension)) {
            return ApiResponse.fail(400, "不支持的文件类型: " + extension);
        }

        // 3. 文件大小检查
        long maxBytes = DataSize.parse(uploadProperties.getMaxFileSize()).toBytes();
        if (file.getSize() > maxBytes) {
            return ApiResponse.fail(400, "文件大小不能超过" + uploadProperties.getMaxFileSize());
        }

        // 4. 魔数验证（防止扩展名欺骗）
        try {
            byte[] fileHeader = new byte[4];
            try (var is = file.getInputStream()) {
                // read 返回实际读取的字节数，魔数可能不足 4 字节
                int bytesRead = is.read(fileHeader);
                if (bytesRead < getExpectedMagicLength(extension)) {
                    return ApiResponse.fail(400, "文件内容不完整");
                }
            }

            if (!verifyMagicNumber(extension, fileHeader)) {
                return ApiResponse.fail(400, "文件内容与扩展名不匹配，可能是伪造文件");
            }
        } catch (IOException e) {
            return ApiResponse.fail(500, "文件验证失败");
        }

        // 5. 生成安全的文件路径（防止路径遍历）
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String newFilename = UUID.randomUUID() + "." + extension;
        String relativePath = "/uploads/" + authUser.getUserId() + "/" + dateDir + "/" + newFilename;

        try {
            // 获取规范化的基础路径（解析符号链接），baseDir 必须已存在
            Path baseDir = Paths.get(uploadProperties.getBaseDir()).toRealPath();

            // 构建目标目录路径并规范化（移除 .. 和 .）
            Path targetDir = baseDir
                .resolve(String.valueOf(authUser.getUserId()))
                .resolve(dateDir)
                .normalize();

            // 安全检查：确保目标目录在基础目录内（防止路径遍历）
            if (!targetDir.toAbsolutePath().startsWith(baseDir)) {
                return ApiResponse.fail(500, "路径校验失败，可能存在安全风险");
            }

            // 创建目录
            Files.createDirectories(targetDir);

            // 构建文件路径并再次验证
            Path filePath = targetDir.resolve(newFilename).normalize();
            if (!filePath.toAbsolutePath().startsWith(baseDir)) {
                return ApiResponse.fail(500, "文件路径校验失败");
            }

            // 保存文件
            file.transferTo(filePath.toFile());

        } catch (IOException e) {
            return ApiResponse.fail(500, "文件存储失败: " + e.getMessage());
        }

        return ApiResponse.success(relativePath);
    }

    private boolean verifyMagicNumber(String extension, byte[] header) {
        byte[] expectedMagic = ALLOWED_MAGIC_NUMBERS.get(extension);
        if (expectedMagic == null) {
            return false;
        }
        for (int i = 0; i < expectedMagic.length && i < header.length; i++) {
            if (header[i] != expectedMagic[i]) {
                return false;
            }
        }
        return true;
    }

    private int getExpectedMagicLength(String extension) {
        byte[] magic = ALLOWED_MAGIC_NUMBERS.get(extension);
        return magic == null ? 0 : magic.length;
    }
}
