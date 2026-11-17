package com.tradetrail.domain.integration;

import java.util.List;

/**
 * 待投递事件与消费去重由持久化适配器实现。
 */
public interface OutboxStore {
    record Event(String eventId, String type, String aggregateId, String payload, String state, int attempts,
            String lastError) {
    }

    void add(String id, String type, String aggregateId, String payload);

    List<Event> due();

    boolean claim(String id);

    void success(String id);

    void failure(String id, int attempts, String error);

    boolean consume(String consumer, String eventId);

    List<Event> failed();

    void retry(String id);
}
