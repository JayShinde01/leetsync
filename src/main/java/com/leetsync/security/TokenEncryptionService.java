package com.leetsync.security;

import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for encrypting and decrypting sensitive tokens (GitHub OAuth access tokens).
 * 
 * Uses AES encryption in ECB mode (simple, single-block encryption).
 * For production, consider CBC mode with IV or other robust encryption schemes.
 * 
 * CRITICAL SECURITY NOTE:
 * - Never log encrypted or plaintext tokens
 * - Never expose tokens in API responses
 * - Store encryption key securely in environment variables
 * - Never hardcode encryption keys
 */
@Slf4j
@Service
public class TokenEncryptionService {
    
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    
    private final String encryptionKey;
    
    public TokenEncryptionService(@Value("${encryption.key}") String encryptionKey) {
        this.encryptionKey = encryptionKey;
        
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.error("CRITICAL: Encryption key is not configured. Set LEETSYNC_ENCRYPTION_KEY environment variable.");
            throw new IllegalArgumentException("Encryption key must be configured");
        }
        
        if (encryptionKey.length() < 16) {
            log.error("Encryption key is too short. Minimum 16 characters recommended.");
            throw new IllegalArgumentException("Encryption key must be at least 16 characters");
        }
    }
    
    /**
     * Encrypts a plaintext token (GitHub OAuth access token).
     * 
     * @param plainToken The plaintext token to encrypt
     * @return Base64-encoded encrypted token
     * @throws RuntimeException if encryption fails
     */
    public String encrypt(String plainToken) {
        try {
            SecretKey key = generateKey();
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            
            byte[] encryptedBytes = cipher.doFinal(plainToken.getBytes());
            String encrypted = Base64.getEncoder().encodeToString(encryptedBytes);
            
            log.debug("Token encrypted successfully");
            return encrypted;
            
        } catch (Exception e) {
            log.error("Failed to encrypt token", e);
            throw new RuntimeException("Token encryption failed", e);
        }
    }
    
    /**
     * Decrypts an encrypted token (GitHub OAuth access token).
     * 
     * @param encryptedToken Base64-encoded encrypted token
     * @return Plaintext token
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String encryptedToken) {
        try {
            SecretKey key = generateKey();
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedToken);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            String decrypted = new String(decryptedBytes);
            
            log.debug("Token decrypted successfully");
            return decrypted;
            
        } catch (Exception e) {
            log.error("Failed to decrypt token", e);
            throw new RuntimeException("Token decryption failed", e);
        }
    }
    
    /**
     * Generates a SecretKey from the configured encryption key.
     * Pads or truncates the key to match the required size.
     */
    private SecretKey generateKey() throws Exception {
        byte[] decodedKey = new byte[KEY_SIZE / 8]; // 32 bytes for 256-bit AES
        byte[] keyBytes = encryptionKey.getBytes();
        
        // Copy or pad the key
        for (int i = 0; i < decodedKey.length; i++) {
            if (i < keyBytes.length) {
                decodedKey[i] = keyBytes[i];
            } else {
                decodedKey[i] = 0;
            }
        }
        
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, ENCRYPTION_ALGORITHM);
    }
}
