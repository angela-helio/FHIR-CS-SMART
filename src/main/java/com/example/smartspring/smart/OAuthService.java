package com.example.smartspring.smart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class OAuthService {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    public TokenResult exchangeAuthorizationCode(String tokenEndpoint, String clientId,
                                                 String code, String codeVerifier, String redirectUri) {
        try {
            String body = form(Map.of(
                    "grant_type","authorization_code",
                    "code",code,
                    "redirect_uri",redirectUri,
                    "client_id",clientId,
                    "code_verifier",codeVerifier
            ));
            HttpRequest req = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                    .header("Content-Type","application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("Token response (redacted): {}",
                    resp.body()
                            .replaceAll("\"access_token\"\\s*:\\s*\"[^\"]+\"", "\"access_token\":\"<redacted>\"")
                            .replaceAll("\"id_token\"\\s*:\\s*\"[^\"]+\"", "\"id_token\":\"<redacted>\"")
                            .replaceAll("\"refresh_token\"\\s*:\\s*\"[^\"]+\"", "\"refresh_token\":\"<redacted>\""));

            if (resp.statusCode() >= 300)
                throw new RuntimeException("Token error: " + resp.statusCode() + " " + resp.body());
            JsonNode n = om.readTree(resp.body());
            return new TokenResult(
                    n.path("access_token").asText(),
                    n.path("id_token").asText(null),
                    n.path("expires_in").asLong(0),
                    n.path("scope").asText(null),
                    n.path("patient").asText(null),     // 👈 muchos EHR lo colocan aquí
                    n.path("encounter").asText(null),
                    resp.body()
            );
        } catch (Exception e) { throw new RuntimeException("Token exchange failed", e); }
    }
    // OAuthService.java
    public Map<String,Object> userinfo(String userinfoEndpoint, String accessToken){
        try{
            if(userinfoEndpoint == null) return Map.of();
            HttpRequest req = HttpRequest.newBuilder(URI.create(userinfoEndpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if(resp.statusCode() >= 300) return Map.of();
            return om.readValue(resp.body(), Map.class);
        }catch(Exception e){ return Map.of(); }
    }
    public String userinfoRaw(String userinfoEndpoint, String accessToken){
        try{
            if (userinfoEndpoint == null) return null;
            HttpRequest req = HttpRequest.newBuilder(URI.create(userinfoEndpoint))
                    .header("Authorization","Bearer "+accessToken).GET().build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() < 300 ? resp.body() : null;
        }catch(Exception e){ return null; }
    }

    private static String form(Map<String, String> map){
        return map.entrySet().stream()
                .map(e -> url(e.getKey())+"="+url(e.getValue()))
                .reduce((a,b)->a+"&"+b).orElse("");
    }
    private static String url(String s){ return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public record TokenResult(String accessToken, String idToken, long expiresIn, String scope,  String patient,     // 👈 nuevo
                              String encounter,  String rawJson ) {}
}
