package com.example.smartspring.smart;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class Pkce {
    private final String verifier;
    private final String challenge;

    private Pkce(String v, String c){ this.verifier = v; this.challenge = c; }

    public static Pkce generate() {
        try {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String v = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String c = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(md.digest(v.getBytes()));
            return new Pkce(v, c);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    public String verifier(){ return verifier; }
    public String challenge(){ return challenge; }
}
