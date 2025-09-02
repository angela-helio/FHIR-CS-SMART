package com.example.smartspring.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * JWKS (JSON Web Key Set) endpoint for backend service authentication
 * Required by Veradigm for System-type FHIR applications
 */
@RestController
public class JwksController {

    @Value("${smart.jwks.keyId:#{null}}")
    private String configuredKeyId;

    private JWKSet jwkSet;
    private String keyId;

    /**
     * JWKS endpoint - provides public keys for JWT verification
     * @return JSON Web Key Set
     */
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getJwks() {
        if (jwkSet == null) {
            initializeJwkSet();
        }
        return jwkSet.toString();
    }

    /**
     * Get the current key ID for signing JWTs
     * @return Key ID
     */
    public String getKeyId() {
        if (keyId == null) {
            initializeJwkSet();
        }
        return keyId;
    }

    /**
     * Get the private key for signing JWTs
     * Note: In production, store private keys securely (HSM, key vault, etc.)
     * @return RSA private key
     */
    public RSAPrivateKey getPrivateKey() {
        if (jwkSet == null) {
            initializeJwkSet();
        }
        try {
            RSAKey rsaKey = (RSAKey) jwkSet.getKeys().get(0);
            return rsaKey.toRSAPrivateKey();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get private key", e);
        }
    }

    private void initializeJwkSet() {
        try {
            // Generate RSA key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            // Use configured key ID or generate one
            keyId = configuredKeyId != null ? configuredKeyId : UUID.randomUUID().toString();

            // Create RSA JWK
            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                    .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                    .build();

            // Create JWK Set
            jwkSet = new JWKSet(rsaKey);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JWKS", e);
        }
    }
}
