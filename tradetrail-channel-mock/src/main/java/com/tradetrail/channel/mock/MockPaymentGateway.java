package com.tradetrail.channel.mock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.payment.PaymentGateway;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
@Component
public class MockPaymentGateway implements PaymentGateway {
    private final ObjectMapper json;
    private final String secret;
    private final RestClient client;
    public MockPaymentGateway(ObjectMapper json,@Value("${tradetrail.payment.secret:local-payment-secret-change-me}") String secret,
                              @Value("${tradetrail.payment.callback-url:http://127.0.0.1:8080}") String url){
        this.json=json;this.secret=secret;
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(3000);factory.setReadTimeout(5000);
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public String channel(){return "MOCK";}
    public Notice verify(String raw,String timestamp,String signature){
        HmacProtocol.verify(secret,timestamp,raw,signature);
        try{var notice=json.readValue(raw,Notice.class);if(notice==null)throw new TradeFailure("INVALID_PAYMENT_NOTICE","支付报文不能为空");return notice;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new TradeFailure("INVALID_PAYMENT_NOTICE","支付报文不合法");}
    }
    public void deliver(Notice notice){
        try{
            String raw=json.writeValueAsString(notice),timestamp=Long.toString(Instant.now().getEpochSecond());
            client.post().uri("/callbacks/payments/mock").contentType(MediaType.APPLICATION_JSON)
                .header("X-Payment-Timestamp",timestamp).header("X-Payment-Signature",HmacProtocol.sign(secret,timestamp,raw))
                .body(raw).retrieve().toBodilessEntity();
        }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
}
