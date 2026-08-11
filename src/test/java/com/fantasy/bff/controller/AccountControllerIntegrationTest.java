package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    @MockitoBean
    private DatabaseServiceClient databaseServiceClient;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private String token() {
        return jwtTokenValidator.generateToken(USER_ID.toString(), "owner@example.com");
    }

    @Test
    void returnsTheAccountsProfile() throws Exception {
        when(databaseServiceClient.findUserById(USER_ID))
                .thenReturn(new User(USER_ID.toString(), "owner@example.com", "alex", "hash", 0, true));

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alex"))
                .andExpect(jsonPath("$.email").value("owner@example.com"));
    }

    @Test
    void neverLeaksTheStoredPasswordHash() throws Exception {
        when(databaseServiceClient.findUserById(USER_ID))
                .thenReturn(new User(USER_ID.toString(), "owner@example.com", "alex", "hash", 0, true));

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void reportsAnAccountWithoutAName() throws Exception {
        when(databaseServiceClient.findUserById(USER_ID))
                .thenReturn(new User(USER_ID.toString(), "owner@example.com", null, "hash", 0, true));

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    void setsTheUsername() throws Exception {
        when(databaseServiceClient.setUsername(USER_ID, "alex"))
                .thenReturn(new User(USER_ID.toString(), "owner@example.com", "alex", "hash", 0, true));

        mockMvc.perform(put("/api/v1/account/username")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alex\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alex"));
    }

    @Test
    void rejectsANameWithCharactersThatAreNotAllowed() throws Exception {
        mockMvc.perform(put("/api/v1/account/username")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"al ex!\"}"))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).setUsername(any(), any());
    }

    @Test
    void relaysAConflictWhenTheNameIsTaken() throws Exception {
        when(databaseServiceClient.setUsername(USER_ID, "alex"))
                .thenThrow(new RestClientResponseException(
                        "Conflict", HttpStatus.CONFLICT, "Conflict", null, null, null));

        mockMvc.perform(put("/api/v1/account/username")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alex\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void needsASignedInAccount() throws Exception {
        mockMvc.perform(get("/api/v1/account")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/account/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alex\"}"))
                .andExpect(status().isUnauthorized());
    }
}
