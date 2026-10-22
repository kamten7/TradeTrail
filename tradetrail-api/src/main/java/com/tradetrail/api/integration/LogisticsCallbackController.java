package com.tradetrail.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradetrail.application.fulfillment.FulfillmentService;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.fulfillment.Tracking;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogisticsCallbackController {
    private final FulfillmentService fulfillment;
    private final ObjectMapper json;
    private final String secret;

    public LogisticsCallbackController(FulfillmentService fulfillment, ObjectMapper json,
                                       @Value("${tradetrail.logistics.secret}") String secret) {
        this.fulfillment = fulfillment;
        this.json = json;
        this.secret = secret;
    }

    @PostMapping("/callbacks/logistics/mock")
    public Map<String, String> receive(@RequestBody String raw,
                                       @RequestHeader("X-Logistics-Timestamp") String timestamp,
                                       @RequestHeader("X-Logistics-Signature") String signature) throws Exception {
        long seconds;
        byte[] supplied;
        try {
            seconds = Long.parseLong(timestamp);
            supplied = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException error) {
            throw new TradeFailure("INVALID_SIGNATURE", "物流签名格式错误");
        }
        if (Math.abs(Instant.now().getEpochSecond() - seconds) > 300) {
            throw new TradeFailure("INVALID_SIGNATURE", "回调时间戳无效");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal((timestamp + "." + raw).getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new TradeFailure("INVALID_SIGNATURE", "物流签名错误");
        }
        fulfillment.receive(json.readValue(raw, Tracking.class));
        return Map.of("result", "accepted");
    }

    @GetMapping("/callbacks/logistics/mock/status/{eventId}")
    public Map<String, String> status(@PathVariable String eventId) {
        if (fulfillment.receipt(eventId).isEmpty()) {
            throw new TradeFailure("TRACKING_NOT_FOUND", "物流事件不存在");
        }
        return Map.of("eventId", eventId, "result", "accepted");
    }
}
