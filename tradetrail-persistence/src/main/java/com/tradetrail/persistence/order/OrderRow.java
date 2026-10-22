package com.tradetrail.persistence.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sales_order")
public class OrderRow {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String customer;
    public Long productId;
    public Integer quantity;
    public Long unitPriceCents;
    public Long totalCents;
    public String status;
    public String requestId;
    public String trackingNo;
    public LocalDateTime createdAt;
}
