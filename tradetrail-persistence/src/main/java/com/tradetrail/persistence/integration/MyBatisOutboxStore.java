package com.tradetrail.persistence.integration;

import com.tradetrail.domain.integration.OutboxStore;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOutboxStore implements OutboxStore {
    private final OutboxMapper mapper;

    public MyBatisOutboxStore(OutboxMapper mapper) {
        this.mapper = mapper;
    }

    public void add(String id, String type, String aggregate, String payload) {
        mapper.add(id, type, aggregate, payload);
    }

    public List<Event> due() {
        return mapper.due();
    }

    public boolean claim(String id) {
        return mapper.claim(id) == 1;
    }

    public void success(String id) {
        mapper.success(id);
    }

    public void failure(String id, int attempts, String error) {
        mapper.failure(id, attempts >= 8 ? "FAILED" : "PENDING", attempts,
                error.substring(0, Math.min(error.length(), 500)), Math.min(300, 1 << Math.min(attempts, 8)));
    }

    public boolean consume(String consumer, String id) {
        if (mapper.consumed(consumer, id) > 0) {
            return false;
        }
        mapper.consume(consumer, id);
        return true;
    }

    public List<Event> failed() {
        return mapper.failed();
    }

    public void retry(String id) {
        mapper.retry(id);
    }
}
