package com.example.smartspring.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component
@ConfigurationProperties(prefix="smart")
public class AppProperties {
  private String fhirBase;
  private String clientId;
  private String redirectUri="https://41d2adcbd63f.ngrok-free.app/callback";
  private String scopes="launch/patient openid fhirUser patient/Patient.read patient/MedicationRequest.read";
  private String launch; // optional for standalone EHR-like launch
  private boolean confidential = false;
  private String clientSecret = "";
  public boolean isConfidential(){ return confidential; }
  public void setConfidential(boolean v){ this.confidential = v; }
  public String getClientSecret(){ return clientSecret; }
  public void setClientSecret(String v){ this.clientSecret = v; }
  public String getFhirBase(){return fhirBase;} public void setFhirBase(String v){this.fhirBase=v;}
  public String getClientId(){return clientId;} public void setClientId(String v){this.clientId=v;}
  public String getRedirectUri(){return redirectUri;} public void setRedirectUri(String v){this.redirectUri=v;}
  public String getScopes(){return scopes;} public void setScopes(String v){this.scopes=v;}
  public String getLaunch(){return launch;} public void setLaunch(String v){this.launch=v;}
}
