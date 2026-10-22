package com.tradetrail.domain.fulfillment;

import java.util.List;
import java.util.Optional;

public interface TrackingStore {
    boolean add(Tracking event);
    Optional<Tracking> find(String eventId);
    List<Tracking> byTrackingNo(String trackingNo);
}
