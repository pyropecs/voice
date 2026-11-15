
package com.voip.voice.auth.services;

import com.voip.voice.auth.repositories.UserRepository;
import com.voip.voice.entities.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomerUserDetailsService userDetailsService;

    @DisplayName("To test User details is exists when authentication module exists")
    @Test
    void shouldReturnUserDetailsWhenUserExists() {

        // Arrange
        User user = new User();
        user.setUsername("praveen");
        user.setPassword("encodedPassword");


        when(userRepository.getUserByUsername("praveen"))
                .thenReturn(user);

        // Act
        UserDetails userDetails =
                userDetailsService.loadUserByUsername("praveen");

        // Assert
        assertNotNull(userDetails);
        assertEquals("praveen", userDetails.getUsername());
        assertEquals("encodedPassword", userDetails.getPassword());

        assertTrue(userDetails.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_USER")));

        // Verify
        verify(userRepository, times(1))
                .getUserByUsername("praveen");
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {

        // Arrange
        when(userRepository.getUserByUsername("unknown"))
                .thenReturn(null);

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown")
        );

        assertEquals("User not found", exception.getMessage());

        // Verify
        verify(userRepository, times(1))
                .getUserByUsername("unknown");
    }
}
