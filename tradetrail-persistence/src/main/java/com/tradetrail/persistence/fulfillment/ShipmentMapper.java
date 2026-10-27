package com.tradetrail.persistence.fulfillment;
import com.tradetrail.domain.fulfillment.Shipment;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
public interface ShipmentMapper {
    @Select("SELECT order_id,carrier,tracking_no,status,progress,last_occurred_at,delivered_at FROM shipment WHERE order_id=#{id}") Shipment byOrder(long id);
    @Select("SELECT order_id,carrier,tracking_no,status,progress,last_occurred_at,delivered_at FROM shipment WHERE tracking_no=#{no}") Shipment byTracking(String no);
    @Insert("INSERT INTO shipment(order_id,carrier,tracking_no) VALUES(#{id},'MOCK',#{no})") int create(@Param("id") long id,@Param("no") String no);
    @Update("UPDATE shipment SET status=#{status},progress=#{progress},last_occurred_at=#{time},delivered_at=CASE WHEN #{delivered} THEN #{time} ELSE delivered_at END WHERE order_id=#{id}")
    int update(@Param("id") long id,@Param("status") String status,@Param("progress") int progress,@Param("time") LocalDateTime time,@Param("delivered") boolean delivered);
    @Select("SELECT order_id FROM shipment WHERE order_id>#{afterOrderId} AND delivered_at IS NOT NULL AND delivered_at<=TIMESTAMPADD(DAY,-#{days},CURRENT_TIMESTAMP) ORDER BY order_id LIMIT 100")
    List<Long> autoConfirmDue(@Param("days") int days,@Param("afterOrderId") long afterOrderId);
}
