package com.tradetrail.domain.fulfillment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShipmentStore {
    Optional<Shipment> byOrder(long id);

    Optional<Shipment> byTracking(String no);

    void create(long order, String no);

    void update(long order, String status, int progress, LocalDateTime occurred, boolean delivered);

    List<Long> autoConfirmDue(int days, long afterOrderId);
}
