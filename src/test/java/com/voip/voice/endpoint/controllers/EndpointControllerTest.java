package com.voip.voice.endpoint.controllers;

import com.voip.voice.endpoint.dtos.EndpointDetails;
import com.voip.voice.endpoint.dtos.EndpointResponseDetails;
import com.voip.voice.endpoint.services.EndpointManagementService;
import com.voip.voice.entities.Endpoint;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EndpointController.class)
@AutoConfigureMockMvc(addFilters = false)
class EndpointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EndpointManagementService endpointManagementService;

    @Test
    void createEndpoint_ShouldReturnSuccess() throws Exception {

        EndpointDetails details =
                new EndpointDetails("1001", "password123", "192.168.1.10", "user1");

        when(endpointManagementService.createEndpoint(any(EndpointDetails.class)))
                .thenReturn("Endpoint created successfully");

        mockMvc.perform(post("/endpoint/create")
                        .contentType("application/json")
                        .content("""
                                {
                                    "phoneNumber": "1001",
                                    "password": "password123",
                                    "sipServer": "192.168.1.10",
                                    "username": "user1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Endpoint created successfully"));

        verify(endpointManagementService).createEndpoint(any(EndpointDetails.class));
    }

    @Test
    void getEndpoint_ShouldReturnEndpoint() throws Exception {

        Endpoint endpoint = new Endpoint();
        endpoint.setEndpoint("1001");

        when(endpointManagementService.getEndpointById(1))
                .thenReturn(endpoint);

        mockMvc.perform(get("/endpoint/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value("1001"));

        verify(endpointManagementService).getEndpointById(1);
    }

    @Test
    void deleteEndpoint_ShouldReturnSuccess() throws Exception {

        when(endpointManagementService.deleteEndpoint(1))
                .thenReturn("Endpoint deleted Sucessfully");

        mockMvc.perform(delete("/endpoint/delete/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Endpoint deleted Sucessfully"));

        verify(endpointManagementService).deleteEndpoint(1);
    }

    @Test
    void updateEndpoint_ShouldReturnSuccess() throws Exception {

        when(endpointManagementService.updateEndpoint(any(EndpointDetails.class)))
                .thenReturn("Endpoint updated successfully");

        mockMvc.perform(post("/endpoint/update")
                        .contentType("application/json")
                        .content("""
                                {
                                    "phoneNumber": "1001",
                                    "password": "newpassword",
                                    "sipServer": "192.168.1.20",
                                    "username": "user1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Endpoint updated successfully"));

        verify(endpointManagementService).updateEndpoint(any(EndpointDetails.class));
    }

    @Test
    void getAllEndpoints_ShouldReturnEndpointList() throws Exception {

        Endpoint endpoint1 = new Endpoint();
        endpoint1.setEndpoint("1001");

        Endpoint endpoint2 = new Endpoint();
        endpoint2.setEndpoint("1002");

        when(endpointManagementService.getAllEndpoints())
                .thenReturn(List.of(endpoint1, endpoint2));

        mockMvc.perform(get("/endpoint/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].endpoint").value("1001"))
                .andExpect(jsonPath("$[1].endpoint").value("1002"));

        verify(endpointManagementService).getAllEndpoints();
    }

    @Test
    void getEndpoint_WhenServiceThrowsException_ShouldReturnInternalServerError()
            throws Exception {

        when(endpointManagementService.getEndpointById(1))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/endpoint/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Database error"));
    }
}