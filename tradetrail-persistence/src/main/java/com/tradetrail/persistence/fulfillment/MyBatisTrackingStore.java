package com.tradetrail.persistence.fulfillment;

import com.tradetrail.domain.fulfillment.Tracking;
import com.tradetrail.domain.fulfillment.TrackingStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisTrackingStore implements TrackingStore {
    private final TrackingMapper mapper;

    public MyBatisTrackingStore(TrackingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean add(Tracking event) {
        if (mapper.count(event.eventId()) > 0) {
            return false;
        }
        return mapper.add(event) == 1;
    }

    @Override
    public List<Tracking> byTrackingNo(String trackingNo) {
        return mapper.timeline(trackingNo);
    }

    @Override
    public Optional<Tracking> find(String eventId) {
        return Optional.ofNullable(mapper.find(eventId));
    }
}
