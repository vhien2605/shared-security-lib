package vdt.mini.management_service.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import vdt.mini.management_service.config.SecurityCredentialsProperties;
import vdt.mini.management_service.exception.AppException;
import vdt.mini.management_service.util.enums.ErrorCode;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretCipherService {
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String ENVELOPE_VERSION = "v1";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecurityCredentialsProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public SecretCipherService(SecurityCredentialsProperties properties) {
        this(properties, new SecureRandom());
    }

    SecretCipherService(SecurityCredentialsProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Plaintext secret is required");
        }
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ENVELOPE_VERSION + ":" + encode(iv) + ":" + encode(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt secret", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Secret ciphertext is required");
        }
        String[] parts = ciphertext.split(":", -1);
        if (parts.length != 3 || !ENVELOPE_VERSION.equals(parts[0]) || parts[1].isBlank() || parts[2].isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Malformed secret ciphertext");
        }
        try {
            byte[] iv = decode(parts[1]);
            if (iv.length != GCM_IV_BYTES) {
                throw new AppException(ErrorCode.INVALID_INPUT, "Malformed secret ciphertext");
            }
            byte[] encrypted = decode(parts[2]);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Secret ciphertext cannot be decrypted");
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Malformed secret ciphertext");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt secret", ex);
        }
    }

    private SecretKeySpec secretKey() {
        return new SecretKeySpec(properties.requireMasterSecretBytes(), KEY_ALGORITHM);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
