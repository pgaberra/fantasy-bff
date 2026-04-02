package com.fantasy.bff.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Claims claims;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withValidToken_setsAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token");
        when(jwtTokenValidator.validateAndExtractClaims("valid.token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-123");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("user-123");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withNoAuthHeader_doesNotSetAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenValidator, never()).validateAndExtractClaims(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withInvalidToken_clearsContextAndContinues() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token");
        when(jwtTokenValidator.validateAndExtractClaims("invalid.token"))
                .thenThrow(new SecurityException("Invalid token"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withNonBearerHeader_doesNotSetAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenValidator, never()).validateAndExtractClaims(anyString());
        verify(filterChain).doFilter(request, response);
    }
}
