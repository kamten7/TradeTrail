package com.tradetrail.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    long product() throws Exception {
        MvcResult result = mvc.perform(post("/admin/products").with(user("merchant").roles("MERCHANT_ADMIN"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"测试商品\",\"description\":\"演示\",\"priceCents\":1299,\"initialStock\":10}"))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError(result.getResponse().getContentAsString());
        }
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    long publishedProduct() throws Exception {
        long id = product();
        mvc.perform(post("/admin/products/{id}/publish", id).with(user("merchant").roles("MERCHANT_ADMIN"))
                .with(csrf())).andReturn();
        return id;
    }

    long order(long productId, String requestId) throws Exception {
        MvcResult result = mvc.perform(post("/orders").with(user("customer").roles("CUSTOMER"))
                .with(csrf()).header("Idempotency-Key", requestId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":1}"))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new AssertionError(result.getResponse().getContentAsString());
        }
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    long paidOrder() throws Exception {
        long id = order(publishedProduct(), java.util.UUID.randomUUID().toString());
        mvc.perform(post("/orders/{id}/payments", id).with(user("customer").roles("CUSTOMER"))
                .with(csrf())).andReturn();
        return id;
    }

    JsonNode shippedOrder() throws Exception {
        long id = paidOrder();
        MvcResult result = mvc.perform(post("/admin/orders/{id}/shipments", id)
                .with(user("merchant").roles("MERCHANT_ADMIN")).with(csrf())).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
