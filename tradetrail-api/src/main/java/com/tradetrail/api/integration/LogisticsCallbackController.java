package com.tradetrail.api.integration;

import com.tradetrail.application.fulfillment.FulfillmentService;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.fulfillment.TrackingProvider;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogisticsCallbackController {
    private final FulfillmentService fulfillment;
    private final TrackingProvider provider;

    public LogisticsCallbackController(FulfillmentService fulfillment, TrackingProvider provider) {
        this.fulfillment = fulfillment;
        this.provider = provider;
    }

    @PostMapping("/callbacks/logistics/mock")
    public Map<String, String> receive(@RequestBody String raw,
            @RequestHeader("X-Logistics-Timestamp") String timestamp,
            @RequestHeader("X-Logistics-Signature") String signature) {
        fulfillment.receive(provider.verify(raw, timestamp, signature));
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
