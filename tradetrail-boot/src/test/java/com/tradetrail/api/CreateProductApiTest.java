package com.tradetrail.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreateProductApiTest extends ApiTestBase {
    @Test
    void createsProductAndRejectsInvalidPrice() throws Exception {
        mvc.perform(post("/admin/products").with(user("merchant").roles("MERCHANT_ADMIN"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"茶杯\",\"description\":\"手作\",\"priceCents\":3200,\"initialStock\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("茶杯"));
        mvc.perform(post("/admin/products").with(user("merchant").roles("MERCHANT_ADMIN"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"茶杯\",\"priceCents\":0,\"initialStock\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
