package com.tradetrail.domain.fulfillment;

public interface TrackingProvider {
    String carrier();

    Tracking verify(String raw, String timestamp, String signature);
}
