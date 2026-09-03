package com.fantasy.bff.service;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.model.downstream.Avatar;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * The signed-in account's profile picture. The upload is the one place user-supplied bytes enter
 * the system to be served back out again as an image, so this is where they are checked: what the
 * file says it is counts for nothing, only what its first bytes say. Anything that is not a PNG, a
 * JPEG or a WebP is refused — an SVG in particular, which is a document with scripts in it, not a
 * picture. db-service trusts the type it is handed, so the check has to happen here.
 */
@Service
public class AvatarService {

    public static final int MAX_BYTES = 512 * 1024;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private final DatabaseServiceClient databaseServiceClient;

    public AvatarService(DatabaseServiceClient databaseServiceClient) {
        this.databaseServiceClient = databaseServiceClient;
    }

    public Optional<Avatar> find(UUID userId) {
        return databaseServiceClient.findAvatar(userId);
    }

    public void set(UUID userId, byte[] image) {
        if (image.length == 0) {
            throw new IllegalArgumentException("The picture is empty");
        }
        if (image.length > MAX_BYTES) {
            throw new IllegalArgumentException("The picture is larger than 512 KB");
        }
        String contentType = contentTypeOf(image)
                .orElseThrow(() -> new IllegalArgumentException("The picture must be a PNG, JPEG or WebP"));
        databaseServiceClient.setAvatar(userId, new Avatar(contentType, image));
    }

    public void remove(UUID userId) {
        databaseServiceClient.deleteAvatar(userId);
    }

    static Optional<String> contentTypeOf(byte[] image) {
        if (startsWith(image, PNG, 0)) {
            return Optional.of("image/png");
        }
        if (startsWith(image, JPEG, 0)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(image, RIFF, 0) && startsWith(image, WEBP, 8)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] image, byte[] marker, int offset) {
        return image.length >= offset + marker.length
                && Arrays.equals(image, offset, offset + marker.length, marker, 0, marker.length);
    }
}
