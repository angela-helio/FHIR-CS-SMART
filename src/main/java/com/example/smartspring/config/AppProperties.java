package com.example.smartspring.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component
@ConfigurationProperties(prefix="smart")
public class AppProperties {
  private String fhirBase;
  private String clientId;
  private String redirectUri="http://127.0.0.1:8080/callback";
  private String scopes="launch/patient patient.read openid fhirUser offline_access";
  private String launch; // optional for standalone EHR-like launch
  public String getFhirBase(){return fhirBase;} public void setFhirBase(String v){this.fhirBase=v;}
  public String getClientId(){return clientId;} public void setClientId(String v){this.clientId=v;}
  public String getRedirectUri(){return redirectUri;} public void setRedirectUri(String v){this.redirectUri=v;}
  public String getScopes(){return scopes;} public void setScopes(String v){this.scopes=v;}
  public String getLaunch(){return launch;} public void setLaunch(String v){this.launch=v;}
}
