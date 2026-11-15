package com.voip.voice.auth.services;

import com.voip.voice.auth.dtos.LoginRequestDetails;
import com.voip.voice.auth.dtos.LoginResponseMessage;
import com.voip.voice.auth.dtos.SignupDetails;
import com.voip.voice.auth.repositories.UserRepository;
import com.voip.voice.entities.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTests {

    @Mock
    private UserRepository userRepository;


    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JWTService jwtService;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    public void setUp() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("User successfully registered")
    @Test
    void registerShouldSaveEncodedPassword() {

        SignupDetails signupDetails = new SignupDetails("username", "password");
        when(userRepository.existsByUsername(signupDetails.username())).thenReturn(false);
        when(passwordEncoder.encode(signupDetails.password())).thenReturn("encoded-password");
        User user = authService.register(signupDetails);
        assertNotNull(user);
        assertEquals("encoded-password", user.getPassword());
        assertEquals("username", user.getUsername());

        verify(userRepository).existsByUsername("username");
        verify(passwordEncoder).encode("password");
        verify(userRepository).save(any(User.class));


    }

   @DisplayName("Register should thrpw if the user is already existed")
    @Test
    void registerShouldThrowExceptionIfUserAlreadyExists() {
        SignupDetails signupDetails = new SignupDetails("username", "password");
        when(userRepository.existsByUsername(signupDetails.username())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(signupDetails));
        verify(userRepository).existsByUsername("username");
        verify(passwordEncoder,never()).encode("password");
        verify(userRepository,never()).save(any(User.class));
    }

    @DisplayName("User should be logged in with correct credentials and token should be passed")
    @Test
    void userShouldBeLoggedInWithCorrectCredentials() {

    LoginRequestDetails loginRequestDetails = new LoginRequestDetails("John", "password-123");
        Authentication authentication = mock(Authentication.class);
       when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
       when(authentication.isAuthenticated()).thenReturn(true);
        when(jwtService.generateToken("John"))
                .thenReturn("test.jwt.token");
        LoginResponseMessage loginResponseMessage = authService.login(loginRequestDetails);
        assertNotNull(loginResponseMessage);
        assertEquals("Login Successful",loginResponseMessage.message());
        assertEquals("test.jwt.token", loginResponseMessage.token());

    }
    @DisplayName("User should not logged in if the cedentials is incorrect")
    @Test
    void loginShouldNotGenerateTokenWhenAuthenticationFails() {

        // Arrange
        LoginRequestDetails loginRequest =
                new LoginRequestDetails("praveen", "wrongPassword");

        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        when(authentication.isAuthenticated()).thenReturn(false);

        // Act
        LoginResponseMessage response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("Incorrect Username or Password", response.message());
        assertEquals("", response.token());

        // Verify JWT was never generated
        verify(jwtService, never()).generateToken(anyString());
    }

    @DisplayName("throws Exception if the user is bad credentials")

    @Test
    void loginShouldThrowExceptionWhenCredentialsAreInvalid() {

        LoginRequestDetails loginRequest =
                new LoginRequestDetails("praveen", "wrongPassword");

        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        verify(jwtService, never()).generateToken(anyString());
    }

}
