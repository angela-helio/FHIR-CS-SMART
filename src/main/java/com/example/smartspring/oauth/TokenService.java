package com.example.smartspring.oauth;
import com.nimbusds.oauth2.sdk.*; import com.nimbusds.oauth2.sdk.auth.ClientAuthentication; import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.*; import org.springframework.stereotype.Service;
import java.net.*; import java.net.http.*;
@Service
public class TokenService {
  public TokenSet exchangeCode(URI tokenEndpoint, String clientId, String redirectUri, AuthorizationCode code, CodeVerifier verifier){
    try{
      AuthorizationGrant grant=new AuthorizationCodeGrant(code, new URI(redirectUri), null);
      TokenRequest req=new TokenRequest(tokenEndpoint, (ClientAuthentication)null, grant, null, null, null, verifier);
      HttpRequest httpReq=HttpRequest.newBuilder(req.toHTTPRequest().getURI())
        .header("Content-Type","application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(req.toHTTPRequest().getQuery())).build();
      HttpResponse<String> resp=HttpClient.newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofString());
      TokenResponse tr=TokenResponse.parse(resp.body()); if(!tr.indicatesSuccess()) throw new RuntimeException("Token error: "+tr.toErrorResponse().getErrorObject());
      AccessTokenResponse ok=tr.toSuccessResponse(); AccessToken at=ok.getTokens().getAccessToken(); RefreshToken rt=ok.getTokens().getRefreshToken();
      long exp=at.getLifetime(); long epoch=exp>0?(System.currentTimeMillis()/1000)+exp:0;
      return new TokenSet(at.getValue(), rt==null?null:rt.getValue(), epoch, at.getType().getValue());
    }catch(Exception e){ throw new RuntimeException(e); }
  }
  public TokenSet refresh(URI tokenEndpoint, String clientId, String refreshToken){
    try{
      RefreshTokenGrant grant=new RefreshTokenGrant(new RefreshToken(refreshToken));
      TokenRequest req=new TokenRequest(tokenEndpoint, null, grant);
      HttpRequest httpReq=HttpRequest.newBuilder(req.toHTTPRequest().getURI())
        .header("Content-Type","application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(req.toHTTPRequest().getQuery())).build();
      HttpResponse<String> resp=HttpClient.newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofString());
      TokenResponse tr=TokenResponse.parse(resp.body()); if(!tr.indicatesSuccess()) throw new RuntimeException("Refresh error: "+tr.toErrorResponse().getErrorObject());
      AccessTokenResponse ok=tr.toSuccessResponse(); AccessToken at=ok.getTokens().getAccessToken(); RefreshToken rt=ok.getTokens().getRefreshToken();
      long exp=at.getLifetime(); long epoch=exp>0?(System.currentTimeMillis()/1000)+exp:0;
      return new TokenSet(at.getValue(), rt==null?refreshToken:rt.getValue(), epoch, at.getType().getValue());
    }catch(Exception e){ throw new RuntimeException(e); }
  }
  public static record TokenSet(String accessToken, String refreshToken, long expiresEpochSeconds, String tokenType){}
}
