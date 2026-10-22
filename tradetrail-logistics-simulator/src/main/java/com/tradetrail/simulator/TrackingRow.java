package com.tradetrail.simulator;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sim_tracking_event")
public class TrackingRow {
    @TableId
    public String eventId;
    public String trackingNo;
    public String status;
    public String location;
    public LocalDateTime occurredAt;
    public String deliveryState;
    public Integer retryCount;
    public String lastError;
}
