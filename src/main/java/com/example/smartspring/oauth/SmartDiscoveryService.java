package com.example.smartspring.oauth;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import javax.net.ssl.*;
import java.net.*;
import java.net.http.*;
import java.security.cert.X509Certificate;

@Service
public class SmartDiscoveryService {
    private static final ObjectMapper M = new ObjectMapper();
    private static HttpClient httpClient;

    static {
        try {
            // Create a trust manager that accepts all certificates (for development only)
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();
        } catch (Exception e) {
            // Fallback to default client
            httpClient = HttpClient.newHttpClient();
        }
    }

    public SmartEndpoints discover(String fhirBase) {
        String base = fhirBase.replaceAll("/+$", "");
        
        System.out.printf("=== SMART DISCOVERY DEBUG ===%n");
        System.out.printf("FHIR Base: %s%n", fhirBase);
        
        try {
            HttpResponse<String> r = httpClient.send(
                HttpRequest.newBuilder(URI.create(base + "/.well-known/smart-configuration"))
                    .GET()
                    .build(), 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (r.statusCode() / 100 == 2) {
                JsonNode j = M.readTree(r.body());
                String a = j.path("authorization_endpoint").asText(null);
                String t = j.path("token_endpoint").asText(null);
                if (a != null && t != null) {
                    System.out.printf("Found via .well-known/smart-configuration:%n");
                    System.out.printf("  Authorization: %s%n", a);
                    System.out.printf("  Token: %s%n", t);
                    return new SmartEndpoints(URI.create(a), URI.create(t));
                }
            }
        } catch (Exception ignore) {
            // Fall through to metadata endpoint
        }
        
        try {
            HttpResponse<String> r = httpClient.send(
                HttpRequest.newBuilder(URI.create(base + "/metadata"))
                    .GET()
                    .build(), 
                HttpResponse.BodyHandlers.ofString()
            );
            
            if (r.statusCode() / 100 != 2) 
                throw new IllegalStateException("No SMART discovery and /metadata failed: " + r.statusCode());
            
            JsonNode root = M.readTree(r.body());
            JsonNode rests = root.path("rest");
            if (rests.isArray() && rests.size() > 0) {
                JsonNode exts = rests.get(0).path("security").path("extension");
                if (exts.isArray()) {
                    for (JsonNode ext : exts) {
                        if (ext.path("url").asText("").contains("oauth-uris")) {
                            String auth = null, tok = null;
                            for (JsonNode e : ext.path("extension")) {
                                String k = e.path("url").asText("");
                                String v = e.path("valueUri").asText("");
                                if (k.endsWith("authorize")) auth = v;
                                if (k.endsWith("token")) tok = v;
                            }
                            if (auth != null && tok != null) {
                                System.out.printf("Found via /metadata:%n");
                                System.out.printf("  Authorization: %s%n", auth);
                                System.out.printf("  Token: %s%n", tok);
                                return new SmartEndpoints(URI.create(auth), URI.create(tok));
                            }
                        }
                    }
                }
            }
            throw new IllegalStateException("SMART endpoints not found in CapabilityStatement");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static record SmartEndpoints(URI authorizationEndpoint, URI tokenEndpoint) {}
}
