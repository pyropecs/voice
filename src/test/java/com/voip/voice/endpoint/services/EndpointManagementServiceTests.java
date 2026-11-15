
package com.voip.voice.endpoint.services;

import com.voip.voice.auth.repositories.UserRepository;
import com.voip.voice.endpoint.dtos.EndpointDetails;
import com.voip.voice.endpoint.repositories.EndpointRepository;
import com.voip.voice.entities.Endpoint;
import com.voip.voice.entities.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointManagementServiceTest {

    @Mock
    private EndpointRepository endpointRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EndpointManagementService endpointManagementService;

    private EndpointDetails endpointDetails;
    private User user;

    @BeforeEach
    void setUp() {
        endpointDetails = new EndpointDetails(
                "1001",
                "sipPassword123",
                "192.168.1.100",
                "praveen"
        );

        user = mock(User.class);
    }

    // ---------------- CREATE ENDPOINT ----------------

    @Test
    void shouldCreateEndpointSuccessfully() throws Exception {

        when(userRepository.getUserByUsername("praveen"))
                .thenReturn(user);

        String result =
                endpointManagementService.createEndpoint(endpointDetails);

        assertEquals("Endpoint created successfully", result);

        ArgumentCaptor<Endpoint> captor =
                ArgumentCaptor.forClass(Endpoint.class);

        verify(endpointRepository).save(captor.capture());

        Endpoint savedEndpoint = captor.getValue();

        assertEquals("1001", savedEndpoint.getEndpoint());
        assertEquals("sipPassword123", savedEndpoint.getPassword());
        assertEquals("192.168.1.100", savedEndpoint.getSipServer());
        assertSame(user, savedEndpoint.getUser());

        verify(userRepository).getUserByUsername("praveen");
    }

    @Test
    void shouldReturnErrorWhenPhoneNumberIsEmpty() throws Exception {

        EndpointDetails details = new EndpointDetails(
                "",
                "sipPassword123",
                "192.168.1.100",
                "praveen"
        );

        String result =
                endpointManagementService.createEndpoint(details);

        assertEquals(
                "Please Enter the Phone Number and Password",
                result
        );

        verifyNoInteractions(endpointRepository, userRepository);
    }

    @Test
    void shouldReturnErrorWhenPasswordIsEmpty() throws Exception {

        EndpointDetails details = new EndpointDetails(
                "1001",
                "",
                "192.168.1.100",
                "praveen"
        );

        String result =
                endpointManagementService.createEndpoint(details);

        assertEquals(
                "Please Enter the Phone Number and Password",
                result
        );

        verifyNoInteractions(endpointRepository, userRepository);
    }

    @Test
    void shouldReturnErrorWhenSipServerIsEmpty() throws Exception {

        EndpointDetails details = new EndpointDetails(
                "1001",
                "sipPassword123",
                "",
                "praveen"
        );

        String result =
                endpointManagementService.createEndpoint(details);

        assertEquals("Please Enter the Sip Server Address", result);

        verifyNoInteractions(endpointRepository, userRepository);
    }

    // ---------------- UPDATE ENDPOINT ----------------

    @Test
    void shouldUpdateEndpointSuccessfully() throws Exception {

        Endpoint existingEndpoint = new Endpoint();

        existingEndpoint.setEndpoint("1001");

        when(endpointRepository.findByEndpoint("1001"))
                .thenReturn(existingEndpoint);

        when(userRepository.getUserByUsername("praveen"))
                .thenReturn(user);

        String result =
                endpointManagementService.updateEndpoint(endpointDetails);

        assertEquals("Endpoint updated successfully", result);

        assertEquals("1001", existingEndpoint.getEndpoint());
        assertEquals("sipPassword123", existingEndpoint.getPassword());
        assertEquals("192.168.1.100", existingEndpoint.getSipServer());
        assertSame(user, existingEndpoint.getUser());

        verify(endpointRepository).findByEndpoint("1001");
        verify(userRepository).getUserByUsername("praveen");
        verify(endpointRepository).save(existingEndpoint);
    }

    @Test
    void shouldReturnErrorWhenEndpointDoesNotExist() throws Exception {

        when(endpointRepository.findByEndpoint("1001"))
                .thenReturn(null);

        String result =
                endpointManagementService.updateEndpoint(endpointDetails);

        assertEquals("Endpoint 1001 not found", result);

        verify(endpointRepository).findByEndpoint("1001");
        verify(endpointRepository, never()).save(any(Endpoint.class));
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldReturnErrorWhenUpdatingWithEmptyPhoneNumber()
            throws Exception {

        EndpointDetails details = new EndpointDetails(
                "",
                "sipPassword123",
                "192.168.1.100",
                "praveen"
        );

        String result =
                endpointManagementService.updateEndpoint(details);

        assertEquals(
                "Please Enter the Phone Number and Password",
                result
        );

        verifyNoInteractions(endpointRepository, userRepository);
    }

    // ---------------- DELETE ENDPOINT ----------------

    @Test
    void shouldDeleteEndpointSuccessfully() throws Exception {

        when(endpointRepository.existsById(1)).thenReturn(true);

        String result = endpointManagementService.deleteEndpoint(1);

        assertEquals("Endpoint deleted Sucessfully", result);

        verify(endpointRepository).existsById(1);
        verify(endpointRepository).deleteById(1);
    }

    @Test
    void shouldReturnErrorWhenDeletingNonExistingEndpoint()
            throws Exception {

        when(endpointRepository.existsById(1)).thenReturn(false);

        String result = endpointManagementService.deleteEndpoint(1);

        assertEquals("Endpoint not found", result);

        verify(endpointRepository).existsById(1);
        verify(endpointRepository, never()).deleteById(anyInt());
    }

    // ---------------- GET ENDPOINT BY ID ----------------

    @Test
    void shouldReturnEndpointWhenIdExists() throws Exception {

        Endpoint endpoint = new Endpoint();
        endpoint.setEndpoint("1001");

        when(endpointRepository.existsById(1)).thenReturn(true);
        when(endpointRepository.findById(1)).thenReturn(endpoint);

        Endpoint result = endpointManagementService.getEndpointById(1);

        assertNotNull(result);
        assertSame(endpoint, result);

        verify(endpointRepository).existsById(1);
        verify(endpointRepository).findById(1);
    }

    @Test
    void shouldReturnNullWhenEndpointIdDoesNotExist() throws Exception {

        when(endpointRepository.existsById(1)).thenReturn(false);

        Endpoint result = endpointManagementService.getEndpointById(1);

        assertNull(result);

        verify(endpointRepository).existsById(1);
        verify(endpointRepository, never()).findById(anyInt());
    }

    // ---------------- GET ALL ENDPOINTS ----------------

    @Test
    void shouldReturnAllEndpoints() throws Exception {

        Endpoint endpoint1 = new Endpoint();
        endpoint1.setEndpoint("1001");

        Endpoint endpoint2 = new Endpoint();
        endpoint2.setEndpoint("1002");

        List<Endpoint> endpoints = List.of(endpoint1, endpoint2);

        when(endpointRepository.findAll()).thenReturn(endpoints);

        List<Endpoint> result =
                endpointManagementService.getAllEndpoints();

        assertEquals(2, result.size());
        assertSame(endpoints, result);

        verify(endpointRepository).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenNoEndpointsExist() throws Exception {

        when(endpointRepository.findAll()).thenReturn(List.of());

        List<Endpoint> result =
                endpointManagementService.getAllEndpoints();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(endpointRepository).findAll();
    }
}
