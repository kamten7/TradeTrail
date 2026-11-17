package com.tradetrail.domain.integration;

/**
 * 幂等存储保留请求指纹和成功响应，供同键重放。
 */
public interface CommandStore {
    record Receipt(String fingerprint, Integer responseStatus, String responseBody) {
    }

    Receipt lock(String key, String fingerprint);

    void complete(String key, int status, String body);
}
