package com.tradetrail.domain.fulfillment;

/**
 * 物流渠道端口定义承运商身份及回调验签。
 */
public interface TrackingProvider {
    String carrier();

    Tracking verify(String raw, String timestamp, String signature);
}
