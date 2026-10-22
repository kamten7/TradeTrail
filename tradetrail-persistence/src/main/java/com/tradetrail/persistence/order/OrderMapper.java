package com.tradetrail.persistence.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface OrderMapper extends BaseMapper<OrderRow> {
    @Update("UPDATE sales_order SET status=#{next} WHERE id=#{id} AND status=#{expected}")
    int transition(@Param("id") long id, @Param("expected") String expected, @Param("next") String next);

    @Update("UPDATE sales_order SET status='SHIPPED', tracking_no=#{trackingNo} WHERE id=#{id} AND status='PAID'")
    int ship(@Param("id") long id, @Param("trackingNo") String trackingNo);
}
