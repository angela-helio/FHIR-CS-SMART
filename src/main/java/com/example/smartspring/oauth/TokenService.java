package com.example.smartspring.oauth;

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;

@Service
public class TokenService {

  public TokenSet exchangeCode(URI tokenEndpoint, String clientId, String redirectUri,
                               AuthorizationCode code, CodeVerifier verifier) {
    try {
      AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, new URI(redirectUri), verifier);

      // Client Public (PKCE): use ClientID
      TokenRequest req = new TokenRequest(tokenEndpoint, new ClientID(clientId), codeGrant);

      HTTPResponse httpResp = req.toHTTPRequest().send();
      TokenResponse tr = TokenResponse.parse(httpResp);
      if (!tr.indicatesSuccess()) {
        throw new RuntimeException("Token error: " + tr.toErrorResponse().getErrorObject());
      }

      AccessTokenResponse ok = tr.toSuccessResponse();
      AccessToken at = ok.getTokens().getAccessToken();
      RefreshToken rt = ok.getTokens().getRefreshToken();

      long expSec = at.getLifetime();
      long expEpoch = expSec > 0 ? (System.currentTimeMillis() / 1000) + expSec : 0;

      // SMART v2: the AS can return "patient" in the token response
      String patientId = null;
      Map<String, Object> extras = ok.getCustomParameters();
      if (extras != null && extras.get("patient") != null) {
        patientId = String.valueOf(extras.get("patient"));
      }

      return new TokenSet(at.getValue(), rt == null ? null : rt.getValue(), expEpoch, at.getType().getValue(), patientId);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public TokenSet refresh(URI tokenEndpoint, String clientId, String refreshToken) {
    try {
      RefreshTokenGrant grant = new RefreshTokenGrant(new RefreshToken(refreshToken));
      TokenRequest req = new TokenRequest(tokenEndpoint, new ClientID(clientId), grant);

      HTTPResponse httpResp = req.toHTTPRequest().send();
      TokenResponse tr = TokenResponse.parse(httpResp);
      if (!tr.indicatesSuccess()) {
        throw new RuntimeException("Refresh error: " + tr.toErrorResponse().getErrorObject());
      }

      AccessTokenResponse ok = tr.toSuccessResponse();
      AccessToken at = ok.getTokens().getAccessToken();
      RefreshToken rt = ok.getTokens().getRefreshToken();

      long expSec = at.getLifetime();
      long expEpoch = expSec > 0 ? (System.currentTimeMillis() / 1000) + expSec : 0;

      // The refresh typically doesn't return the new patient; maintain the one stored in session.
      return new TokenSet(at.getValue(), rt == null ? refreshToken : rt.getValue(), expEpoch, at.getType().getValue(), null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static record TokenSet(String accessToken, String refreshToken, long expiresEpochSeconds,
                                String tokenType, String patientId) {}
}
