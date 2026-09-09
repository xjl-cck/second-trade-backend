package org.example.secondhandtradingplatform.product.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.example.secondhandtradingplatform.common.exception.BizException;
import org.example.secondhandtradingplatform.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.UUID;

@Service
@Conditional(OssEnabledCondition.class)
public class OssStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    private final OSS client;
    private final String bucket;
    private final String endpoint;
    private final String publicDomain;

    public OssStorageService(@Value("${app.oss.endpoint}") String endpoint,
                             @Value("${app.oss.bucket}") String bucket,
                             @Value("${app.oss.access-key-id}") String accessKeyId,
                             @Value("${app.oss.access-key-secret}") String accessKeySecret,
                             @Value("${app.oss.public-domain:}") String publicDomain) {
        this.endpoint = trimTrailingSlash(endpoint);
        this.bucket = bucket;
        this.publicDomain = trimTrailingSlash(publicDomain);
        this.client = new OSSClientBuilder().build(this.endpoint, accessKeyId, accessKeySecret);
    }

    public String upload(MultipartFile file, String suffix) {
        String key = "products/" + UUID.randomUUID() + suffix;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (StringUtils.hasText(file.getContentType())) {
            metadata.setContentType(file.getContentType());
        }
        try (var inputStream = file.getInputStream()) {
            client.putObject(bucket, key, inputStream, metadata);
            return buildUrl(key);
        } catch (IOException | RuntimeException exception) {
            log.error("Upload image to OSS failed. bucket={}, endpoint={}, key={}",
                    bucket, endpoint, key, exception);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片上传 OSS 失败，请检查 OSS 配置、权限和 Bucket 地域");
        }
    }

    private String buildUrl(String key) {
        if (StringUtils.hasText(publicDomain)) {
            return publicDomain + "/" + key;
        }
        return endpoint + "/" + bucket + "/" + key;
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    @PreDestroy
    public void shutdown() {
        client.shutdown();
    }
}
