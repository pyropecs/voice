
package com.voip.voice.auth.filters;

import com.voip.voice.auth.services.JWTService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JWTAuthenticationFilterTest {

    @Mock
    private JWTService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    private JWTAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter =
                new JWTAuthenticationFilter(jwtService, userDetailsService);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest createRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/endpoints");

        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }

        return request;
    }

    @Test
    void shouldContinueFilterChainWhenBearerTokenIsMissing()
            throws ServletException, IOException {

        MockHttpServletRequest request = createRequest(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());

        verifyNoInteractions(jwtService, userDetailsService);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAuthenticateUserWhenTokenIsValid()
            throws ServletException, IOException {

        String token = "valid.jwt.token";

        UserDetails userDetails = User.withUsername("praveen")
                .password("encodedPassword")
                .authorities("ROLE_USER")
                .build();

        when(jwtService.extractUsername(token)).thenReturn("praveen");
        when(userDetailsService.loadUserByUsername("praveen"))
                .thenReturn(userDetails);
        when(jwtService.validateToken(token, userDetails)).thenReturn(true);

        MockHttpServletRequest request = createRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, chain);

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertSame(userDetails, authentication.getPrincipal());
        assertNull(authentication.getCredentials());
        assertTrue(authentication.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));

        assertNotNull(chain.getRequest());

        verify(jwtService).extractUsername(token);
        verify(userDetailsService).loadUserByUsername("praveen");
        verify(jwtService).validateToken(token, userDetails);
    }

    @Test
    void shouldNotAuthenticateWhenTokenValidationReturnsFalse()
            throws ServletException, IOException {

        String token = "invalid.jwt.token";

        UserDetails userDetails = User.withUsername("praveen")
                .password("encodedPassword")
                .authorities("ROLE_USER")
                .build();

        when(jwtService.extractUsername(token)).thenReturn("praveen");
        when(userDetailsService.loadUserByUsername("praveen"))
                .thenReturn(userDetails);
        when(jwtService.validateToken(token, userDetails)).thenReturn(false);

        MockHttpServletRequest request = createRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());

        verify(jwtService).validateToken(token, userDetails);
    }

    @Test
    void shouldNotAuthenticateWhenJwtThrowsException()
            throws ServletException, IOException {

        String token = "expired.jwt.token";

        when(jwtService.extractUsername(token))
                .thenThrow(new JwtException("Token expired"));

        MockHttpServletRequest request = createRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());

        verifyNoInteractions(userDetailsService);
    }

    @Test
    void shouldNotAuthenticateWhenUserDoesNotExist()
            throws ServletException, IOException {

        String token = "valid.jwt.token";

        when(jwtService.extractUsername(token)).thenReturn("unknown");
        when(userDetailsService.loadUserByUsername("unknown"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        MockHttpServletRequest request = createRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());

        verify(jwtService, never()).validateToken(anyString(), any());
    }

    @Test
    void shouldNotOverwriteExistingAuthentication()
            throws ServletException, IOException {

        Authentication existingAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "existing-user",
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")
                );

        SecurityContextHolder.getContext()
                .setAuthentication(existingAuthentication);

        String token = "valid.jwt.token";

        when(jwtService.extractUsername(token)).thenReturn("praveen");

        MockHttpServletRequest request = createRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtAuthenticationFilter.doFilter(request, response, chain);

        assertSame(existingAuthentication,
                SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(userDetailsService);
        verify(jwtService, never()).validateToken(anyString(), any());
    }
}
