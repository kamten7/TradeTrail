package com.tradetrail.api;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListProductsApiTest extends ApiTestBase {
    @Test
    void listsPublishedProducts() throws Exception {
        publishedProduct();
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$[0].published").value(true));
    }
}
