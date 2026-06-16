package vdt.mini.management_service.service;

import org.junit.jupiter.api.Test;
import vdt.mini.management_service.config.SecurityCredentialsProperties;
import vdt.mini.management_service.exception.AppException;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCipherServiceTest {
    @Test
    void encryptAndDecrypt_shouldRoundTrip() {
        SecretCipherService service = new SecretCipherService(properties(1), new SecureRandom());

        String ciphertext = service.encrypt("hs_test-secret");

        assertTrue(ciphertext.startsWith("v1:"));
        assertEquals("hs_test-secret", service.decrypt(ciphertext));
    }

    @Test
    void encrypt_shouldUseRandomIv() {
        SecretCipherService service = new SecretCipherService(properties(1), new SecureRandom());

        String first = service.encrypt("hs_test-secret");
        String second = service.encrypt("hs_test-secret");

        assertNotEquals(first, second);
        assertEquals("hs_test-secret", service.decrypt(first));
        assertEquals("hs_test-secret", service.decrypt(second));
    }

    @Test
    void decrypt_shouldRejectMalformedEnvelope() {
        SecretCipherService service = new SecretCipherService(properties(1), new SecureRandom());

        assertThrows(AppException.class, () -> service.decrypt("not-an-envelope"));
    }

    @Test
    void decrypt_shouldRejectWrongMasterSecret() {
        SecretCipherService encryptingService = new SecretCipherService(properties(1), new SecureRandom());
        SecretCipherService decryptingService = new SecretCipherService(properties(2), new SecureRandom());
        String ciphertext = encryptingService.encrypt("hs_test-secret");

        assertThrows(AppException.class, () -> decryptingService.decrypt(ciphertext));
    }

    @Test
    void encrypt_shouldRejectMissingMasterSecret() {
        SecurityCredentialsProperties properties = new SecurityCredentialsProperties();
        SecretCipherService service = new SecretCipherService(properties, new SecureRandom());

        assertThrows(IllegalStateException.class, () -> service.encrypt("hs_test-secret"));
    }

    @Test
    void requireMasterSecretBytes_shouldDecodeValid32ByteBase64() {
        SecurityCredentialsProperties properties = properties(1);

        byte[] decoded = properties.requireMasterSecretBytes();

        assertEquals(32, decoded.length);
    }

    @Test
    void requireMasterSecretBytes_shouldRejectMalformedBase64() {
        SecurityCredentialsProperties properties = new SecurityCredentialsProperties();
        properties.setMasterSecret("not-valid-base64!");

        IllegalStateException exception = assertThrows(IllegalStateException.class, properties::requireMasterSecretBytes);

        assertEquals("security.credentials.master-secret must be base64 encoded", exception.getMessage());
    }

    @Test
    void requireMasterSecretBytes_shouldRejectNon32ByteSecret() {
        SecurityCredentialsProperties properties = new SecurityCredentialsProperties();
        properties.setMasterSecret(Base64.getEncoder().encodeToString(new byte[16]));

        IllegalStateException exception = assertThrows(IllegalStateException.class, properties::requireMasterSecretBytes);

        assertEquals("security.credentials.master-secret must decode to 32 bytes", exception.getMessage());
    }

    private SecurityCredentialsProperties properties(int seed) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (seed + index);
        }
        SecurityCredentialsProperties properties = new SecurityCredentialsProperties();
        properties.setMasterSecret(Base64.getEncoder().encodeToString(key));
        return properties;
    }
}
