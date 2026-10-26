package com.tradetrail.application.integration;
import com.tradetrail.domain.integration.OutboxStore;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class EventService {
    private final OutboxStore store;
    private final CommandService commands;
    public EventService(OutboxStore store,CommandService commands){this.store=store;this.commands=commands;}
    @Transactional public void publish(String type,String aggregate,String payload){store.add(UUID.randomUUID().toString(),type,aggregate,payload);}
    @Transactional public boolean consume(String consumer,String eventId){commands.lock("inbox:"+consumer+":"+eventId,eventId);return store.consume(consumer,eventId);}
    public List<OutboxStore.Event> failed(){return store.failed();}
    @Transactional public void retry(String id){store.retry(id);}
}
