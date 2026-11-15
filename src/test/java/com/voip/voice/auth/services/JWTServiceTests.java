package com.voip.voice.auth.services;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JWTServiceTest {

    @Autowired
    private JWTService jwtService;

    @Test
    void shouldGenerateJwtToken() {

        String token = jwtService.generateToken("praveen");

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void shouldExtractUsernameFromToken() {

        String token = jwtService.generateToken("praveen");

        String username = jwtService.extractUsername(token);

        assertEquals("praveen", username);
    }

    @Test
    void shouldValidateTokenForCorrectUser() {

        String token = jwtService.generateToken("praveen");

        UserDetails userDetails = User.withUsername("praveen")
                .password("encodedPassword")
                .authorities("ROLE_USER")
                .build();

        assertTrue(jwtService.validateToken(token, userDetails));
    }

    @Test
    void shouldRejectTokenForDifferentUser() {

        String token = jwtService.generateToken("praveen");

        UserDetails userDetails = User.withUsername("anotherUser")
                .password("encodedPassword")
                .authorities("ROLE_USER")
                .build();

        assertFalse(jwtService.validateToken(token, userDetails));
    }

    @Test
    void shouldRejectMalformedToken() {

        assertThrows(
                JwtException.class,
                () -> jwtService.extractUsername("invalid.token.value")
        );
    }
}