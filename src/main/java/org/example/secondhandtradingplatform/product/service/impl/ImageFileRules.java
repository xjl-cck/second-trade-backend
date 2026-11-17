package org.example.secondhandtradingplatform.product.service.impl;

import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 商品图片上传规则：后缀白名单、大小上限、文件头（魔数）校验。
 * <p>
 * 只校验扩展名挡不住"把任意文件改成 .jpg 上传"，这里读取文件头确认真实格式。
 */
final class ImageFileRules {

    static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    static final int MAX_IMAGES_PER_PRODUCT = 9;

    private static final Set<String> ALLOWED_SUFFIXES = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final int HEADER_LENGTH = 12;

    private ImageFileRules() {
    }

    static String resolveSuffix(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalName) || !originalName.contains(".")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 jpg、jpeg、png、webp 图片");
        }
        String suffix = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (!ALLOWED_SUFFIXES.contains(suffix)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 jpg、jpeg、png、webp 图片");
        }
        return suffix;
    }

    static void verifySize(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能为空");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单张图片不能超过 10MB");
        }
    }

    static void verifyContent(MultipartFile file, String suffix) {
        byte[] header = new byte[HEADER_LENGTH];
        int length;
        try (InputStream inputStream = file.getInputStream()) {
            length = inputStream.readNBytes(header, 0, HEADER_LENGTH);
        } catch (IOException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片读取失败");
        }
        if (!matches(suffix, header, length)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片内容与扩展名不一致，请上传真实的 jpg/png/webp 图片");
        }
    }

    private static boolean matches(String suffix, byte[] header, int length) {
        if (length < 4) {
            return false;
        }
        return switch (suffix) {
            case ".jpg", ".jpeg" -> length >= 3
                    && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case ".png" -> length >= 8
                    && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
                    && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A;
            case ".webp" -> length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }
}
