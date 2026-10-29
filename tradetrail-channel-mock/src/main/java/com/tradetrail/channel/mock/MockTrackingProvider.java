package com.tradetrail.channel.mock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.fulfillment.Tracking;
import com.tradetrail.domain.fulfillment.TrackingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockTrackingProvider implements TrackingProvider {
    private final ObjectMapper json;
    private final String secret;

    public MockTrackingProvider(ObjectMapper json, @Value("${tradetrail.logistics.secret}") String secret) {
        this.json = json;
        this.secret = secret;
    }

    public String carrier() {
        return "MOCK";
    }

    public Tracking verify(String raw, String timestamp, String signature) {
        HmacProtocol.verify(secret, timestamp, raw, signature);
        try {
            return json.readValue(raw, Tracking.class);
        } catch (JsonProcessingException e) {
            throw new TradeFailure("INVALID_TRACKING_EVENT", "物流报文不合法");
        }
    }
}
