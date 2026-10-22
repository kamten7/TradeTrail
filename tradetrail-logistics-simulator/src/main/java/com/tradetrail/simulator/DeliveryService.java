package com.tradetrail.simulator;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class DeliveryService {
    private final TrackingMapper mapper;
    private final ObjectMapper json;
    private final RestClient client;
    private final String secret;

    public DeliveryService(TrackingMapper mapper, ObjectMapper json,
                           @Value("${tradetrail.mall-url}") String mallUrl,
                           @Value("${tradetrail.logistics-secret}") String secret) {
        this.mapper = mapper;
        this.json = json;
        this.client = RestClient.builder().baseUrl(mallUrl).build();
        this.secret = secret;
    }

    @Transactional
    public TrackingRow create(String trackingNo, String status, String location) {
        TrackingRow row = new TrackingRow();
        row.eventId = UUID.randomUUID().toString();
        row.trackingNo = trackingNo.strip();
        row.status = status;
        row.location = location.strip();
        row.occurredAt = LocalDateTime.now();
        row.deliveryState = "PENDING";
        row.retryCount = 0;
        mapper.insert(row);
        return row;
    }

    public List<TrackingRow> list() {
        return mapper.selectList(new QueryWrapper<TrackingRow>().orderByDesc("occurred_at"));
    }

    public void deliver(String eventId) {
        TrackingRow row = mapper.selectById(eventId);
        if (row == null || "SUCCESS".equals(row.deliveryState)) return;
        try {
            String raw = json.writeValueAsString(Map.of("eventId", row.eventId, "trackingNo", row.trackingNo,
                    "status", row.status, "location", row.location, "occurredAt", row.occurredAt));
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + raw).getBytes(StandardCharsets.UTF_8)));
            client.post().uri("/callbacks/logistics/mock").contentType(MediaType.APPLICATION_JSON)
                    .header("X-Logistics-Timestamp", timestamp).header("X-Logistics-Signature", signature)
                    .body(raw).retrieve().toBodilessEntity();
            row.deliveryState = "SUCCESS";
            row.lastError = null;
        } catch (Exception error) {
            row.deliveryState = "FAILED";
            row.lastError = error.getMessage() == null ? error.getClass().getSimpleName()
                    : error.getMessage().substring(0, Math.min(500, error.getMessage().length()));
        }
        row.retryCount += 1;
        mapper.updateById(row);
    }
}
