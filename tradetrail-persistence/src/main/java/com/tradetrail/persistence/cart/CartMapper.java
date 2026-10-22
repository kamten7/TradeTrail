package com.tradetrail.persistence.cart;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CartMapper extends BaseMapper<CartRow> {
    @Update("UPDATE cart_item SET quantity=#{quantity}, selected=#{selected} WHERE id=#{id} AND customer=#{customer}")
    int updateOwned(@Param("id") long id, @Param("customer") String customer,
                    @Param("quantity") int quantity, @Param("selected") boolean selected);

    @Delete("DELETE FROM cart_item WHERE id=#{id} AND customer=#{customer}")
    int deleteOwned(@Param("id") long id, @Param("customer") String customer);
}
