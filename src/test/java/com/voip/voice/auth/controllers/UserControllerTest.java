
package com.voip.voice.auth.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voip.voice.auth.dtos.LoginRequestDetails;
import com.voip.voice.auth.dtos.LoginResponseMessage;
import com.voip.voice.auth.dtos.SignupDetails;
import com.voip.voice.auth.services.AuthService;
import com.voip.voice.entities.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    // ---------------- SIGNUP TESTS ----------------

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {

        SignupDetails signupDetails =
                new SignupDetails("praveen", "password123");

        when(authService.register(any(SignupDetails.class)))
                .thenReturn(mock(User.class));

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("User Registered Successfully"));

        verify(authService).register(any(SignupDetails.class));
    }

    @Test
    void shouldReturnBadRequestWhenRegistrationFails() throws Exception {

        SignupDetails signupDetails =
                new SignupDetails("praveen", "password123");

        when(authService.register(any(SignupDetails.class)))
                .thenReturn(null);

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("User Registration Failed"));

        verify(authService).register(any(SignupDetails.class));
    }

    @Test
    void shouldReturnBadRequestWhenUsernameAlreadyExists() throws Exception {

        SignupDetails signupDetails =
                new SignupDetails("praveen", "password123");

        when(authService.register(any(SignupDetails.class)))
                .thenThrow(new IllegalArgumentException(
                        "Username is already exists"));

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Username is already exists"));

        verify(authService).register(any(SignupDetails.class));
    }

    @Test
    void shouldReturnInternalServerErrorWhenSignupThrowsUnexpectedException()
            throws Exception {

        SignupDetails signupDetails =
                new SignupDetails("praveen", "password123");

        when(authService.register(any(SignupDetails.class)))
                .thenThrow(new RuntimeException("Database unavailable"));

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupDetails)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("Database unavailable"));

        verify(authService).register(any(SignupDetails.class));
    }

    // ---------------- LOGIN TESTS ----------------

    @Test
    void shouldLoginUserSuccessfully() throws Exception {

        LoginRequestDetails loginRequest =
                new LoginRequestDetails("praveen", "password123");

        LoginResponseMessage loginResponse =
                new LoginResponseMessage(
                        "Login Successful",
                        "test.jwt.token"
                );

        when(authService.login(any(LoginRequestDetails.class)))
                .thenReturn(loginResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Login Successful"))
                .andExpect(jsonPath("$.token")
                        .value("test.jwt.token"));

        verify(authService).login(any(LoginRequestDetails.class));
    }

    @Test
    void shouldReturnBadRequestWhenLoginResponseIsUnsuccessful()
            throws Exception {

        LoginRequestDetails loginRequest =
                new LoginRequestDetails("praveen", "password123");

        LoginResponseMessage loginResponse =
                new LoginResponseMessage(
                        "Login Failed",
                        ""
                );

        when(authService.login(any(LoginRequestDetails.class)))
                .thenReturn(loginResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Login Failed"))
                .andExpect(jsonPath("$.token").value(""));

        verify(authService).login(any(LoginRequestDetails.class));
    }

    @Test
    void shouldReturnBadRequestWhenCredentialsAreInvalid()
            throws Exception {

        LoginRequestDetails loginRequest =
                new LoginRequestDetails("praveen", "wrongPassword");

        when(authService.login(any(LoginRequestDetails.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Invalid username or password"))
                .andExpect(jsonPath("$.token").value(""));

        verify(authService).login(any(LoginRequestDetails.class));
    }

    @Test
    void shouldReturnInternalServerErrorWhenLoginThrowsUnexpectedException()
            throws Exception {

        LoginRequestDetails loginRequest =
                new LoginRequestDetails("praveen", "password123");

        when(authService.login(any(LoginRequestDetails.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("Unexpected error"))
                .andExpect(jsonPath("$.token").value(""));

        verify(authService).login(any(LoginRequestDetails.class));
    }
}
