package com.tradetrail.persistence.integration;
import com.tradetrail.domain.integration.CommandStore;
import org.springframework.stereotype.Repository;
@Repository
public class MyBatisCommandStore implements CommandStore {
    private final CommandMapper mapper;
    public MyBatisCommandStore(CommandMapper mapper) { this.mapper=mapper; }
    public Receipt lock(String key,String fingerprint) { mapper.acquire(key,fingerprint); return mapper.receipt(key); }
    public void complete(String key,int status,String body) { mapper.complete(key,status,body); }
}
