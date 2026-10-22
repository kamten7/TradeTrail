package com.tradetrail.persistence.inventory;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("stock")
public class StockRow {
    @TableId
    public Long productId;
    public Integer available;
    public Integer reserved;
    public Integer sold;
    public Integer version;
}
