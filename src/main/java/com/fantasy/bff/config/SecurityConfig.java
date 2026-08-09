package com.fantasy.bff.config;

import com.fantasy.bff.security.JwtAuthenticationFilter;
import com.fantasy.bff.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final SecurityProperties securityProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter,
                          SecurityProperties securityProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.securityProperties = securityProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth
                            .requestMatchers(securityProperties.permittedUrls().toArray(String[]::new)).permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/players/skaters").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/v1/players/goalies").permitAll()
                            .requestMatchers("/api/v1/projections", "/api/v1/projections/**").authenticated();

                    if (securityProperties.projectionModelEnabled()) {
                        auth.requestMatchers("/api/v1/projection-model", "/api/v1/projection-model/**")
                                .authenticated();
                    } else {
                        auth.requestMatchers("/api/v1/projection-model", "/api/v1/projection-model/**")
                                .denyAll();
                    }

                    auth
                            .requestMatchers("/api/v1/yahoo", "/api/v1/yahoo/**").authenticated()
                            .requestMatchers("/api/v1/espn", "/api/v1/espn/**").authenticated()
                            .requestMatchers("/api/v1/admin", "/api/v1/admin/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhook").permitAll()
                            .requestMatchers("/api/v1/billing/mock/**").permitAll()
                            .requestMatchers("/api/v1/billing", "/api/v1/billing/**").authenticated()
                            .anyRequest().denyAll();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((_, response, _) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(securityProperties.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
