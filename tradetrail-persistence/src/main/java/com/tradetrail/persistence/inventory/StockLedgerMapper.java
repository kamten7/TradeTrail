package com.tradetrail.persistence.inventory;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface StockLedgerMapper {
    @Insert("INSERT INTO stock_ledger(order_id, product_id, quantity, action) VALUES(#{orderId}, "
            + "#{productId}, #{quantity}, #{action})")
    int add(@Param("orderId") long orderId, @Param("productId") long productId, @Param("quantity") int quantity,
            @Param("action") String action);
}
