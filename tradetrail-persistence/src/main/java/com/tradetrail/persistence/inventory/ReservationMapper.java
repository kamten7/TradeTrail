package com.tradetrail.persistence.inventory;
import org.apache.ibatis.annotations.*;
public interface ReservationMapper {
    @Insert("INSERT INTO inventory_reservation(order_id,product_id,quantity,state) VALUES(#{order},#{product},#{qty},'RESERVED')")
    int reserve(@Param("order") long order,@Param("product") long product,@Param("qty") int qty);
    @Update("UPDATE inventory_reservation SET state=#{next} WHERE order_id=#{order} AND product_id=#{product} AND quantity=#{qty} AND state=#{expected}")
    int transition(@Param("order") long order,@Param("product") long product,@Param("qty") int qty,@Param("expected") String expected,@Param("next") String next);
}
