package com.tradetrail.application.integration;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.integration.CommandStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CommandService {
    private final CommandStore store;
    public CommandService(CommandStore store) { this.store=store; }
    @Transactional
    public CommandStore.Receipt lock(String key,String payload) {
        if(key==null || key.length()>240) throw new TradeFailure("INVALID_REQUEST","请求号不合法");
        String hash=hash(payload);
        var receipt=store.lock(key,hash);
        if(!hash.equals(receipt.fingerprint())) throw new TradeFailure("IDEMPOTENCY_CONFLICT","相同请求号不能用于不同参数");
        return receipt;
    }
    @Transactional
    public void complete(String key,int status,String body) { store.complete(key,status,body); }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
