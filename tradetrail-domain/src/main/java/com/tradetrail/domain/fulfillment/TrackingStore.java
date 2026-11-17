package com.tradetrail.domain.fulfillment;

import java.util.List;
import java.util.Optional;

/**
 * 定义履约数据访问端口，使业务规则不依赖数据库实现。
 */
public interface TrackingStore {
    boolean add(Tracking event);

    Optional<Tracking> find(String eventId);

    List<Tracking> byTrackingNo(String trackingNo);
}
