package com.tradetrail.persistence.cart;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("cart_item")
public class CartRow {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String customer;
    public Long productId;
    public Integer quantity;
    public Boolean selected;
}
