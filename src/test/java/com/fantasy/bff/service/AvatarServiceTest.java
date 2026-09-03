package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.model.downstream.Avatar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8'};
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 2};
    private static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes();

    @Mock
    private DatabaseServiceClient databaseServiceClient;

    private AvatarService service() {
        return new AvatarService(databaseServiceClient);
    }

    @Test
    void storesAPngUnderTheTypeItsBytesSay() {
        service().set(USER_ID, PNG);

        ArgumentCaptor<Avatar> stored = ArgumentCaptor.forClass(Avatar.class);
        verify(databaseServiceClient).setAvatar(eq(USER_ID), stored.capture());
        assertThat(stored.getValue().contentType()).isEqualTo("image/png");
        assertThat(stored.getValue().data()).isEqualTo(PNG);
    }

    @Test
    void recognisesAJpeg() {
        service().set(USER_ID, JPEG);

        ArgumentCaptor<Avatar> stored = ArgumentCaptor.forClass(Avatar.class);
        verify(databaseServiceClient).setAvatar(eq(USER_ID), stored.capture());
        assertThat(stored.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void recognisesAWebp() {
        service().set(USER_ID, WEBP);

        ArgumentCaptor<Avatar> stored = ArgumentCaptor.forClass(Avatar.class);
        verify(databaseServiceClient).setAvatar(eq(USER_ID), stored.capture());
        assertThat(stored.getValue().contentType()).isEqualTo("image/webp");
    }

    @Test
    void refusesAnImageFormatItDoesNotServe() {
        assertThatThrownBy(() -> service().set(USER_ID, GIF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG, JPEG or WebP");

        verify(databaseServiceClient, never()).setAvatar(any(), any());
    }

    @Test
    void refusesAnSvgWhateverItClaimsToBe() {
        assertThatThrownBy(() -> service().set(USER_ID, SVG))
                .isInstanceOf(IllegalArgumentException.class);

        verify(databaseServiceClient, never()).setAvatar(any(), any());
    }

    @Test
    void refusesAnEmptyUpload() {
        assertThatThrownBy(() -> service().set(USER_ID, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");

        verify(databaseServiceClient, never()).setAvatar(any(), any());
    }

    @Test
    void refusesAPictureLargerThanTheCap() {
        byte[] oversized = new byte[AvatarService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);

        assertThatThrownBy(() -> service().set(USER_ID, oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512 KB");

        verify(databaseServiceClient, never()).setAvatar(any(), any());
    }

    @Test
    void acceptsAPictureExactlyAtTheCap() {
        byte[] largest = new byte[AvatarService.MAX_BYTES];
        System.arraycopy(PNG, 0, largest, 0, PNG.length);

        service().set(USER_ID, largest);

        verify(databaseServiceClient).setAvatar(eq(USER_ID), any());
    }

    @Test
    void readsThePictureThroughToTheStore() {
        when(databaseServiceClient.findAvatar(USER_ID)).thenReturn(Optional.of(new Avatar("image/png", PNG)));

        assertThat(service().find(USER_ID)).contains(new Avatar("image/png", PNG));
    }

    @Test
    void removesThePictureThroughToTheStore() {
        service().remove(USER_ID);

        verify(databaseServiceClient).deleteAvatar(USER_ID);
    }
}
