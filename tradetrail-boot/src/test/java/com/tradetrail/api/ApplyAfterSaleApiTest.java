package com.tradetrail.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证售后申请入口的响应及关键业务约束。
 */
class ApplyAfterSaleApiTest extends ApiTestBase {
    @Test
    void acceptsPaidOrderApplication() throws Exception {
        long id = order(publishedProduct(), UUID.randomUUID().toString());
        pay(id);
        mvc.perform(post("/orders/{id}/after-sales", id).with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"商品问题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }
}
