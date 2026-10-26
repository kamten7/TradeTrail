package com.tradetrail.simulator;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
public interface TrackingMapper extends BaseMapper<TrackingRow> {
    @Select("SELECT event_id FROM sim_tracking_event WHERE delivery_state IN ('PENDING','RETRY','PROCESSING') AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY occurred_at LIMIT 50") List<String> due();
    @Update("UPDATE sim_tracking_event SET delivery_state='PROCESSING',next_attempt_at=TIMESTAMPADD(SECOND,60,CURRENT_TIMESTAMP) WHERE event_id=#{id} AND delivery_state IN ('PENDING','RETRY','PROCESSING') AND next_attempt_at<=CURRENT_TIMESTAMP") int claim(String id);
    @Update("UPDATE sim_tracking_event SET delivery_state=#{state},retry_count=retry_count+1,last_error=#{error},next_attempt_at=TIMESTAMPADD(SECOND,#{delay},CURRENT_TIMESTAMP) WHERE event_id=#{id}")
    int result(@Param("id") String id,@Param("state") String state,@Param("error") String error,@Param("delay") int delay);
    @Update("UPDATE sim_tracking_event SET delivery_state='RETRY',retry_count=0,next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=#{id} AND delivery_state IN ('FAILED','REJECTED','RETRY')") int requeue(String id);
}
