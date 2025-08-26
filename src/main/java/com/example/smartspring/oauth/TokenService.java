package com.example.smartspring.oauth;

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

@Service
public class TokenService {

    public TokenSet exchangeCode(URI tokenEndpoint, String clientId, String redirectUri,
                                 AuthorizationCode code, CodeVerifier verifier) {
        try {
            AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, new URI(redirectUri), null);
            ClientAuthentication clientAuth = null; // public client
            TokenRequest req = new TokenRequest(tokenEndpoint, clientAuth, codeGrant, null, null, null, verifier);

            HttpRequest httpReq = HttpRequest.newBuilder(req.toHTTPRequest().getURI())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(req.toHTTPRequest().getQuery()))
                    .build();

            HttpResponse<String> resp = HttpClient.newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofString());
            TokenResponse tr = TokenResponse.parse(resp.body());
            if (!tr.indicatesSuccess()) {
                throw new RuntimeException("Token error: " + tr.toErrorResponse().getErrorObject());
            }
            AccessTokenResponse ok = tr.toSuccessResponse();
            AccessToken at = ok.getTokens().getAccessToken();
            RefreshToken rt = ok.getTokens().getRefreshToken();
            long expiresIn = at.getLifetime(); // seconds, may be 0 if unknown
            long expEpoch = expiresIn > 0 ? Instant.now().getEpochSecond() + expiresIn : 0;
            return new TokenSet(at.getValue(), rt == null ? null : rt.getValue(), expEpoch, "Bearer");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public TokenSet refresh(URI tokenEndpoint, String clientId, String refreshToken) {
        try {
            RefreshTokenGrant grant = new RefreshTokenGrant(new RefreshToken(refreshToken));
            TokenRequest req = new TokenRequest(tokenEndpoint, null, grant);

            HttpRequest httpReq = HttpRequest.newBuilder(req.toHTTPRequest().getURI())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(req.toHTTPRequest().getQuery()))
                    .build();

            HttpResponse<String> resp = HttpClient.newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofString());
            TokenResponse tr = TokenResponse.parse(resp.body());
            if (!tr.indicatesSuccess()) {
                throw new RuntimeException("Refresh error: " + tr.toErrorResponse().getErrorObject());
            }
            AccessTokenResponse ok = tr.toSuccessResponse();
            AccessToken at = ok.getTokens().getAccessToken();
            RefreshToken rt = ok.getTokens().getRefreshToken();
            long expiresIn = at.getLifetime();
            long expEpoch = expiresIn > 0 ? (System.currentTimeMillis()/1000) + expiresIn : 0;
            return new TokenSet(at.getValue(), rt == null ? refreshToken : rt.getValue(), expEpoch, "Bearer");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static record TokenSet(String accessToken, String refreshToken, long expiresEpochSeconds, String tokenType) {}
}
