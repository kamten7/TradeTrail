package com.tradetrail.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CancelOrderApiTest extends ApiTestBase {
    @Test
    void releasesReservationWhenCancelled() throws Exception {
        long id = order(publishedProduct(), UUID.randomUUID().toString());
        mvc.perform(post("/orders/{id}/cancel", id).with(user("customer").roles("CUSTOMER"))
                .with(write())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED_UNPAID"));
    }
}
