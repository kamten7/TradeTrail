package com.tradetrail.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GetSessionApiTest extends ApiTestBase {
    @Test void returnsCsrfToken() throws Exception {
        mvc.perform(get("/session")).andExpect(status().isOk()).andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }
}

class LogoutSessionApiTest extends ApiTestBase {
    @Test void signsOut() throws Exception {
        mvc.perform(post("/session/logout").with(user("customer").roles("CUSTOMER")).with(write()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("signed_out"));
    }
}

class GetProductApiTest extends ApiTestBase {
    @Test void returnsPublishedProduct() throws Exception {
        mvc.perform(get("/products/{id}", publishedProduct())).andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
    }
}

class ListAdminProductsApiTest extends ApiTestBase {
    @Test void listsDraftsForMerchant() throws Exception {
        product();
        mvc.perform(get("/admin/products").with(user("merchant").roles("MERCHANT_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
    }
}

class PublishProductApiTest extends ApiTestBase {
    @Test void publishesDraft() throws Exception {
        mvc.perform(post("/admin/products/{id}/publish", product())
                .with(user("merchant").roles("MERCHANT_ADMIN")).with(write()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
    }
}

class ListOrdersApiTest extends ApiTestBase {
    @Test void listsOwnOrders() throws Exception {
        order(publishedProduct(), UUID.randomUUID().toString());
        mvc.perform(get("/orders").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].customer").value("customer"));
    }
}

class GetOrderApiTest extends ApiTestBase {
    @Test void returnsOwnedOrder() throws Exception {
        mvc.perform(get("/orders/{id}", order(publishedProduct(), UUID.randomUUID().toString()))
                .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }
}

class GetPaymentApiTest extends ApiTestBase {
    @Test void returnsPaymentState() throws Exception {
        mvc.perform(get("/orders/{id}/payments", paidOrder()).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));
    }
}

class ListAdminOrdersApiTest extends ApiTestBase {
    @Test void listsOrdersForMerchant() throws Exception {
        order(publishedProduct(), UUID.randomUUID().toString());
        mvc.perform(get("/admin/orders").with(user("merchant").roles("MERCHANT_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
    }
}

class GetShipmentApiTest extends ApiTestBase {
    @Test void returnsTrackingNumber() throws Exception {
        JsonNode order = shippedOrder();
        mvc.perform(get("/orders/{id}/shipment", order.get("id").asLong())
                .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trackingNo").isNotEmpty());
    }
}

class ListTrackingApiTest extends ApiTestBase {
    @Test void returnsEmptyTimelineBeforeFirstPush() throws Exception {
        mvc.perform(get("/orders/{id}/tracking", shippedOrder().get("id").asLong())
                .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }
}

class GetAfterSaleApiTest extends ApiTestBase {
    @Test void returnsExistingCase() throws Exception {
        long id = paidOrder();
        mvc.perform(post("/orders/{id}/after-sales", id).with(user("customer").roles("CUSTOMER"))
                .with(write()).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"破损\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/orders/{id}/after-sales", id).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("破损"));
    }
}

class ReceiveTrackingApiTest extends ApiTestBase {
    @Test void acceptsSignedEventAndRejectsForgedSignature() throws Exception {
        String trackingNo = shippedOrder().get("trackingNo").asText();
        String raw = json.writeValueAsString(new Event(UUID.randomUUID().toString(), trackingNo,
                "PICKED_UP", "香港", LocalDateTime.now()));
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("local-demo-secret-change-me".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + raw).getBytes(StandardCharsets.UTF_8)));
        mvc.perform(post("/callbacks/logistics/mock").contentType(MediaType.APPLICATION_JSON).content(raw)
                .header("X-Logistics-Timestamp", timestamp).header("X-Logistics-Signature", signature))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("accepted"));
        mvc.perform(post("/callbacks/logistics/mock").contentType(MediaType.APPLICATION_JSON).content(raw)
                .header("X-Logistics-Timestamp", timestamp).header("X-Logistics-Signature", signature))
                .andExpect(status().isOk());
        mvc.perform(post("/callbacks/logistics/mock").contentType(MediaType.APPLICATION_JSON).content(raw)
                .header("X-Logistics-Timestamp", timestamp).header("X-Logistics-Signature", "00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    record Event(String eventId, String trackingNo, String status, String location, LocalDateTime occurredAt) {}
}

class GetTrackingReceiptApiTest extends ApiTestBase {
    @Test void rejectsUnknownEvent() throws Exception {
        mvc.perform(get("/callbacks/logistics/mock/status/{eventId}", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRACKING_NOT_FOUND"));
    }
}
