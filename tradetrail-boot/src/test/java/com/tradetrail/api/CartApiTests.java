package com.tradetrail.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AddCartItemApiTest extends ApiTestBase {
    @Test
    void addsPublishedProduct() throws Exception {
        mvc.perform(post("/cart/items").with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + publishedProduct() + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2));
    }
}

class ListCartItemsApiTest extends ApiTestBase {
    @Test
    void listsOwnItems() throws Exception {
        mvc.perform(get("/cart/items").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

class QuoteCartApiTest extends ApiTestBase {
    @Test
    void computesCurrentPrice() throws Exception {
        mvc.perform(post("/cart/items").with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + publishedProduct() + ",\"quantity\":2}")).andExpect(status().isCreated());
        mvc.perform(get("/cart/quote").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subtotalCents").isNumber());
    }
}

class UpdateCartItemApiTest extends ApiTestBase {
    @Test
    void changesQuantity() throws Exception {
        long productId = publishedProduct();
        var result = mvc.perform(post("/cart/items").with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":1}")).andReturn();
        JsonNode item = json.readTree(result.getResponse().getContentAsString());
        mvc.perform(put("/cart/items/{id}", item.get("id").asLong()).with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":3,\"selected\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));
    }
}

class DeleteCartItemApiTest extends ApiTestBase {
    @Test
    void removesOwnedItem() throws Exception {
        long productId = publishedProduct();
        var result = mvc.perform(post("/cart/items").with(user("customer").roles("CUSTOMER"))
                .with(write())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"quantity\":1}")).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(delete("/cart/items/{id}", id).with(user("customer").roles("CUSTOMER")).with(write()))
                .andExpect(status().isNoContent());
    }
}

class AdjustStockApiTest extends ApiTestBase {
    @Test
    void adjustsAvailableStockWithReason() throws Exception {
        mvc.perform(
                post("/admin/products/{id}/stock-adjustments", product()).with(user("merchant").roles("MERCHANT_ADMIN"))
                        .with(write())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":5,\"reason\":\"盘点入库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(15));
    }
}
