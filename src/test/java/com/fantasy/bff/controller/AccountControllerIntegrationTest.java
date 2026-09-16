package com.fantasy.bff.controller;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.model.downstream.Avatar;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.security.JwtTokenValidator;
import com.fantasy.bff.service.AvatarService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5};

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
    void servesThePictureUnderTheTypeItWasStoredAs() throws Exception {
        when(databaseServiceClient.findAvatar(USER_ID))
                .thenReturn(Optional.of(new Avatar("image/jpeg", JPEG)));

        mockMvc.perform(get("/api/v1/account/avatar").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(JPEG));
    }

    @Test
    void answersAnAccountWithoutAPictureWithNothingRatherThanAnError() throws Exception {
        when(databaseServiceClient.findAvatar(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/account/avatar").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void storesAnUploadedPictureUnderTheTypeItsBytesSay() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/account/avatar")
                        .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", PNG))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());

        ArgumentCaptor<Avatar> stored = ArgumentCaptor.forClass(Avatar.class);
        verify(databaseServiceClient).setAvatar(eq(USER_ID), stored.capture());
        assertThat(stored.getValue().contentType()).isEqualTo("image/png");
        assertThat(stored.getValue().data()).isEqualTo(PNG);
    }

    @Test
    void refusesAnUploadThatIsNotAPicture() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/account/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png", "not a picture".getBytes()))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).setAvatar(any(), any());
    }

    @Test
    void refusesAPictureLargerThanAllowed() throws Exception {
        byte[] oversized = new byte[AvatarService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/account/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png", oversized))
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isBadRequest());

        verify(databaseServiceClient, never()).setAvatar(any(), any());
    }

    @Test
    void removesThePicture() throws Exception {
        mockMvc.perform(delete("/api/v1/account/avatar").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());

        verify(databaseServiceClient).deleteAvatar(USER_ID);
    }

    @Test
    void needsASignedInAccount() throws Exception {
        mockMvc.perform(get("/api/v1/account")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/account/username")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alex\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/account/avatar")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/account/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/account/avatar")).andExpect(status().isUnauthorized());
    }

    @Test
    void signOutEverywhere_revokesTheSessionsOfTheAccountInTheToken() throws Exception {
        mockMvc.perform(post("/api/v1/account/sessions/revoke").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNoContent());

        verify(databaseServiceClient).revokeSessions(USER_ID);
    }

    @Test
    void signOutEverywhere_requiresASignedInCaller() throws Exception {
        mockMvc.perform(post("/api/v1/account/sessions/revoke")).andExpect(status().isUnauthorized());

        verify(databaseServiceClient, never()).revokeSessions(any());
    }
}
