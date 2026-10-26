package com.tradetrail.persistence.integration;
import com.tradetrail.domain.integration.OutboxStore.Event;
import java.util.List;
import org.apache.ibatis.annotations.*;
public interface OutboxMapper {
    @Insert("INSERT INTO outbox_event(event_id,type,aggregate_id,payload) VALUES(#{id},#{type},#{aggregate},#{payload})")
    int add(@Param("id") String id,@Param("type") String type,@Param("aggregate") String aggregate,@Param("payload") String payload);
    @Select("SELECT event_id,type,aggregate_id,payload,state,attempts,last_error FROM outbox_event WHERE state IN ('PENDING','PROCESSING') AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 50")
    List<Event> due();
    @Update("UPDATE outbox_event SET state='PROCESSING',next_attempt_at=TIMESTAMPADD(SECOND,60,CURRENT_TIMESTAMP) WHERE event_id=#{id} AND state IN ('PENDING','PROCESSING') AND next_attempt_at<=CURRENT_TIMESTAMP")
    int claim(String id);
    @Update("UPDATE outbox_event SET state='SENT',last_error=NULL WHERE event_id=#{id}") int success(String id);
    @Update("UPDATE outbox_event SET state=#{state},attempts=#{attempts},last_error=#{error},next_attempt_at=TIMESTAMPADD(SECOND,#{delay},CURRENT_TIMESTAMP) WHERE event_id=#{id}")
    int failure(@Param("id") String id,@Param("state") String state,@Param("attempts") int attempts,@Param("error") String error,@Param("delay") int delay);
    @Select("SELECT COUNT(*) FROM inbox_event WHERE consumer=#{consumer} AND event_id=#{id}") int consumed(@Param("consumer") String consumer,@Param("id") String id);
    @Insert("INSERT INTO inbox_event(consumer,event_id) VALUES(#{consumer},#{id})") int consume(@Param("consumer") String consumer,@Param("id") String id);
    @Select("SELECT event_id,type,aggregate_id,payload,state,attempts,last_error FROM outbox_event WHERE state='FAILED' ORDER BY created_at") List<Event> failed();
    @Update("UPDATE outbox_event SET state='PENDING',attempts=0,next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=#{id} AND state='FAILED'") int retry(String id);
}
