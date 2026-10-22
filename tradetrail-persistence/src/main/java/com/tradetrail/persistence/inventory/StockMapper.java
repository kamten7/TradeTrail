package com.tradetrail.persistence.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface StockMapper extends BaseMapper<StockRow> {
    @Update("UPDATE stock SET available=available-#{qty}, reserved=reserved+#{qty}, version=version+1 WHERE product_id=#{id} AND available>=#{qty}")
    int reserve(@Param("id") long productId, @Param("qty") int quantity);

    @Update("UPDATE stock SET reserved=reserved-#{qty}, sold=sold+#{qty}, version=version+1 WHERE product_id=#{id} AND reserved>=#{qty}")
    int confirm(@Param("id") long productId, @Param("qty") int quantity);

    @Update("UPDATE stock SET reserved=reserved-#{qty}, available=available+#{qty}, version=version+1 WHERE product_id=#{id} AND reserved>=#{qty}")
    int release(@Param("id") long productId, @Param("qty") int quantity);

    @Update("UPDATE stock SET available=available+#{delta}, version=version+1 WHERE product_id=#{id} AND available+#{delta}>=0")
    int adjust(@Param("id") long productId, @Param("delta") int delta);
}
