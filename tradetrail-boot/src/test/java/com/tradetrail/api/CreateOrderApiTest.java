package com.tradetrail.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreateOrderApiTest extends ApiTestBase {
    @Test
    void createsOrderAndReturnsSameIdForRepeatedRequest() throws Exception {
        long productId = publishedProduct();
        String key = UUID.randomUUID().toString();
        long orderId = order(productId, key);
        mvc.perform(post("/orders").with(user("customer").roles("CUSTOMER")).with(write())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(productId,1)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(orderId));
    }

    @Test
    void rejectsQuantityAboveAvailableStock() throws Exception {
        long productId = publishedProduct();
        mvc.perform(post("/orders").with(user("customer").roles("CUSTOMER")).with(write())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(orderBody(productId,11)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
    }
}
