package com.tradetrail.simulator;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class SimulatorApiTestBase {
    @Autowired MockMvc mvc;
    @MockitoBean DeliveryService service;
}

class GetSimulatorPageApiTest extends SimulatorApiTestBase {
    @Test void showsOperatorPage() throws Exception {
        when(service.list()).thenReturn(List.of());
        mvc.perform(get("/").with(user("logistics").roles("LOGISTICS_OPERATOR")))
                .andExpect(status().isOk());
    }
}

class CreateTrackingEventApiTest extends SimulatorApiTestBase {
    @Test void savesAndAttemptsDelivery() throws Exception {
        TrackingRow row = new TrackingRow();
        row.eventId = "event-1";
        when(service.create(anyString(), anyString(), anyString())).thenReturn(row);
        mvc.perform(post("/events").with(user("logistics").roles("LOGISTICS_OPERATOR")).with(csrf())
                .param("trackingNo", "MOCK-123").param("status", "PICKED_UP").param("location", "香港"))
                .andExpect(status().is3xxRedirection());
        verify(service).deliver("event-1");
    }
}

class RetryTrackingEventApiTest extends SimulatorApiTestBase {
    @Test void retriesExistingEvent() throws Exception {
        mvc.perform(post("/events/{eventId}/retry", "event-1")
                .with(user("logistics").roles("LOGISTICS_OPERATOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(service).retry("event-1");
    }
}
