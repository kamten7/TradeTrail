package com.tradetrail.domain.integration;

public interface CommandStore {
    record Receipt(String fingerprint, Integer responseStatus, String responseBody) {
    }

    Receipt lock(String key, String fingerprint);

    void complete(String key, int status, String body);
}
