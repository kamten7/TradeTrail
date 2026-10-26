package com.tradetrail.persistence.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradetrail.domain.catalog.Product;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper extends BaseMapper<ProductRow> {
    @Update("UPDATE product SET name=#{name},description=#{description},price_cents=#{price},published=#{published} WHERE id=#{id}")
    int updateProduct(@Param("id") long id,@Param("name") String name,@Param("description") String description,@Param("price") long price,@Param("published") boolean published);
    @Select("SELECT p.id, p.name, p.description, p.price_cents, p.published, s.available FROM product p JOIN stock s ON s.product_id=p.id WHERE p.id=#{id}")
    Product detail(@Param("id") long id);

    @Select("SELECT p.id, p.name, p.description, p.price_cents, p.published, s.available FROM product p JOIN stock s ON s.product_id=p.id WHERE (#{all}=TRUE OR p.published=TRUE) ORDER BY p.id DESC")
    List<Product> browse(@Param("all") boolean all);

    @Update("UPDATE product SET published=TRUE WHERE id=#{id}")
    int publish(@Param("id") long id);
}
