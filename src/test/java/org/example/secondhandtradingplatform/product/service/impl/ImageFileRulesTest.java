package org.example.secondhandtradingplatform.product.service.impl;

import org.example.secondhandtradingplatform.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageFileRulesTest {

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] WEBP_HEADER = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Test
    @DisplayName("后缀统一小写并只放行 jpg/jpeg/png/webp")
    void resolvesSuffix() {
        MockMultipartFile file = new MockMultipartFile("file", "A.JPG", "image/jpeg", new byte[]{1});
        assertEquals(".jpg", ImageFileRules.resolveSuffix(file));

        MockMultipartFile exe = new MockMultipartFile("file", "evil.exe", "application/octet-stream", new byte[]{1});
        assertThrows(BizException.class, () -> ImageFileRules.resolveSuffix(exe));

        MockMultipartFile noSuffix = new MockMultipartFile("file", "noext", "image/png", new byte[]{1});
        assertThrows(BizException.class, () -> ImageFileRules.resolveSuffix(noSuffix));
    }

    @Test
    @DisplayName("空文件和超过 10MB 的图片被拒绝")
    void enforcesSizeLimit() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThrows(BizException.class, () -> ImageFileRules.verifySize(empty));

        byte[] oversized = new byte[(int) ImageFileRules.MAX_SIZE_BYTES + 1];
        MockMultipartFile tooLarge = new MockMultipartFile("file", "a.png", "image/png", oversized);
        assertThrows(BizException.class, () -> ImageFileRules.verifySize(tooLarge));
    }

    @Test
    @DisplayName("真实图片头通过校验")
    void acceptsRealImageHeaders() {
        assertDoesNotThrow(() -> ImageFileRules.verifyContent(fileOf(PNG_HEADER), ".png"));
        assertDoesNotThrow(() -> ImageFileRules.verifyContent(fileOf(JPEG_HEADER), ".jpeg"));
        assertDoesNotThrow(() -> ImageFileRules.verifyContent(fileOf(WEBP_HEADER), ".webp"));
    }

    @Test
    @DisplayName("改后缀伪装成图片的文件被拒绝")
    void rejectsFakeImage() {
        MockMultipartFile fake = new MockMultipartFile("file", "a.jpg", "image/jpeg",
                "<?php echo 1; ?>".getBytes(StandardCharsets.UTF_8));
        assertThrows(BizException.class, () -> ImageFileRules.verifyContent(fake, ".jpg"));

        // 扩展名是 png，内容却是 JPEG
        MockMultipartFile mismatched = new MockMultipartFile("file", "a.png", "image/png", JPEG_HEADER);
        assertThrows(BizException.class, () -> ImageFileRules.verifyContent(mismatched, ".png"));
    }

    private MockMultipartFile fileOf(byte[] header) {
        return new MockMultipartFile("file", "a", null, header);
    }
}
