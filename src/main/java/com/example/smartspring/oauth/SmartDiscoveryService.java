package com.example.smartspring.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class SmartDiscoveryService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SmartEndpoints discover(String fhirBase) {
        String base = fhirBase.replaceAll("/+$","");
        // Try .well-known/smart-configuration
        URI wellKnown = URI.create(base + "/.well-known/smart-configuration");
        try {
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(wellKnown).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) {
                JsonNode j = MAPPER.readTree(resp.body());
                String auth = j.path("authorization_endpoint").asText(null);
                String token = j.path("token_endpoint").asText(null);
                if (auth != null && token != null) {
                    return new SmartEndpoints(URI.create(auth), URI.create(token));
                }
            }
        } catch (Exception ignore) {}

        // Fallback to /metadata
        URI meta = URI.create(base + "/metadata");
        try {
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(meta).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("No SMART discovery and /metadata failed: " + resp.statusCode());
            }
            JsonNode root = MAPPER.readTree(resp.body());
            // Find security extension with oauth-uris
            JsonNode rests = root.path("rest");
            if (rests.isArray() && rests.size() > 0) {
                JsonNode sec = rests.get(0).path("security");
                JsonNode exts = sec.path("extension");
                if (exts.isArray()) {
                    for (JsonNode ext : exts) {
                        String url = ext.path("url").asText("");
                        if (url.contains("oauth-uris")) {
                            JsonNode inner = ext.path("extension");
                            String auth = null, token = null;
                            for (JsonNode e : inner) {
                                String k = e.path("url").asText("");
                                String v = e.path("valueUri").asText("");
                                if (k.endsWith("authorize")) auth = v;
                                if (k.endsWith("token")) token = v;
                            }
                            if (auth != null && token != null) {
                                return new SmartEndpoints(URI.create(auth), URI.create(token));
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
