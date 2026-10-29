package com.tradetrail.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PayOrderApiTest extends ApiTestBase {
    @Test
    void createsOneAttemptAndWaitsForVerifiedCallback() throws Exception {
        long id = order(publishedProduct(), UUID.randomUUID().toString());
        var first = mvc
                .perform(post("/orders/{id}/payments", id).with(user("customer").roles("CUSTOMER")).with(write()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andReturn();
        String attempt = json.readTree(first.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/orders/{id}/payments", id).with(user("customer").roles("CUSTOMER")).with(write()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(attempt));
        mvc.perform(get("/orders/{id}", id).with(user("customer").roles("CUSTOMER")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        pay(id);
        mvc.perform(get("/orders/{id}", id).with(user("customer").roles("CUSTOMER")))
                .andExpect(jsonPath("$.status").value("AWAITING_SHIPMENT"));
    }
}
