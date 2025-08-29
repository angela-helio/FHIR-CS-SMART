package com.example.smartspring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "smart")
public class AppProperties {
  private String fhirBase;
  private String clientId;
  private String clientSecret;
  private String redirectUri = "http://127.0.0.1:8080/callback";
  private String scopes = "launch/patient patient.read openid fhirUser offline_access";
  private String launch; // optional for standalone EHR-like launch
}
