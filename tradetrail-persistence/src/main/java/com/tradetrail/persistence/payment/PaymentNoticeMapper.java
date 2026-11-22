package com.tradetrail.persistence.payment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付通知按事件 ID 去重，避免重复确认。
 */
public interface PaymentNoticeMapper {
    @Select("SELECT COUNT(*) FROM payment_notice WHERE event_id=#{eventId}")
    int count(@Param("eventId") String eventId);

    @Insert("INSERT INTO payment_notice(event_id, order_id) VALUES(#{eventId}, #{orderId})")
    int insertNotice(@Param("eventId") String eventId, @Param("orderId") long orderId);
}
