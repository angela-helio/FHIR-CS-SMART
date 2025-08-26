package com.example.smartspring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "smart")
public class AppProperties {
    /**
     * Base URL of the FHIR server (e.g. https://launch.smarthealthit.org/v/r4/sim/.../fhir)
     */
    private String fhirBase;
    /**
     * Registered SMART public client ID.
     */
    private String clientId;
    /**
     * Redirect URI configured in the SMART client registration (default http://127.0.0.1:8080/callback).
     */
    private String redirectUri = "http://127.0.0.1:8080/callback";
    /**
     * Space-separated scopes, e.g. "launch/patient patient.read openid fhirUser offline_access".
     */
    private String scopes = "launch/patient patient.read openid fhirUser";
    /**
     * Optional SMART 'launch' context (for EHR-launch). Leave blank for standalone.
     */
    private String launch;

    public String getFhirBase() { return fhirBase; }
    public void setFhirBase(String fhirBase) { this.fhirBase = fhirBase; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }

    public String getLaunch() { return launch; }
    public void setLaunch(String launch) { this.launch = launch; }
}
