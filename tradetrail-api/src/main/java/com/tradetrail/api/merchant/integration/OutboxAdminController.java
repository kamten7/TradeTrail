package com.tradetrail.api.merchant.integration;
import com.tradetrail.application.integration.EventService;
import com.tradetrail.domain.integration.OutboxStore;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class OutboxAdminController {
    private final EventService events;
    public OutboxAdminController(EventService events){this.events=events;}
    @GetMapping("/admin/outbox") public List<OutboxStore.Event> failed(){return events.failed();}
    @PostMapping("/admin/outbox/{id}/retry") public Map<String,String> retry(@PathVariable String id){events.retry(id);return Map.of("result","queued");}
}
