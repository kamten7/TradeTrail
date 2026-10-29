package com.tradetrail.api;

import com.tradetrail.application.after_sale.AfterSaleService;
import com.tradetrail.application.integration.EventService;
import com.tradetrail.application.payment.PaymentQueryService;
import com.tradetrail.application.payment.PaymentService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UpdateProductApiTest extends ApiTestBase {
    @Test
    void updatesPriceUnpublishesAndRejectsWrongRole() throws Exception {
        long id = publishedProduct();
        String body = "{\"name\":\"新名称\",\"description\":\"新描述\",\"priceCents\":2600,\"published\":false}";
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/admin/products/{id}", id).with(user("merchant").roles("MERCHANT_ADMIN"))
                    .with(write())
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priceCents").value(2600))
                    .andExpect(jsonPath("$.published").value(false));
        }
        mvc.perform(get("/products/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(put("/admin/products/{id}", id).with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/admin/products/{id}", id).with(user("merchant").roles("MERCHANT_ADMIN"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("2600", "0"))).andExpect(status().isBadRequest());
    }
}

class CheckoutCartApiTest extends ApiTestBase {
    @Test
    void checkoutIsAtomicIdempotentAndRejectsChangedQuote() throws Exception {
        long product = publishedProduct();
        mvc.perform(post("/cart/items").with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"quantity\":1}")).andExpect(status().isCreated());
        String key = UUID.randomUUID().toString();
        String body = orderBody(product, 1);
        // 当前用户可能有其他测试留下的条目，取消其他条目的勾选。
        var items = json.readTree(mvc.perform(get("/cart/items").with(user("customer").roles("CUSTOMER")))
                .andReturn()
                .getResponse()
                .getContentAsString());
        for (var item : items) {
            if (item.get("productId").asLong() != product) {
                mvc.perform(put("/cart/items/{id}", item.get("id").asLong()).with(user("customer").roles("CUSTOMER"))
                        .with(write())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"selected\":false}")).andExpect(status().isOk());
            }
        }
        mvc.perform(post("/cart/checkout").with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("1299", "1200")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
        var first = mvc.perform(post("/cart/checkout").with(user("customer").roles("CUSTOMER"))
                .with(write())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andReturn();
        long id = json.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/cart/checkout").with(user("customer").roles("CUSTOMER"))
                .with(write())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/cart/checkout").with(user("merchant").roles("MERCHANT_ADMIN"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
    }
}

class ConfirmOrderApiTest extends ApiTestBase {
    @Test
    void confirmsOwnedShipmentOnceAndRejectsForeignOrUnpaidOrders() throws Exception {
        long id = shippedOrder().get("id").asLong();
        mvc.perform(post("/orders/{id}/confirm", id).with(user("other").roles("CUSTOMER")).with(write()))
                .andExpect(status().isForbidden());
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/orders/{id}/confirm", id).with(user("customer").roles("CUSTOMER"))
                    .with(write())
                    .header("Idempotency-Key", key))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
        long pending = order(publishedProduct(), UUID.randomUUID().toString());
        mvc.perform(post("/orders/{id}/confirm", pending).with(user("customer").roles("CUSTOMER")).with(write()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.allowedActions[0]").value("PAY"));
    }
}

class GetOrderHistoryApiTest extends ApiTestBase {
    @Test
    void returnsAuditWithTraceAndProtectsOwnership() throws Exception {
        long id = paidOrder();
        mvc.perform(get("/orders/{id}/history", id).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].event").value("PLACE_ORDER"))
                .andExpect(jsonPath("$[0].traceId").isNotEmpty());
        mvc.perform(get("/orders/{id}/history", id).with(user("other").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/orders/{id}/history", Long.MAX_VALUE).with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isNotFound());
    }
}

class CompleteMockCheckoutApiTest extends ApiTestBase {
    @Test
    void recordsProviderSuccessWithoutSynchronouslyMarkingOrderPaid() throws Exception {
        long id = order(publishedProduct(), UUID.randomUUID().toString());
        var response = mvc
                .perform(post("/orders/{id}/payments", id).with(user("customer").roles("CUSTOMER")).with(write()))
                .andReturn();
        String payment = json.readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/mock/checkout/{id}/complete", payment).with(user("other").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"success\":true}")).andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/mock/checkout/{id}/complete", payment).with(user("customer").roles("CUSTOMER"))
                    .with(write())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"success\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.providerStatus").value("SUCCEEDED"));
        }
        mvc.perform(get("/orders/{id}", id).with(user("customer").roles("CUSTOMER")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }
}

class ReceivePaymentApiTest extends ApiTestBase {
    @Autowired
    PaymentService payments;

    @Test
    void verifiesAmountSignatureAndConcurrentReplayContract() throws Exception {
        long id = order(publishedProduct(), UUID.randomUUID().toString());
        var payment = payments.create(id, "customer");
        payments.simulate(payment.id(), "customer", true);
        String raw = json.writeValueAsString(Map.of("eventId", UUID.randomUUID().toString(), "paymentId", payment.id(),
                "amountCents", payment.amountCents(), "result", "SUCCEEDED"));
        String time = Long.toString(Instant.now().getEpochSecond());
        mvc.perform(post("/callbacks/payments/mock").contentType(MediaType.APPLICATION_JSON)
                .content(raw)
                .header("X-Payment-Timestamp", time)
                .header("X-Payment-Signature", "00")).andExpect(status().isBadRequest());
        String wrong = raw.replace(Long.toString(payment.amountCents()), "1");
        mvc.perform(post("/callbacks/payments/mock").contentType(MediaType.APPLICATION_JSON)
                .content(wrong)
                .header("X-Payment-Timestamp", time)
                .header("X-Payment-Signature", signature("local-payment-secret-change-me", time, wrong)))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/callbacks/payments/mock").contentType(MediaType.APPLICATION_JSON)
                    .content(raw)
                    .header("X-Payment-Timestamp", time)
                    .header("X-Payment-Signature", signature("local-payment-secret-change-me", time, raw)))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/orders/{id}", id).with(user("customer").roles("CUSTOMER")))
                .andExpect(jsonPath("$.status").value("AWAITING_SHIPMENT"));
    }
}

class ListAfterSalesAdminApiTest extends ApiTestBase {
    @Test
    void requiresMerchantAndValidatesPagination() throws Exception {
        mvc.perform(get("/admin/after-sales").with(user("merchant").roles("MERCHANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/admin/after-sales").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/after-sales?size=101").with(user("merchant").roles("MERCHANT_ADMIN")))
                .andExpect(status().isBadRequest());
    }
}

class DecideAfterSaleApiTest extends ApiTestBase {
    @Autowired
    AfterSaleService cases;

    @Test
    void approvesOnceAndKeepsShipmentBlocked() throws Exception {
        long id = paidOrder();
        var c = cases.apply(id, "customer", "退款原因");
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/admin/after-sales/{id}/decisions", c.id()).with(user("merchant").roles("MERCHANT_ADMIN"))
                    .with(write())
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"approve\":true,\"reason\":\"批准退款\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REFUNDING"));
        }
        mvc.perform(
                post("/admin/orders/{id}/shipments", id).with(user("merchant").roles("MERCHANT_ADMIN")).with(write()))
                .andExpect(status().isConflict());
        mvc.perform(post("/admin/after-sales/{id}/decisions", c.id()).with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approve\":true,\"reason\":\"越权\"}")).andExpect(status().isForbidden());
    }
}

class SubmitReturnParcelApiTest extends ApiTestBase {
    @Autowired
    AfterSaleService cases;

    @Test
    void acceptsOwnedReturnAndRejectsWrongOwnerOrBlankTracking() throws Exception {
        long id = shippedOrder().get("id").asLong();
        var c = cases.apply(id, "customer", "退货");
        cases.decide(c.id(), true, "批准", "merchant");
        mvc.perform(post("/after-sales/{id}/return", c.id()).with(user("other").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNo\":\"RET-001\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/after-sales/{id}/return", c.id()).with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackingNo\":\"\"}")).andExpect(status().isBadRequest());
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/after-sales/{id}/return", c.id()).with(user("customer").roles("CUSTOMER"))
                    .with(write())
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"trackingNo\":\"RET-001\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RETURN_IN_TRANSIT"));
        }
    }
}

class ReceiveReturnParcelApiTest extends ApiTestBase {
    @Autowired
    AfterSaleService cases;

    @Test
    void qualifiedReturnRestocksOnceAndQueuesRefund() throws Exception {
        long id = shippedOrder().get("id").asLong();
        var c = cases.apply(id, "customer", "退货");
        cases.decide(c.id(), true, "批准", "merchant");
        cases.returnParcel(c.id(), "customer", "RET-002");
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/admin/after-sales/{id}/receipts", c.id()).with(user("merchant").roles("MERCHANT_ADMIN"))
                    .with(write())
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"qualified\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.restocked").value(true))
                    .andExpect(jsonPath("$.status").value("REFUNDING"));
        }
        mvc.perform(post("/admin/after-sales/{id}/receipts", c.id()).with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qualified\":true}")).andExpect(status().isForbidden());
    }
}

class RetryRefundApiTest extends ApiTestBase {
    @Autowired
    AfterSaleService cases;
    @Autowired
    PaymentService payments;
    @Autowired
    PaymentQueryService query;

    @Test
    void requeuesFailedRefundAndRequiresMerchant() throws Exception {
        long id = paidOrder();
        var c = cases.apply(id, "customer", "退款");
        cases.decide(c.id(), true, "批准", "merchant");
        var r = query.refund(id).orElseThrow();
        payments.failRefund(r.id(), "模拟故障");
        mvc.perform(post("/admin/refunds/{id}/retry", r.id()).with(user("customer").roles("CUSTOMER")).with(write()))
                .andExpect(status().isForbidden());
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/admin/refunds/{id}/retry", r.id()).with(user("merchant").roles("MERCHANT_ADMIN"))
                    .with(write())
                    .header("Idempotency-Key", key))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(r.id()))
                    .andExpect(jsonPath("$.status").value("REQUESTED"));
        }
    }
}

class ListOutboxApiTest extends ApiTestBase {
    @Test
    void exposesRecoveryQueueOnlyToMerchant() throws Exception {
        mvc.perform(get("/admin/outbox").with(user("merchant").roles("MERCHANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/admin/outbox").with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
    }
}

class RetryOutboxApiTest extends ApiTestBase {
    @Autowired
    EventService events;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void requeuesDeadLetterWithoutChangingEventIdentity() throws Exception {
        String aggregate = UUID.randomUUID().toString();
        events.publish("ORDER_CHANGED", aggregate, "test");
        String id = jdbc.queryForObject("SELECT event_id FROM outbox_event WHERE aggregate_id=?", String.class,
                aggregate);
        jdbc.update("UPDATE outbox_event SET state='FAILED',attempts=8 WHERE event_id=?", id);
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/admin/outbox/{id}/retry", id).with(user("merchant").roles("MERCHANT_ADMIN"))
                    .with(write())
                    .header("Idempotency-Key", key)).andExpect(status().isOk());
        }
        assertEquals("PENDING",
                jdbc.queryForObject("SELECT state FROM outbox_event WHERE event_id=?", String.class, id));
        mvc.perform(post("/admin/outbox/{id}/retry", id).with(user("customer").roles("CUSTOMER")).with(write()))
                .andExpect(status().isForbidden());
    }
}
