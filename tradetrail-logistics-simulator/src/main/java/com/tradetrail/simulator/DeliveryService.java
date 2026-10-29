package com.tradetrail.simulator;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeliveryService {
    private final TrackingMapper mapper;
    private final ObjectMapper json;
    private final RestClient client;
    private final String secret;

    public DeliveryService(TrackingMapper mapper, ObjectMapper json, @Value("${tradetrail.mall-url}") String url,
            @Value("${tradetrail.logistics-secret}") String secret) {
        this.mapper = mapper;
        this.json = json;
        this.secret = secret;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Transactional
    public TrackingRow create(String tracking, String status, String location) {
        if (tracking == null || !tracking.startsWith("MOCK-") || tracking.length() > 100 || location == null
                || location.isBlank() || location.length() > 120
                || !List.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "EXCEPTION", "RESUMED")
                        .contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "运单、节点或地点不合法");
        }
        var row = new TrackingRow();
        row.eventId = UUID.randomUUID().toString();
        row.trackingNo = tracking.strip();
        row.status = status;
        row.location = location.strip();
        row.occurredAt = LocalDateTime.now(ZoneOffset.UTC);
        row.deliveryState = "PENDING";
        row.retryCount = 0;
        mapper.insert(row);
        return row;
    }

    public List<TrackingRow> list() {
        return mapper.selectList(new QueryWrapper<TrackingRow>().orderByDesc("occurred_at"));
    }

    public void retry(String id) {
        mapper.requeue(id);
        deliver(id);
    }

    public void recover() {
        for (String id : mapper.due()) {
            deliver(id);
        }
    }

    public void deliver(String id) {
        if (mapper.claim(id) != 1) {
            return;
        }
        var row = mapper.selectById(id);
        try {
            String raw = json.writeValueAsString(Map.of("eventId", row.eventId, "trackingNo", row.trackingNo, "status",
                    row.status, "location", row.location, "occurredAt", row.occurredAt));
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of()
                    .formatHex(mac.doFinal((timestamp + "." + raw).getBytes(StandardCharsets.UTF_8)));
            client.post()
                    .uri("/callbacks/logistics/mock")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Logistics-Timestamp", timestamp)
                    .header("X-Logistics-Signature", signature)
                    .body(raw)
                    .retrieve()
                    .toBodilessEntity();
            mapper.result(id, "SUCCESS", null, 0);
        } catch (HttpClientErrorException e) {
            mapper.result(id, "REJECTED", "商城拒绝事件，HTTP " + e.getStatusCode().value(), 0);
        } catch (Exception e) {
            mapper.result(id, row.retryCount >= 7 ? "FAILED" : "RETRY", e.getClass().getSimpleName(),
                    Math.min(300, 1 << Math.min(row.retryCount + 1, 8)));
        }
    }
}
