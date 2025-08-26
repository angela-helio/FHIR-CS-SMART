package com.example.smartspring.oauth;

import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class PkceUtil {
    private static final SecureRandom RNG = new SecureRandom();
    private PkceUtil(){}

    public static String generateCodeVerifier() {
        byte[] code = new byte[64];
        RNG.nextBytes(code);
        return base64Url(code);
    }

    public static String codeChallengeS256(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return base64Url(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String base64Url(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }
}
