package com.tradetrail.persistence.after_sale;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 售后工单表映射，仅在持久层转换为领域记录。
 */
@TableName("after_sale_case")
public class AfterSaleRow {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long orderId;
    public String customer;
    public String reason;
    public String status;
    public String type;
    public String decisionReason;
    public String returnTrackingNo;
    public Boolean restocked;
    public LocalDateTime createdAt;
}
