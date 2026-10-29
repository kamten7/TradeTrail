package com.tradetrail.persistence.fulfillment;

import com.tradetrail.domain.fulfillment.Tracking;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TrackingMapper {
    @Select("SELECT COUNT(*) FROM tracking_event WHERE event_id=#{eventId}")
    int count(@Param("eventId") String eventId);

    @Insert("INSERT INTO tracking_event(event_id, tracking_no, status, location, occurred_at) "
            + "VALUES(#{eventId}, #{trackingNo}, #{status}, #{location}, #{occurredAt})")
    int add(Tracking event);

    @Select("SELECT event_id, tracking_no, status, location, occurred_at FROM tracking_event WHERE "
            + "event_id=#{eventId}")
    Tracking find(@Param("eventId") String eventId);

    @Select("SELECT event_id, tracking_no, status, location, occurred_at FROM tracking_event WHERE "
            + "tracking_no=#{trackingNo} ORDER BY occurred_at, event_id")
    List<Tracking> timeline(@Param("trackingNo") String trackingNo);
}
