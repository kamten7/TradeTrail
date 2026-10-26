package com.tradetrail.api;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    static RequestPostProcessor write(){return request->{csrf().postProcessRequest(request);if(request.getHeader("Idempotency-Key")==null)request.addHeader("Idempotency-Key",UUID.randomUUID().toString());return request;};}
    static String orderBody(long product,int qty){return "{\"items\":[{\"productId\":"+product+",\"quantity\":"+qty+",\"expectedPriceCents\":1299}],\"address\":\"香港测试地址\"}";}
    long product()throws Exception{
        var result=mvc.perform(post("/admin/products").with(user("merchant").roles("MERCHANT_ADMIN")).with(write()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"测试商品\",\"description\":\"演示\",\"priceCents\":1299,\"initialStock\":10}")).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    long publishedProduct()throws Exception{long id=product();mvc.perform(post("/admin/products/{id}/publish",id).with(user("merchant").roles("MERCHANT_ADMIN")).with(write())).andExpect(status().isOk());return id;}
    long order(long product,String key)throws Exception{
        var result=mvc.perform(post("/orders").with(user("customer").roles("CUSTOMER")).with(write()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(orderBody(product,1))).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    void pay(long order)throws Exception{
        var result=mvc.perform(post("/orders/{id}/payments",order).with(user("customer").roles("CUSTOMER")).with(write())).andExpect(status().isOk()).andReturn();
        var payment=json.readTree(result.getResponse().getContentAsString());
        mvc.perform(post("/mock/checkout/{id}/complete",payment.get("id").asText()).with(user("customer").roles("CUSTOMER")).with(write()).contentType(MediaType.APPLICATION_JSON).content("{\"success\":true}")).andExpect(status().isOk());
        String raw=json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),"paymentId",payment.get("id").asText(),"amountCents",payment.get("amountCents").asLong(),"result","SUCCEEDED"));
        String timestamp=Long.toString(Instant.now().getEpochSecond());
        mvc.perform(post("/callbacks/payments/mock").contentType(MediaType.APPLICATION_JSON).content(raw).header("X-Payment-Timestamp",timestamp).header("X-Payment-Signature",signature("local-payment-secret-change-me",timestamp,raw))).andExpect(status().isOk());
    }
    static String signature(String secret,String timestamp,String raw)throws Exception{
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp+"."+raw).getBytes(StandardCharsets.UTF_8)));
    }
    long paidOrder()throws Exception{long id=order(publishedProduct(),UUID.randomUUID().toString());pay(id);return id;}
    JsonNode shippedOrder()throws Exception{
        long id=paidOrder();var result=mvc.perform(post("/admin/orders/{id}/shipments",id).with(user("merchant").roles("MERCHANT_ADMIN")).with(write())).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
