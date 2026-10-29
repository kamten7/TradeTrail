package com.tradetrail.persistence.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradetrail.domain.order.OrderItem;
import com.tradetrail.domain.order.OrderStore.StatusLog;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OrderMapper extends BaseMapper<OrderRow> {
    @Select("SELECT * FROM sales_order WHERE id=#{id} FOR UPDATE")
    OrderRow lock(long id);

    @Update("UPDATE sales_order SET status=#{next},tracking_no=#{tracking},version=version+1,"
            + "completed_at=CASE WHEN #{next}='COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at "
            + "END WHERE id=#{id} AND version=#{version} AND status=#{expected}")
    int transition(@Param("id") long id, @Param("version") long version, @Param("expected") String expected,
            @Param("next") String next, @Param("tracking") String tracking);

    @Update("UPDATE sales_order SET fulfillment_hold=#{hold},version=version+1 WHERE id=#{id} AND "
            + "version=#{version}")
    int hold(@Param("id") long id, @Param("version") long version, @Param("hold") boolean hold);

    @Insert("INSERT INTO order_item(order_id,product_id,product_name,quantity,unit_price_cents) "
            + "VALUES(#{id},#{item.productId},#{item.productName},#{item.quantity}," + "#{item.unitPriceCents})")
    int item(@Param("id") long id, @Param("item") OrderItem item);

    @Select("SELECT product_id,product_name,quantity,unit_price_cents FROM order_item WHERE "
            + "order_id=#{id} ORDER BY product_id")
    List<OrderItem> items(long id);

    @Insert("INSERT INTO order_status_log(order_id,from_status,to_status,event,actor,command_id,"
            + "trace_id) VALUES(#{id},#{from},#{to},#{event},#{actor},#{command},#{trace})")
    int log(@Param("id") long id, @Param("from") String from, @Param("to") String to, @Param("event") String event,
            @Param("actor") String actor, @Param("command") String command, @Param("trace") String trace);

    @Select("SELECT from_status,to_status,event,actor,command_id,trace_id,occurred_at FROM "
            + "order_status_log WHERE order_id=#{id} ORDER BY id")
    List<StatusLog> history(long id);

    @Select("SELECT id FROM sales_order WHERE status='PENDING_PAYMENT' AND "
            + "pay_deadline<=CURRENT_TIMESTAMP ORDER BY id LIMIT 100")
    List<Long> expired();

    @Select("SELECT CURRENT_TIMESTAMP")
    LocalDateTime now();
}
