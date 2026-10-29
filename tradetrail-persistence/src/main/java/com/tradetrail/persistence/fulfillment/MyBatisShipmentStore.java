package com.tradetrail.persistence.fulfillment;

import com.tradetrail.domain.fulfillment.Shipment;
import com.tradetrail.domain.fulfillment.ShipmentStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisShipmentStore implements ShipmentStore {
    private final ShipmentMapper mapper;

    public MyBatisShipmentStore(ShipmentMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<Shipment> byOrder(long id) {
        return Optional.ofNullable(mapper.byOrder(id));
    }

    public Optional<Shipment> byTracking(String no) {
        return Optional.ofNullable(mapper.byTracking(no));
    }

    public void create(long id, String no) {
        mapper.create(id, no);
    }

    public void update(long id, String status, int progress, LocalDateTime time, boolean delivered) {
        mapper.update(id, status, progress, time, delivered);
    }

    public List<Long> autoConfirmDue(int days, long afterOrderId) {
        return mapper.autoConfirmDue(days, afterOrderId);
    }
}
