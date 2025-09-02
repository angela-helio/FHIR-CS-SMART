package com.example.smartspring.oauth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Backend Service Authentication for System-type FHIR applications
 * Implements client_credentials grant with JWT assertion (RFC 7523)
 */
@Service
public class BackendServiceAuth {

    /**
     * Generate JWT assertion for backend service authentication
     * @param tokenUrl The token endpoint URL
     * @param clientId The client ID
     * @param privateKey RSA private key for signing
     * @param keyId Key ID from JWKS
     * @return Signed JWT assertion
     */
    public String generateJwtAssertion(String tokenUrl, String clientId, RSAPrivateKey privateKey, String keyId) {
        try {
            // Create JWT header
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .build();

            // Create JWT claims
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(clientId)
                    .subject(clientId)
                    .audience(tokenUrl)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300))) // 5 minutes
                    .build();

            // Sign the JWT
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(privateKey));

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT assertion", e);
        }
    }

    /**
     * Obtain access token using client_credentials grant with JWT assertion
     * @param tokenUrl Token endpoint URL
     * @param clientId Client ID
     * @param jwtAssertion Signed JWT assertion
     * @param scope Requested scopes (e.g., "system/*.read")
     * @return Access token response
     */
    public BackendTokenResponse getAccessToken(String tokenUrl, String clientId, String jwtAssertion, String scope) {
        try {
            String requestBody = String.format(
                    "grant_type=client_credentials&" +
                    "client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&" +
                    "client_assertion=%s&" +
                    "scope=%s",
                    jwtAssertion, scope
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Token request failed: " + response.statusCode() + " " + response.body());
            }

            // Parse JSON response (simplified - in production use proper JSON parser)
            String responseBody = response.body();
            String accessToken = extractJsonValue(responseBody, "access_token");
            String tokenType = extractJsonValue(responseBody, "token_type");
            int expiresIn = Integer.parseInt(extractJsonValue(responseBody, "expires_in"));

            return new BackendTokenResponse(accessToken, tokenType, expiresIn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain access token", e);
        }
    }

    private String extractJsonValue(String json, String key) {
        // Simple JSON extraction - in production use proper JSON parser
        String pattern = "\"" + key + "\"\\s*:\\s*\"?([^,}\"]+)\"?";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        throw new RuntimeException("Key not found in JSON: " + key);
    }

    public record BackendTokenResponse(String accessToken, String tokenType, int expiresIn) {}
}
