package com.tradetrail.persistence.inventory;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface StockAdjustmentMapper {
    @Insert("INSERT INTO stock_adjustment(product_id, delta, reason, operator_name) "
            + "VALUES(#{productId}, #{delta}, #{reason}, #{operator})")
    int add(@Param("productId") long productId, @Param("delta") int delta, @Param("reason") String reason,
            @Param("operator") String operator);
}
