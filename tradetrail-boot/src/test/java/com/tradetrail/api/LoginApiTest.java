package com.tradetrail.api;

import org.junit.jupiter.api.Test;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginApiTest extends ApiTestBase {
    @Test void successfulLoginReturnsJsonWithoutCrossOriginRedirect() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username","merchant").param("password","merchant123"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.user").value("merchant"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_MERCHANT_ADMIN"));
    }

    @Test void incorrectPasswordUsesTheApiErrorContract() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username","merchant").param("password","wrong"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andExpect(jsonPath("$.allowedActions").isArray());
    }

    @Test void loginStillRequiresCsrfProtection() throws Exception {
        mvc.perform(post("/login").param("username","merchant").param("password","merchant123"))
            .andExpect(status().isForbidden());
    }
}
