package com.tradetrail.channel.mock;

import com.tradetrail.domain.TradeFailure;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class HmacProtocol {
    private HmacProtocol() {
    }

    static String sign(String secret, String timestamp, String raw) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + raw).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static void verify(String secret, String timestamp, String raw, String signature) {
        try {
            long seconds = Long.parseLong(timestamp), now = Instant.now().getEpochSecond();
            if (seconds < now - 300 || seconds > now + 300
                    || !MessageDigest.isEqual(HexFormat.of().parseHex(sign(secret, timestamp, raw)),
                            HexFormat.of().parseHex(signature))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new TradeFailure("INVALID_SIGNATURE", "回调签名或时间戳无效");
        }
    }
}
