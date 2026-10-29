package com.tradetrail.api;

import com.tradetrail.application.after_sale.AfterSaleService;
import com.tradetrail.application.payment.PaymentQueryService;
import com.tradetrail.application.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "tradetrail.mock-payments.refund-failures-before-success=1")
class ListFailedRefundsApiTest extends ApiTestBase {
    @Autowired
    AfterSaleService cases;
    @Autowired
    PaymentService payments;
    @Autowired
    PaymentQueryService query;

    @Test
    void failedRefundCanBeFoundAndRetriedWithTheSameIdentity() throws Exception {
        long order = paidOrder();
        var c = cases.apply(order, "customer", "退款演练");
        cases.decide(c.id(), true, "同意退款", "merchant");
        var refund = query.refund(order).orElseThrow();
        payments.executeRefund(refund.id());
        mvc.perform(get("/admin/refunds/failed").with(user("merchant").roles("MERCHANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(refund.id()))
                .andExpect(jsonPath("$[0].status").value("FAILED"));
        mvc.perform(get("/admin/refunds/failed").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        payments.executeRefund(refund.id());
        assertEquals(1, payments.refund(refund.id()).attempts());
        assertEquals(refund.id(), payments.retryRefund(refund.id()).id());
        payments.executeRefund(refund.id());
        cases.refunded(refund.id());
        payments.executeRefund(refund.id());
        assertEquals(2, payments.refund(refund.id()).attempts());
        assertEquals("REFUNDED", cases.find(c.id()).status());
        mvc.perform(get("/admin/refunds/failed").with(user("merchant").roles("MERCHANT_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
