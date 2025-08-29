package com.example.smartspring.oauth;

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
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

  public TokenSet exchangeCode(URI tokenEndpoint, String clientId,
                                   boolean confidential, String clientSecret,
                                   String code, String redirectUri, String codeVerifier) {
    try {
      AuthorizationCode grantCode = new AuthorizationCode(code);
      AuthorizationCodeGrant grant = new AuthorizationCodeGrant(grantCode, new URI(redirectUri), new CodeVerifier(codeVerifier));

      ClientID cid = new ClientID(clientId);
      ClientAuthentication clientAuth = null;
      if (confidential) {
        clientAuth = new ClientSecretBasic(cid, new Secret(clientSecret));
      }

      TokenRequest req = new TokenRequest(tokenEndpoint, clientAuth, grant);
      HTTPResponse httpResp = req.toHTTPRequest().send();
      TokenResponse tr = TokenResponse.parse(httpResp);

      if (!tr.indicatesSuccess()) {
        throw new RuntimeException("Token error: " + tr.toErrorResponse().getErrorObject());
      }
      AccessToken at = tr.toSuccessResponse().getTokens().getAccessToken();
      RefreshToken rt = tr.toSuccessResponse().getTokens().getRefreshToken();
      long expEpoch = (System.currentTimeMillis()/1000) + Math.max(0, at.getLifetime());

      // Si el servidor devuelve 'patient' en la respuesta:
      String patientId = null;
      if (tr.toSuccessResponse().getCustomParameters() != null) {
        var p = tr.toSuccessResponse().getCustomParameters().get("patient");
        if (p != null) patientId = p.toString();
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
