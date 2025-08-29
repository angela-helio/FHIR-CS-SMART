package com.example.smartspring.smart;

import com.example.smartspring.config.SmartConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class SmartDiscoveryClient {
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public SmartConfig discover(String iss) {
        try {
            String wellKnown = iss.endsWith("/") ? iss + ".well-known/smart-configuration"
                    : iss + "/.well-known/smart-configuration";
            HttpRequest rq = HttpRequest.newBuilder(URI.create(wellKnown)).GET().build();
            HttpResponse<String> rs = http.send(rq, HttpResponse.BodyHandlers.ofString());
            // SmartDiscoveryClient.java (solo cambia el retorno)
            JsonNode n = om.readTree(rs.body());
            return new SmartConfig(
                    n.path("authorization_endpoint").asText(),
                    n.path("token_endpoint").asText(),
                    n.path("userinfo_endpoint").asText(null) // puede no venir
            );

        } catch (Exception e) {
            throw new RuntimeException("SMART discovery failed for iss=" + iss, e);
        }
    }
}

