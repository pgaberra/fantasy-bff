package com.fantasy.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ApplicationTests extends BaseIntegrationTest {

    @Autowired
    private UserDetailsService userDetailsService;

    @Test
    void contextLoads() {
    }

    @Test
    void springBootsDefaultUserDoesNotExist() {
        // Spring Boot's fallback account is named "user"; the application defines no local accounts.
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
