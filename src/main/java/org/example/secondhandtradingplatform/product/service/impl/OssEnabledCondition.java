package org.example.secondhandtradingplatform.product.service.impl;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OssEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("app.oss.enabled", "false");
        String accessKeyId = context.getEnvironment().getProperty("app.oss.access-key-id", "");
        String accessKeySecret = context.getEnvironment().getProperty("app.oss.access-key-secret", "");
        return Boolean.parseBoolean(enabled)
                && !accessKeyId.isBlank()
                && !accessKeySecret.isBlank();
    }
}
