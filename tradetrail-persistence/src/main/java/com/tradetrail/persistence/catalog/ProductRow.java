package com.tradetrail.persistence.catalog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 商品表映射，不向接口层暴露数据库实体。
 */
@TableName("product")
public class ProductRow {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String name;
    public String description;
    public Long priceCents;
    public Boolean published;
}
