package io.tallyard.webhooks.internal;

import io.tallyard.shared.config.TallyardProperties;
import io.tallyard.shared.error.ConflictException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for webhook signing secrets at rest. The key comes from {@code TALLYARD_ENCRYPTION_KEY}
 * (base64, 32 bytes). Without it, registering endpoints fails loudly rather than storing plaintext.
 */
@Component
class SecretEncryptor {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    SecretEncryptor(TallyardProperties props) {
        String configured = props.encryptionKey();
        if (configured == null || configured.isBlank()) {
            this.key = null;
        } else {
            byte[] raw = Base64.getDecoder().decode(configured.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("TALLYARD_ENCRYPTION_KEY must decode to exactly 32 bytes");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    boolean isConfigured() {
        return key != null;
    }

    String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + ct.length)
                            .put(iv)
                            .put(ct)
                            .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    String decrypt(String ciphertext) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new ConflictException(
                    "webhooks.encryption_key_missing",
                    "Webhooks require TALLYARD_ENCRYPTION_KEY (base64 of 32 random bytes) to protect signing secrets");
        }
    }
}
