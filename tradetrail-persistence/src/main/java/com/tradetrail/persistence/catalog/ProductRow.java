package com.tradetrail.persistence.catalog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("product")
public class ProductRow {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String name;
    public String description;
    public Long priceCents;
    public Boolean published;
}
