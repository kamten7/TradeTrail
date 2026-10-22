package com.tradetrail.persistence.payment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PaymentNoticeMapper {
    @Select("SELECT COUNT(*) FROM payment_notice WHERE event_id=#{eventId}")
    int count(@Param("eventId") String eventId);

    @Insert("INSERT INTO payment_notice(event_id, order_id) VALUES(#{eventId}, #{orderId})")
    int insertNotice(@Param("eventId") String eventId, @Param("orderId") long orderId);
}
